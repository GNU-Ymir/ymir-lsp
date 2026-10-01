package org.gnu.ymir.intellij.run

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * A run icon in the gutter of `fn main`, running the package, and of each
 * `__test`, running its tests. A TextMate file being a single leaf, the
 * entries are found in its text.
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
                val tooltip = "Run 'gyllir ${command.id}'"
                result.add(LineMarkerInfo(
                    element,
                    TextRange(start + token.range.first, start + token.range.last + 1),
                    AllIcons.RunConfigurations.TestState.Run,
                    { tooltip },
                    { _, elt -> GyllirRuns.run(elt.project, pkg, command) },
                    GutterIconRenderer.Alignment.CENTER,
                    { tooltip },
                ))
            }
        }
    }

    private companion object {
        val ENTRY = Regex("""^[ \t]*(?:pub[ \t]+)?(?:fn[ \t]+(main)[ \t]*\(|(__test)\b)""", RegexOption.MULTILINE)
    }
}
