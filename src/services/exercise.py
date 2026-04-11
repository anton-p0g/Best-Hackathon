import os
import subprocess
from pathlib import Path


def setup_broken_repo(base_dir: str = "target_repo"):
    """
    Creates a dummy local repository with a pristine codebase in main,
    and a broken codebase in `exercise/auth-broken` so the student can fix it.
    """
    repo_path = Path(base_dir)
    
    if (repo_path / ".git").exists():
        print(f"Repository {base_dir} already exists. Skipping initialization.")
        return

    repo_path.mkdir(parents=True, exist_ok=True)

    pristine_main = '''from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel

    app = FastAPI()
    USERS = {"admin": "secret123"}

    class LoginRequest(BaseModel):
        username: str
        password: str

    @app.post("/login")
    def login(request: LoginRequest):
        if USERS.get(request.username) == request.password:
            return {"token": "fake-jwt-token"}
        raise HTTPException(status_code=401, detail="Invalid credentials")
    ''' 
    (repo_path / "main.py").write_text(pristine_main)
    (repo_path / "requirements.txt").write_text("fastapi\npydantic\nuvicorn\n")

    subprocess.run(["git", "init"], cwd=repo_path, check=True, capture_output=True)
    subprocess.run(["git", "add", "."], cwd=repo_path, check=True, capture_output=True)
    subprocess.run(["git", "commit", "-m", "Initial pristine codebase"], cwd=repo_path, check=True, capture_output=True)


    active_branch = "exercise/auth-broken"
    subprocess.run(["git", "checkout", "-b", active_branch], cwd=repo_path, check=True, capture_output=True)

    # Write broken codebase
    broken_main = pristine_main.replace('''    if USERS.get(request.username) == request.password:
        return {"token": "fake-jwt-token"}
    raise HTTPException(status_code=401, detail="Invalid credentials")''', '''    """
    TODO: Implement login logic here.
    Validate the request against the USERS dictionary.
    Return {"token": "fake-jwt-token"} if successful,
    otherwise raise an HTTPException 401.
    """
    pass  # Student starts typing here''')
    (repo_path / "main.py").write_text(broken_main)

    # Commit broken state
    subprocess.run(["git", "add", "."], cwd=repo_path, check=True, capture_output=True)
    subprocess.run(["git", "commit", "-m", "BREAK: remove auth logic"], cwd=repo_path, check=True, capture_output=True)

    print(f"Broken repository setup complete at {repo_path.absolute()}")

if __name__ == "__main__":
    setup_broken_repo()
