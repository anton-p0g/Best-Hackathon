"""
Launch the Brook FastAPI backend.

Usage:
    python run_server.py

This avoids needing to manually set PYTHONPATH before running uvicorn.
"""
import sys
import os

# Ensure the project root is on sys.path so `from src.…` imports work.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import uvicorn

if __name__ == "__main__":
    uvicorn.run("src.api:app", host="0.0.0.0", port=8000, reload=True)
