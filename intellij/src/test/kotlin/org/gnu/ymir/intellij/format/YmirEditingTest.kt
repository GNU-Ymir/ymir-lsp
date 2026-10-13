package org.gnu.ymir.intellij.format

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.textmate.TextMateLanguage
import org.jetbrains.plugins.textmate.TextMateService

class YmirEditingTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        TextMateService.getInstance().reloadEnabledBundles()
        PlatformTestUtil.waitWithEventsDispatching(
            "the Ymir bundle is not loaded",
            { TextMateService.getInstance().getLanguageDescriptorByFileName("main.yr") != null },
            30,
        )
    }

    private fun typed(text: String, typing: String): String {
        myFixture.configureByText("main.yr", text)
        myFixture.type(typing)
        return myFixture.editor.document.text.replaceRange(myFixture.caretOffset, myFixture.caretOffset, "<caret>")
    }

    fun testEnterIndentsTheBlock() {
        assertEquals("fn main() {\n    <caret>\n}", typed("fn main() {<caret>\n}", "\n"))
        assertEquals("fn main() {\n    if (x) {\n        <caret>\n    }\n}", typed("fn main() {\n    if (x) {<caret>\n    }\n}", "\n"))
    }

    fun testEnterBetweenBracesPutsTheClosingOneOnItsLine() {
        assertEquals("fn main() {\n    <caret>\n}", typed("fn main() {<caret>}", "\n"))
    }

    fun testEnterAlignsOnTheList() {
        assertEquals("foo(a,\n    <caret>)", typed("foo(a,<caret>)", "\n"))
    }

    fun testEnterAfterAStatement() {
        assertEquals("fn main() {\n    foo();\n    <caret>\n}", typed("fn main() {\n    foo();<caret>\n}", "\n"))
    }

    fun testClosingBraceTypedIndentsItsLine() {
        assertEquals("fn main() {\n    if (x) {\n        a();\n    }<caret>\n}", typed("fn main() {\n    if (x) {\n        a();\n        <caret>\n}", "}"))
    }

    fun testReformatCodeWithoutTheServer() {
        myFixture.configureByText("main.yr", "fn main ( ) {\nlet x=1 ;\n}\n")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
        myFixture.checkResult("fn main() {\n    let x = 1;\n}\n")
    }

    fun testOtherFilesLeftToTextMate() {
        myFixture.configureByText("notes.txt", "fn main() {\n<caret>\n}")
        val indent = YmirLineIndentProvider().getLineIndent(project, myFixture.editor, TextMateLanguage.LANGUAGE, myFixture.caretOffset)
        assertNull(indent)
    }
}
