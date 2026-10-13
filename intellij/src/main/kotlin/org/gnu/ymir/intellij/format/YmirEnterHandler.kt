package org.gnu.ymir.intellij.format

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile

/**
 * Enter between the brackets of an empty block or list of a Ymir file puts
 * the closing one on a line of its own, indented as [YmirIndenter] decides,
 * the caret staying on the line between them.
 */
class YmirEnterHandler : EnterHandlerDelegateAdapter() {

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?,
    ): Result {
        if (!YmirReformatAction.isYmir(file.name)) return Result.Continue
        val text = editor.document.charsSequence
        val offset = caretOffset.get()
        if (offset <= 0 || offset >= text.length || PAIRS[text[offset - 1]] != text[offset]) return Result.Continue

        editor.document.insertString(offset, "\n")
        val closer = offset + 1
        val indent = YmirIndenter.indentAt(editor.document.immutableCharSequence, closer) ?: return Result.DefaultForceIndent
        editor.document.insertString(closer, " ".repeat(indent))
        return Result.DefaultForceIndent
    }

    private companion object {
        val PAIRS = mapOf('{' to '}', '(' to ')', '[' to ']')
    }
}
