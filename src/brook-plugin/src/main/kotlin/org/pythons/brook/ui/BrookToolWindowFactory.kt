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

        // 1. Menu Panel (formerly Controls)
        val menuPanel = BrookPanel(project, toolWindow)
        val menuContent = contentFactory.createContent(menuPanel.root, "Menu", false)
        toolWindow.contentManager.addContent(menuContent)

        // 2. Chat Panel
        val chatPanel = BrookChatPanel(project)
        val chatContent = contentFactory.createContent(chatPanel.root, "Chat", false)
        toolWindow.contentManager.addContent(chatContent)
    }

    private fun refreshContainer(container: JPanel) {
        container.revalidate()
        container.repaint()
        var parent = container.parent
        while (parent != null) {
            parent.revalidate()
            parent.repaint()
            parent = parent.parent
        }
    }

    inner class BrookPanel(private val project: Project, private val toolWindow: ToolWindow) {

        private val LOG = Logger.getInstance(BrookPanel::class.java)
        val root = JPanel(BorderLayout())
        
        private val exercisesContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = JPanel.LEFT_ALIGNMENT
        }

        // — Specialty section —
        private val specialtyValueLabel = JBLabel("—").apply {
            font = font.deriveFont(Font.BOLD, font.size + 1f)
        }

        private val changeSpecialtyButton = JButton("Change").apply {
            addActionListener { onChangeSpecialtyClicked() }
        }

        // — Status —
        private val statusLabel = JBLabel("Set your specialty and press Start.").apply {
            border = JBUI.Borders.empty(4, 0)
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }

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
                statusLabel.alignmentX = JPanel.CENTER_ALIGNMENT
                add(statusLabel)
                add(Box.createRigidArea(Dimension(0, 10)))

                // Start Button (Centered and Big)
                val buttonContainer = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
                    alignmentX = JPanel.CENTER_ALIGNMENT
                    isOpaque = false
                    add(startButton.apply {
                        font = font.deriveFont(Font.BOLD, font.size + 4f)
                        preferredSize = Dimension(180, 45)
                    })
                }
                add(buttonContainer)
                add(Box.createRigidArea(Dimension(0, 12)))
                
                // Dynamic Exercises List
                add(JSeparator())
                add(Box.createRigidArea(Dimension(0, 10)))
                val title = JBLabel("Available Exercises").apply {
                    font = font.deriveFont(Font.BOLD)
                    alignmentX = JBLabel.LEFT_ALIGNMENT
                }
                add(title)
                add(Box.createRigidArea(Dimension(0, 8)))
                add(exercisesContainer)
                
                // Pushes everything up
                add(Box.createVerticalGlue())
            }

            root.add(JBScrollPane(sidebar), BorderLayout.CENTER)
        }

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

        private fun onStartClicked() {
            val state = BrookState.getInstance(project)

            if (state.specialty.isBlank()) {
                val dialog = SpecialtyDialog(project)
                if (!dialog.showAndGet()) return
                state.specialty = dialog.getSelectedSpecialty()
                refreshSpecialtyLabel()
            }

            setStatus("Connecting to Brook backend…")
            startButton.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                if (!BrookApiClient.isBackendReachable()) {
                    ApplicationManager.getApplication().invokeLater {
                        setStatus("Error: Backend unreachable.")
                        startButton.isEnabled = true
                    }
                    return@launch
                }

                val result = BrookApiClient.inject(repoPath = "target_repo", specialty = state.specialty)

                ApplicationManager.getApplication().invokeLater {
                    if (result.isSuccess) {
                        setStatus("Ready! Select an exercise below.")
                        loadExercisesInMenu()
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                        setStatus("Error: $msg")
                        startButton.isEnabled = true
                    }
                }
            }
        }

        private fun loadExercisesInMenu() {
            exercisesContainer.removeAll()
            exercisesContainer.alignmentX = JPanel.CENTER_ALIGNMENT
            exercisesContainer.add(JBLabel("Fetching list...").apply {
                alignmentX = JBLabel.CENTER_ALIGNMENT
            })
            refreshContainer(exercisesContainer)

            CoroutineScope(Dispatchers.IO).launch {
                val result = BrookApiClient.getExercises()
                ApplicationManager.getApplication().invokeLater {
                    exercisesContainer.removeAll()
                    if (result.isSuccess) {
                        val exercises = result.getOrNull() ?: emptyList()
                        if (exercises.isEmpty()) {
                            exercisesContainer.add(JBLabel("No exercises found.").apply {
                                alignmentX = JBLabel.CENTER_ALIGNMENT
                            })
                        } else {
                            exercises.forEach { (id, label) ->
                                val btn = JButton(label).apply {
                                    alignmentX = JButton.CENTER_ALIGNMENT
                                    maximumSize = Dimension(180, preferredSize.height)
                                    addActionListener { openExerciseTab(id, label) }
                                }
                                exercisesContainer.add(btn)
                                exercisesContainer.add(Box.createRigidArea(Dimension(0, 4)))
                            }
                        }
                    } else {
                        exercisesContainer.add(JBLabel("Failed to load exercises.").apply {
                            alignmentX = JBLabel.CENTER_ALIGNMENT
                        })
                    }
                    refreshContainer(exercisesContainer)
                }
            }
        }

        private fun openExerciseTab(exerciseId: String, title: String) {
            val contentManager = toolWindow.contentManager
            
            // Check if tab already exists
            val existing = contentManager.contents.find { it.displayName == title }
            if (existing != null) {
                contentManager.setSelectedContent(existing)
                return
            }

            // Create new panel
            val panel = MarkdownViewerPanel(exerciseId, "EXERCISE.html")
            val content = ContentFactory.getInstance().createContent(panel.root, title, false)
            content.isCloseable = true
            
            contentManager.addContent(content)
            contentManager.setSelectedContent(content)
        }

        private fun setStatus(text: String) {
            statusLabel.text = text
        }
    }
}
