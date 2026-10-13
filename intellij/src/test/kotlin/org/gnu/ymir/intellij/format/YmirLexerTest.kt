package org.gnu.ymir.intellij.format

import junit.framework.TestCase
import org.gnu.ymir.intellij.format.YmirToken.Kind

class YmirLexerTest : TestCase() {

    private fun lexed(text: String): List<Pair<Kind, String>> = YmirLexer.tokens(text).map { it.kind to it.text }

    fun testKeywordsIdentifiersAndOperators() {
        assertEquals(
            listOf(Kind.KEYWORD to "fn", Kind.IDENTIFIER to "main", Kind.PUNCTUATION to "(", Kind.PUNCTUATION to ")",
                Kind.PUNCTUATION to "->", Kind.IDENTIFIER to "i32", Kind.PUNCTUATION to "{", Kind.PUNCTUATION to "}"),
            lexed("fn main()-> i32 {}"),
        )
    }

    fun testWordsTheCompilerDoesNotReserveAreIdentifiers() {
        assertEquals(listOf(Kind.IDENTIFIER, Kind.IDENTIFIER, Kind.IDENTIFIER, Kind.IDENTIFIER), lexed("pub main exit end").map { it.first })
    }

    fun testNumbersAndRanges() {
        assertEquals(listOf("0us", "..", "src", ".", "len", "1.5f", "x", ".", "0"), lexed("0us .. src.len 1.5f x.0").map { it.second })
    }

    fun testStrings() {
        assertEquals(
            listOf(Kind.STRING to "\"a \\\" b\"s8", Kind.STRING to "f\"{x}\"", Kind.STRING to "\"\"\"a\n\"b\"\n\"\"\""),
            lexed("\"a \\\" b\"s8 f\"{x}\" \"\"\"a\n\"b\"\n\"\"\""),
        )
    }

    fun testCharactersAndQuotes() {
        assertEquals(listOf(Kind.CHAR to "'a'", Kind.CHAR to "'\\n'c8", Kind.PUNCTUATION to "'"), lexed("'a' '\\n'c8 '"))
    }

    fun testCommentsNested() {
        assertEquals(
            listOf(Kind.LINE_COMMENT to "// a { b", Kind.BLOCK_COMMENT to "/* c /* d */ e */", Kind.IDENTIFIER to "f"),
            lexed("// a { b\n/* c /* d */ e */ f"),
        )
    }

    fun testUnterminatedStringRunsToTheEnd() {
        assertEquals(listOf(Kind.STRING to "\"a\nb"), lexed("\"a\nb"))
    }

    fun testNegatedOperators() {
        assertEquals(listOf("x", "!in", "y", "!", "inside"), lexed("x !in y !inside").map { it.second })
    }

    fun testMacrosAndQuotedIdentifiers() {
        assertEquals(listOf(Kind.STRING to "#{a {b} c}", Kind.IDENTIFIER to "`if`"), lexed("#{a {b} c} `if`"))
    }
}
