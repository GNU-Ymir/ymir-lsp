package org.gnu.ymir.intellij.debug

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The gdb script of the plugin, loaded in the gdb of the PATH, the test
 * passing without one.
 */
class YmirGdbScriptTest : BasePlatformTestCase() {

    /** The lines `python print(<expression>)` prints in gdb for each expression, `null` without a gdb. */
    private fun python(vararg expressions: String): List<String>? {
        val gdb = PathEnvironmentVariableUtil.findInPath("gdb") ?: return null
        val script = YmirDebugAdapterDescriptor.script()!!
        val commandLine = GeneralCommandLine(gdb.path, "-batch", "-nx", "-x", script.toString())
            .withParameters(expressions.flatMap { listOf("-ex", "python print($it)") })
        val output = CapturingProcessHandler(commandLine).runProcess(30_000)
        assertEquals(output.stderr, 0, output.exitCode)
        return output.stdoutLines
    }

    private fun assertDemangled(vararg cases: Pair<String, String>) {
        val names = python(*cases.map { "demangle('${it.first}')" }.toTypedArray()) ?: return
        assertEquals(cases.map { it.second }, names)
    }

    fun testFunctions() = assertDemangled(
        "_Y4main3addFi32i32Zi32" to "main::add(i32, i32)",
        "_Y4main4mainFZv" to "main::main()",
        "_Y4main4main6_50_13Fi32Zi32" to "main::main::_50_13(i32)",
        "_Y4main6__test10T" to "main::__test::0",
    )

    fun testMethodsWithoutTheirSelf() = assertDemangled(
        "_Y4main5Point4normMTP114main5Pointi32Zf64" to "main::Point::norm(i32)",
        "_Y5ymirc6parser6Parser4selfCTxP21x5ymirc6parser6ParserS2c8MP24173std2fs4path4Path_S2c8bZv"
            to "ymirc::parser::Parser::self([c8], [std::fs::path::Path => [c8]], bool)",
    )

    fun testTemplates() = assertDemangled(
        "_Y4main5twiceNi32Fi32Zi32" to "main::twice{i32}(i32)",
        "_Y3std4conv2toNu32NS2c8Nc8Ns198FS2c8Zu32" to "std::conv::to{u32, [c8], c8, \"b\"}([c8])",
        "_Y3std2fs6errors2toNS2c8Nc8N3std2fs6errors11FsErrorCodeF3std2fs6errors11FsErrorCodeZS2c8"
            to "std::fs::errors::to{[c8], c8, std::fs::errors::FsErrorCode}(std::fs::errors::FsErrorCode)",
    )

    fun testOtherSymbols() = assertDemangled(
        "_Y4main5PointVT" to "main::Point",
        "_YMP10S2c8_usizeMI" to "[[c8] => usize]",
        "_Y3std4time7instant7Instant8opBinaryNs143MTR253std4time7instant7Instant223std4time3dur8DurationZ253std4time7instant7Instant.localalias"
            to "std::time::instant::Instant::opBinary{\"+\"}(std::time::dur::Duration) [clone .localalias]",
        "_Y4main3addFQQ" to "main::add(...)",
        "_yrt_run_main_debug" to "_yrt_run_main_debug",
    )

    fun testTheFrameFilterAndThePrintersAreRegistered() {
        val registered = python("'ymir' in gdb.frame_filters", "[p.name for p in gdb.pretty_printers]") ?: return
        assertEquals("True", registered[0])
        assertTrue(registered[1], "'ymir'" in registered[1])
    }
}
