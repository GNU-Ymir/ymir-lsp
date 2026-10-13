package org.gnu.ymir.intellij.format

import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider
import org.jetbrains.plugins.textmate.TextMateLanguage
import org.jetbrains.plugins.textmate.editor.TextMateLineIndentProvider

/**
 * The indentation of a new line of a Ymir file, on Enter, computed by
 * [YmirIndenter] rather than by the language server: in a block comment, the
 * one of the line before it, aligned on the `*` of a comment opening there,
 * and in a string, the one written. The other TextMate files are left to the
 * provider of TextMate.
 */
class YmirLineIndentProvider : LineIndentProvider {

    private val textMate = TextMateLineIndentProvider()

    override fun getLineIndent(project: Project, editor: Editor, language: Language?, offset: Int): String? {
        val document = editor.document
        if (!YmirReformatAction.isYmir(FileDocumentManager.getInstance().getFile(document)?.name)) {
            return textMate.getLineIndent(project, editor, language, offset)
        }

        val text = document.immutableCharSequence
        val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
        YmirIndenter.indentAt(text, lineStart)?.let { return " ".repeat(it) }

        val token = YmirLexer.tokens(text.subSequence(0, lineStart)).lastOrNull()
        if (token == null || token.kind != YmirToken.Kind.BLOCK_COMMENT || lineStart == 0) return LineIndentProvider.DO_NOT_ADJUST
        val previous = YmirIndenter.lineStart(text, lineStart - 1)
        if (token.start >= previous) return " ".repeat(YmirIndenter.column(text, previous, token.start, YmirIndenter.INDENT) + 1)

        var end = previous
        while (end < lineStart && (text[end] == ' ' || text[end] == '\t')) end++
        return text.substring(previous, end)
    }

    override fun isSuitableFor(language: Language?): Boolean = language is TextMateLanguage
}
