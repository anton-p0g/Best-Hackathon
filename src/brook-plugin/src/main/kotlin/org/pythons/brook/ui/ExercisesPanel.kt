package org.pythons.brook.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import org.pythons.brook.runner.BrookApiClient
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ExercisesPanel(
    private val onExerciseSelected: (exerciseId: String, fileName: String) -> Unit
) {

    val root = JPanel(BorderLayout())
    private val content = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(16)
    }

    init {
        root.add(JBScrollPane(content), BorderLayout.CENTER)
        loadExercises()
    }

    private fun loadExercises() {
        content.removeAll()
        content.add(JBLabel("Loading exercises...").apply {
            alignmentX = JBLabel.LEFT_ALIGNMENT
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        })
        content.revalidate()
        content.repaint()

        CoroutineScope(Dispatchers.IO).launch {
            val result = BrookApiClient.getExercises()
            
            withContext(Dispatchers.Main) {
                content.removeAll()
                
                val title = JBLabel("Choose an exercise").apply {
                    font = font.deriveFont(Font.BOLD, font.size + 3f)
                    alignmentX = JBLabel.LEFT_ALIGNMENT
                    border = JBUI.Borders.emptyBottom(8)
                }
                content.add(title)

                val subtitle = JBLabel(
                    "<html><small>Select an exercise to start working on it.</small></html>"
                ).apply {
                    alignmentX = JBLabel.LEFT_ALIGNMENT
                    border = JBUI.Borders.emptyBottom(16)
                }
                content.add(subtitle)

                if (result.isSuccess) {
                    val exercises = result.getOrNull() ?: emptyList()
                    if (exercises.isEmpty()) {
                        content.add(JBLabel("No exercises found on server.").apply {
                            alignmentX = JBLabel.LEFT_ALIGNMENT
                            foreground = JBUI.CurrentTheme.Label.disabledForeground()
                        })
                    } else {
                        exercises.forEach { (id, label) ->
                            val btn = JButton(label).apply {
                                alignmentX = JButton.LEFT_ALIGNMENT
                                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height + 8)
                                addActionListener { onExerciseSelected(id, "EXERCISE.html") }
                            }
                            content.add(btn)
                            content.add(Box.createRigidArea(Dimension(0, 8)))
                        }
                    }
                } else {
                    content.add(JBLabel("Error: ${result.exceptionOrNull()?.message}").apply {
                        alignmentX = JBLabel.LEFT_ALIGNMENT
                        foreground = JBUI.CurrentTheme.Label.errorForeground()
                    })
                    
                    val retryBtn = JButton("Retry").apply {
                        alignmentX = JButton.LEFT_ALIGNMENT
                        addActionListener { loadExercises() }
                    }
                    content.add(Box.createRigidArea(Dimension(0, 8)))
                    content.add(retryBtn)
                }
                
                content.revalidate()
                content.repaint()
            }
        }
    }
}
