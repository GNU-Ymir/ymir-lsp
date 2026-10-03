package org.gnu.ymir.intellij.symbol

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.indexing.FindSymbolParameters
import com.redhat.devtools.lsp4ij.LSPIJUtils
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.nio.file.Files

class YmirSymbolsTest : BasePlatformTestCase() {

    override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

    private fun symbol(name: String, kind: SymbolKind, uri: String, container: String = "shapes"): YmirSymbol {
        val start = Position(2, 11)
        return YmirSymbol.of(project, WorkspaceSymbol(name, kind, Either.forLeft(Location(uri, Range(start, start))), container))
    }

    fun testQueryOfThePattern() {
        assertEquals("Square", YmirSymbols.query(" *Square:12 "))
        assertEquals("Square", YmirSymbols.query("Square:12:4"))
        assertEquals("std::io::print", YmirSymbols.query("std::io::print"))
    }

    fun testPresentation() {
        val file = myFixture.addFileToProject("pkg/src/shapes.yr", "in shapes;\n\npub record Square {}\n")
        val square = symbol("Square", SymbolKind.Struct, LSPIJUtils.toUriAsString(file.virtualFile))
        assertEquals("Square", square.presentableText)
        assertEquals("shapes", square.locationString)
        assertNotNull(square.getIcon(false))
        assertEquals(file.virtualFile, square.file)
    }

    fun testProjectDeclarationsAndTheOthersWithTheNonProjectItems() {
        val file = myFixture.addFileToProject("pkg/src/shapes.yr", "in shapes;\n\npub record Square {}\n")
        val pkg = file.virtualFile.parent.parent
        val own = symbol("Square", SymbolKind.Struct, LSPIJUtils.toUriAsString(file.virtualFile))

        val std = Files.createTempDirectory("ymir-std").resolve("io.yr")
        Files.writeString(std, "in io;\n\npub fn println() {}\n")
        val stdFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(std)!!
        val println = symbol("println", SymbolKind.Function, LSPIJUtils.toUriAsString(stdFile), "std::io")

        val root = PsiTestUtil.addContentRoot(module, pkg)
        try {
            val inProject = FindSymbolParameters.wrap("", project, false)
            val everywhere = FindSymbolParameters.wrap("", project, true)
            assertTrue(YmirSymbols.isListed(own, inProject))
            assertTrue(YmirSymbols.isListed(own, everywhere))
            assertFalse(YmirSymbols.isListed(println, inProject))
            assertTrue(YmirSymbols.isListed(println, everywhere))
        } finally {
            PsiTestUtil.removeContentEntry(module, root.file!!)
        }
    }

    fun testBothShapesOfTheAnswerAreRead() {
        val start = Position(2, 11)
        val location = Location("file:///pkg/src/shapes.yr", Range(start, start))
        val symbols = listOf(WorkspaceSymbol("Square", SymbolKind.Struct, Either.forLeft(location), "shapes"))
        assertEquals(listOf("Square"), YmirSymbols.read(project, Either.forRight(symbols)).map { it.name })

        @Suppress("DEPRECATION")
        val infos = listOf(SymbolInformation("Square", SymbolKind.Struct, location, "shapes"))
        assertEquals(listOf("shapes"), YmirSymbols.read(project, Either.forLeft(infos)).map { it.locationString })
        assertEmpty(YmirSymbols.read(project, null))
    }

    fun testGotoClassListsTheTypes() {
        val types = SymbolKind.entries.filter { it in YmirGotoClassContributor.TYPES }
        assertEquals(listOf(SymbolKind.Class, SymbolKind.Enum, SymbolKind.Interface, SymbolKind.Struct), types)
    }
}
