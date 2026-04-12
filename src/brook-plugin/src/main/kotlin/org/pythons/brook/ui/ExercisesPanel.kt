package org.pythons.brook.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import org.pythons.brook.runner.BrookApiClient
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.AbstractBorder

class ExercisesPanel(
    private val onExerciseSelected: (exerciseId: String, fileName: String) -> Unit
) {

    val root = JPanel(BorderLayout())
    private val content = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(20, 16)
        isOpaque = false
    }

    // ── Theme colours ────────────────────────────────────────────────
    private val accentColor    = JBColor(Color(0x3574F0), Color(0x548AF7))
    private val subtitleColor  = JBColor(Color(0x777777), Color(0x999999))
    private val cardBg         = JBColor(Color(0xF7F8FA), Color(0x2B2D30))
    private val cardHoverBg    = JBColor(Color(0xEBEDF0), Color(0x35373A))
    private val cardBorder     = JBColor(Color(0xD0D0D0), Color(0x505050))

    init {
        root.add(JBScrollPane(content), BorderLayout.CENTER)
        loadExercises()
    }

    private fun loadExercises() {
        content.removeAll()

        val loadingLabel = JBLabel("Loading exercises…").apply {
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
        content.add(loadingWrapper)
        content.revalidate()
        content.repaint()

        CoroutineScope(Dispatchers.IO).launch {
            val result = BrookApiClient.getExercises()
            
            withContext(Dispatchers.Main) {
                content.removeAll()
                
                // ── Title ────────────────────────────────────────────
                val title = JBLabel("Choose an exercise").apply {
                    font = font.deriveFont(Font.BOLD, font.size + 4f)
                    foreground = accentColor
                    alignmentX = JBLabel.CENTER_ALIGNMENT
                    horizontalAlignment = SwingConstants.CENTER
                    border = JBUI.Borders.emptyBottom(4)
                }
                val titleWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                    isOpaque = false
                    alignmentX = JPanel.CENTER_ALIGNMENT
                    add(title)
                }
                content.add(titleWrapper)

                // ── Subtitle ─────────────────────────────────────────
                val subtitle = JBLabel("Select an exercise to start working on it.").apply {
                    foreground = subtitleColor
                    alignmentX = JBLabel.CENTER_ALIGNMENT
                    horizontalAlignment = SwingConstants.CENTER
                    border = JBUI.Borders.emptyBottom(16)
                }
                val subtitleWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                    isOpaque = false
                    alignmentX = JPanel.CENTER_ALIGNMENT
                    add(subtitle)
                }
                content.add(subtitleWrapper)

                if (result.isSuccess) {
                    val exercises = result.getOrNull() ?: emptyList()
                    if (exercises.isEmpty()) {
                        val emptyLabel = JBLabel("No exercises found on server.").apply {
                            foreground = subtitleColor
                            alignmentX = JBLabel.CENTER_ALIGNMENT
                            horizontalAlignment = SwingConstants.CENTER
                        }
                        val emptyWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                            isOpaque = false
                            alignmentX = JPanel.CENTER_ALIGNMENT
                            add(emptyLabel)
                        }
                        content.add(emptyWrapper)
                    } else {
                        exercises.forEach { (id, label) ->
                            content.add(createExerciseCard(label) {
                                onExerciseSelected(id, "EXERCISE.html")
                            })
                            content.add(Box.createRigidArea(Dimension(0, 8)))
                        }
                    }
                } else {
                    val errorLabel = JBLabel("Error: ${result.exceptionOrNull()?.message}").apply {
                        foreground = JBColor(Color(0xCC3333), Color(0xFF6666))
                        alignmentX = JBLabel.CENTER_ALIGNMENT
                        horizontalAlignment = SwingConstants.CENTER
                    }
                    val errorWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                        isOpaque = false
                        alignmentX = JPanel.CENTER_ALIGNMENT
                        add(errorLabel)
                    }
                    content.add(errorWrapper)
                    content.add(Box.createRigidArea(Dimension(0, 10)))
                    
                    val retryBtn = object : JButton("Retry") {
                        private var hovering = false
                        init {
                            isContentAreaFilled = false
                            isFocusPainted = false
                            isBorderPainted = false
                            isOpaque = false
                            foreground = Color.WHITE
                            font = font.deriveFont(Font.BOLD)
                            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            border = JBUI.Borders.empty(8, 24)
                            addMouseListener(object : MouseAdapter() {
                                override fun mouseEntered(e: MouseEvent) { hovering = true; repaint() }
                                override fun mouseExited(e: MouseEvent) { hovering = false; repaint() }
                            })
                            addActionListener { loadExercises() }
                        }
                        override fun paintComponent(g: Graphics) {
                            val g2 = g.create() as Graphics2D
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                            g2.color = if (hovering) JBColor(Color(0x2860D8), Color(0x6B9FFF)) else accentColor
                            g2.fillRoundRect(0, 0, width, height, 10, 10)
                            g2.dispose()
                            super.paintComponent(g)
                        }
                    }
                    val retryWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                        isOpaque = false
                        alignmentX = JPanel.CENTER_ALIGNMENT
                        add(retryBtn)
                    }
                    content.add(retryWrapper)
                }
                
                content.revalidate()
                content.repaint()
            }
        }
    }

    /**
     * Creates a card-style button for each exercise with a rounded border,
     * accent-colored left stripe, hover effect, and centered layout.
     */
    private fun createExerciseCard(label: String, onClick: () -> Unit): JPanel {
        val card = object : JPanel() {
            var hovering = false
            init {
                layout = BorderLayout()
                isOpaque = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                border = JBUI.Borders.empty(0)
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
                g2.color = accentColor
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
}
