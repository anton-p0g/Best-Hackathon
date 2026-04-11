package org.pythons.brook.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel

class HintToolWindow(private val toolWindow: ToolWindow) {

    private val hintArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        border = JBUI.Borders.empty(12)
        text = "Press \"Brook: Get Hint\" or Ctrl+Alt+H to receive a hint."
    }

    val content: JPanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(hintArea), BorderLayout.CENTER)
    }

    fun updateHint(hint: String) {
        hintArea.text = hint
        hintArea.caretPosition = 0
        toolWindow.show()
    }

    companion object {

        private const val TOOL_WINDOW_ID = "Brook Hints"

        /**
         * Shows the hint in the Brook tool window.
         * Registers the window on first call; subsequent calls just update the text.
         */
        fun showHint(project: Project, hint: String) {
            val manager = ToolWindowManager.getInstance(project)

            var toolWindow = manager.getToolWindow(TOOL_WINDOW_ID)

            if (toolWindow == null) {
                toolWindow = manager.registerToolWindow(TOOL_WINDOW_ID) {
                    anchor = ToolWindowAnchor.BOTTOM
                    canCloseContent = false
                }

                val hintPanel = HintToolWindow(toolWindow)
                val contentFactory = com.intellij.ui.content.ContentFactory.getInstance()
                val content = contentFactory.createContent(hintPanel.content, "", false)
                toolWindow.contentManager.addContent(content)

                // Store reference so we can update the text later
                toolWindow.component.putClientProperty("brookPanel", hintPanel)
            }

            val hintPanel = toolWindow.component
                .getClientProperty("brookPanel") as? HintToolWindow

            hintPanel?.updateHint(hint) ?: run {
                // Fallback: panel reference lost, recreate content
                toolWindow.contentManager.removeAllContents(true)
                val newPanel = HintToolWindow(toolWindow)
                val contentFactory = com.intellij.ui.content.ContentFactory.getInstance()
                val content = contentFactory.createContent(newPanel.content, "", false)
                toolWindow.contentManager.addContent(content)
                toolWindow.component.putClientProperty("brookPanel", newPanel)
                newPanel.updateHint(hint)
            }
        }
    }
}
