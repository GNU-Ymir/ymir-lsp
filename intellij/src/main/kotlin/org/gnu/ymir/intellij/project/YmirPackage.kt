package org.gnu.ymir.intellij.project

import java.nio.file.Files
import java.nio.file.Path

enum class YmirPackageType(val id: String, val label: String) {
    EXECUTABLE("executable", "Executable"),
    LIBRARY("library", "Library"),
}

/**
 * A new gyllir package, laid out as `gyllir init` does: its `gyllir.toml`,
 * a `.gitignore`, the root module under `src/` and the one of the tests under
 * `test/`.
 */
data class YmirPackage(
    val name: String,
    val type: YmirPackageType,
    val authors: List<String>,
    val description: String,
    val license: String,
    val registry: String,
) {

    /** The files of the package, by path relative to its directory. */
    fun files(): Map<String, String> = linkedMapOf(
        "gyllir.toml" to manifest(),
        ".gitignore" to gitignore(),
        rootModule() to rootModuleContent(),
        "test/__test__.yr" to TEST_CONTENT,
    )

    /** Writes the files of the package into `dir`, created if missing. */
    fun writeTo(dir: Path) {
        for ((path, content) in files()) {
            val file = dir.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
    }

    fun rootModule(): String = when (type) {
        YmirPackageType.EXECUTABLE -> "src/main.yr"
        YmirPackageType.LIBRARY -> "src/__lib__.yr"
    }

    private fun manifest(): String = buildString {
        appendLine("name = ${quote(name)}")
        appendLine("license = ${quote(license)}")
        appendLine("description = ${quote(description)}")
        appendLine("type = ${quote(type.id)}")
        appendLine("version = \"0.1.0\"")
        appendLine("authors = [${authors.joinToString(", ") { quote(it) }}]")
        appendLine("registry = ${quote(registry)}")
    }

    private fun gitignore(): String = GITIGNORE_CONTENT + when (type) {
        YmirPackageType.EXECUTABLE -> "/$name\n"
        YmirPackageType.LIBRARY -> "/lib$name.a\n"
    } + "/$name.test\n"

    private fun rootModuleContent(): String = when (type) {
        YmirPackageType.EXECUTABLE -> MAIN_CONTENT
        YmirPackageType.LIBRARY -> LIB_CONTENT
    }

    companion object {

        /** `name` with the characters a package name cannot hold replaced by `_`. */
        fun packageName(name: String): String =
            name.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "main" }

        /** The registry `gyllir init` proposes, in `~/.local/gyllir`. */
        fun defaultRegistry(name: String): String =
            "local:" + Path.of(System.getProperty("user.home"), ".local", "gyllir", name)

        private fun quote(s: String): String =
            "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        private val MAIN_CONTENT = """
            |use std::io;
            |
            |fn main() {
            |    println("Hello World!");
            |}
            |""".trimMargin()

        private val LIB_CONTENT = """
            |use std::io;
            |
            |pub fn foo() {
            |    println("Hello from lib");
            |}
            |""".trimMargin()

        private val TEST_CONTENT = """
            |use std::io;
            |
            |__test {
            |    println("Example of first test");
            |
            |    // To make a test fail, throw an exception
            |    // for example, assert(false);
            |}
            |""".trimMargin()

        private val GITIGNORE_CONTENT = """
            |
            |# Dependencies resolved by gyllir
            |.deps/
            |
            |# Build cache and generated documentation
            |.target/
            |__doc/
            |
            |# Left behind by a test run
            |.ymir_test_success
            |.ymir_coverage_*
            |
            |# Produced artifacts
            |""".trimMargin()
    }
}
