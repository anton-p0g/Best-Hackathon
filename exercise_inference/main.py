from exercise_inference.runner import run, generate_tree


if __name__ == "__main__":
    tree = generate_tree("target_repo/")
    print("GENERATING EXERCISE...")
    result = run(tree, speciality="Auth")
    print(f"Done! Exercise ID: {result['exercise_id']}")