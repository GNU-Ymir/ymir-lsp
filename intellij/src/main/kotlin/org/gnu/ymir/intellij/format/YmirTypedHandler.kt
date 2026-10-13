package org.gnu.ymir.intellij.format

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Indents the line of a Ymir file a closing bracket was typed at the start of
 * as the line opening it, as [YmirIndenter] decides, and leaves an opening
 * bracket typed before its closing one ending the file without another one,
 * which TextMate would add there.
 */
class YmirTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (!YmirReformatAction.isYmir(file.name)) return Result.CONTINUE
        if (c in PAIRS) return if (isClosedAtTheEnd(c, editor)) Result.STOP else Result.CONTINUE
        if (c != '}' && c != ')' && c != ']') return Result.CONTINUE

        val document = editor.document
        val offset = editor.caretModel.offset - 1
        if (offset < 0 || document.charsSequence[offset] != c) return Result.CONTINUE

        val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
        if (document.charsSequence.subSequence(lineStart, offset).isNotBlank()) return Result.CONTINUE

        val indent = YmirIndenter.indentAt(document.immutableCharSequence, lineStart) ?: return Result.CONTINUE
        if (offset - lineStart != indent || document.charsSequence.subSequence(lineStart, offset).contains('\t')) {
            document.replaceString(lineStart, offset, " ".repeat(indent))
        }

        return Result.CONTINUE
    }

    /** Whether the opening bracket `c` was typed before its closing one, the last character of the file. */
    private fun isClosedAtTheEnd(c: Char, editor: Editor): Boolean {
        val text = editor.document.charsSequence
        val offset = editor.caretModel.offset
        return offset > 0 && text[offset - 1] == c && offset == text.length - 1 && text[offset] == PAIRS[c]
    }

    private companion object {
        val PAIRS = mapOf('{' to '}', '(' to ')', '[' to ']')
    }
}
