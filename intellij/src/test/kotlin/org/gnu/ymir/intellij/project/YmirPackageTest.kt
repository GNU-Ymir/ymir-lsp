package org.gnu.ymir.intellij.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class YmirPackageTest {

    private fun pkg(type: YmirPackageType) = YmirPackage(
        name = "hello",
        type = type,
        authors = listOf("Jane \"J\" Doe"),
        description = "A minimal Ymir app",
        license = "MIT",
        registry = "local:/home/jane/.local/gyllir/hello",
    )

    @Test
    fun executableLayout() {
        val files = pkg(YmirPackageType.EXECUTABLE).files()
        assertEquals(listOf("gyllir.toml", ".gitignore", "src/main.yr", "test/__test__.yr"), files.keys.toList())
        assertTrue(files.getValue(".gitignore").endsWith("/hello\n/hello.test\n"))
        assertTrue(files.getValue("src/main.yr").contains("fn main()"))
    }

    @Test
    fun libraryLayout() {
        val files = pkg(YmirPackageType.LIBRARY).files()
        assertEquals(listOf("gyllir.toml", ".gitignore", "src/__lib__.yr", "test/__test__.yr"), files.keys.toList())
        assertTrue(files.getValue(".gitignore").endsWith("/libhello.a\n/hello.test\n"))
    }

    @Test
    fun manifest() {
        assertEquals(
            """
            |name = "hello"
            |license = "MIT"
            |description = "A minimal Ymir app"
            |type = "library"
            |version = "0.1.0"
            |authors = ["Jane \"J\" Doe"]
            |registry = "local:/home/jane/.local/gyllir/hello"
            |""".trimMargin(),
            pkg(YmirPackageType.LIBRARY).files().getValue("gyllir.toml"),
        )
    }

    @Test
    fun packageName() {
        assertEquals("my_project-2", YmirPackage.packageName(" my project-2 "))
        assertEquals("main", YmirPackage.packageName(""))
    }

    @Test
    fun writeTo() {
        val dir = Files.createTempDirectory("ymir-package")
        try {
            pkg(YmirPackageType.EXECUTABLE).writeTo(dir)
            assertTrue(Files.isRegularFile(dir.resolve("gyllir.toml")))
            assertTrue(Files.isRegularFile(dir.resolve("src/main.yr")))
            assertTrue(Files.isRegularFile(dir.resolve("test/__test__.yr")))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
