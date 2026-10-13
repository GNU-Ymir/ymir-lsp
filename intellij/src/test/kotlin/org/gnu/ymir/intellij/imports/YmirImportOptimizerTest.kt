package org.gnu.ymir.intellij.imports

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class YmirImportOptimizerTest : BasePlatformTestCase() {

    fun testOptimizeImportsWithoutTheServer() {
        val file = myFixture.configureByText("main.yr", "in main;\n\nuse std::io;\nuse b::c;\nuse a::d;\nuse b::e;\n\nfn main() {}\n")
        val unused = Diagnostic(Range(Position(4, 4), Position(4, 5)), "no symbol is resolved through the use of b::e", DiagnosticSeverity.Warning, "ymir", "E3038")
        YmirUnusedUses.getInstance(project).update("file://" + file.virtualFile.path, listOf(unused))

        OptimizeImportsProcessor(project, file).run()
        myFixture.checkResult("in main;\n\nuse a::d;\n\nuse b::c;\n\nuse std::io;\n\nfn main() {}\n")
    }

    fun testOtherFilesNotSupported() {
        assertFalse(YmirImportOptimizer().supports(myFixture.configureByText("notes.txt", "use b;\n")))
    }
}
