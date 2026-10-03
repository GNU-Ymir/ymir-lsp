package org.gnu.ymir.intellij.debug

import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.runners.ProgramRunner
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.redhat.devtools.lsp4ij.dap.DAPDebugRunner
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager
import org.gnu.ymir.intellij.YmirSettings
import org.gnu.ymir.intellij.run.GyllirCommand
import org.gnu.ymir.intellij.run.GyllirPackages
import org.gnu.ymir.intellij.run.GyllirRunConfiguration
import org.gnu.ymir.intellij.run.GyllirRuns
import org.gnu.ymir.intellij.run.GyllirTarget
import org.junit.Assert
import java.nio.file.Path

class YmirDebugTest : BasePlatformTestCase() {

    override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

    private fun pkg(manifest: String): Path =
        myFixture.addFileToProject("pkg/gyllir.toml", manifest).virtualFile.parent.toNioPath()

    private val several = """
        name = "pkg"
        type = "executable"

        [targets.core]
        type = "library"
        tests = false

        [targets."cli"]
        type = "executable" # the program
        output = "pkg-cli"

        [targets.tool]
        type = "executable"

        [registry]
        git = "git@github.com:me/pkg.git"
    """.trimIndent()

    fun testTheTargetOfAPackageDeclaringNoneIsNamedAfterIt() {
        val dir = pkg("name = \"demo\"\ntype = \"executable\"\n\n[dependencies.ymirc]\nversion = \"master\"\n")
        assertEquals(mapOf("demo" to GyllirTarget("demo", false, true, "demo")), GyllirPackages.targets(dir))
    }

    fun testDeclaredTargets() {
        assertEquals(
            mapOf(
                "core" to GyllirTarget("core", true, false, "core"),
                "cli" to GyllirTarget("cli", false, true, "pkg-cli"),
                "tool" to GyllirTarget("tool", false, true, "tool"),
            ),
            GyllirPackages.targets(pkg(several)),
        )
    }

    fun testDebuggingRunBuildsTheProgramAndRunsIt() {
        val dir = pkg("name = \"demo\"\n")
        assertEquals(
            GyllirDebugTarget(dir, listOf("build", "demo"), dir.resolve("demo"), listOf("a", "b")),
            GyllirDebugTarget.of(GyllirCommand.RUN, dir, listOf("a", "b")),
        )
        assertEquals(listOf("demo", "x"), GyllirDebugTarget.of(GyllirCommand.RUN, dir, listOf("--", "demo", "x")).arguments)
        assertEquals(listOf("x"), GyllirDebugTarget.of(GyllirCommand.RUN, dir, listOf("demo", "x")).arguments)
    }

    fun testDebuggingRunOfSeveralTargetsNamesOne() {
        val dir = pkg(several)
        assertEquals(
            GyllirDebugTarget(dir, listOf("build", "cli"), dir.resolve("pkg-cli"), listOf("x")),
            GyllirDebugTarget.of(GyllirCommand.RUN, dir, listOf("cli", "x")),
        )
        val unnamed = Assert.assertThrows(ExecutionException::class.java) { GyllirDebugTarget.of(GyllirCommand.RUN, dir, listOf("x")) }
        assertEquals("Name the target to debug first in the arguments, among cli, core, tool", unnamed.message)
        Assert.assertThrows(ExecutionException::class.java) { GyllirDebugTarget.of(GyllirCommand.RUN, dir, listOf("core")) }
    }

    fun testDebuggingTestBuildsTheTestsWithoutRunningThem() {
        val dir = pkg("name = \"demo\"\n")
        assertEquals(
            GyllirDebugTarget(dir, listOf("test", "--dry", "--j", "4"), dir.resolve("demo.test"), emptyList()),
            GyllirDebugTarget.of(GyllirCommand.TEST, dir, listOf("--j", "4")),
        )

        val several = pkg(several)
        assertEquals(several.resolve("tool.test"), GyllirDebugTarget.of(GyllirCommand.TEST, several, listOf("tool")).program)
        val unnamed = Assert.assertThrows(ExecutionException::class.java) { GyllirDebugTarget.of(GyllirCommand.TEST, several, emptyList()) }
        assertEquals("Name the target whose tests to debug in the arguments, among cli, tool", unnamed.message)
    }

    fun testBuildRunsNothingToDebug() {
        Assert.assertThrows(ExecutionException::class.java) { GyllirDebugTarget.of(GyllirCommand.BUILD, pkg("name = \"demo\"\n"), emptyList()) }
    }

    fun testGdbStartsOnceGyllirBuiltTheProgram() {
        val dir = pkg("name = \"demo\"\n")
        val target = GyllirDebugTarget(dir, listOf("build", "it's"), dir.resolve("demo"), emptyList())
        val commandLine = YmirDebugAdapterDescriptor.commandLine(target, YmirSettings.getInstance(), Path.of("/gdb/ymir.py"))
        assertEquals(
            listOf("/bin/sh", "-c", "'gyllir' 'build' 'it'\\''s' </dev/null 1>&2 && exec 'gdb' '-q' '-i=dap' '-x' '/gdb/ymir.py' '$dir/demo'"),
            listOf(commandLine.exePath) + commandLine.parametersList.list,
        )
        assertEquals(dir, commandLine.workingDirectory)
    }

    fun testTheOutputOfGyllirStaysOutOfTheProtocol() {
        val settings = YmirSettings.getInstance()
        val dir = pkg("name = \"demo\"\n")
        val target = GyllirDebugTarget(dir, listOf("build", "it's"), dir.resolve("demo"), emptyList())
        settings.gyllirPath = "echo"
        settings.gdbPath = "echo"
        try {
            val output = CapturingProcessHandler(YmirDebugAdapterDescriptor.commandLine(target, settings, null)).runProcess(10_000)
            assertEquals("build it's\n", output.stderr)
            assertEquals("-q -i=dap $dir/demo\n", output.stdout)

            settings.gyllirPath = "false"
            assertEquals("", CapturingProcessHandler(YmirDebugAdapterDescriptor.commandLine(target, settings, null)).runProcess(10_000).stdout)
        } finally {
            settings.gyllirPath = YmirSettings.DEFAULT_GYLLIR
            settings.gdbPath = YmirSettings.DEFAULT_GDB
        }
    }

    fun testTheDebugExecutorRunsThroughGdb() {
        val dir = pkg("name = \"demo\"\n")
        val run = GyllirRuns.create(project, dir, GyllirCommand.RUN).configuration as GyllirRunConfiguration
        assertInstanceOf(ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, run), DAPDebugRunner::class.java)
        assertFalse(ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, run) is DAPDebugRunner)
        assertNotNull(DebugAdapterManager.getInstance().getDebugAdapterServerById(YmirDebugAdapterFactory.ID))

        val build = GyllirRuns.create(project, dir, GyllirCommand.BUILD).configuration as GyllirRunConfiguration
        assertFalse(build.canRun(DefaultDebugExecutor.EXECUTOR_ID))
    }

    fun testYmirFilesTakeTheBreakpoints() {
        val manager = DebugAdapterManager.getInstance()
        assertTrue(manager.isDebuggableFile(myFixture.addFileToProject("pkg/src/main.yr", "fn main() {}\n").virtualFile, project))
        assertFalse(manager.isDebuggableFile(myFixture.addFileToProject("pkg/notes.txt", "").virtualFile, project))
    }

    fun testTheGdbScriptShipsWithThePlugin() {
        assertNotNull(YmirDebugAdapterDescriptor.script())
    }
}
