package org.gnu.ymir.intellij.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class GyllirSettingsEditor(project: Project) : SettingsEditor<GyllirRunConfiguration>() {

    private val command = ComboBox(GyllirCommand.entries.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create("") { it.label }
    }

    private val arguments = JBTextField()

    private val workingDirectory = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Package Directory"))
    }

    override fun resetEditorFrom(configuration: GyllirRunConfiguration) {
        command.selectedItem = configuration.command
        arguments.text = configuration.arguments
        workingDirectory.text = configuration.workingDirectory
    }

    override fun applyEditorTo(configuration: GyllirRunConfiguration) {
        configuration.command = command.selectedItem as GyllirCommand
        configuration.arguments = arguments.text
        configuration.workingDirectory = workingDirectory.text
    }

    override fun createEditor(): JComponent = panel {
        row("Command:") {
            cell(command)
        }
        row("Arguments:") {
            cell(arguments).align(AlignX.FILL)
                .comment("Passed to the program by <code>run</code>, to gyllir otherwise (e.g. <code>--release</code>)")
        }
        row("Package directory:") {
            cell(workingDirectory).align(AlignX.FILL)
        }
    }
}
