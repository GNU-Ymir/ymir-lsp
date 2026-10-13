package org.gnu.ymir.intellij.imports

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.gnu.ymir.intellij.YmirLanguageClient
import org.gnu.ymir.intellij.YmirLanguageServerFactory

class YmirUnusedUsesTest : BasePlatformTestCase() {

    private fun diagnostic(code: String, message: String) =
        Diagnostic(Range(Position(0, 0), Position(0, 1)), message, DiagnosticSeverity.Warning, "ymir", code)

    fun testUnusedPathOfTheWarning() {
        assertEquals(listOf("a", "b"), YmirUnusedUses.unusedPath(diagnostic("E3038", "no symbol is resolved through the use of a::b")))
        assertNull(YmirUnusedUses.unusedPath(diagnostic("E3001", "undefined symbol a::b")))
    }

    fun testLatestDiagnosticsOfTheFile() {
        val file = myFixture.configureByText("main.yr", "use a::b;\n").virtualFile
        val uses = YmirUnusedUses.getInstance(project)
        val uri = "file://" + file.path

        uses.update(uri, listOf(diagnostic("E3038", "no symbol is resolved through the use of a::b"), diagnostic("E3001", "x")))
        assertEquals(setOf(listOf("a", "b")), uses.of(file))

        uses.update(uri, emptyList())
        assertEmpty(uses.of(file))
    }

    fun testTheClientOfTheServerRecordsThem() {
        assertInstanceOf(YmirLanguageServerFactory().createLanguageClient(project), YmirLanguageClient::class.java)
    }
}
