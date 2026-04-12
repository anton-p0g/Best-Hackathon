"""
FastAPI REST layer for the Brook Onboarding Platform.
Wraps the OnboardingService so any client (PyCharm plugin, Streamlit, etc.)
can communicate over HTTP instead of importing Python directly.
"""
import json
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, HTMLResponse
from pydantic import BaseModel
from dotenv import load_dotenv

from src.services.onboarding import OnboardingService
from src.domain.models import Speciality

from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, PlainTextResponse
from fastapi import Request
import os
import shutil
import subprocess
import time

from exercise_inference.runner import run, generate_tree

load_dotenv()

app = FastAPI(title="Brook API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    body = await request.body()
    print(f"Validation Error! Raw Body: {body.decode('utf-8')}")
    print(f"Details: {exc.errors()}")
    return JSONResponse(
        status_code=422,
        content={"detail": exc.errors(), "body": body.decode("utf-8")},
    )

class InjectRequest(BaseModel):
    repo_path: str = "target_repo"
    speciality: str = "Auth"


class HintRequest(BaseModel):
    repo_path: str = "target_repo"
    speciality: str = "Auth"
    exercise_id: str = ""
    message: str = ""
    active_file: str = "" 


class ChatRequest(BaseModel):
    repo_path: str = "target_repo"
    speciality: str = "Auth"
    exercise_id: str = ""
    message: str
    active_file: str = ""


class VerifyRequest(BaseModel):
    repo_path: str = "target_repo"
    speciality: str = "Auth"
    exercise_id: str = ""
    active_file: str = ""


class GenerateExerciseRequest(BaseModel):
    repo_path: str = "target_repo"
    speciality: str = "Auth"


class CloneRepoRequest(BaseModel):
    repo_url: str


_services: dict[str, OnboardingService] = {}


def _get_service(repo_path: str = "target_repo") -> OnboardingService:
    if repo_path not in _services:
        _services[repo_path] = OnboardingService(repo_path=repo_path)
    return _services[repo_path]


@app.post("/inject")
def inject(req: InjectRequest):
    """Initialise the broken repo and return the directory tree."""
    service = _get_service(req.repo_path)
    speciality = Speciality(speciality=req.speciality)
    state = service.tracker.get_state(speciality)
    return {
        "status": "ok",
        "directory_tree": state.directory_tree,
        "git_diff": state.git_diff,
    }


@app.post("/hint")
def hint(req: HintRequest):
    """Stream a Socratic hint back to the client using SSE."""
    service = _get_service(req.repo_path)
    speciality = Speciality(speciality=req.speciality)

    def event_stream():
        stream = service.process_message(
            req.message, speciality=speciality, exercise_id=req.exercise_id, is_hint_trigger=True, active_file=req.active_file
        )
        for chunk in stream:
            yield f"data: {json.dumps({'chunk': chunk})}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/chat")
def chat(req: ChatRequest):
    """Stream a free-form tutoring response back to the client using SSE."""
    service = _get_service(req.repo_path)
    speciality = Speciality(speciality=req.speciality)

    def event_stream():
        stream = service.process_message(
            req.message, speciality=speciality, exercise_id=req.exercise_id, is_hint_trigger=False, active_file=req.active_file
        )
        for chunk in stream:
            yield f"data: {json.dumps({'chunk': chunk})}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/verify")
def verify(req: VerifyRequest):
    """Grade the student's current code and return a JSON verdict."""
    service = _get_service(req.repo_path)
    speciality = Speciality(speciality=req.speciality)
    result = service.verify_solution(speciality=speciality, exercise_id=req.exercise_id, active_file=req.active_file)
    return result


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/exercises")
def list_exercises():
    """List available exercises in the 'exercises' directory."""
    exercises_dir = "exercises"
    if not os.path.isdir(exercises_dir):
        return {"exercises": []}
    
    result = []
    for item in sorted(os.listdir(exercises_dir)):
        item_path = os.path.join(exercises_dir, item)
        if os.path.isdir(item_path):
            # Fallback name based on directory name
            name = item.replace("-", " ").replace("_", " ").title()
            import re
            name = re.sub(r'(\d+)', r' \1', name).strip()
            
            # Try to extract the title from EXERCISE.html
            html_path = os.path.join(item_path, "EXERCISE.html")
            if os.path.isfile(html_path):
                try:
                    with open(html_path, "r", encoding="utf-8") as f:
                        content = f.read()
                        match = re.search(r'<title>(.*?)</title>', content, re.IGNORECASE | re.DOTALL)
                        if match:
                            html_title = match.group(1).strip()
                            # Optional: remove the "Debugging Exercise -" prefix if present
                            if html_title.startswith("Debugging Exercise -"):
                                html_title = html_title.replace("Debugging Exercise -", "", 1).strip()
                            if html_title:
                                name = html_title
                except Exception:
                    pass

            result.append({"id": item, "name": name})
    return {"exercises": result}


@app.get("/exercises/{exercise_id}/{file_name}")
def get_exercise_file(exercise_id: str, file_name: str):
    """Return the raw file content of an exercise."""
    # Prevent directory traversal
    if ".." in exercise_id or ".." in file_name or "/" in file_name:
        raise HTTPException(status_code=400, detail="Invalid path")
    
    file_path = os.path.join("exercises", exercise_id, file_name)
    if not os.path.isfile(file_path):
        return HTMLResponse(f"<p>Could not find {file_name}</p>", status_code=404)
        
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    return HTMLResponse(content)


@app.post("/generate-exercise")
def generate_exercise(req: GenerateExerciseRequest):
    """Dynamically generate a new debugging exercise using the LangGraph agent."""
    try:
        tree = generate_tree(req.repo_path)
        result = run(tree, speciality=req.speciality)
        return {
            "status": "ok",
            "exercise_id": result["exercise_id"],
            "patched_file": result["patched_file"],
        }
    except Exception as e:
        import traceback
        traceback.print_exc()
        return JSONResponse(
            status_code=500,
            content={"status": "error", "detail": str(e)},
        )


@app.post("/clone-repo")
def clone_repo(req: CloneRepoRequest):
    target = os.path.abspath("target_repo")
    temp_clone = os.path.join(target, "_clone_tmp")

    def force_remove_readonly(func, path, _exc_info):
        import stat
        os.chmod(path, stat.S_IWRITE)
        func(path)

    try:
        if os.path.isdir(temp_clone):
            shutil.rmtree(temp_clone, onexc=force_remove_readonly)

        if os.path.isdir(target):
            for entry in os.listdir(target):
                entry_path = os.path.join(target, entry)
                if os.path.isdir(entry_path):
                    shutil.rmtree(entry_path, onexc=force_remove_readonly)
                else:
                    try:
                        os.remove(entry_path)
                    except PermissionError:
                        import stat
                        os.chmod(entry_path, stat.S_IWRITE)
                        os.remove(entry_path)
        else:
            os.makedirs(target, exist_ok=True)
            time.sleep(0.2)

        result = subprocess.run(
            ["git", "clone", "--depth", "1", req.repo_url, temp_clone],
            capture_output=True, text=True
        )
        if result.returncode != 0:
            return JSONResponse(
                status_code=400,
                content={
                    "status": "error", 
                    "detail": f"git clone failed: {result.stderr}",  # sin .strip() para ver todo
                    "stdout": result.stdout,
                    "returncode": result.returncode
                },
            )

        for entry in os.listdir(temp_clone):
            shutil.move(os.path.join(temp_clone, entry), os.path.join(target, entry))

        if os.path.isdir(temp_clone):
            shutil.rmtree(temp_clone, onexc=force_remove_readonly)

        git_dir = os.path.join(target, ".git")
        if os.path.isdir(git_dir):
            shutil.rmtree(git_dir, onexc=force_remove_readonly)

        return {"status": "ok", "message": f"Repository cloned into {target}"}

    except Exception as e:
        import traceback
        traceback.print_exc()
        if os.path.isdir(temp_clone):
            shutil.rmtree(temp_clone, onexc=force_remove_readonly)
        return JSONResponse(
            status_code=500,
            content={"status": "error", "detail": str(e)},
        )