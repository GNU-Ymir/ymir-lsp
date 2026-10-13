package org.gnu.ymir.intellij.format

import junit.framework.TestCase

class YmirIndenterTest : TestCase() {

    /** The indentation of the line of `text` holding `|`, the marker removed. */
    private fun indent(text: String): Int? {
        val at = text.indexOf('|')
        val source = text.removeRange(at, at + 1)
        return YmirIndenter.indentAt(source, YmirIndenter.lineStart(source, at))
    }

    fun testTopLevel() {
        assertEquals(0, indent("in main;\n|"))
    }

    fun testBlockContent() {
        assertEquals(4, indent("fn main() {\n|"))
        assertEquals(8, indent("fn main() {\n    if (x) {\n|"))
        assertEquals(4, indent("fn main() {\n    foo();\n|"))
    }

    fun testClosingBracketIsTheLineOpeningIt() {
        assertEquals(0, indent("fn main() {\n    foo();\n|}"))
        assertEquals(4, indent("fn main() {\n    let x = [\n        1,\n|    ];"))
    }

    fun testListAlignedOnItsFirstElement() {
        assertEquals(4, indent("foo(a,\n|"))
        assertEquals(8, indent("    if (a &&\n|"))
    }

    fun testListOpenedAtTheEndOfItsLineIndentedOnce() {
        assertEquals(8, indent("    foo(\n|"))
    }

    fun testBlockOfALineClosingAListOpenedBefore() {
        assertEquals(4, indent("fn foo(a: i32,\n       b: i32) {\n|"))
    }

    fun testBodyWithoutBraces() {
        assertEquals(8, indent("fn f() {\n    if (x)\n|"))
        assertEquals(8, indent("fn f() {\n    if x\n|"))
        assertEquals(8, indent("fn f() {\n    if (x) {\n    } else\n|"))
        assertEquals(8, indent("fn f() {\n    while (x)\n|"))
        assertEquals(4, indent("fn f() {\n    if (x) return;\n|"))
        assertEquals(4, indent("fn f() {\n    if (x)\n        foo();\n|"))
        assertEquals(4, indent("fn f() {\n    if (x) {\n|    }"))
    }

    fun testValueAfterAnAssignment() {
        assertEquals(8, indent("fn f() {\n    let x =\n|"))
        assertEquals(12, indent("fn f() {\n    match x {\n        A =>\n|"))
    }

    fun testChainIndentedOnceOnItsStatement() {
        assertEquals(8, indent("fn f() {\n    let x = a\n|.b()"))
        assertEquals(8, indent("fn f() {\n    let x = a\n        .b()\n|.c()"))
    }

    fun testStringsAndCommentsLeftToTheCaller() {
        assertNull(indent("let x = \"a\n|b\";"))
        assertNull(indent("/* a\n| */"))
        assertEquals(0, indent("// a {\n|"))
    }

    fun testTabsAreColumns() {
        assertEquals(8, indent("fn f() {\n\tif (x) {\n|"))
    }
}
