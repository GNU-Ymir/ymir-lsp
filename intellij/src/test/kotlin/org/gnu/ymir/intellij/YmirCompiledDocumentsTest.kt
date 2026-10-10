package org.gnu.ymir.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class YmirCompiledDocumentsTest : BasePlatformTestCase() {

    private val settings get() = YmirSettings.getInstance()

    override fun tearDown() {
        try {
            settings.compiledDocuments = YmirSettings.DEFAULT_COMPILED_DOCUMENTS
        } finally {
            super.tearDown()
        }
    }

    fun testTheServerCompilesFiveDocumentsByDefault() {
        assertEquals(mapOf("compiledDocuments" to 5), initializationOptions())
    }

    fun testTheServerCompilesTheDocumentsOfTheSettings() {
        settings.compiledDocuments = 2
        assertEquals(mapOf("compiledDocuments" to 2), initializationOptions())

        settings.compiledDocuments = -3
        assertEquals(0, settings.compiledDocuments)
        assertEquals(mapOf("compiledDocuments" to 0), initializationOptions())
    }

    private fun initializationOptions(): Any? =
        YmirLanguageServerFactory().createConnectionProvider(project).getInitializationOptions(null)
}
