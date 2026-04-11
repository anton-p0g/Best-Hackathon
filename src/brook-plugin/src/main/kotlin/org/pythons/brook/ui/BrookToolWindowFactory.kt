package org.pythons.brook.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pythons.brook.BrookState
import org.pythons.brook.runner.BrookApiClient
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSeparator

class BrookToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // 1. Controls Panel
        val panel = BrookPanel(project)
        val content = contentFactory.createContent(panel.root, "Controls", false)
        toolWindow.contentManager.addContent(content)

        // 2. Chat Panel
        val chatPanel = BrookChatPanel(project)
        val chatContent = contentFactory.createContent(chatPanel.root, "Chat", false)
        toolWindow.contentManager.addContent(chatContent)
    }

    inner class BrookPanel(private val project: Project) {

        private val LOG = Logger.getInstance(BrookPanel::class.java)

        val root = JPanel(BorderLayout())

        // — Specialty section —
        private val specialtyValueLabel = JBLabel("—").apply {
            font = font.deriveFont(Font.BOLD, font.size + 1f)
        }

        private val changeSpecialtyButton = JButton("Change").apply {
            addActionListener { onChangeSpecialtyClicked() }
        }

        // — Status & hint —
        private val statusLabel = JBLabel("Set your specialty and press Start.").apply {
            border = JBUI.Borders.empty(4, 0)
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }

    // — Action buttons —
        private val startButton = JButton("Start Brook").apply {
            addActionListener { onStartClicked() }
        }

        init {
            refreshSpecialtyLabel()
            buildUI()
        }

        private fun buildUI() {
            val sidebar = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = JBUI.Borders.empty(12)

                // Specialty row
                val specialtyRow = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    alignmentX = JPanel.LEFT_ALIGNMENT
                    add(JBLabel("Specialty: "))
                    add(specialtyValueLabel)
                    add(Box.createHorizontalGlue())
                    add(changeSpecialtyButton)
                }
                add(specialtyRow)
                add(Box.createRigidArea(Dimension(0, 10)))
                add(JSeparator())
                add(Box.createRigidArea(Dimension(0, 10)))

                // Status
                statusLabel.alignmentX = JPanel.LEFT_ALIGNMENT
                add(statusLabel)
                add(Box.createRigidArea(Dimension(0, 10)))

                // Buttons
                startButton.alignmentX = JButton.LEFT_ALIGNMENT
                add(startButton)
                add(Box.createRigidArea(Dimension(0, 12)))
            }

            root.add(sidebar, BorderLayout.NORTH)
            root.add(JPanel(), BorderLayout.CENTER) // Empty filler space
        }

        // — Specialty —

        private fun refreshSpecialtyLabel() {
            val state = BrookState.getInstance(project)
            specialtyValueLabel.text = if (state.specialty.isBlank()) "Not set" else state.specialty
        }

        private fun onChangeSpecialtyClicked() {
            val state = BrookState.getInstance(project)
            val dialog = SpecialtyDialog(project, current = state.specialty)
            if (dialog.showAndGet()) {
                state.specialty = dialog.getSelectedSpecialty()
                refreshSpecialtyLabel()
                setStatus("Specialty updated to \"${state.specialty}\".")
            }
        }

        // — Start —

        private fun onStartClicked() {
            val state = BrookState.getInstance(project)

            if (state.specialty.isBlank()) {
                val dialog = SpecialtyDialog(project)
                if (!dialog.showAndGet()) return
                state.specialty = dialog.getSelectedSpecialty()
                refreshSpecialtyLabel()
            }

            // Check backend health first
            setStatus("Connecting to Brook backend…")
            startButton.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                if (!BrookApiClient.isBackendReachable()) {
                    ApplicationManager.getApplication().invokeLater {
                        setStatus("Error: Backend not running. Start it with: PYTHONPATH=. uvicorn src.api:app --reload")
                        startButton.isEnabled = true
                    }
                    return@launch
                }

                val result = BrookApiClient.inject(
                    repoPath = "target_repo",
                    specialty = state.specialty
                )

                ApplicationManager.getApplication().invokeLater {
                    if (result.isSuccess) {
                        setStatus("Exercise ready. Switch to the Chat tab!")
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                        LOG.warn("Brook inject failed: $msg")
                        setStatus("Error: $msg")
                        startButton.isEnabled = true
                    }
                }
            }
        }

        private fun setStatus(text: String) {
            statusLabel.text = text
        }
    }
}
