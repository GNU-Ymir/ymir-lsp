package org.gnu.ymir.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider

/**
 * Runs the language server of `commandLine`, started under `budget`, compiling at most
 * `compiledDocuments` open documents. A server killed on the budget is stopped and disabled,
 * rather than restarted by LSP4IJ, and a notification names the budget and where to raise it.
 */
class YmirServerConnectionProvider(
    private val project: Project,
    private val budget: YmirMemoryBudget,
    commandLine: GeneralCommandLine,
    private val compiledDocuments: Int = YmirSettings.DEFAULT_COMPILED_DOCUMENTS,
) : OSProcessStreamConnectionProvider(commandLine) {

    @Volatile
    private var gcOutOfMemory = false

    init {
        addLogErrorHandler { error -> if (YmirMemoryBudget.GC_OUT_OF_MEMORY in error) gcOutOfMemory = true }
        super.addUnexpectedServerStopHandler {
            if (exceeded()) ApplicationManager.getApplication().executeOnPooledThread { stopOnBudget(project, budget) }
        }
    }

    /** The handlers of LSP4IJ, restarting the server, are left out when the budget killed it. */
    override fun addUnexpectedServerStopHandler(handler: Runnable) {
        super.addUnexpectedServerStopHandler { if (!exceeded()) handler.run() }
    }

    override fun getInitializationOptions(rootUri: VirtualFile?): Any =
        mapOf("compiledDocuments" to compiledDocuments)

    private fun exceeded(): Boolean {
        val exitCode = processHandler?.exitCode ?: return false
        return budget.exceeded(exitCode, gcOutOfMemory)
    }

    companion object {
        const val NOTIFICATION_GROUP = "Ymir"

        fun stopOnBudget(project: Project, budget: YmirMemoryBudget) {
            if (project.isDisposed) return
            val servers = LanguageServerManager.getInstance(project)
            servers.stop(YmirLanguageServerFactory.SERVER_ID, LanguageServerManager.StopOptions().setWillDisable(true))
            NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(
                    "Ymir language server stopped",
                    "It exceeded its memory budget of ${budget.mebibytes} MiB, set under " +
                        "<i>Settings > Languages & Frameworks > Ymir</i>.",
                    NotificationType.ERROR,
                )
                .addAction(NotificationAction.createSimpleExpiring("Raise the budget") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, YmirConfigurable::class.java)
                })
                .addAction(NotificationAction.createSimpleExpiring("Restart") {
                    servers.start(YmirLanguageServerFactory.SERVER_ID, LanguageServerManager.StartOptions().setWillEnable(true))
                })
                .notify(project)
        }
    }
}
