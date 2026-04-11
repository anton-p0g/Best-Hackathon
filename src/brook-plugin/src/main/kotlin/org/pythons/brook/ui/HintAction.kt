package org.pythons.brook.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.pythons.brook.BrookState
import org.pythons.brook.runner.BrookApiClient

class HintAction : AnAction() {

    private val LOG = Logger.getInstance(HintAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        val state = BrookState.getInstance(project)
        e.presentation.isEnabled = state.specialty.isNotBlank()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val state = BrookState.getInstance(project)

        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?.path
            ?.removePrefix(project.basePath ?: "")
            ?.trimStart('/', '\\')
            ?: ""

        requestHint(project, state.specialty, currentFile)
    }

    private fun requestHint(project: Project, specialty: String, currentFile: String) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Brook: fetching hint…", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    val fullHint = StringBuilder()
                    val result = BrookApiClient.hintStream(
                        repoPath = "target_repo",
                        specialty = specialty,
                        currentFile = currentFile
                    ) { chunk ->
                        fullHint.append(chunk)
                    }

                    ApplicationManager.getApplication().invokeLater {
                        if (result.isSuccess) {
                            val hint = result.getOrNull() ?: "No hint available."
                            HintToolWindow.showHint(project, hint)
                        } else {
                            val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                            LOG.warn("Brook hint failed: $msg")
                            Messages.showErrorDialog(
                                project,
                                "Could not fetch hint:\n$msg",
                                "Brook"
                            )
                        }
                    }
                }
            }
        )
    }
}
