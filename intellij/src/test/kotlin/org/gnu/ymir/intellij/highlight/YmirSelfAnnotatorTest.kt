package org.gnu.ymir.intellij.highlight

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.textmate.TextMateService

class YmirSelfAnnotatorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        TextMateService.getInstance().reloadEnabledBundles()
        PlatformTestUtil.waitWithEventsDispatching(
            "the Ymir bundle is not loaded",
            { TextMateService.getInstance().getLanguageDescriptorByFileName("main.yr") != null },
            30,
        )
    }

    private fun colored(text: String): List<Pair<Int, String>> {
        myFixture.configureByText("main.yr", text)
        return myFixture.doHighlighting()
            .filter { it.forcedTextAttributesKey == YmirColors.SELF }
            .map { it.startOffset to text.substring(it.startOffset, it.endOffset) }
            .sortedBy { it.first }
    }

    fun testSelfAndSuperColored() {
        val text = "pub self (x: i32) with super(x) {}\nfn f(self) { self::super.g(self.x); }\n"
        val expected = Regex("""\b(self|super)\b""").findAll(text).map { it.range.first to it.value }.toList()
        assertEquals(expected, colored(text))
    }

    fun testCommentsAndStringsNotColored() {
        assertEmpty(colored("// self\n/** super */\nfn f() { \"self\"; myself; superb; }\n"))
    }

    fun testInterpolationColored() {
        assertEquals(listOf(12 to "self"), colored("fn f() { f\"{self.x}\"; }\n"))
    }
}
