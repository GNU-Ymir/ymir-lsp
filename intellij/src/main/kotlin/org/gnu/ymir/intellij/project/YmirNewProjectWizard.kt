package org.gnu.ymir.intellij.project

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GitNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.ide.wizard.newProjectWizardBaseStepWithoutGap
import com.intellij.ide.wizard.setupProjectFromBuilder
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import org.gnu.ymir.intellij.YmirIcons
import java.nio.file.Path
import javax.swing.Icon

/**
 * The Ymir entry of the New Project dialog, creating a gyllir package.
 */
class YmirNewProjectWizard : GeneratorNewProjectWizard {

    override val id: String = "ymir"

    override val name: String = "Ymir"

    override val icon: Icon = YmirIcons.Ymir

    override val description: String = "A gyllir package: its gyllir.toml, src/ and test/."

    override fun createStep(context: WizardContext): NewProjectWizardStep =
        RootNewProjectWizardStep(context)
            .nextStep(::newProjectWizardBaseStepWithoutGap)
            .nextStep(::GitNewProjectWizardStep)
            .nextStep(::Step)

    private class Step(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

        private val typeProperty = propertyGraph.property(YmirPackageType.EXECUTABLE)
        private val authorProperty = propertyGraph.property(System.getProperty("user.name").orEmpty())
        private val descriptionProperty = propertyGraph.property("A minimal Ymir app")
        private val licenseProperty = propertyGraph.property("proprietary")

        override fun setupUI(builder: Panel) {
            with(builder) {
                row("Type:") {
                    segmentedButton(YmirPackageType.entries) { text = it.label }
                        .bind(typeProperty)
                }
                row("Author:") {
                    textField().bindText(authorProperty).align(AlignX.FILL)
                }
                row("Description:") {
                    textField().bindText(descriptionProperty).align(AlignX.FILL)
                }
                row("License:") {
                    textField().bindText(licenseProperty)
                }
            }
        }

        override fun setupProject(project: Project) {
            val data = baseData!!
            val name = YmirPackage.packageName(data.name)
            val dir = Path.of(data.path, data.name)
            YmirPackage(
                name = name,
                type = typeProperty.get(),
                authors = listOf(authorProperty.get().trim()).filter { it.isNotEmpty() },
                description = descriptionProperty.get().trim(),
                license = licenseProperty.get().trim(),
                registry = YmirPackage.defaultRegistry(name),
            ).writeTo(dir)

            val builder = ModuleTypeManager.getInstance().findByID("GENERAL_MODULE").createModuleBuilder()
            val module = setupProjectFromBuilder(project, builder) ?: return
            val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir) ?: return
            ModuleRootModificationUtil.updateModel(module) { model ->
                val entry = model.contentEntries.firstOrNull { it.file == root } ?: model.addContentEntry(root)
                root.findChild("src")?.let { entry.addSourceFolder(it, false) }
                root.findChild("test")?.let { entry.addSourceFolder(it, true) }
                entry.addExcludeFolder(root.url + "/.target")
                entry.addExcludeFolder(root.url + "/.deps")
            }
        }
    }
}
