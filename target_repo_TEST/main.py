from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()
USERS = {"admin": "secret123"}

class LoginRequest(BaseModel):
    username: str
    password: str

@app.post("/login")
def login(request: LoginRequest):
    """
    Validate the request against the USERS dictionary.
    Return {"token": "fake-jwt-token"} if successful,
    otherwise raise an HTTPException 401.
    """
    stored_password = USERS.get(request.username)

    if not stored_password or stored_password != request.password:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, 
            detail="Invalid credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return {"token": "fake-jwt-token"}

