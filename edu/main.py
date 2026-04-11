"""
Standalone CLI entry point for the exercise generator.
For API usage, call edu.runner.run() directly.
"""
from edu.runner import run, generate_tree


if __name__ == "__main__":
    tree = generate_tree("target_repo/")
    print("GENERATING EXERCISE...")
    result = run(tree, speciality="Auth")
    print(f"Done! Exercise ID: {result['exercise_id']}")