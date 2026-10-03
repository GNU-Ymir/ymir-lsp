package org.gnu.ymir.intellij.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

/**
 * The colors of the `.yr` files the TextMate grammar alone cannot give, their defaults set by
 * the schemes of `colorSchemes`.
 */
object YmirColors {
    @JvmField
    val SELF: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("YMIR_SELF", DefaultLanguageHighlighterColors.KEYWORD)
}
