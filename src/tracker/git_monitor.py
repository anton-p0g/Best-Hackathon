import os
import subprocess
from pathlib import Path
from typing import Optional

from src.domain.models import WorkspaceState, Speciality


class WorkspaceTracker:
    """Monitors the user workspace via Git and filesystem."""

    def __init__(self, repo_path: str):
        self.repo_path = Path(repo_path)
        if not self.repo_path.exists():
            raise FileNotFoundError(f"Target repository {self.repo_path} does not exist.")

    def get_git_diff(self) -> str:
        """Runs git diff in the target repository to monitor uncommitted changes."""
        try:
            result = subprocess.run(
                ["git", "diff"],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                check=True
            )
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            print(f"Git diff failed: {e}")
            return ""
        except FileNotFoundError:
            return ""

    def get_latest_modified_file(self, extension: str = ".py") -> Optional[Path]:
        latest_file = None
        latest_time = 0.0

        for root, dirs, files in os.walk(self.repo_path):
            # Exclude hidden directories from search
            dirs[:] = [d for d in dirs if not d.startswith('.')]
            
            for file in files:
                if file.endswith(extension):
                    filepath = Path(root) / file
                    mtime = filepath.stat().st_mtime
                    if mtime > latest_time:
                        latest_time = mtime
                        latest_file = filepath

        return latest_file

    def get_active_file_content(self) -> str:
        latest_file = self.get_latest_modified_file()
        if not latest_file:
            return ""
        try:
            return latest_file.read_text(encoding="utf-8")
        except Exception as e:
            print(f"Could not read {latest_file}: {e}")
            return ""

    def get_directory_tree(self) -> str:
        tree_str = f"[{self.repo_path.name}]\n"
        for root, dirs, files in os.walk(self.repo_path):
            # Skip hidden folders
            dirs[:] = [d for d in dirs if not d.startswith('.') and d != '__pycache__']
            level = root.replace(str(self.repo_path), '').count(os.sep)
            indent = ' ' * 4 * (level)
            folder_name = os.path.basename(root)
            if level > 0:
                tree_str += f"{indent}📁 {folder_name}/\n"
            subindent = ' ' * 4 * (level + 1)
            for f in files:
                tree_str += f"{subindent}📄 {f}\n"
        return tree_str

    def get_state(self, speciality: Speciality = None, exercise_id: str = "", active_file: str = "") -> WorkspaceState:
        """Add current directory context into a domain model."""
        if speciality is None:
            speciality = Speciality(speciality="UNKNOWN")
            
        bug_sheet_content = ""
        # Try to resolve BUG_SHEET.html
        if exercise_id:
            exercise_path = Path("exercises") / exercise_id / "BUG_SHEET.html"
            if exercise_path.exists():
                bug_sheet_content = exercise_path.read_text(encoding="utf-8")
        
        # Fallback to root directory if not found in exercises folder
        if not bug_sheet_content:
            root_bug_sheet = Path("BUG_SHEET.html")
            if root_bug_sheet.exists():
                bug_sheet_content = root_bug_sheet.read_text(encoding="utf-8")
            
        # Resolve active file content natively from the IDE payload
        if active_file:
            final_active_content = active_file
        else:
            final_active_content = self.get_active_file_content()
            
        return WorkspaceState(
            git_diff=self.get_git_diff(),
            active_file_content=final_active_content,
            bug_sheet_content=bug_sheet_content,
            directory_tree=self.get_directory_tree(),
            speciality=speciality.model_dump() if hasattr(speciality, 'model_dump') else speciality.dict()
        )
    