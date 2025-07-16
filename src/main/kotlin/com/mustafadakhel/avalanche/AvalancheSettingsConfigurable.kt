package com.mustafadakhel.avalanche

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class AvalancheSettingsConfigurable(
    private val project: Project
) : Configurable {
    private val settings: AutoUpdateSettingsService = project.getService(AutoUpdateSettingsService::class.java)

    private val intervalField = JBTextField()
    private val conflictCombo = ComboBox(AutoUpdateSettingsService.ConflictHandlingMode.values())
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Avalanche"

    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Update interval (minutes):", intervalField, 1, false)
                .addLabeledComponent("Conflict handling:", conflictCombo, 1, false)
                .panel
        }
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val interval = intervalField.text.toLongOrNull() ?: settings.state.intervalMinutes
        val mode = conflictCombo.selectedItem as AutoUpdateSettingsService.ConflictHandlingMode
        return interval != settings.state.intervalMinutes || mode != settings.state.conflictHandlingMode
    }

    override fun apply() {
        val interval = intervalField.text.toLongOrNull() ?: settings.state.intervalMinutes
        val mode = conflictCombo.selectedItem as AutoUpdateSettingsService.ConflictHandlingMode
        settings.loadState(
            settings.state.copy(
                intervalMinutes = interval,
                conflictHandlingMode = mode
            )
        )
    }

    override fun reset() {
        intervalField.text = settings.state.intervalMinutes.toString()
        conflictCombo.selectedItem = settings.state.conflictHandlingMode
    }

    override fun disposeUIResources() {
        panel = null
    }
}
