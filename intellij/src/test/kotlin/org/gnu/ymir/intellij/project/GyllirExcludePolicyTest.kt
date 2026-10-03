package org.gnu.ymir.intellij.project

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class GyllirExcludePolicyTest {

    @Test
    fun theDependenciesOfAPackageAreExcluded() {
        val dir = Files.createTempDirectory("ymir-pkg")
        assertEquals(emptyList<Any>(), GyllirExcludePolicy.excluded(dir))

        Files.writeString(dir.resolve("gyllir.toml"), "name = \"pkg\"\n")
        assertEquals(listOf(dir.resolve(".deps")), GyllirExcludePolicy.excluded(dir))
    }
}
