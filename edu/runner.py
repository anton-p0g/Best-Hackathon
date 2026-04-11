"""
Exercise generator runner.
Calls the LangGraph agent to dynamically create a debugging exercise,
then saves the outputs to exercises/<slug>/.
"""
import json
import os
import re
import time
import subprocess

from edu.agent import graph
from edu.tools import write_full_file


def extract_json(text: str):
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        raise Exception("No JSON found in agent output")
    return json.loads(match.group())


def generate_tree(start_path: str) -> str:
    """Walk the repo and return an indented directory tree string."""
    tree = []
    for root, dirs, files in os.walk(start_path):
        level = root.replace(start_path, "").count(os.sep)
        indent = "    " * level
        tree.append(f"{indent}{os.path.basename(root)}/")

        subindent = "    " * (level + 1)
        for f in files:
            tree.append(f"{subindent}{f}")
    return "\n".join(tree)


def run(directory_tree: str, speciality: str = "General") -> dict:
    """
    Generate a debugging exercise using the LangGraph agent.

    Args:
        directory_tree: String representation of the project file tree.
        speciality: The domain focus for the exercise (e.g. "Auth", "Pagination").

    Returns:
        dict with keys: exercise_id, exercise_html_path, bug_sheet_html_path, patched_file
    """
    result = graph.invoke({
        "messages": [("user", f"""
    Here is the project structure:

    {directory_tree}

    The developer's speciality is: {speciality}
    Focus the debugging exercise on the {speciality} domain if possible.

    Follow the debugging exercise prompt.
    """)]
    })

    # The output is in the last assistant message
    output = result["messages"][-1].content
    print(output)

    data = extract_json(output)
    
    slug = f"exercise_{speciality.lower().replace(' ', '_')}_{int(time.time())}"
    
    repo_dir = "target_repo"
    if not os.path.exists(os.path.join(repo_dir, ".git")):
        subprocess.run(["git", "init"], cwd=repo_dir, check=False)
        
    subprocess.run(["git", "add", "."], cwd=repo_dir, check=False)
    subprocess.run(["git", "commit", "-m", "Base codebase state before exercise"], cwd=repo_dir, check=False)
    
    subprocess.run(["git", "checkout", "-b", slug], cwd=repo_dir, check=False)

    patch = data["bug_patch"]
    patch_result = write_full_file.invoke({
        "file_path": patch["file_path"],
        "content": patch["full_content"]
    })
    print("Patch result:", patch_result)
    
    subprocess.run(["git", "add", "."], cwd=repo_dir, check=False)
    subprocess.run(["git", "commit", "-m", f"BREAK: Injected {speciality} exercise bug"], cwd=repo_dir, check=False)

    # Create a unique exercise directory under exercises/
    exercise_dir = os.path.join("exercises", slug)
    os.makedirs(exercise_dir, exist_ok=True)

    # Save the generated HTML files
    exercise_path = os.path.join(exercise_dir, "EXERCISE.html")
    bug_sheet_path = os.path.join(exercise_dir, "BUG_SHEET.html")

    with open(exercise_path, "w", encoding="utf-8") as f:
        f.write(data["exercise_html"])

    with open(bug_sheet_path, "w", encoding="utf-8") as f:
        f.write(data["bug_sheet_html"])

    print(f"Exercise generated ✅ → {exercise_dir}")

    return {
        "exercise_id": slug,
        "exercise_html_path": exercise_path,
        "bug_sheet_html_path": bug_sheet_path,
        "patched_file": patch["file_path"],
    }