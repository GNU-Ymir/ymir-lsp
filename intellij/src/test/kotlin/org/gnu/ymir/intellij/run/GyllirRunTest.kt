package org.gnu.ymir.intellij.run

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.execution.RunManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import java.nio.file.Path

class GyllirRunTest : BasePlatformTestCase() {

    override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

    private fun pkg(type: String = "executable"): Path {
        val manifest = myFixture.addFileToProject("pkg/gyllir.toml", "name = \"pkg\"\ntype = \"$type\"\n")
        return manifest.virtualFile.parent.toNioPath()
    }

    fun testMarkersOnMainAndTests() {
        pkg()
        val file = myFixture.addFileToProject(
            "pkg/src/main.yr",
            "use std::io;\n\npub fn main() {\n    // fn main() in a comment line is indented\n}\n\n__test {\n}\n\nfn mainly() {}\n",
        )
        val leaves = PsiTreeUtil.collectElements(file) { it.firstChild == null }.toList()
        val markers = mutableListOf<LineMarkerInfo<*>>()
        YmirRunLineMarkerProvider().collectSlowLineMarkers(leaves, markers)

        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        assertEquals(listOf(2, 6), markers.map { document.getLineNumber(it.startOffset) })
    }

    fun testNoMarkerOutsideAPackage() {
        val file = myFixture.addFileToProject("loose/main.yr", "fn main() {}\n")
        val leaves = PsiTreeUtil.collectElements(file) { it.firstChild == null }.toList()
        val markers = mutableListOf<LineMarkerInfo<*>>()
        YmirRunLineMarkerProvider().collectSlowLineMarkers(leaves, markers)
        assertEmpty(markers)
    }

    fun testSettingsAreReusedPerPackageAndCommand() {
        val dir = pkg()
        val run = GyllirRuns.settingsFor(project, dir, GyllirCommand.RUN)
        assertTrue(run.isTemporary)
        assertEquals("Run pkg", run.name)
        assertSame(run, GyllirRuns.settingsFor(project, dir, GyllirCommand.RUN))
        assertNotSame(run, GyllirRuns.settingsFor(project, dir, GyllirCommand.TEST))

        val configuration = run.configuration as GyllirRunConfiguration
        assertEquals(dir.toString(), configuration.workingDirectory)
        configuration.checkConfiguration()
        RunManager.getInstance(project).removeConfiguration(run)
    }

    fun testCommandLine() {
        val dir = pkg()
        val configuration = GyllirRuns.create(project, dir, GyllirCommand.TEST).configuration as GyllirRunConfiguration
        configuration.arguments = "--release -j 4 \"a b\""
        val commandLine = configuration.commandLine()
        assertEquals(listOf("gyllir", "test", "--release", "-j", "4", "a b"), listOf(commandLine.exePath) + commandLine.parametersList.list)
        assertEquals(dir, commandLine.workingDirectory)
    }

    fun testLibraryDetection() {
        assertTrue(GyllirPackages.isLibrary(pkg("library")))
    }

    fun testLocationsAreLinked() {
        val dir = pkg()
        myFixture.addFileToProject("pkg/src/main.yr", "fn main() {\n    let x : i32 = \"a\";\n}\n")
        val line = " --> src/main.yr:(2,9)\n"
        val result = GyllirLocationFilter(project, dir).applyFilter(line, 100 + line.length)!!
        val item = result.resultItems.single()
        assertEquals("src/main.yr:(2,9)", line.substring(item.highlightStartOffset - 100, item.highlightEndOffset - 100))
        assertNull(GyllirLocationFilter(project, dir).applyFilter(" --> src/missing.yr:(1,1)\n", 30))
    }
}
