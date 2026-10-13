package org.gnu.ymir.intellij.format

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Indents the line of a Ymir file a closing bracket was typed at the start of
 * as the line opening it, as [YmirIndenter] decides.
 */
class YmirTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if ((c != '}' && c != ')' && c != ']') || !YmirReformatAction.isYmir(file.name)) return Result.CONTINUE

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
}
