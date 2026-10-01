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
    }

    /** The command starting the language server, a path or a name looked up in the PATH. */
    var serverPath: String
        get() = state.serverPath?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER
        set(value) {
            state.serverPath = value.trim().ifEmpty { DEFAULT_SERVER }
        }

    companion object {
        const val DEFAULT_SERVER = "ymir-lsp"

        fun getInstance(): YmirSettings = service()
    }
}
