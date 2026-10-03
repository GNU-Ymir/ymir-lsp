package org.gnu.ymir.intellij.format

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware

/**
 * Rewrites the Ymir file of the editor in its canonical form, or the
 * declarations of its selection, by running *Reformat Code*, that LSP4IJ
 * answers with the formatting of `ymir-lsp`. Bound to Meta+Alt+L, Ctrl+Alt+L
 * being the screen lock of most Linux desktops.
 */
class YmirReformatAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
            && e.getData(CommonDataKeys.EDITOR) != null
            && isYmir(e.getData(CommonDataKeys.VIRTUAL_FILE)?.name)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val reformat = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_REFORMAT) ?: return
        ActionUtil.invokeAction(reformat, e.dataContext, e.place, e.inputEvent, null)
    }

    companion object {
        /** The id of the action in `plugin.xml`. */
        const val ID = "Ymir.ReformatCode"

        fun isYmir(name: String?): Boolean = name != null && name.endsWith(".yr")
    }
}
