import os
from typing import Iterator

from dotenv import load_dotenv, find_dotenv
from openai import OpenAI

from src.agents.interface import TutorAgent
from src.domain.models import WorkspaceState
from src.agents.prompts.system_prompt import get_system_prompt
from src.agents.prompts.hint import get_hint_prompt


class OpenAITutorAgent(TutorAgent):
    """OpenAI implementation of the TutorAgent."""

    def __init__(self, model: str = "gpt-4o-mini"):
        load_dotenv(find_dotenv())

        api_key = os.environ.get("OPENAI_API_KEY")
        if not api_key:
            raise ValueError("OPENAI_API_KEY environment variable not set.")

        self.client = OpenAI(api_key=api_key)
        self.model = model

    def generate_hint_stream(
        self, student_message: str, workspace_state: WorkspaceState, is_hint_trigger: bool = False
    ) -> Iterator[str]:
        
        system_instructions = get_system_prompt(workspace_state)
        
        if is_hint_trigger:
            user_prompt = get_hint_prompt(workspace_state)
        else:
            user_prompt = f"STUDENT MESSAGE: {student_message}"

        response = self.client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": system_instructions},
                {"role": "user", "content": user_prompt},
            ],
            stream=True,
            temperature=0.7,
        )

        for chunk in response:
            if chunk.choices[0].delta.content is not None:
                yield chunk.choices[0].delta.content

    def verify_solution(self, workspace_state: WorkspaceState) -> dict:
        import json
        from src.agents.prompts.verify import get_verification_prompt
        
        prompt = get_verification_prompt(workspace_state)
        
        response = self.client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": "You are a JSON-only grading API."},
                {"role": "user", "content": prompt},
            ],
            response_format={"type": "json_object"},
            temperature=0.3,
        )
        
        try:
            return json.loads(response.choices[0].message.content)
        except Exception as e:
            return {"solved": False, "feedback": "Grader failed to parse response: " + str(e)}
