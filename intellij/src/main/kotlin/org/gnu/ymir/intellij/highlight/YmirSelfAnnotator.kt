package org.gnu.ymir.intellij.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType

/**
 * Colors `self` and `super` with [YmirColors.SELF], TextMate mapping their scope to the
 * uncolored local variables. The PSI of a TextMate file holding no token, they are found by
 * lexing the file with the grammar.
 */
class YmirSelfAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return
        val file = element.virtualFile ?: return
        if (file.extension != "yr") return
        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(element.language, element.project, file)
        val lexer = highlighter.highlightingLexer

        lexer.start(element.text)
        while (lexer.tokenType != null) {
            val type = lexer.tokenType
            if (type is TextMateElementType && type.scope.scopeName?.toString() == SCOPE) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(TextRange(lexer.tokenStart, lexer.tokenEnd))
                    .textAttributes(YmirColors.SELF)
                    .create()
            }
            lexer.advance()
        }
    }

    private companion object {
        const val SCOPE = "variable.language.ymir"
    }
}
