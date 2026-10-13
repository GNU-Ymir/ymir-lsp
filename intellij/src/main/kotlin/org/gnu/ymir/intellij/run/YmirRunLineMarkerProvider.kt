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
 * and of each `__test`, running or debugging this test alone, or the tests of
 * the package in a file outside its source and test directories. A TextMate
 * file being a single leaf, the entries are found in its text.
 */
class YmirRunLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            if (element.firstChild != null) continue
            val file = element.containingFile?.virtualFile ?: continue
            if (file.extension != "yr") continue
            val pkg = GyllirPackages.packageOf(file) ?: continue
            val dir = pkg.toNioPath()
            val range = element.textRange

            for (match in MAIN.findAll(element.text)) {
                val token = match.groups[1]!!.range
                val main = TextRange(range.startOffset + token.first, range.startOffset + token.last + 1)
                result.add(GyllirLineMarkerInfo(element, main, dir, GyllirCommand.RUN, ""))
            }

            val module = GyllirPackages.moduleOf(file, pkg)
            for (test in GyllirTests.of(element.containingFile.text, module)) {
                if (!range.contains(test.range)) continue
                result.add(GyllirLineMarkerInfo(element, test.range, dir, GyllirCommand.TEST, test.filter.orEmpty()))
            }
        }
    }

    private companion object {
        val MAIN = Regex("""^[ \t]*(?:pub[ \t]+)?fn[ \t]+(main)[ \t]*\(""", RegexOption.MULTILINE)
    }
}

/**
 * The icon running `command` on the package `pkg`, the tests of `filter` for
 * `test`, offering to run it or to debug it on a click.
 */
private class GyllirLineMarkerInfo(element: PsiElement, range: TextRange, pkg: Path, command: GyllirCommand, filter: String) :
    LineMarkerInfo<PsiElement>(
        element,
        range,
        AllIcons.RunConfigurations.TestState.Run,
        { "Run '${label(command, filter)}'" },
        null,
        GutterIconRenderer.Alignment.CENTER,
        { "Run '${label(command, filter)}'" },
    ) {

    private val actions: ActionGroup = DefaultActionGroup(
        listOf(DefaultRunExecutor.getRunExecutorInstance(), DefaultDebugExecutor.getDebugExecutorInstance())
            .map { GyllirRunAction(pkg, command, filter, it) },
    )

    override fun createGutterRenderer(): GutterIconRenderer = object : LineMarkerGutterIconRenderer<PsiElement>(this) {
        override fun getClickAction(): AnAction? = null

        override fun isNavigateAction(): Boolean = true

        override fun getPopupMenuActions(): ActionGroup = actions
    }
}

private class GyllirRunAction(
    private val pkg: Path,
    private val command: GyllirCommand,
    private val filter: String,
    private val executor: Executor,
) : DumbAwareAction("${executor.actionName} '${label(command, filter)}'", null, executor.icon) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        GyllirRuns.run(project, pkg, command, executor, filter)
    }
}

private fun label(command: GyllirCommand, filter: String): String = filter.ifEmpty { "gyllir ${command.id}" }
