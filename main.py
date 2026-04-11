import sys
from dotenv import load_dotenv
from src.agents.openai_tutor import OpenAITutorAgent


def test_tutor():
    load_dotenv()
    
    print("Connecting to OpenAI...")
    try:
        agent = OpenAITutorAgent()
    except Exception as e:
        print(f"Failed to initialize Agent: {e}")
        sys.exit(1)

    print("Sending dummy request...")
    
    student_msg = "Why is this not running?"
    file_content = "def add(a, b):\n    return a - b"
    git_diff = "-    return a + b\n+    return a - b"
    
    stream = agent.generate_hint_stream(
        student_message=student_msg,
        current_file_content=file_content,
        git_diff=git_diff
    )
    
    print("\n--- Response Stream ---")
    for chunk in stream:
        print(chunk, end="", flush=True)
    print("\n-----------------------")


if __name__ == "__main__":
    test_tutor()
