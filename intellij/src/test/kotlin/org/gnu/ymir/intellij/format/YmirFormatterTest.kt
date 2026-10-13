package org.gnu.ymir.intellij.format

import com.intellij.openapi.util.TextRange
import junit.framework.TestCase
import java.io.File

class YmirFormatterTest : TestCase() {

    /** The `.yr` files of the server and of its tests, which are in their canonical form. */
    private val sources: List<File> = listOf(File("../src"), File("../test")).flatMap { root ->
        root.walk().filter { it.extension == "yr" }.toList()
    }

    private fun unindented(text: String): String {
        val tokens = YmirLexer.tokens(text)
        return text.split('\n').runningFold(0 to "") { (start, _), line -> start + line.length + 1 to line }
            .drop(1)
            .joinToString("\n") { (end, line) ->
                val start = end - line.length - 1
                if (tokens.any { it.start < start && it.end > start }) line else line.trimStart()
            }
    }

    fun testCanonicalSourcesLeftAsTheyAre() {
        assertTrue(sources.size > 20)
        for (f in sources) assertEquals(f.path, f.readText(), YmirFormatter.format(f.readText()))
    }

    fun testCanonicalSourcesIndentedAgain() {
        for (f in sources) {
            val text = f.readText()
            val formatted = YmirFormatter.format(unindented(text))
            val lines = text.lines().zip(formatted.lines()).filter { (a, b) -> a != b && !a.trimStart().startsWith("*") }
            assertEquals(f.path, emptyList<Pair<String, String>>(), lines)
        }
    }

    fun testIdempotent() {
        val text = "fn main ( ) {\nlet x = foo(a,\nb);\n    if x\nreturn ;\n}\n"
        val once = YmirFormatter.format(text)
        assertEquals(once, YmirFormatter.format(once))
    }

    fun testSpacing() {
        assertEquals("fn main(args: [[c8]])-> i32 {\n", YmirFormatter.format("fn main ( args : [ [ c8 ] ] ) -> i32{\n"))
        assertEquals("let x = cast!i64(-y) + a[0us .. $];\n", YmirFormatter.format("let x=cast !i64 ( - y )+a [0us..$] ;\n"))
        assertEquals("use a::{b, c};\n", YmirFormatter.format("use a :: { b , c } ;\n"))
        assertEquals("let f = |a, b| => a < b;\n", YmirFormatter.format("let f = | a , b | => a < b;\n"))
        assertEquals("if (x) { y } else { z }\n", YmirFormatter.format("if(x){y}else{z}\n"))
        assertEquals("pub self (x: i32) with super(x) {}\n", YmirFormatter.format("pub self(x: i32) with super (x) { }\n"))
    }

    fun testPadsKept() {
        val text = "let a   = 1;\nlet bcd = 2;\nmatch x {\n    A   => 1,\n    BCD => 2,\n}\n"
        assertEquals(text, YmirFormatter.format(text))
        assertEquals("let a = 1;   // one\n", YmirFormatter.format("let  a = 1;   // one   \n"))
    }

    fun testEmptyLinesMergedAndTrailingSpacesRemoved() {
        assertEquals("fn f() {\n    a();\n\n    b();\n}\n", YmirFormatter.format("fn f() {   \n  a();\n\n\n\n b();\n}\n\n"))
    }

    fun testCommentsMoveWithTheirLine() {
        assertEquals(
            "fn f() {\n    /**\n     * doc\n     */\n    a();\n}\n",
            YmirFormatter.format("fn f() {\n/**\n * doc\n */\na();\n}\n"),
        )
    }

    fun testStringsLeftAsTheyAre() {
        val text = "fn f() {\n    let s = \"\"\"\n  a  \n\"\"\";\n}\n"
        assertEquals(text, YmirFormatter.format(text))
    }

    fun testContinuationsMovedWithTheirStatement() {
        assertEquals(
            "fn f() {\n    let x = a\n        .b()\n        .c();\n}\n",
            YmirFormatter.format("fn f() {\nlet x = a\n.b()\n.c();\n}\n"),
        )
        assertEquals(
            "fn f() {\n    let x = aaa\n            ~ b;\n}\n",
            YmirFormatter.format("fn f() {\n  let x = aaa\n          ~ b;\n}\n"),
        )
    }

    fun testNamedElementContinuedOnItsValue() {
        assertEquals(
            "fn f() {\n    foo(b, a-> 12\n               + 12,\n        c);\n}\n",
            YmirFormatter.format("fn f() {\nfoo(b, a-> 12\n+ 12,\nc);\n}\n"),
        )
    }

    fun testOnlyTheLinesOfTheRanges() {
        val text = "fn f() {\na();\nb();\n}\n"
        val b = text.indexOf("b()")
        assertEquals("fn f() {\na();\n    b();\n}\n", YmirFormatter.format(text, listOf(TextRange(b, b + 1))))
    }
}
