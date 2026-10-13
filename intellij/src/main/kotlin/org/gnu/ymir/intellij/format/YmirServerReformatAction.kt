package org.gnu.ymir.intellij.format

import com.intellij.formatting.service.FormattingService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.features.formatting.AbstractLSPFormattingService

/**
 * Rewrites the Ymir file of the editor in the canonical form of `ymir-lsp`,
 * or the declarations of its selection, by the formatting service of LSP4IJ
 * asking the language server, [YmirFormattingService] formatting the file
 * otherwise.
 */
class YmirServerReformatAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
            && e.getData(CommonDataKeys.EDITOR) != null
            && YmirReformatAction.isYmir(e.getData(CommonDataKeys.VIRTUAL_FILE)?.name)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        val service = serverService(file)
        if (service == null) {
            NotificationGroupManager.getInstance().getNotificationGroup("Ymir")
                .createNotification("The Ymir language server is not running, or does not format the file", NotificationType.WARNING)
                .notify(project)
            return
        }

        val selection = editor.selectionModel
        val range = if (selection.hasSelection()) TextRange(selection.selectionStart, selection.selectionEnd) else file.textRange
        WriteCommandAction.runWriteCommandAction(project, "Reformat with ymir-lsp", null, {
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
            service.formatElement(file, range, false)
        }, file)
    }

    companion object {
        /** The id of the action in `plugin.xml`. */
        const val ID = "Ymir.ReformatWithServer"

        /** The formatting service of LSP4IJ formatting `file` by the language server, null if it does not. */
        fun serverService(file: PsiFile): FormattingService? =
            FormattingService.EP_NAME.extensionList.firstOrNull { it is AbstractLSPFormattingService && it.canFormat(file) }
    }
}
