package org.pythons.brook.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.pythons.brook.BrookState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import org.pythons.brook.runner.BrookApiClient

class BrookChatPanel(private val project: Project) {
    val root = JPanel(BorderLayout())
    
    private var hasWon = false

    // 1. Usar JEditorPane con HTML para un formato enriquecido
    private val chatHistory = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(12)
    }

    // Usaremos un StringBuilder para acumular el contenido HTML de forma segura
    private val messagesHistoryHtml = StringBuilder()
    private val activeStreamText = StringBuilder()

    private val inputField = JBTextField().apply {
        emptyText.text = "Pregunta algo sobre el código..."
        preferredSize = Dimension(0, 32)
    }

    private val sendButton = JButton("Enviar").apply {
        preferredSize = Dimension(80, 32)
    }

    private val hintButton = JButton("Get Hint").apply {
        addActionListener { onHintClicked() }
    }

    private val verifyButton = JButton("✅ Check Solution").apply {
        addActionListener { onVerifyClicked() }
    }

    private val activeExerciseLabel = com.intellij.ui.components.JBLabel("Active Exercise: None").apply {
        font = font.deriveFont(java.awt.Font.BOLD)
        border = JBUI.Borders.empty(8)
        foreground = com.intellij.ui.JBColor.GRAY
    }

    init {
        val scrollPane = JBScrollPane(chatHistory).apply {
            border = JBUI.Borders.empty()
        }

        val inputPanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1, 0, 0, 0),
                JBUI.Borders.empty(10)
            )
            add(inputField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        val actionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(hintButton)
            add(javax.swing.Box.createHorizontalStrut(8))
            add(verifyButton)
            border = JBUI.Borders.empty(4, 8, 0, 8)
        }

        val bottomContainer = JPanel(BorderLayout()).apply {
            add(actionPanel, BorderLayout.NORTH)
            add(inputPanel, BorderLayout.CENTER)
        }

        root.add(activeExerciseLabel, BorderLayout.NORTH)
        root.add(scrollPane, BorderLayout.CENTER)
        root.add(bottomContainer, BorderLayout.SOUTH)

        // Mensaje de bienvenida inicial
        appendMessage("Brook", "Hello! I'm Brook, your AI coding assistant and I will be helping you complete the exercise. Let's get started!", true)

        // Eventos
        sendButton.addActionListener { sendMessage() }
        inputField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) sendMessage()
            }
        })

        // Re-enable buttons if the user edits their code after a win
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (hasWon) {
                    hasWon = false
                    SwingUtilities.invokeLater {
                        setLoadingState(false)
                    }
                }
            }
        }, project)
    }

    fun updateActiveExercise(title: String) {
        activeExerciseLabel.text = "Active Exercise: $title"
    }

    /**
     * Genera la burbuja de chat con estilo de nube crudo
     */
    private fun buildBubbleHtml(sender: String, text: String, isBot: Boolean): String {
        // Safe colors
        val bubbleColor = if (isBot) JBColor(Color(0xE2E2E2), Color(0x3B3C3D))
                          else JBColor(Color(0xD1E4FF), Color(0x2D5488))

        val bgColorHex = ColorUtil.toHtmlColor(bubbleColor)
        val textColorHex = ColorUtil.toHtmlColor(JBColor.foreground())
        val labelColorHex = if (isBot) "#3574F0" else "#548AF7"

        val align = if (isBot) "left" else "right"

        var safeText = text.replace("\n", "<br>")

        return """
            <table width="100%" border="0" cellspacing="0" cellpadding="0" style="margin-bottom: 5px;">
                <tr>
                    <td align="$align">
                        <table border="0" cellspacing="0" cellpadding="8" bgcolor="$bgColorHex">
                            <tr>
                                <td>
                                    <font face="Verdana, sans-serif" size="2" color="$labelColorHex">
                                        <b>${if (isBot) "BROOK Agent" else "Me"}</b>
                                    </font><br>
                                    <font face="Verdana, sans-serif" size="3" color="$textColorHex">
                                        ${safeText}
                                    </font>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        """.trimIndent()
    }

    private fun appendMessage(sender: String, text: String, isBot: Boolean) {
        messagesHistoryHtml.append(buildBubbleHtml(sender, text, isBot))
        updateHtmlView()
    }

    private fun updateHtmlView() {
        val fullHtml = StringBuilder(messagesHistoryHtml)
        if (activeStreamText.isNotEmpty()) {
            fullHtml.append(buildBubbleHtml("Brook", activeStreamText.toString(), true))
        }

        val finalHtml = "<html><body style='padding: 5px;'>${fullHtml}</body></html>"

        SwingUtilities.invokeLater {
            try {
                chatHistory.text = finalHtml
                // Auto-scroll al final
                val scrollPane = SwingUtilities.getAncestorOfClass(JBScrollPane::class.java, chatHistory) as? JBScrollPane
                scrollPane?.verticalScrollBar?.let { bar ->
                    bar.value = bar.maximum
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendMessage() {
        val text = inputField.text.trim()
        if (text.isEmpty()) return

        appendMessage("Me", text, false)
        inputField.text = ""
        setLoadingState(true)

        // Start SSE Stream Tracker
        activeStreamText.clear()
        updateHtmlView()

        val activeFileText = FileEditorManager.getInstance(project).selectedTextEditor?.document?.text ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            val state = BrookState.getInstance(project)
            val result = BrookApiClient.chatStream(
                repoPath = "target_repo",
                specialty = state.specialty,
                exerciseId = state.activeExerciseId,
                message = text,
                activeFile = activeFileText
            ) { chunk ->
                // Consume Chunk real time!
                activeStreamText.append(chunk)

                // Debounce UI rendering incrementally
                updateHtmlView()
            }

            // Once streaming sequence is complete
            ApplicationManager.getApplication().invokeLater {
                if (result.isSuccess) {
                    // Lock the final output chunk into actual history
                    val resp = result.getOrNull() ?: ""
                    activeStreamText.clear()
                    appendMessage("Brook", resp, true)
                } else {
                    activeStreamText.clear()
                    appendMessage("Brook", "There was an error connecting to the backend bot.", true)
                }
                setLoadingState(false)
            }
        }
    }

    private fun onHintClicked() {
        val state = BrookState.getInstance(project)
        appendMessage("Me", "Requested a hint.", false)
        setLoadingState(true)

        activeStreamText.clear()
        updateHtmlView()

        val activeFileText = FileEditorManager.getInstance(project).selectedTextEditor?.document?.text ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            val result = BrookApiClient.hintStream(
                repoPath = "target_repo",
                specialty = state.specialty,
                exerciseId = state.activeExerciseId,
                activeFile = activeFileText
            ) { chunk ->
                activeStreamText.append(chunk)
                updateHtmlView()
            }

            ApplicationManager.getApplication().invokeLater {
                if (result.isSuccess) {
                    val resp = result.getOrNull() ?: ""
                    activeStreamText.clear()
                    appendMessage("Brook", resp, true)
                } else {
                    activeStreamText.clear()
                    appendMessage("Brook", "There was an error connecting to the backend bot.", true)
                }
                setLoadingState(false)
            }
        }
    }

    private fun onVerifyClicked() {
        val state = BrookState.getInstance(project)
        appendMessage("Me", "Checking solution...", false)
        setLoadingState(true)
        
        val activeFileText = FileEditorManager.getInstance(project).selectedTextEditor?.document?.text ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            val result = BrookApiClient.verify(
                repoPath = "target_repo",
                specialty = state.specialty,
                exerciseId = state.activeExerciseId,
                activeFile = activeFileText
            )

            ApplicationManager.getApplication().invokeLater {
                if (result.isSuccess) {
                    val verdict = result.getOrNull()!!
                    if (verdict.solved) {
                        hasWon = true
                        appendMessage("Brook", "🎉 Correct!\n\n${verdict.feedback}", true)
                    } else {
                        appendMessage("Brook", "❌ Not quite right.\n\n${verdict.feedback}", true)
                    }
                } else {
                    appendMessage("Brook", "There was an error connecting to the backend bot.", true)
                }
                setLoadingState(false)
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        val uiEnabled = !isLoading && !hasWon

        inputField.isEnabled = uiEnabled
        sendButton.isEnabled = uiEnabled
        hintButton.isEnabled = uiEnabled
        verifyButton.isEnabled = uiEnabled
        
        sendButton.text = if (isLoading) "..." else "Enviar"
        if (uiEnabled) inputField.requestFocus()
    }
}