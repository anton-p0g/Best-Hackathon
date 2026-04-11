package org.pythons.brook.runner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

object FileModifier {

    private val LOG = Logger.getInstance(FileModifier::class.java)
    /**
     * Applies a list of file modifications to the project.
     * Each modification replaces the full content of a file.
     *
     * Runs inside a WriteCommandAction so:
     *   - The IDE treats it as a single undoable operation (Ctrl+Z)
     *   - VFS is notified and the editor refreshes automatically
     */
    fun applyModifications(
        project: Project,
        modifications: List<BrookApiClient.FileModification>
    ) {
        if (modifications.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project, "Brook: inject issues", null, {
            for (mod in modifications) {
                applyModification(project, mod)
            }
        })
    }

    private fun applyModification(
        project: Project,
        mod: BrookApiClient.FileModification
    ) {
        val projectPath = project.basePath ?: return
        val targetFile = File(projectPath, mod.file)

        try {
            // Ensure the file exists on disk
            if (!targetFile.exists()) {
                LOG.warn("Brook: target file does not exist: ${mod.file}")
                return
            }

            // Write new content to disk
            targetFile.writeText(mod.content, Charsets.UTF_8)

            // Notify IntelliJ's Virtual File System so editors refresh
            val vFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByIoFile(targetFile)

            if (vFile == null) {
                LOG.warn("Brook: VFS could not find file after write: ${mod.file}")
                return
            }

            VfsUtil.markDirtyAndRefresh(
                false,  // synchronous refresh
                false,  // not recursive
                true,   // refresh in event dispatch thread
                vFile
            )

            LOG.info("Brook: modified ${mod.file}")

        } catch (e: Exception) {
            LOG.error("Brook: failed to modify ${mod.file}", e)
        }
    }

    /**
     * Reads the current content of a file in the project.
     * Useful for passing context to the hint script.
     */
    fun readFileContent(project: Project, relativePath: String): String? {
        val projectPath = project.basePath ?: return null
        val file = File(projectPath, relativePath)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }
}
