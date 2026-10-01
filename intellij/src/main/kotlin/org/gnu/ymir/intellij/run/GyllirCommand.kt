package org.gnu.ymir.intellij.run

enum class GyllirCommand(val id: String, val label: String) {
    RUN("run", "Run"),
    BUILD("build", "Build"),
    TEST("test", "Test");

    companion object {
        fun of(id: String?): GyllirCommand = entries.firstOrNull { it.id == id } ?: RUN
    }
}
