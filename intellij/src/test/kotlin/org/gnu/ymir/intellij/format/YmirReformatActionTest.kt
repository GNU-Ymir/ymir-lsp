package org.gnu.ymir.intellij.format

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class YmirReformatActionTest : BasePlatformTestCase() {

    private val action get() = ActionManager.getInstance().getAction(YmirReformatAction.ID)

    private fun updated(): AnActionEvent {
        val event = TestActionEvent.createTestEvent(action, (myFixture.editor as EditorEx).dataContext)
        action.update(event)
        return event
    }

    fun testBoundToMetaAltL() {
        assertInstanceOf(action, YmirReformatAction::class.java)
        val keymap = KeymapManager.getInstance().getKeymap(KeymapManager.DEFAULT_IDEA_KEYMAP)!!
        val metaAltL = KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.META_DOWN_MASK or InputEvent.ALT_DOWN_MASK)
        assertContainsElements(keymap.getShortcuts(YmirReformatAction.ID).toList(), KeyboardShortcut(metaAltL, null))
    }

    fun testEnabledInTheEditorOfAYmirFile() {
        myFixture.configureByText("main.yr", "fn main() {}\n")
        assertTrue(updated().presentation.isEnabledAndVisible)
    }

    fun testHiddenInAnotherFile() {
        myFixture.configureByText("notes.txt", "fn main() {}\n")
        assertFalse(updated().presentation.isEnabledAndVisible)
    }

    fun testYmirFiles() {
        assertTrue(YmirReformatAction.isYmir("main.yr"))
        assertFalse(YmirReformatAction.isYmir("main.yr.txt"))
        assertFalse(YmirReformatAction.isYmir(null))
    }
}
