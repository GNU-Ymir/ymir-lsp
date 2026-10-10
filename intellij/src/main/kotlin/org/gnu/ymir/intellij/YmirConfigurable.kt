package org.gnu.ymir.intellij

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

/**
 * The page of the settings under Languages & Frameworks, choosing the language server, its memory budget and
 * the documents it compiles, gyllir and gdb.
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
            row("Memory budget (MiB):") {
                intTextField(0..Int.MAX_VALUE)
                    .bindIntText(settings::memoryBudget)
                    .comment("The memory the language server may use before it is stopped, <code>0</code> for no limit. " +
                             "Taken into account when the server is restarted.")
            }
            row("Compiled documents:") {
                intTextField(0..Int.MAX_VALUE)
                    .bindIntText(settings::compiledDocuments)
                    .comment("The number of open documents the language server compiles, the most recently used, " +
                             "<code>0</code> for all. Taken into account when the server is restarted.")
            }
            row("Gyllir:") {
                textField()
                    .bindText(settings::gyllirPath)
                    .align(AlignX.FILL)
                    .comment("The path of <code>gyllir</code>, or its name to look it up in the PATH.")
            }
            row("GDB:") {
                textField()
                    .bindText(settings::gdbPath)
                    .align(AlignX.FILL)
                    .comment("The path of <code>gdb</code>, 14 or later, or its name to look it up in the PATH. " +
                             "Debugs the programs of the Gyllir run configurations.")
            }
        }
    }
}
