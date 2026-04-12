import os
from pathlib import Path
from dotenv import load_dotenv, find_dotenv
from langchain_openai import ChatOpenAI
from langgraph.prebuilt import create_react_agent
from exercise_inference.tools import read_file, write_full_file

load_dotenv(find_dotenv())

PROMPT_PATH = Path(__file__).parent / "PROMPT.md"

def load_prompt() -> str:
    with open(PROMPT_PATH, "r", encoding="utf-8") as f:
        return f.read()

llm = ChatOpenAI(model="gpt-5.4", temperature=0, api_key=os.getenv("OPENAI_API_KEY"))

tools = [read_file, write_full_file]

graph = create_react_agent(
    model=llm,
    tools=tools,
    prompt=load_prompt()
)