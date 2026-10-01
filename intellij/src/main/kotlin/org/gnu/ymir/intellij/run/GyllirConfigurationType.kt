package org.gnu.ymir.intellij.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import org.gnu.ymir.intellij.YmirIcons

/**
 * The run configurations calling gyllir on a package.
 */
class GyllirConfigurationType : ConfigurationTypeBase(
    ID,
    "Gyllir",
    "Build, run or test a gyllir package",
    NotNullLazyValue.createValue { YmirIcons.Ymir },
) {
    init {
        addFactory(GyllirConfigurationFactory(this))
    }

    val factory: ConfigurationFactory
        get() = configurationFactories.single()

    companion object {
        const val ID = "GyllirRunConfiguration"

        fun getInstance(): GyllirConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(GyllirConfigurationType::class.java)
    }
}

class GyllirConfigurationFactory(type: GyllirConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "Gyllir"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        GyllirRunConfiguration(project, this, "Gyllir")

    override fun getOptionsClass(): Class<out BaseState> = GyllirRunConfigurationOptions::class.java
}
