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
import com.intellij.ui.layout.selectedValueIs
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

    private val filter = JBTextField()

    private val envFile = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("Environment File"))
    }

    override fun resetEditorFrom(configuration: GyllirRunConfiguration) {
        command.selectedItem = configuration.command
        arguments.text = configuration.arguments
        workingDirectory.text = configuration.workingDirectory
        filter.text = configuration.filter
        envFile.text = configuration.envFile
    }

    override fun applyEditorTo(configuration: GyllirRunConfiguration) {
        configuration.command = command.selectedItem as GyllirCommand
        configuration.arguments = arguments.text
        configuration.workingDirectory = workingDirectory.text
        configuration.filter = filter.text
        configuration.envFile = envFile.text
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
        row("Test filter:") {
            cell(filter).align(AlignX.FILL)
                .comment("The tests to run, <code>::</code>-separated, <code>*</code> standing for any characters (e.g. <code>server::*</code>)")
        }.visibleIf(command.selectedValueIs(GyllirCommand.TEST))
        row("Environment file:") {
            cell(envFile).align(AlignX.FILL)
                .comment("<code>KEY=VALUE</code> lines set in the environment of the tests")
        }.visibleIf(command.selectedValueIs(GyllirCommand.TEST))
    }
}
