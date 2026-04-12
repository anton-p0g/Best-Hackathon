# Prompt: Generate a Backend Debugging Exercise for Developer Onboarding

## Context

You are helping onboard a new developer onto an existing project. Your goal is to
introduce a **subtle but realistic bug** into the backend codebase and write a
**debugging exercise** based on it.

You will first be provided with the **directory tree** of the project. Analyze the
structure, identify the files most relevant for a beginner-level debugging exercise,
and **ask for the contents of one or more of those files** before proceeding.

Do not generate any files until you have read the files you need.

---

## Step 1 — Analyze the project structure

Based on the proyect structure, decide which files you need to read in order to introduce a
meaningful bug and read those files.

When you need to read files, you MUST use the available tools to read the files you have chosen.
Do not guess file contents. 

---

## Step 2 — Introduce the bug and generate the exercise

Once you have received the file contents, YOU MUST IN ANY CASE introduce a **subtle but realistic bug** and
produce **two separate HTML files** as described below. DO NOT INCLUDE COMMENTS.

### Criteria for the bug

- It is **not immediately obvious** — the kind of mistake that can slip through a code
  review.
- It produces **wrong behavior** but **not a crash or exception**. The application keeps
  running; the output is silently incorrect.
- It only affects a **specific condition or code path** (e.g. paginated results but not
  single-item lookups, a filtered query but not the default one).
- It involves a **logical inconsistency** between two related values in the same
  function — for example, a response field that is computed using a request parameter
  it should never depend on.

When you introduce the bug, you must ALSO provide a structured patch with:

- file_path
- full_content: the whole file with the bug introducted

IMPORTANT:

Include the entire file, not just the changes. Do not use ellipses (...) or omit parts of the original code; otherwise, the file will be corrupted.

When introducing the bug, you MUST return this UNIQUE JSON:

{
"bug_patch": {
"file_path": "...",
"full_content": "...",
},
"bug_sheet_html": "...",
"exercise_html": "..."
}

Rules:

- The original_snippet must match EXACTLY a contiguous block from the file
- The change must be minimal
- Do not include unrelated lines

---

## Output: two files

### File 1 — BUG_SHEET.html

> Instructor and reviewer reference only. Do not share with the developer.

This file must contain the following four sections in this exact order.

**Section 1 — File changed**

The filename and path of the modified file.

**Section 2 — Code before the change**

A code block containing the original lines, with a comment at the top showing
the file path and approximate line numbers.

**Section 3 — Code after the change (buggy version)**

A code block containing the modified lines, using the same comment format as
Section 2.

**Section 4 — Why this bug is subtle**

An HTML table with at least the following rows:

| Row label                   | Content                                                            |
| --------------------------- | ------------------------------------------------------------------ |
| Crash?                      | Whether the application raises an exception                        |
| First affected path         | The specific request or condition that triggers the wrong behavior |
| Unaffected paths            | Endpoints, roles, or conditions that appear normal                 |
| Why it survives code review | The specific reason a reviewer would not catch it at a glance      |

Add further rows as needed to fully explain why the bug is deceptive.

**HTML Structure and Styling Requirements for BUG_SHEET.html:**

Use the following HTML structure and CSS styling:

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Bug Sheet - [Bug Title]</title>
    <style>
      * {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
      }

      body {
        font-family:
          -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu,
          Cantarell, sans-serif;
        line-height: 1.6;
        color: #333;
        background: #f5f5f5;
        padding: 20px;
      }

      .container {
        max-width: 900px;
        margin: 0 auto;
        background: white;
        padding: 40px;
        border-radius: 8px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      }

      .header {
        border-bottom: 3px solid #dc2626;
        padding-bottom: 20px;
        margin-bottom: 30px;
      }

      .warning-banner {
        background: #fef2f2;
        border: 2px solid #dc2626;
        border-radius: 6px;
        padding: 15px;
        margin-bottom: 20px;
        color: #991b1b;
        font-weight: 600;
      }

      h1 {
        color: #1e293b;
        font-size: 2.5em;
        margin-bottom: 10px;
      }

      h2 {
        color: #1e293b;
        font-size: 1.8em;
        margin-top: 40px;
        margin-bottom: 20px;
        padding-left: 15px;
        border-left: 4px solid #dc2626;
      }

      pre {
        background: #1e293b;
        color: #e2e8f0;
        padding: 15px;
        border-radius: 6px;
        overflow-x: auto;
        margin: 10px 0;
        font-size: 0.9em;
      }

      code {
        font-family: "Courier New", monospace;
      }

      table {
        width: 100%;
        border-collapse: collapse;
        margin: 20px 0;
        background: white;
      }

      th,
      td {
        padding: 12px;
        text-align: left;
        border: 1px solid #e2e8f0;
      }

      th {
        background: #f8fafc;
        font-weight: 600;
        color: #1e293b;
      }

      tr:nth-child(even) {
        background: #f8fafc;
      }

      .file-path {
        background: #dbeafe;
        border-left: 4px solid #2563eb;
        padding: 15px;
        margin: 15px 0;
        border-radius: 4px;
        font-family: "Courier New", monospace;
        color: #1e40af;
      }
    </style>
  </head>
  <body>
    <!-- Content here -->
  </body>
</html>
```

---

### File 2 — EXERCISE.html

> Give this file to the developer. Do not include the solution or corrected code
> anywhere in this file.

This file must contain the following four sections in this exact order.

**Section 1 — Statement** (`## 📋 Statement`)

Two to three paragraphs written in the style of a bug report from a real user such as
a frontend engineer or QA tester. The report must include:

- What the user observes — the symptom, not the cause.
- The specific condition under which it occurs, such as a particular offset, filter,
  role, or action.
- A note that other endpoints or default usage are unaffected.
- An observation about how the defect scales or compounds (e.g. the wrong value grows
  proportionally with a request parameter), without naming the root cause.

**Section 2 — Observed defective output** (`## 🔴 Observed defective output`)

A numbered sequence of at least three HTTP request/response pairs in code
blocks that demonstrate how the bug compounds across calls. Each pair must be annotated
with a ✅ or ❌ emoji and a one-line explanation of what is correct or wrong.

Follow the sequence with an HTML table that makes the defective pattern visually
obvious. Suggested columns: offset sent, items returned, field value returned, expected
field value.

**Section 3 — File guide** (`## 🗺️ File guide`)

An ordered list of two to four files the developer should visit to understand and fix
the problem. For each file, write exactly one sentence explaining what to look for
there. Do not reveal the location or nature of the bug directly.

**Section 4 — Hint** (`## 💡 Hint`)

A single question that nudges the developer toward the root cause without giving it
away. The question should highlight a conceptual tension — for example, between a value
that describes the request and one that should describe the response — rather than
pointing to a specific line of code.

**HTML Structure and Styling Requirements for EXERCISE.html:**

Use the following HTML structure and CSS styling:

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Debugging Exercise - [Exercise Title]</title>
    <style>
      * {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
      }

      body {
        font-family:
          -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu,
          Cantarell, sans-serif;
        line-height: 1.6;
        color: #333;
        background: #f5f5f5;
        padding: 20px;
      }

      .container {
        max-width: 900px;
        margin: 0 auto;
        background: white;
        padding: 40px;
        border-radius: 8px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      }

      .header {
        border-bottom: 3px solid #2563eb;
        padding-bottom: 20px;
        margin-bottom: 30px;
      }

      h1 {
        color: #1e293b;
        font-size: 2.5em;
        margin-bottom: 10px;
      }

      .subtitle {
        color: #64748b;
        font-size: 1.1em;
        font-weight: 500;
      }

      h2 {
        color: #1e293b;
        font-size: 1.8em;
        margin-top: 40px;
        margin-bottom: 20px;
        padding-left: 15px;
        border-left: 4px solid #2563eb;
      }

      h3 {
        color: #334155;
        font-size: 1.3em;
        margin-top: 25px;
        margin-bottom: 15px;
      }

      p {
        margin-bottom: 15px;
        color: #475569;
      }

      .bug-report {
        background: #fef2f2;
        border-left: 4px solid #ef4444;
        padding: 20px;
        margin: 20px 0;
        border-radius: 4px;
      }

      .bug-report p {
        color: #7f1d1d;
        margin-bottom: 15px;
      }

      .request-block {
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        padding: 20px;
        margin: 20px 0;
      }

      .request-block h3 {
        margin-top: 0;
        color: #0f172a;
      }

      pre {
        background: #1e293b;
        color: #e2e8f0;
        padding: 15px;
        border-radius: 6px;
        overflow-x: auto;
        margin: 10px 0;
        font-size: 0.9em;
      }

      code {
        font-family: "Courier New", monospace;
        background: #f1f5f9;
        padding: 2px 6px;
        border-radius: 3px;
        color: #dc2626;
        font-size: 0.9em;
      }

      pre code {
        background: none;
        padding: 0;
        color: #e2e8f0;
      }

      .status {
        display: inline-block;
        padding: 5px 12px;
        border-radius: 4px;
        font-weight: 600;
        font-size: 0.9em;
        margin: 10px 0;
      }

      .status.correct {
        background: #dcfce7;
        color: #166534;
      }

      .status.wrong {
        background: #fee2e2;
        color: #991b1b;
      }

      table {
        width: 100%;
        border-collapse: collapse;
        margin: 20px 0;
        background: white;
      }

      th,
      td {
        padding: 12px;
        text-align: left;
        border: 1px solid #e2e8f0;
      }

      th {
        background: #f8fafc;
        font-weight: 600;
        color: #1e293b;
      }

      tr:nth-child(even) {
        background: #f8fafc;
      }

      .file-guide {
        background: #fffbeb;
        border-left: 4px solid #f59e0b;
        padding: 20px;
        margin: 20px 0;
        border-radius: 4px;
      }

      .file-guide ol {
        margin-left: 20px;
        margin-top: 15px;
      }

      .file-guide li {
        margin-bottom: 15px;
        color: #78350f;
      }

      .file-guide strong {
        color: #92400e;
      }

      .hint {
        background: #dbeafe;
        border-left: 4px solid #2563eb;
        padding: 20px;
        margin: 20px 0;
        border-radius: 4px;
      }

      .hint p {
        color: #1e40af;
        margin: 0;
      }

      hr {
        border: none;
        border-top: 2px solid #e2e8f0;
        margin: 30px 0;
      }
    </style>
  </head>
  <body>
    <!-- Content here -->
  </body>
</html>
```

Use appropriate HTML classes from the provided CSS:

- `.bug-report` for user bug reports
- `.request-block` for HTTP request/response pairs
- `.status.correct` and `.status.wrong` for ✅ and ❌ annotations
- `.file-guide` for the file guide section
- `.hint` for the hint section
- `<pre><code>` for code blocks
