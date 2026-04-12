# Brook: Codebase Tutor 🎓

Brook is an advanced PyCharm plugin designed to help how developers onboard to new projects and learn unfamiliar codebases.
By integrating an "Over-the-Shoulder" AI tutor directly into your development environment, Brook eliminates the friction of switching context between your IDE and external documentation or chat tools.
It creates an immersive learning experience by tracking your coding progress in real time, understanding the exact context of the files you are working on, and guiding you through interactive exercises.

## Purpose & Goal

The primary purpose of Brook is to redce the steep learning curve often associated with diving into a complex codebase.Wheter you are a new hire trying to understand the architecture, or a student learning specific design patterns, Brook as a knowledgeable mentor sitting right beside you. Instead of simply generating code for you to copy and paste, Brook's AI is explicitly prompted to act Socratically. It analyzes your active workspace, evluates your uncommitted changes, and provides tailored hints that encourage you to discover the solution yourself.

## Overview

The Brook Plugin acts as the frontend client to he Brook backend. By living directly in the IDE, it automatically tracks what you're doing, reading your active code and offering customized guidance.

Key features of the plugin include:
- **Integrated Tool Window**: A custom Tool Window that splits into a "Menu" and "Chat" panel.
- **Embedded Browser**: Uses JCEF (Java Chromium Embedded Framework) to render exercise instructions.
- **Context-Aware Assistance**: The plugin automatically grabs the content of your currently active editor window (`FileEditorManager`) and sends it to the tutor, so the AI knows exactly wht you are looking at.
- **Socratic Hints**: A dedicated "Get Hint" button provides real-time, typed-out hints without just printing the final solution.
- **Automated Grading**: "Check Solution" instantly validates your active code against the backend validation criteria.

## Plugin UI Architecture

The plugin is architected using Kotlin Coroutines and the Intellij Platform SDK:

### 1. Tool Window (`BrookToolWindowFactory`)
- The main entry point that registers the plugin workspace.
- **Menu Panel (`BrookPanel`)**: 
  - Allows you to set your target "Specialty" (e.g., Auth, DevOps, UI).
  - Injects the repository state into the backend.
  - Dynamically lists and generates new LangGraph-powered debugging exercises.
  - Manages the JCEF browser lifecycle for rendering the exercise content.
- **Chat Panel (`BrookChatPanel`)**:
  - A robust HTML-based chat bubble system using `JEditorPane`.
  - Sends user queries and active file context to the AI.
  - Unlocks/Locks UI controls defensively if you complete an exercise, re-enabling them seamlessly via a `DocumentListener` if you start typing in your editor again.

### 2. State & Networking (`BrookState`, `BrookApiClient`)
- State persistance for tracking the user's Specialty and Active Exercise.
- Fast Coroutine-based REST/SSE client that talks directly to the local FastAPI tutor engine.

## Setup & Development

The plugin is built using Gradle. Make sure you have JDK 21 configured.

### Running the Plugin

You can spin up an isolated Sandbox IDE with the plugin pre-installed:

```bash
cd src/brook-plugin
./gradlew runIde
```

### Building the Plugin

To build a standalone zip file that can be distributed and installed manually into any JetBrains IDE:

```bash
./gradlew buildPlugin
```

*Note: Ensure the Brook Python backend is running simultaneously on `http://localhost:8000` for the plugin to successfully connect and fetch exercises.*