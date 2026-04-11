package org.pythons.brook.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
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

        // Create Chat Panel first
        val chatPanel = BrookChatPanel(project)

        // Menu Panel gets a reference to the chat panel
        val menuPanel = BrookPanel(project, toolWindow, chatPanel)
        val menuContent = contentFactory.createContent(menuPanel.root, "Menu", false)
        toolWindow.contentManager.addContent(menuContent)

        // Then add Chat Panel content
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

    inner class BrookPanel(private val project: Project, private val toolWindow: ToolWindow, private val chatPanel: BrookChatPanel) {

        private val LOG = Logger.getInstance(BrookPanel::class.java)
        val root = JPanel(BorderLayout())

        private val exercisesContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = JPanel.LEFT_ALIGNMENT
        }

        // Single browser + panel kept alive for the lifetime of the tool window.
        // Reusing one JBCefBrowser avoids the JCEF creation-while-still-alive crash
        // that causes the second exercise to silently fail to render.
        private val sharedExerciseBrowser = JBCefBrowser()
        private val exercisePanel = MarkdownViewerPanel(sharedExerciseBrowser)
        private var exerciseContent: com.intellij.ui.content.Content? = null

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

        private val generateButton = JButton("Generate Exercise").apply {
            isEnabled = false
            addActionListener { onGenerateClicked() }
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
                add(Box.createRigidArea(Dimension(0, 6)))

                // Generate Exercise Button (Centered)
                val generateContainer = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
                    alignmentX = JPanel.CENTER_ALIGNMENT
                    isOpaque = false
                    add(generateButton.apply {
                        preferredSize = Dimension(180, 35)
                    })
                }
                add(generateContainer)
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
                        generateButton.isEnabled = true
                        loadExercisesInMenu()
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                        setStatus("Error: $msg")
                        startButton.isEnabled = true
                    }
                }
            }
        }

        private fun onGenerateClicked() {
            val state = BrookState.getInstance(project)
            generateButton.isEnabled = false
            setStatus("Generating exercise… this may take a minute.")

            CoroutineScope(Dispatchers.IO).launch {
                val result = BrookApiClient.generateExercise(
                    repoPath = "target_repo",
                    specialty = state.specialty
                )

                ApplicationManager.getApplication().invokeLater {
                    generateButton.isEnabled = true
                    if (result.isSuccess) {
                        val id = result.getOrNull()?.exercise_id ?: "unknown"
                        setStatus("Exercise \"$id\" created!")
                        loadExercisesInMenu()
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                        setStatus("Generation failed: $msg")
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
                                    preferredSize = Dimension(180, preferredSize.height + 4)
                                    addActionListener { openExerciseTab(id, label) }
                                }
                                // Wrap in FlowLayout panel so BoxLayout centres it correctly,
                                // same pattern as the "Start Brook" button above.
                                val btnWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                                    alignmentX = JPanel.CENTER_ALIGNMENT
                                    isOpaque = false
                                    add(btn)
                                }
                                exercisesContainer.add(btnWrapper)
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
            val state = BrookState.getInstance(project)
            state.activeExerciseId = exerciseId
            chatPanel.updateActiveExercise(title)
            
            val contentManager = toolWindow.contentManager
            val fileName = "EXERCISE.html"

            // If the exercise tab is already open, update its title, select it,
            // then reload the content (browser is already visible — safe to call loadHTML).
            val existing = exerciseContent
            if (existing != null && contentManager.contents.contains(existing)) {
                existing.displayName = title
                contentManager.setSelectedContent(existing)
                exercisePanel.loadExercise(exerciseId, fileName)
                return
            }

            // First time: attach the tab to the UI BEFORE calling loadExercise().
            // JCEF silently ignores loadHTML() calls made before the browser component
            // is part of a visible window hierarchy — that's why the first open always
            // appeared blank. Adding and selecting the content first puts the component
            // on screen, then loadExercise() can successfully push HTML into it.
            val content = ContentFactory.getInstance().createContent(exercisePanel.root, title, false)
            content.isCloseable = true
            exerciseContent = content
            contentManager.addContent(content)
            contentManager.setSelectedContent(content)

            // Now the browser is visible — load the exercise content.
            exercisePanel.loadExercise(exerciseId, fileName)
        }

        private fun setStatus(text: String) {
            statusLabel.text = text
        }
    }
}
