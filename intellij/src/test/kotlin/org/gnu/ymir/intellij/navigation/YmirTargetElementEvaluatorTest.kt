package org.gnu.ymir.intellij.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.features.LSPPsiElement
import org.gnu.ymir.intellij.YmirLanguageServerFactory

class YmirTargetElementEvaluatorTest : BasePlatformTestCase() {

    private val text = """
        in main;

        class X {
            let x: i32 = 0;

            pub fn foo(self)-> i32 {
                self.x + undefinedThing
            }
        }
    """.trimIndent()

    /** Stops the server LSP4IJ starts to look for a definition, the project being shared by the light tests. */
    override fun tearDown() {
        try {
            LanguageServerManager.getInstance(project).stop(YmirLanguageServerFactory.SERVER_ID)
        } finally {
            super.tearDown()
        }
    }

    private fun outcome(offset: Int) =
        ReadAction.computeBlocking<GotoDeclarationOrUsageHandler2.GTDUOutcome?, Throwable> {
            GotoDeclarationOrUsageHandler2.testGTDUOutcome(myFixture.editor, myFixture.file, offset)
        }

    private fun highlighted(offset: Int): List<TextRange>? =
        ReadAction.computeBlocking<List<TextRange>?, Throwable> {
            GotoDeclarationOrUsageHandler2.getCtrlMouseData(myFixture.editor, myFixture.file, offset)?.ranges
        }

    fun testANameWithoutSemanticTokenIsNeitherNavigatedToNorHighlighted() {
        myFixture.configureByText("main.yr", text)
        for (name in listOf("x:", "foo", "undefinedThing")) {
            assertNull(name, outcome(text.indexOf(name)))
            assertNull(name, highlighted(text.indexOf(name)))
        }
    }

    fun testOnlyTheElementsSpanningAYmirFileAreNoTargets() {
        val file = myFixture.configureByText("main.yr", text)
        val foo = text.indexOf("foo")
        assertTrue(YmirTargetElementEvaluator.spansYmirFile(LSPPsiElement(file, file.textRange)))
        assertFalse(YmirTargetElementEvaluator.spansYmirFile(LSPPsiElement(file, TextRange(foo, foo + 3))))

        val notes = myFixture.configureByText("notes.txt", text)
        assertFalse(YmirTargetElementEvaluator.spansYmirFile(LSPPsiElement(notes, notes.textRange)))
    }
}
