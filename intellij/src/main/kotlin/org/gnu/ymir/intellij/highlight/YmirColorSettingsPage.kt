package org.gnu.ymir.intellij.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.testFramework.LightVirtualFile
import org.gnu.ymir.intellij.YmirIcons
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateSyntaxHighlighterFactory
import javax.swing.Icon

/**
 * The page of *Settings > Editor > Color Scheme > Ymir*, choosing the colors of [YmirColors].
 */
class YmirColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = "Ymir"

    override fun getIcon(): Icon = YmirIcons.Ymir

    override fun getHighlighter(): SyntaxHighlighter =
        TextMateSyntaxHighlighterFactory().getSyntaxHighlighter(null, LightVirtualFile("demo.yr"))

    override fun getDemoText(): String = DEMO

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> =
        mapOf("self" to YmirColors.SELF)

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> =
        arrayOf(AttributesDescriptor("self and super", YmirColors.SELF))

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    private companion object {
        val DEMO = """
            pub class Point over Shape {
                let _x: i32;

                pub <self>self</self> (x: i32) with <self>super</self>(x), _x = x {}

                pub fn x (<self>self</self>)-> i32 {
                    <self>self</self>._x + <self>self</self>::<self>super</self>.area()
                }
            }
        """.trimIndent()
    }
}
