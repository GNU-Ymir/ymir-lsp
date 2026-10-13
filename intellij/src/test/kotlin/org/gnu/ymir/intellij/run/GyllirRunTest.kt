package org.gnu.ymir.intellij.run

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.junit.Assert
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

        val actions = markers.map { marker ->
            (marker.createGutterRenderer()!!.popupMenuActions as DefaultActionGroup).getChildren(ActionManager.getInstance())
                .map { it.templatePresentation.text }
        }
        assertEquals(
            listOf(listOf("Run 'gyllir run'", "Debug 'gyllir run'"), listOf("Run 'main::0'", "Debug 'main::0'")),
            actions,
        )
    }

    fun testATestIsRunByItsFilter() {
        pkg()
        val file = myFixture.addFileToProject(
            "pkg/test/server/hover.yr",
            "__test markdown {\n}\n\n__test {\n}\n\n@parameters ((yield i for i in 0 .. 2))\npub __test cases (i: i32) {\n}\n",
        )
        val module = GyllirPackages.moduleOf(file.virtualFile, file.virtualFile.parent.parent.parent)
        assertEquals("server::hover", module)
        assertEquals(
            listOf("server::hover::markdown", "server::hover::1", "server::hover::cases[*]"),
            GyllirTests.of(file.text, module).map { it.filter },
        )
        assertEquals(listOf(0, 3, 7), GyllirTests.of(file.text, module).map { file.viewProvider.document!!.getLineNumber(it.range.startOffset) })
    }

    fun testTheModuleOfAFileIsItsPathUnderTheSourceOrTestDirectory() {
        val manifest = myFixture.addFileToProject("pkg/gyllir.toml", "name = \"pkg\"\n\n[targets.cli]\nsource-dir = \"cli/src\"\n")
        val pkg = manifest.virtualFile.parent
        val main = myFixture.addFileToProject("pkg/cli/src/main.yr", "fn main() {}\n").virtualFile
        val nested = myFixture.addFileToProject("pkg/src/a/b.yr", "__test {}\n").virtualFile
        val loose = myFixture.addFileToProject("pkg/tools/c.yr", "__test {}\n").virtualFile
        assertEquals("main", GyllirPackages.moduleOf(main, pkg))
        assertEquals("a::b", GyllirPackages.moduleOf(nested, pkg))
        assertNull(GyllirPackages.moduleOf(loose, pkg))
        assertEquals(listOf(null), GyllirTests.of("__test {}\n", null).map { it.filter })
    }

    fun testATestTakesTheConfigurationOfTheTestsOfItsPackage() {
        val dir = pkg()
        val tests = GyllirRuns.settingsFor(project, dir, GyllirCommand.TEST)
        (tests.configuration as GyllirRunConfiguration).apply {
            arguments = "--release"
            envFile = ".env"
        }

        val one = GyllirRuns.settingsFor(project, dir, GyllirCommand.TEST, "a::b")
        assertNotSame(tests, one)
        assertSame(one, GyllirRuns.settingsFor(project, dir, GyllirCommand.TEST, "a::b"))
        assertEquals("a::b (pkg)", one.name)
        val configuration = one.configuration as GyllirRunConfiguration
        assertEquals("a::b", configuration.filter)
        assertEquals("--release", configuration.arguments)
        assertEquals(".env", configuration.envFile)
        RunManager.getInstance(project).removeConfiguration(one)
        RunManager.getInstance(project).removeConfiguration(tests)
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

    fun testTheFilterAndTheEnvironmentFileAreOptionsOfTest() {
        val dir = pkg()
        val configuration = GyllirRuns.create(project, dir, GyllirCommand.TEST, "server::*").configuration as GyllirRunConfiguration
        configuration.envFile = ".env"
        configuration.arguments = "-- -s"
        val commandLine = configuration.commandLine()
        assertEquals(listOf("test", "--env-file", ".env", "--filter", "server::*", "--", "-s"), commandLine.parametersList.list)
        Assert.assertThrows(RuntimeConfigurationError::class.java) { configuration.checkConfiguration() }
        myFixture.addFileToProject("pkg/.env", "A=1\n")
        configuration.checkConfiguration()

        configuration.command = GyllirCommand.RUN
        assertEquals(listOf("run", "--", "-s"), configuration.commandLine().parametersList.list)
    }

    private fun envFileError(content: String): String? =
        (runCatching { GyllirEnvFile.parse(content, ".env") }.exceptionOrNull() as? ExecutionException)?.message

    fun testTheEnvironmentFileIsReadAsGyllirReadsIt() {
        assertEquals(
            mapOf("A" to "1", "B" to "two words", "C" to "'c", "D" to ""),
            GyllirEnvFile.parse("# comment\n\nexport A=1\n  B = \"two words\"\r\nC='c\nD=\n", ".env"),
        )
        assertEquals("In '.env', line 2, '1A' is not a variable name", envFileError("A=1\n1A=2\n"))
        assertEquals("In '.env', line 1, expected 'KEY=VALUE'", envFileError("A\n"))
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
