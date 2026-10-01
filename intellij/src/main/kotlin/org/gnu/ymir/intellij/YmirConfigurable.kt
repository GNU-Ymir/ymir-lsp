package org.gnu.ymir.intellij

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

/**
 * The page of the settings under Languages & Frameworks, choosing the language server.
 */
class YmirConfigurable : BoundConfigurable("Ymir") {

    override fun createPanel(): DialogPanel {
        val settings = YmirSettings.getInstance()
        return panel {
            row("Language server:") {
                textField()
                    .bindText(settings::serverPath)
                    .align(AlignX.FILL)
                    .comment("The path of <code>ymir-lsp</code>, or its name to look it up in the PATH. " +
                             "Taken into account when the server is restarted.")
            }
        }
    }
}
