package org.gnu.ymir.intellij

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider.PluginBundle
import kotlin.io.path.isDirectory

/**
 * The TextMate bundle highlighting the `.yr` files, shipped in the `textmate`
 * directory of the plugin.
 */
class YmirTextMateBundleProvider : TextMateBundleProvider {

    override fun getBundles(): List<PluginBundle> {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID)) ?: return emptyList()
        val bundle = plugin.pluginPath.resolve("textmate").resolve("ymir")
        return if (bundle.isDirectory()) listOf(PluginBundle("Ymir", bundle)) else emptyList()
    }

    companion object {
        const val PLUGIN_ID = "org.gnu.ymir"
    }
}
