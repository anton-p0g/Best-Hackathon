"""
FastAPI REST layer for the Brook Onboarding Platform.
Wraps the OnboardingService so any client (PyCharm plugin, Streamlit, etc.)
can communicate over HTTP instead of importing Python directly.
"""
import json
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from dotenv import load_dotenv

from src.services.onboarding import OnboardingService
from src.domain.models import Speciality

load_dotenv()

app = FastAPI(title="Brook API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── Request / Response DTOs ──────────────────────────────────────────
class InjectRequest(BaseModel):
    repo_path: str = "target_repo"
    speciality: str = "Auth"


class HintRequest(BaseModel):
    repo_path: str = "target_repo"
    speciality: str = "Auth"
    message: str = ""


class VerifyRequest(BaseModel):
    repo_path: str = "target_repo"
    speciality: str = "Auth"


# ── Service factory (one per repo_path) ──────────────────────────────
_services: dict[str, OnboardingService] = {}


def _get_service(repo_path: str = "target_repo") -> OnboardingService:
    if repo_path not in _services:
        _services[repo_path] = OnboardingService(repo_path=repo_path)
    return _services[repo_path]


# ── Routes ───────────────────────────────────────────────────────────
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
            req.message, speciality=speciality, is_hint_trigger=True
        )
        for chunk in stream:
            # SSE format: each event is "data: <payload>\n\n"
            yield f"data: {json.dumps({'chunk': chunk})}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/verify")
def verify(req: VerifyRequest):
    """Grade the student's current code and return a JSON verdict."""
    service = _get_service(req.repo_path)
    speciality = Speciality(speciality=req.speciality)
    result = service.verify_solution(speciality=speciality)
    return result


@app.get("/health")
def health():
    return {"status": "ok"}
