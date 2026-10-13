package org.gnu.ymir.intellij.navigation

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.features.highlight.LSPTargetElementEvaluator
import org.gnu.ymir.intellij.format.YmirReformatAction

/**
 * The target element evaluator of LSP4IJ, never targeting in a Ymir file an element spanning
 * the whole file: LSP4IJ stands with it for a name the language server sent no semantic token
 * of, which navigating to or finding the usages of would select the file instead of nothing.
 */
class YmirTargetElementEvaluator : LSPTargetElementEvaluator() {

    override fun adjustReferenceOrReferencedElement(
        file: PsiFile,
        editor: Editor,
        offset: Int,
        flags: Int,
        refElement: PsiElement?,
    ): PsiElement? {
        if (spansYmirFile(file.findElementAt(offset))) return null
        val res = super.adjustReferenceOrReferencedElement(file, editor, offset, flags, refElement)
        return if (spansYmirFile(res)) null else res
    }

    override fun isAcceptableNamedParent(parent: PsiElement): Boolean =
        !spansYmirFile(parent) && super.isAcceptableNamedParent(parent)

    companion object {
        /** Whether `element` is an element of a Ymir file spanning all of it. */
        fun spansYmirFile(element: PsiElement?): Boolean {
            val file = element?.containingFile ?: return false
            return YmirReformatAction.isYmir(file.name) && element.textRange == file.textRange
        }
    }
}
