# Brook: Codebase Tutor 🎓

Brook is a IntelliJ Platform plugin designed to bring an interactive "Over-the-Shoulder" AI tutor directly into your development environment. This allows for immersive, context-aware onboarding and learning exercises right inside your IDE.

## Overview

The Brook Plugin acts as the frontend client to the Brook backend. By living directly in the IDE, it goes beyond generic AI assistants by automatically tracking what you're doing, reading your active code, and offering customized guidance.

Key features of the plugin include:
- **Integrated Tool Window**: A custom Tool Window that splits into a "Menu" and "Chat" panel.
- **Embedded Browser**: Uses JCEF (Java Chromium Embedded Framework) to render exercise instructions.
- **Context-Aware Assistance**: The plugin automatically grabs the content of your currently active editor window (`FileEditorManager`) and sends it to the tutor, so the AI knows exactly what you are looking at.
- **Socratic Hints**: A dedicated "Get Hint" button triggers an SSE (Server-Sent Events) stream to provide real-time, typed-out hints without just printing the final solution.
- **Automated Grading**: "Check Solution" instantly validates your active code against the backend validation criteria.

## Plugin UI Architecture

The plugin is architected using Kotlin Coroutines and the IntelliJ Platform SDK:

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
