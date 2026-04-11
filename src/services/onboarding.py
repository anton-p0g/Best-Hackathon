from typing import Iterator
from src.agents.openai_tutor import OpenAITutorAgent
from src.tracker.git_monitor import WorkspaceTracker
from src.domain.models import Speciality


class OnboardingService:
    """Controller for the workspace Tracker and LLM Agent"""
    
    def __init__(self, repo_path: str = "target_repo", model: str = "gpt-5.4"):
        self.tracker = WorkspaceTracker(repo_path)
        self.agent = OpenAITutorAgent(model=model)

    def process_message(self, student_message: str, speciality: Speciality, exercise_id: str = "", is_hint_trigger: bool = False) -> Iterator[str]:
        """Collects local IDE context and streams the LLM response."""
        workspace_state = self.tracker.get_state(speciality, exercise_id=exercise_id)
        
        return self.agent.generate_hint_stream(
            student_message=student_message,
            workspace_state=workspace_state,
            is_hint_trigger=is_hint_trigger
        )

    def verify_solution(self, speciality: Speciality, exercise_id: str = "") -> dict:
        workspace_state = self.tracker.get_state(speciality, exercise_id=exercise_id)
        return self.agent.verify_solution(workspace_state)
