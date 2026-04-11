# Integration Plan: PyCharm Plugin -> Brook Backend

## 1. Analysis of the Teammate's Code
Your teammate has done an excellent job building a native Kotlin UI for PyCharm. 
Here is exactly how their codebase operates right now:
1. **The UI**: They've built `BrookToolWindowFactory` which drops a sidebar mimicking our Streamlit interface (capturing Specialty, a Start button, and a Hint button).
2. **The Execution Bridge**: They built `ScriptRunner.kt`. Instead of communicating over an HTTP API, this Kotlin class spawns a local System shell to run Python via a CLI Subprocess (`ProcessBuilder(python, script.py ...)`).
3. **The Dummy Script**: They bundled a mock script into the Java `resources` folder called `brook_plugin.py` which just prints hardcoded `json.dumps()` data (currently hardcoded with mock Spanish strings).

## 2. Path Forward: Connecting to the Backend
You have two architectural choices for hooking this up.

### Option A: The Fast Hackathon Path (CLI Wrapping)
Since your teammate already built the Kotlin side to expect JSON payloads from a Python CLI executable, we just rewrite `src/brook-plugin/src/main/resources/brook_plugin.py` to import and wrap our actual `OnboardingService`.

**Steps for Option A:**
1. **Update `brook_plugin.py`**: Import `OnboardingService` and `Speciality` from our `src` folder. 
2. When `--mode hint` is called, we synchronously run `service.process_message()` (we consume the whole stream generator internally and just print the final aggregated string to stdout as JSON).
3. **Add Validation**: We add `--mode verify` to the `brook_plugin.py` script so we can trigger `service.verify_solution()` and return the Boolean JSON output. We will need to update the Kotlin UI slightly to contain the "Check Solution" button.

*Pros:* Lightning fast to implement.
*Cons:* You lose the "typewriter" streaming effect because the Kotlin `ScriptRunner` waits for the subprocess CLI to exit entirely before parsing the JSON stdout.

### Option B: The "Enterprise" Path (Local HTTP API)
We abandon the `ScriptRunner.kt` subprocess approach completely. Instead, we wrap our `OnboardingService` inside a `FastAPI` application running on `localhost:8000`.

**Steps for Option B:**
1. **Build `src/api.py`**: Create 3 routes (`/start`, `/hint`, `/verify`).
2. **Rewrite Kotlin Runner**: Inside PyCharm, we replace `ProcessBuilder` with an `HttpClient` call to make standard Web Requests.
3. **Streaming**: For hints, we use Server-Sent Events (SSE) so the PyCharm text area can stream the answer line-by-line just like Streamlit!

---

> [!IMPORTANT]
> **User Review Required**
> Which path do you want to take? **Option A** gets you across the finish line fastest since Wenjie already implemented the Subprocess Logic successfully on the IDE side. **Option B** lets you keep the typewriter streaming effect but requires ripping out Wenjie's `ScriptRunner` code and adding HTTP logic in Kotlin.
