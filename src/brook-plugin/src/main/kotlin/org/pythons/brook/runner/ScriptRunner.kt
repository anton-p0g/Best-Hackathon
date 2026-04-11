package org.pythons.brook.runner

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object ScriptRunner {

    private val LOG = Logger.getInstance(ScriptRunner::class.java)

    // Expected JSON output from the Python script when injecting issues:
    // [{ "file": "relative/path.py", "content": "full file content with issue" }, ...]
    @Serializable
    data class FileModification(
        val file: String,
        val content: String
    )

    // Expected JSON output when requesting a hint:
    // { "hint": "Look at line 42, the variable is never reset between iterations." }
    @Serializable
    data class HintResult(
        val hint: String
    )

    /**
     * Runs the inject script. Returns a list of file modifications to apply.
     * The Python script receives: --mode inject --specialty <specialty>
     */
    fun runInjectScript(
        projectPath: String,
        specialty: String
    ): Result<List<FileModification>> {
        val scriptPath = extractScript() ?: return Result.failure(
            RuntimeException("Brook: could not extract brook_plugin.py")
        )
        return runScript(
            scriptPath = scriptPath,
            args = listOf("--mode", "inject", "--specialty", specialty),
            projectPath = projectPath
        ) { output ->
            Json.decodeFromString<List<FileModification>>(output)
        }
    }


    fun runHintScript(
        projectPath: String,
        specialty: String,
        currentFile: String
    ): Result<String> {
        val scriptPath = extractScript() ?: return Result.failure(
            RuntimeException("Brook: could not extract brook_plugin.py")
        )
        return runScript(
            scriptPath = scriptPath,
            args = listOf("--mode", "hint", "--specialty", specialty, "--file", currentFile),
            projectPath = projectPath
        ) { output ->
            Json.decodeFromString<HintResult>(output).hint
        }
    }

    /**
     * Extracts brook_plugin.py from the jar to a temp file and returns its path.
     * The temp file is deleted on JVM exit.
     */
    private fun extractScript(): String? {
        return try {
            val input = ScriptRunner::class.java.classLoader
                .getResourceAsStream("brook_plugin.py")
                ?: return null

            val tempFile = java.io.File.createTempFile("brook_plugin", ".py")
            tempFile.deleteOnExit()
            tempFile.outputStream().use { out -> input.copyTo(out) }
            tempFile.absolutePath
        } catch (e: Exception) {
            LOG.error("Brook: failed to extract script", e)
            null
        }
    }

    /**
     * Generic script launcher. Runs a Python process, captures stdout,
     * and deserializes the output using the provided parser.
     */
    private fun <T> runScript(
        scriptPath: String,
        args: List<String>,
        projectPath: String,
        parser: (String) -> T
    ): Result<T> {
        return try {
            val pythonCmd = findPython()
                ?: return Result.failure(RuntimeException(
                    "Python not found. Make sure Python is installed and in your PATH."
                ))

            val command = buildList {
                add(pythonCmd)
                add(scriptPath)
                addAll(args)
            }

            val process = ProcessBuilder(command)
                .directory(java.io.File(projectPath))
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                LOG.warn("Brook script exited with code $exitCode. stderr: $stderr")
                return Result.failure(RuntimeException("Script failed (exit $exitCode): $stderr"))
            }

            if (stderr.isNotBlank()) LOG.info("Brook script stderr: $stderr")

            Result.success(parser(stdout.trim()))
        } catch (e: Exception) {
            LOG.error("Brook: error running script", e)
            Result.failure(e)
        }
    }

    /**
     * Finds the Python executable on Windows (python) or Unix (python3 / python).
     */
    private fun findPython(): String? {
        val candidates = if (System.getProperty("os.name").lowercase().contains("win")) {
            listOf("python", "python3")
        } else {
            listOf("python3", "python")
        }

        for (cmd in candidates) {
            return try {
                val process = ProcessBuilder(cmd, "--version")
                    .redirectErrorStream(true)
                    .start()
                process.waitFor()
                if (process.exitValue() == 0) cmd else continue
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }
}
