package org.gnu.ymir.intellij.format

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AbstractDocumentFormattingService
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * Formats the Ymir files with [YmirFormatter], in the IDE rather than by the
 * language server: *Reformat Code*, on the whole file or on the lines of its
 * selection, and the formatting the IDE does on its own, as when a closing
 * brace is typed. Only the whitespace that changes is replaced.
 */
class YmirFormattingService : AbstractDocumentFormattingService() {

    override fun getFeatures(): Set<FormattingService.Feature> = FEATURES

    override fun canFormat(file: PsiFile): Boolean = YmirReformatAction.isYmir(file.name)

    override fun formatDocument(
        document: Document,
        formattingRanges: List<TextRange>,
        formattingContext: FormattingContext,
        canChangeWhiteSpaceOnly: Boolean,
        quickFormat: Boolean,
    ) {
        val text = document.text
        val ranges = formattingRanges.takeUnless { it.size == 1 && it[0].startOffset == 0 && it[0].endOffset >= text.length }
        val formatted = YmirFormatter.format(text, ranges)
        if (formatted == text) return

        for ((start, end, replacement) in changes(text, formatted).asReversed()) {
            document.replaceString(start, end, replacement)
        }
    }

    companion object {
        private val FEATURES = setOf(FormattingService.Feature.AD_HOC_FORMATTING, FormattingService.Feature.FORMAT_FRAGMENTS)

        /**
         * The replacements of the whitespace of `text` turning it into
         * `formatted`, the same text but for its whitespace, in order.
         */
        fun changes(text: String, formatted: String): List<Triple<Int, Int, String>> {
            val res = ArrayList<Triple<Int, Int, String>>()
            var i = 0
            var j = 0
            while (i < text.length || j < formatted.length) {
                if (i < text.length && j < formatted.length && text[i] == formatted[j] && !text[i].isWhitespace()) {
                    i++
                    j++
                    continue
                }

                val a = i
                val b = j
                while (i < text.length && text[i].isWhitespace()) i++
                while (j < formatted.length && formatted[j].isWhitespace()) j++
                if (i == a && j == b) {
                    i++
                    j++
                    continue
                }

                val old = text.substring(a, i)
                val new = formatted.substring(b, j)
                if (old != new) res.add(Triple(a, i, new))
            }

            return res
        }
    }
}
