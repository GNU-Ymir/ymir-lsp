package org.gnu.ymir.intellij.imports

import junit.framework.TestCase

class YmirImportsTest : TestCase() {

    private fun optimized(text: String, remove: Set<List<String>> = emptySet()): String {
        var res = text
        for (e in YmirImports.optimize(text, remove).asReversed()) res = res.replaceRange(e.start, e.end, e.text)
        return res
    }

    fun testMergedSortedStdLast() {
        assertEquals(
            "in m;\n\nuse a::b;\nuse a::c::{d, e};\n\nuse z::y;\n\nuse std::io;\n\nfn f() {}\n",
            optimized("in m;\n\nuse std::io;\nuse a::c::e;\nuse z::y;\nuse a::b;\nuse a::c::d;\nuse a::b;\n\nfn f() {}\n"),
        )
    }

    fun testGroupsExpanded() {
        assertEquals("use a::{b, c, d::_};\n", optimized("use a::{c, d::_, b};\nuse a::b;\n"))
    }

    fun testCanonicalLeftAsItIs() {
        assertTrue(YmirImports.optimize("in m;\n\nuse a::{b, c};\n\nuse std::io;\n").isEmpty())
    }

    fun testUnusedRemoved() {
        assertEquals("use a::c;\n", optimized("use a::{b, c};\n", setOf(listOf("a", "b"))))
        assertEquals("in m;\n\nfn f() {}\n", optimized("in m;\n\nuse a::b;\n\nfn f() {}\n", setOf(listOf("a", "b"))))
    }

    fun testEachRunOnItsOwn() {
        assertEquals("use a::{b, c};\n\nfn f() {}\n\nuse d::{e, f};\n", optimized("use a::c;\nuse a::b;\n\nfn f() {}\n\nuse d::f;\nuse d::e;\n"))
    }

    fun testRunsWithCommentsOrNamesLeftAsTheyAre() {
        assertTrue(YmirImports.optimize("use b;\n// a\nuse a;\n").isEmpty())
        assertTrue(YmirImports.optimize("use b;\nuse x = a::c;\n").isEmpty())
        assertTrue(YmirImports.optimize("use b;\nuse ::a;\n").isEmpty())
        assertTrue(YmirImports.optimize("use b;\npub use a;\n").isEmpty())
    }

    fun testLocalUsesLeftAsTheyAre() {
        assertTrue(YmirImports.optimize("fn f() {\n    use b;\n    use a;\n}\n").isEmpty())
    }
}
