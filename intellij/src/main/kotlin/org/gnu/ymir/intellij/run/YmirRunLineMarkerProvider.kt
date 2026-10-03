package org.gnu.ymir.intellij.run

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import java.nio.file.Path

/**
 * A run icon in the gutter of `fn main`, running or debugging the package,
 * and of each `__test`, running or debugging its tests. A TextMate file being
 * a single leaf, the entries are found in its text.
 */
class YmirRunLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            if (element.firstChild != null) continue
            val file = element.containingFile?.virtualFile ?: continue
            if (file.extension != "yr") continue
            val pkg = GyllirPackages.packageOf(file)?.toNioPath() ?: continue
            val start = element.textRange.startOffset

            for (match in ENTRY.findAll(element.text)) {
                val token = match.groups[1] ?: match.groups[2] ?: continue
                val command = if (token.value == "__test") GyllirCommand.TEST else GyllirCommand.RUN
                result.add(GyllirLineMarkerInfo(element, TextRange(start + token.range.first, start + token.range.last + 1), pkg, command))
            }
        }
    }

    private companion object {
        val ENTRY = Regex("""^[ \t]*(?:pub[ \t]+)?(?:fn[ \t]+(main)[ \t]*\(|(__test)\b)""", RegexOption.MULTILINE)
    }
}

/**
 * The icon running `command` on the package `pkg`, offering to run it or to
 * debug it on a click.
 */
private class GyllirLineMarkerInfo(element: PsiElement, range: TextRange, pkg: Path, command: GyllirCommand) :
    LineMarkerInfo<PsiElement>(
        element,
        range,
        AllIcons.RunConfigurations.TestState.Run,
        { "Run 'gyllir ${command.id}'" },
        null,
        GutterIconRenderer.Alignment.CENTER,
        { "Run 'gyllir ${command.id}'" },
    ) {

    private val actions: ActionGroup = DefaultActionGroup(
        listOf(DefaultRunExecutor.getRunExecutorInstance(), DefaultDebugExecutor.getDebugExecutorInstance())
            .map { GyllirRunAction(pkg, command, it) },
    )

    override fun createGutterRenderer(): GutterIconRenderer = object : LineMarkerGutterIconRenderer<PsiElement>(this) {
        override fun getClickAction(): AnAction? = null

        override fun isNavigateAction(): Boolean = true

        override fun getPopupMenuActions(): ActionGroup = actions
    }
}

private class GyllirRunAction(private val pkg: Path, private val command: GyllirCommand, private val executor: Executor) :
    DumbAwareAction("${executor.actionName} 'gyllir ${command.id}'", null, executor.icon) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        GyllirRuns.run(project, pkg, command, executor)
    }
}
