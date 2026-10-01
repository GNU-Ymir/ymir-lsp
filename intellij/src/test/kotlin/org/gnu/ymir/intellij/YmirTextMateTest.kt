package org.gnu.ymir.intellij

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.textmate.TextMateFileType
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.psi.TextMateFile

class YmirTextMateTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        TextMateService.getInstance().reloadEnabledBundles()
        PlatformTestUtil.waitWithEventsDispatching(
            "the Ymir bundle is not loaded",
            { TextMateService.getInstance().getLanguageDescriptorByFileName("main.yr") != null },
            30,
        )
    }

    fun testBundleFound() {
        assertNotEmpty(YmirTextMateBundleProvider().getBundles())
    }

    fun testYrFilesAreTextMate() {
        val file = myFixture.configureByText("main.yr", "fn main() {}\n")
        assertSame(TextMateFileType.INSTANCE, file.virtualFile.fileType)
        assertInstanceOf(file, TextMateFile::class.java)
    }
}
