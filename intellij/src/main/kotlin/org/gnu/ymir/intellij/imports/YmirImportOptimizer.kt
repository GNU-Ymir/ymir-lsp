package org.gnu.ymir.intellij.imports

import com.intellij.lang.ImportOptimizer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.gnu.ymir.intellij.format.YmirReformatAction

/**
 * *Optimize Imports* of a Ymir file, without the language server: its
 * top-level `use` declarations rewritten by [YmirImports], without the ones
 * the latest diagnostics of the session report unused.
 */
class YmirImportOptimizer : ImportOptimizer {

    override fun supports(file: PsiFile): Boolean = YmirReformatAction.isYmir(file.name)

    override fun processFile(file: PsiFile): Runnable {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return Runnable {}
        val unused = file.virtualFile?.let { YmirUnusedUses.getInstance(file.project).of(it) } ?: emptySet()
        val edits = YmirImports.optimize(document.text, unused)
        return object : ImportOptimizer.CollectingInfoRunnable {
            override fun run() {
                for (e in edits.asReversed()) document.replaceString(e.start, e.end, e.text)
                PsiDocumentManager.getInstance(file.project).commitDocument(document)
            }

            override fun getUserNotificationInfo(): String =
                if (edits.isEmpty()) "Imports already optimized" else "Rewrote the use declarations"
        }
    }
}
