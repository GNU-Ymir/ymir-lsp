package org.gnu.ymir.intellij

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import java.util.concurrent.atomic.AtomicBoolean

class YmirMemoryBudgetTest : HeavyPlatformTestCase() {

    private val notifications = mutableListOf<Notification>()

    override fun setUp() {
        super.setUp()
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications += notification
            }
        })
    }

    override fun tearDown() {
        try {
            LanguageServersRegistry.getInstance().getServerDefinition(YmirLanguageServerFactory.SERVER_ID)?.setEnabled(true, project)
        } finally {
            super.tearDown()
        }
    }

    fun testNoBudgetStartsTheServerAlone() {
        for (systemd in listOf(true, false)) {
            val commandLine = YmirMemoryBudget(0, systemd).commandLine("ymir-lsp")
            assertEquals("ymir-lsp", commandLine.commandLineString)
            assertEmpty(commandLine.environment.keys)
        }
    }

    fun testASystemdScopeKillsTheServerPastTheBudgetWithoutSwapping() {
        val commandLine = YmirMemoryBudget(2048, systemd = true).commandLine("/opt/ymir-lsp")
        assertEquals(
            listOf("systemd-run", "--user", "--scope", "--quiet", "-p", "MemoryMax=2048M", "-p", "MemorySwapMax=0", "/opt/ymir-lsp"),
            commandLine.getCommandLineList(null),
        )
        assertEmpty(commandLine.environment.keys)
    }

    fun testWithoutSystemdTheGcHeapIsCapped() {
        val commandLine = YmirMemoryBudget(512, systemd = false).commandLine("ymir-lsp")
        assertEquals("ymir-lsp", commandLine.commandLineString)
        assertEquals(mapOf("GC_MAXIMUM_HEAP_SIZE" to "536870912"), commandLine.environment)
    }

    fun testExceeded() {
        assertTrue(YmirMemoryBudget(64, systemd = true).exceeded(137, false))
        assertFalse(YmirMemoryBudget(64, systemd = true).exceeded(1, false))
        assertTrue(YmirMemoryBudget(64, systemd = false).exceeded(139, true))
        assertFalse(YmirMemoryBudget(64, systemd = false).exceeded(137, false))
        assertFalse(YmirMemoryBudget(0, systemd = true).exceeded(137, true))
    }

    fun testAServerAllocatingWithoutBoundIsStoppedOnTheBudget() {
        if (!YmirMemoryBudget.systemdUserSession()) return
        val restarted = run(YmirMemoryBudget(64), "exec awk 'BEGIN { s = \"x\"; while (1) s = s s }'")
        assertBudgetNamed("64 MiB")
        assertFalse(restarted.get())
    }

    fun testAServerTheGcRanOutOfMemoryInIsStoppedOnTheBudget() {
        val restarted = run(
            YmirMemoryBudget(64, systemd = false),
            "echo 'GC Warning: Out of Memory! Heap size: 63 MiB. Returning NULL!' >&2\nkill -SEGV $$",
        )
        assertBudgetNamed("64 MiB")
        assertFalse(restarted.get())
    }

    fun testAServerCrashingOtherwiseIsLeftToLsp4ij() {
        val restarted = run(YmirMemoryBudget(64, systemd = false), "exit 1")
        PlatformTestUtil.waitWithEventsDispatching("the server was not restarted", { restarted.get() }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEmpty(notifications)
    }

    /** Starts the server of `script` under `budget`, returning whether the stop handler of LSP4IJ was run. */
    private fun run(budget: YmirMemoryBudget, script: String): AtomicBoolean {
        val server = FileUtil.createTempFile("ymir-lsp", ".sh", true)
        server.writeText("#!/bin/sh\n$script\n")
        server.setExecutable(true)
        val commandLine = budget.commandLine(server.path)
        val provider = YmirServerConnectionProvider(project, budget, commandLine)
        val restarted = AtomicBoolean()
        provider.addUnexpectedServerStopHandler { restarted.set(true) }
        provider.start()
        return restarted
    }

    private fun assertBudgetNamed(budget: String) {
        PlatformTestUtil.waitWithEventsDispatching("no notification of the budget", { notifications.isNotEmpty() }, 30)
        assertEquals(1, notifications.size)
        assertTrue(notifications.single().content, budget in notifications.single().content)
    }
}
