package org.gnu.ymir.intellij

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * The settings of the Ymir support, shared by every project.
 */
@Service(Service.Level.APP)
@State(name = "YmirSettings", storages = [Storage("ymir.xml")])
class YmirSettings : SimplePersistentStateComponent<YmirSettings.Options>(Options()) {

    class Options : BaseState() {
        var serverPath by string(DEFAULT_SERVER)
        var gyllirPath by string(DEFAULT_GYLLIR)
        var gdbPath by string(DEFAULT_GDB)
        var memoryBudget by property(DEFAULT_MEMORY_BUDGET)
        var compiledDocuments by property(DEFAULT_COMPILED_DOCUMENTS)
    }

    /** The command starting the language server, a path or a name looked up in the PATH. */
    var serverPath: String
        get() = state.serverPath?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER
        set(value) {
            state.serverPath = value.trim().ifEmpty { DEFAULT_SERVER }
        }

    /** The command of gyllir, run by the gyllir run configurations. */
    var gyllirPath: String
        get() = state.gyllirPath?.takeIf { it.isNotBlank() } ?: DEFAULT_GYLLIR
        set(value) {
            state.gyllirPath = value.trim().ifEmpty { DEFAULT_GYLLIR }
        }

    /** The command of gdb, debugging the programs of the gyllir run configurations. */
    var gdbPath: String
        get() = state.gdbPath?.takeIf { it.isNotBlank() } ?: DEFAULT_GDB
        set(value) {
            state.gdbPath = value.trim().ifEmpty { DEFAULT_GDB }
        }

    /** The memory the language server may use, in MiB, `0` for no limit. */
    var memoryBudget: Int
        get() = state.memoryBudget.coerceAtLeast(0)
        set(value) {
            state.memoryBudget = value.coerceAtLeast(0)
        }

    /** The number of open documents the language server compiles at most, the most recently used, `0` for all. */
    var compiledDocuments: Int
        get() = state.compiledDocuments.coerceAtLeast(0)
        set(value) {
            state.compiledDocuments = value.coerceAtLeast(0)
        }

    companion object {
        const val DEFAULT_SERVER = "ymir-lsp"
        const val DEFAULT_GYLLIR = "gyllir"
        const val DEFAULT_GDB = "gdb"
        const val DEFAULT_MEMORY_BUDGET = 2048
        const val DEFAULT_COMPILED_DOCUMENTS = 5

        fun getInstance(): YmirSettings = service()
    }
}
