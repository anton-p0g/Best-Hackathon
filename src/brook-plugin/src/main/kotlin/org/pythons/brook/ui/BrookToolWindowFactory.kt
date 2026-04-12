package org.pythons.brook.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
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
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.AbstractBorder

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

    // ── Rounded border utility ──────────────────────────────────────────
    private class RoundedBorder(
        private val color: Color,
        private val radius: Int,
        private val thickness: Int = 1,
        private val insets: Insets = Insets(8, 12, 8, 12)
    ) : AbstractBorder() {
        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, w: Int, h: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.stroke = BasicStroke(thickness.toFloat())
            g2.draw(RoundRectangle2D.Double(
                x + thickness / 2.0, y + thickness / 2.0,
                (w - thickness).toDouble(), (h - thickness).toDouble(),
                radius.toDouble(), radius.toDouble()
            ))
            g2.dispose()
        }

        override fun getBorderInsets(c: Component) = insets
        override fun getBorderInsets(c: Component, ins: Insets): Insets {
            ins.set(insets.top, insets.left, insets.bottom, insets.right)
            return ins
        }
    }

    // ── Pill-shaped colored button ──────────────────────────────────────
    private class StyledButton(
        text: String,
        private val bgColor: Color,
        private val fgColor: Color = Color.WHITE,
        private val hoverColor: Color? = null,
        private val radius: Int = 12
    ) : JButton(text) {

        private var hovering = false

        init {
            isContentAreaFilled = false
            isFocusPainted = false
            isBorderPainted = false
            isOpaque = false
            foreground = fgColor
            font = font.deriveFont(Font.BOLD, font.size + 1f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = JBUI.Borders.empty(10, 24)

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { hovering = true; repaint() }
                override fun mouseExited(e: MouseEvent) { hovering = false; repaint() }
            })
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val fill = when {
                !isEnabled -> JBColor(Color(0xBBBBBB), Color(0x555555))
                hovering && hoverColor != null -> hoverColor
                else -> bgColor
            }
            g2.color = fill
            g2.fillRoundRect(0, 0, width, height, radius, radius)
            g2.dispose()

            super.paintComponent(g)
        }
    }

    inner class BrookPanel(private val project: Project, private val toolWindow: ToolWindow, private val chatPanel: BrookChatPanel) {

        private val LOG = Logger.getInstance(BrookPanel::class.java)
        val root = JPanel(BorderLayout())

        private val exercisesContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = JPanel.CENTER_ALIGNMENT
            isOpaque = false
        }

        // Single browser + panel kept alive for the lifetime of the tool window.
        // Reusing one JBCefBrowser avoids the JCEF creation-while-still-alive crash
        // that causes the second exercise to silently fail to render.
        private val sharedExerciseBrowser = JBCefBrowser()
        private val exercisePanel = MarkdownViewerPanel(sharedExerciseBrowser)
        private var exerciseContent: com.intellij.ui.content.Content? = null

        // ── Theme colours ────────────────────────────────────────────────
        private val accentColor   = JBColor(Color(0x3574F0), Color(0x548AF7))
        private val accentHover   = JBColor(Color(0x2860D8), Color(0x6B9FFF))
        private val secondaryBg   = JBColor(Color(0xF0F0F0), Color(0x3C3F41))
        private val secondaryFg   = JBColor(Color(0x333333), Color(0xDDDDDD))
        private val secondaryHover= JBColor(Color(0xE0E0E0), Color(0x4E5254))
        private val cardBorder    = JBColor(Color(0xD0D0D0), Color(0x505050))
        private val subtitleColor = JBColor(Color(0x777777), Color(0x999999))

        // — Specialty section —
        private val specialtyValueLabel = JBLabel("—").apply {
            font = font.deriveFont(Font.BOLD, font.size + 2f)
            foreground = accentColor
        }

        private val changeSpecialtyButton = StyledButton(
            "Change", secondaryBg, secondaryFg, secondaryHover, 8
        ).apply {
            font = font.deriveFont(Font.PLAIN, font.size - 1f)
            border = JBUI.Borders.empty(4, 14)
            addActionListener { onChangeSpecialtyClicked() }
        }

        // — Status —
        private val statusLabel = JBLabel("Set your specialty and press Start.").apply {
            border = JBUI.Borders.empty(4, 0)
            foreground = subtitleColor
            font = font.deriveFont(Font.ITALIC)
            alignmentX = JBLabel.CENTER_ALIGNMENT
            horizontalAlignment = SwingConstants.CENTER
        }

        private val startButton = StyledButton(
            "▶  Start Brook", accentColor, Color.WHITE, accentHover, 14
        ).apply {
            preferredSize = Dimension(200, 44)
            maximumSize   = Dimension(220, 44)
            addActionListener { onStartClicked() }
        }

        private val generateButton = StyledButton(
            "⚡  Generate Exercise", secondaryBg, secondaryFg, secondaryHover, 10
        ).apply {
            preferredSize = Dimension(200, 38)
            maximumSize   = Dimension(220, 38)
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
                border = JBUI.Borders.empty(16)
                isOpaque = false

                // ── Hero header ─────────────────────────────────────────
                val headerPanel = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    alignmentX = JPanel.CENTER_ALIGNMENT
                    isOpaque = false
                    border = JBUI.Borders.emptyBottom(8)

                    val titleLabel = JBLabel("Brook").apply {
                        font = font.deriveFont(Font.BOLD, font.size + 8f)
                        foreground = accentColor
                        alignmentX = JBLabel.CENTER_ALIGNMENT
                        horizontalAlignment = SwingConstants.CENTER
                    }
                    add(titleLabel)

                    add(Box.createRigidArea(Dimension(0, 2)))

                    val tagline = JBLabel("AI-Powered Coding Exercises").apply {
                        foreground = subtitleColor
                        font = font.deriveFont(Font.PLAIN, font.size + 0f)
                        alignmentX = JBLabel.CENTER_ALIGNMENT
                        horizontalAlignment = SwingConstants.CENTER
                    }
                    add(tagline)
                }
                add(headerPanel)
                add(Box.createRigidArea(Dimension(0, 14)))

                // ── Specialty card ───────────────────────────────────────
                val specialtyCard = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    alignmentX = JPanel.CENTER_ALIGNMENT
                    isOpaque = false
                    border = RoundedBorder(cardBorder, 12, 1, Insets(12, 16, 12, 16))
                    maximumSize = Dimension(Int.MAX_VALUE, 80)

                    val specialtyHeader = JBLabel("SPECIALTY").apply {
                        foreground = subtitleColor
                        font = font.deriveFont(Font.BOLD, font.size - 2f)
                        alignmentX = JBLabel.CENTER_ALIGNMENT
                        horizontalAlignment = SwingConstants.CENTER
                    }
                    add(specialtyHeader)
                    add(Box.createRigidArea(Dimension(0, 4)))

                    val specialtyRow = JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.X_AXIS)
                        isOpaque = false
                        alignmentX = JPanel.CENTER_ALIGNMENT
                        add(Box.createHorizontalGlue())
                        add(specialtyValueLabel)
                        add(Box.createRigidArea(Dimension(8, 0)))
                        add(changeSpecialtyButton)
                        add(Box.createHorizontalGlue())
                    }
                    add(specialtyRow)
                }
                add(specialtyCard)
                add(Box.createRigidArea(Dimension(0, 16)))

                // ── Status label ─────────────────────────────────────────
                val statusWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                    isOpaque = false
                    alignmentX = JPanel.CENTER_ALIGNMENT
                    add(statusLabel)
                }
                add(statusWrapper)
                add(Box.createRigidArea(Dimension(0, 14)))

                // ── Primary action: Start Brook ──────────────────────────
                val startWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                    isOpaque = false
                    alignmentX = JPanel.CENTER_ALIGNMENT
                    maximumSize = Dimension(Int.MAX_VALUE, 50)
                    add(startButton)
                }
                add(startWrapper)
                add(Box.createRigidArea(Dimension(0, 8)))

                // ── Secondary action: Generate Exercise ──────────────────
                val generateWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                    isOpaque = false
                    alignmentX = JPanel.CENTER_ALIGNMENT
                    maximumSize = Dimension(Int.MAX_VALUE, 44)
                    add(generateButton)
                }
                add(generateWrapper)
                add(Box.createRigidArea(Dimension(0, 20)))

                // ── Section header: Available Exercises ───────────────────
                val sectionHeader = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    isOpaque = false
                    alignmentX = JPanel.CENTER_ALIGNMENT
                    maximumSize = Dimension(Int.MAX_VALUE, 24)

                    val line1 = JSeparator().apply { maximumSize = Dimension(Int.MAX_VALUE, 1) }
                    val sectionTitle = JBLabel("  Available Exercises  ").apply {
                        foreground = subtitleColor
                        font = font.deriveFont(Font.BOLD, font.size + 0f)
                    }
                    val line2 = JSeparator().apply { maximumSize = Dimension(Int.MAX_VALUE, 1) }

                    add(line1)
                    add(sectionTitle)
                    add(line2)
                }
                add(sectionHeader)
                add(Box.createRigidArea(Dimension(0, 12)))

                // ── Exercise list ────────────────────────────────────────
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
            val loadingLabel = JBLabel("Fetching exercises…").apply {
                foreground = subtitleColor
                font = font.deriveFont(Font.ITALIC)
                alignmentX = JBLabel.CENTER_ALIGNMENT
                horizontalAlignment = SwingConstants.CENTER
            }
            val loadingWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                isOpaque = false
                alignmentX = JPanel.CENTER_ALIGNMENT
                add(loadingLabel)
            }
            exercisesContainer.add(loadingWrapper)
            refreshContainer(exercisesContainer)

            CoroutineScope(Dispatchers.IO).launch {
                val result = BrookApiClient.getExercises()
                ApplicationManager.getApplication().invokeLater {
                    exercisesContainer.removeAll()
                    if (result.isSuccess) {
                        val exercises = result.getOrNull() ?: emptyList()
                        if (exercises.isEmpty()) {
                            val emptyLabel = JBLabel("No exercises yet — generate one!").apply {
                                foreground = subtitleColor
                                alignmentX = JBLabel.CENTER_ALIGNMENT
                                horizontalAlignment = SwingConstants.CENTER
                            }
                            val emptyWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                                isOpaque = false
                                alignmentX = JPanel.CENTER_ALIGNMENT
                                add(emptyLabel)
                            }
                            exercisesContainer.add(emptyWrapper)
                        } else {
                            exercises.forEach { (id, label) ->
                                val exerciseBtn = createExerciseCard(label) {
                                    openExerciseTab(id, label)
                                }
                                exercisesContainer.add(exerciseBtn)
                                exercisesContainer.add(Box.createRigidArea(Dimension(0, 6)))
                            }
                        }
                    } else {
                        val errorLabel = JBLabel("Failed to load exercises.").apply {
                            foreground = JBColor(Color(0xCC3333), Color(0xFF6666))
                            alignmentX = JBLabel.CENTER_ALIGNMENT
                            horizontalAlignment = SwingConstants.CENTER
                        }
                        val errorWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                            isOpaque = false
                            alignmentX = JPanel.CENTER_ALIGNMENT
                            add(errorLabel)
                        }
                        exercisesContainer.add(errorWrapper)
                    }
                    refreshContainer(exercisesContainer)
                }
            }
        }

        /**
         * Creates a card-style button for each exercise with a rounded border,
         * accent-colored left stripe, hover effect, and centered layout.
         */
        private fun createExerciseCard(label: String, onClick: () -> Unit): JPanel {
            val cardBg     = JBColor(Color(0xF7F8FA), Color(0x2B2D30))
            val cardHoverBg= JBColor(Color(0xEBEDF0), Color(0x35373A))
            val stripColor = accentColor

            val card = object : JPanel() {
                var hovering = false
                init {
                    layout = BorderLayout()
                    isOpaque = false
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    border = JBUI.Borders.empty(0)
                    preferredSize = Dimension(200, 38)
                    maximumSize = Dimension(Int.MAX_VALUE, 42)
                    alignmentX = JPanel.CENTER_ALIGNMENT

                    addMouseListener(object : MouseAdapter() {
                        override fun mouseEntered(e: MouseEvent) { hovering = true; repaint() }
                        override fun mouseExited(e: MouseEvent) { hovering = false; repaint() }
                        override fun mouseClicked(e: MouseEvent) { onClick() }
                    })
                }

                override fun paintComponent(g: Graphics) {
                    val g2 = g.create() as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                    // Background
                    g2.color = if (hovering) cardHoverBg else cardBg
                    g2.fillRoundRect(0, 0, width, height, 10, 10)

                    // Left accent stripe
                    g2.color = stripColor
                    g2.fillRoundRect(0, 0, 4, height, 4, 4)

                    // Subtle border
                    g2.color = cardBorder
                    g2.stroke = BasicStroke(1f)
                    g2.drawRoundRect(0, 0, width - 1, height - 1, 10, 10)

                    g2.dispose()
                    super.paintComponent(g)
                }
            }

            val textLabel = JBLabel("  📝  $label").apply {
                font = font.deriveFont(Font.PLAIN, font.size + 0f)
                border = JBUI.Borders.empty(0, 8)
            }
            val arrowLabel = JBLabel("›").apply {
                font = font.deriveFont(Font.BOLD, font.size + 4f)
                foreground = subtitleColor
                border = JBUI.Borders.empty(0, 0, 0, 10)
            }

            card.add(textLabel, BorderLayout.CENTER)
            card.add(arrowLabel, BorderLayout.EAST)

            return card
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
