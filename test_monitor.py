from src.tracker.git_monitor import WorkspaceTracker
tracker = WorkspaceTracker("target_repo")
state = tracker.get_state()
print("DIFF:\n", state.git_diff)
print("ACTIVE FILE CONTENT LENGTH:", len(state.active_file_content) if state.active_file_content else 0)
