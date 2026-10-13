package org.gnu.ymir.intellij.format

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class YmirServerReformatActionTest : BasePlatformTestCase() {

    private val action get() = ActionManager.getInstance().getAction(YmirServerReformatAction.ID)

    private fun updated(): AnActionEvent {
        val event = TestActionEvent.createTestEvent(action, (myFixture.editor as EditorEx).dataContext)
        action.update(event)
        return event
    }

    fun testEnabledInTheEditorOfAYmirFile() {
        assertInstanceOf(action, YmirServerReformatAction::class.java)
        myFixture.configureByText("main.yr", "fn main() {}\n")
        assertTrue(updated().presentation.isEnabledAndVisible)
    }

    fun testHiddenInAnotherFile() {
        myFixture.configureByText("notes.txt", "fn main() {}\n")
        assertFalse(updated().presentation.isEnabledAndVisible)
    }

    fun testNoServerFormattingWithoutTheServer() {
        assertNull(YmirServerReformatAction.serverService(myFixture.configureByText("main.yr", "fn main() {}\n")))
    }
}
