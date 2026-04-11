from pydantic import BaseModel, Field
from typing import Optional


class Speciality(BaseModel):
    speciality: str = Field(default="UNKNOWN", max_length=40, min_length=1)


class WorkspaceState(BaseModel):
    git_diff: str
    active_file_content: Optional[str] = None
    directory_tree: str
    speciality: Speciality
