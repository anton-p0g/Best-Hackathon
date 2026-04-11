from abc import ABC, abstractmethod
from typing import Iterator

from src.domain.models import WorkspaceState


class TutorAgent(ABC):
    @abstractmethod
    def generate_hint_stream(
        self, student_message: str, workspace_state: WorkspaceState, is_hint_trigger: bool = False
    ) -> Iterator[str]:
        """
        Streams a hint back to the student based on workspace context.

        Args:
            student_message (str): The question or message from the student.
            workspace_state (WorkspaceState): The state of the git diff, active file, etc.

        Returns:
            Iterator[str]: Chunks of the LLM response.
        """
        pass

    @abstractmethod
    def verify_solution(self, workspace_state: WorkspaceState) -> dict:
        """
        Verifies the solution.

        Args:
            workspace_state (WorkspaceState): The state of the git diff, active file, etc.

        Returns:
            dict: A dictionary containing the verification result.
        """
        pass