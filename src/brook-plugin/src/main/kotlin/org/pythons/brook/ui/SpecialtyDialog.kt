package org.pythons.brook.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel

class SpecialtyDialog(project: Project, current: String = "") : DialogWrapper(project) {

    private val textField = JBTextField(current).apply {
        preferredSize = Dimension(260, 30)
        emptyText.text = "e.g. Backend, Data Science, DevOps…"
    }

    init {
        title = "Brook — Set Specialty"
        setOKButtonText("Confirm")
        setCancelButtonText("Cancel")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(6, 8, 6, 8)
        }

        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JBLabel("What is your development specialty?").apply {
            font = font.deriveFont(font.size + 1f)
        }, gbc)

        gbc.gridy = 1
        panel.add(JBLabel(
            "<html><small>Brook will tailor the issues to your area of expertise.</small></html>"
        ), gbc)

        gbc.gridy = 2; gbc.insets = Insets(12, 8, 6, 8)
        panel.add(textField, gbc)

        panel.preferredSize = Dimension(320, 130)
        return panel
    }

    override fun doOKAction() {
        if (textField.text.isBlank()) {
            setErrorText("Please enter your specialty.", textField)
            return
        }
        super.doOKAction()
    }

    fun getSelectedSpecialty(): String = textField.text.trim()
}
