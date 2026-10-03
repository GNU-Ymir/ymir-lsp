package org.gnu.ymir.intellij.debug

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.ui.SimpleColoredText
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointType
import org.gnu.ymir.intellij.run.GyllirCommand
import org.gnu.ymir.intellij.run.GyllirRuns
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.Icon

/**
 * A program built by the gyllir of the PATH, debugged by its gdb, stopping on
 * a breakpoint of its source, the test passing without them. The background
 * tasks stay in the background: LSP4IJ waits in one for the session to start
 * gdb, which never happens when a headless test runs the task on the EDT.
 */
class YmirDebugSessionTest : BasePlatformTestCase() {

    private var ignoreHeadless: String? = null

    override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

    override fun setUp() {
        super.setUp()
        ignoreHeadless = System.setProperty(IGNORE_HEADLESS, "true")
    }

    override fun tearDown() {
        try {
            for (session in XDebuggerManager.getInstance(project).debugSessions) {
                session.stop()
                waitFor { session.isStopped.takeIf { it } }
                Disposer.dispose(session.consoleView)
                Disposer.dispose(session.runContentDescriptor)
            }
            FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
            if (ignoreHeadless == null) System.clearProperty(IGNORE_HEADLESS) else System.setProperty(IGNORE_HEADLESS, ignoreHeadless!!)
        } finally {
            super.tearDown()
        }
    }

    fun testABreakpointStopsTheProgramOnItsFramesAndVariables() {
        if (listOf("gyllir", "gyc", "gdb").any { PathEnvironmentVariableUtil.findInPath(it) == null }) return

        val manifest = myFixture.addFileToProject("demo/gyllir.toml", """
            name = "demo"
            version = "0.1.0"
            type = "executable"
            compiler = "gyc"
            license = "GPL-v3"
            authors = ["me"]
            description = "demo"

            [registry]
            git = "git@github.com:me/demo.git"
        """.trimIndent())
        val main = myFixture.addFileToProject("demo/src/main.yr", """
            use std::io;

            fn add(a: i32, b: i32)-> i32 {
                let x = a * 2;
                let name = "sum";
                x + b
            }

            fn main() {
                println(add(1, 2));
            }
        """.trimIndent()).virtualFile
        val dir = manifest.virtualFile.parent.toNioPath()

        val type = XBreakpointType.EXTENSION_POINT_NAME.findExtension(DAPBreakpointType::class.java)!!
        runWriteAction {
            XDebuggerManager.getInstance(project).breakpointManager.addLineBreakpoint(type, main.url, 5, type.createBreakpointProperties(main, 5))
        }

        val settings = GyllirRuns.create(project, dir, GyllirCommand.RUN)
        val environment = ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance(), settings).build()
        environment.runner.execute(environment)

        val session = waitFor { XDebuggerManager.getInstance(project).currentSession?.takeIf { it.isSuspended } }
        val frames = frames(session.suspendContext!!.activeExecutionStack!!)
        assertEquals(listOf("main::add(i32, i32)", "main::main()"), frames.map { name(it) })
        assertEquals(main to 5, frames[0].sourcePosition!!.let { it.file to it.line })
        assertEquals(listOf("a" to "1", "b" to "2", "x" to "2", "name" to "\"sum\""), variables(frames[0]).filter { it.first in setOf("a", "b", "x", "name") })
    }

    private fun <T : Any> waitFor(timeoutSeconds: Long = 60, value: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            value()?.let { return it }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(50)
        }
        fail("Timed out")
        throw IllegalStateException()
    }

    private fun <T : Any> await(future: CompletableFuture<T>): T = waitFor { future.getNow(null) }

    private fun frames(stack: XExecutionStack): List<XStackFrame> {
        val frames = mutableListOf<XStackFrame>()
        val done = CompletableFuture<Unit>()
        stack.computeStackFrames(0, object : XExecutionStack.XStackFrameContainer {
            override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
                frames += stackFrames
                if (last) done.complete(Unit)
            }

            override fun errorOccurred(errorMessage: String) {
                done.completeExceptionally(IllegalStateException(errorMessage))
            }
        })
        await(done)
        return frames
    }

    /** The function of `frame`, the first fragment of its presentation, before its line and its file. */
    private fun name(frame: XStackFrame): String {
        val text = SimpleColoredText()
        frame.customizePresentation(text)
        return text.texts.first()
    }

    /** The variables of the scopes of `frame`, its groups, by name and rendered value. */
    private fun variables(frame: XStackFrame): List<Pair<String, String>> =
        children(frame::computeChildren).groups.flatMap { scope -> children(scope::computeChildren).values }
            .map { (name, value) -> name to presentation(value) }

    private class Children(val values: List<Pair<String, XValue>>, val groups: List<XValueGroup>)

    private fun children(compute: (XCompositeNode) -> Unit): Children {
        val values = mutableListOf<Pair<String, XValue>>()
        val groups = mutableListOf<XValueGroup>()
        val done = CompletableFuture<Unit>()
        compute(object : XCompositeNode {
            override fun addChildren(list: XValueChildrenList, last: Boolean) {
                for (i in 0 until list.size()) values += list.getName(i) to list.getValue(i)
                groups += list.topGroups
                groups += list.bottomGroups
                if (last) done.complete(Unit)
            }

            override fun tooManyChildren(remaining: Int) = done.complete(Unit).let { }
            override fun setAlreadySorted(alreadySorted: Boolean) {}
            override fun setErrorMessage(errorMessage: String) = done.completeExceptionally(IllegalStateException(errorMessage)).let { }
            override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) = setErrorMessage(errorMessage)
            override fun setMessage(message: String, icon: Icon?, attributes: com.intellij.ui.SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink?) {}
        })
        await(done)
        return Children(values, groups)
    }

    private fun presentation(value: XValue): String {
        val done = CompletableFuture<String>()
        value.computePresentation(object : XValueNode {
            override fun setPresentation(icon: Icon?, type: String?, value: String, hasChildren: Boolean) {
                done.complete(value)
            }

            override fun setPresentation(icon: Icon?, presentation: XValuePresentation, hasChildren: Boolean) {
                val text = StringBuilder()
                presentation.renderValue(object : XValuePresentation.XValueTextRenderer {
                    override fun renderValue(value: String) = text.append(value).let { }
                    override fun renderValue(value: String, key: com.intellij.openapi.editor.colors.TextAttributesKey) = text.append(value).let { }
                    override fun renderStringValue(value: String) = text.append('"').append(value).append('"').let { }
                    override fun renderStringValue(value: String, additionalSpecialCharsToHighlight: String?, maxLength: Int) = renderStringValue(value)
                    override fun renderNumericValue(value: String) = text.append(value).let { }
                    override fun renderKeywordValue(value: String) = text.append(value).let { }
                    override fun renderComment(comment: String) {}
                    override fun renderSpecialSymbol(symbol: String) = text.append(symbol).let { }
                    override fun renderError(error: String) = text.append(error).let { }
                })
                done.complete(text.toString())
            }

            override fun setFullValueEvaluator(fullValueEvaluator: com.intellij.xdebugger.frame.XFullValueEvaluator) {}
        }, XValuePlace.TREE)
        return await(done)
    }

    companion object {
        private const val IGNORE_HEADLESS = "intellij.progress.task.ignoreHeadless"
    }
}
