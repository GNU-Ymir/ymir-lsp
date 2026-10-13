package org.gnu.ymir.intellij.format

/**
 * Whether two tokens following each other on a line are spaced, as the printer
 * of `ymir-lsp` decides it from the tokens around them: the closing `|` of the
 * parameters of a lambda, and the `self` of a constructor, being found from the
 * tokens rather than from the syntax tree.
 */
class YmirSpacing(private val code: List<YmirToken>) {

    private val partners = IntArray(code.size) { -1 }

    private val lambdaCloses = HashSet<Int>()

    private val ctors = HashSet<Int>()

    // The bracket each token is in
    private val enclosing = IntArray(code.size) { -1 }

    init {
        val opened = ArrayList<Int>()
        for (i in code.indices) {
            enclosing[i] = opened.lastOrNull() ?: -1
            if (code[i].isOpening()) {
                opened.add(i)
            } else if (code[i].isClosing() && opened.isNotEmpty()) {
                val o = opened.removeLast()
                partners[o] = i
                partners[i] = o
            }
        }

        for (i in code.indices) {
            if (code[i].isPunctuation("|") && i !in lambdaCloses && isLambdaOpen(i)) {
                var j = i + 1
                while (j < code.size && !code[j].isPunctuation("|")) {
                    if (code[j].isOpening()) {
                        if (partners[j] < 0) break
                        j = partners[j]
                    }
                    j++
                }

                if (j < code.size && code[j].isPunctuation("|")) lambdaCloses.add(j)
            }

            if (code[i].text == "self" && i + 1 < code.size && code[i + 1].isPunctuation("(")) {
                val p = code.getOrNull(i - 1)?.text
                if (p == null || p == "pub" || p == "prv" || p == "prot" || p == ";" || p == "{" || p == "}") ctors.add(i)
            }
        }
    }

    /** Whether the token `j` is written a space after the token `i`, on a line. */
    fun isSpaced(i: Int, j: Int): Boolean = spaced(i, j) || isInsideBlock(i, j)

    private fun spaced(i: Int, j: Int): Boolean {
        val a = code[i].text
        val b = code[j].text
        if (j in lambdaCloses) return false
        if (b == ";" && enclosing[j] >= 0 && code[enclosing[j]].text == "[") return true
        if (b == ":" && i > 0 && code[i - 1].text == "enum") return true

        if (b == "," || b == ";" || b == ")" || b == "]") return false
        if (a == "(" || a == "[" || a == ":[" || a == "@" || a == "." || a == ":." || a == "::") return false
        if (b == "." || b == ":.") return isKeyword(i) && !isValueKeyword(a)
        if (b == "?" || b == "->" || b == ":") return false
        if (a == "->" || a == ":") return true

        if (isUnary(i)) return false
        if (a == "|" && isLambdaOpen(i)) return false

        if (b == "::") return !endsOperand(i) || isKeyword(i)
        if (b == "!") {
            if (isKeyword(i)) return !isBangKeyword(a)
            return !endsOperand(i)
        }

        if (a == "!") return false
        if (b == "{") return a != "!" && a != "{"
        if (a == "{") return false
        if (b == "}") return false
        if (a == "}") return true
        if (b == "(") {
            if (i in ctors) return true
            if (a == ")" || a == "]") return false
            if (isTightKeyword(a)) return false
            if (isKeyword(i)) return true
            return !endsOperand(i)
        }

        if (b == "[") return !endsOperand(i) || (isKeyword(i) && !isValueKeyword(a))
        if (b == "...") {
            val n = code.getOrNull(j + 1)?.text
            if (n == "}" || n == ")" || n == "," || n == "]") return false
        }

        return true
    }

    /** Whether the tokens `i` and `j` are the brace of a block and its content. */
    private fun isInsideBlock(i: Int, j: Int): Boolean {
        if (code[i].isPunctuation("{") && !code[j].isPunctuation("}")) return isBlockBrace(i)
        if (code[j].isPunctuation("}") && !code[i].isPunctuation("{")) return partners[j] >= 0 && isBlockBrace(partners[j])
        return false
    }

    private fun isBlockBrace(open: Int): Boolean {
        if (open == 0) return true
        val p = code[open - 1].text
        return p != "!" && p != "@" && p != "::"
    }

    private fun isUnary(i: Int): Boolean {
        val a = code[i].text
        if (code[i].kind != YmirToken.Kind.PUNCTUATION || a !in UNARIES) return false
        if (i == 0) return true
        if (a == "!" && endsOperand(i - 1)) return false
        return !endsOperand(i - 1)
    }

    private fun isLambdaOpen(i: Int): Boolean = i == 0 || !endsOperand(i - 1)

    /** Whether the token `i` can end an operand. */
    private fun endsOperand(i: Int): Boolean {
        val t = code[i]
        if (t.text == ")" || t.text == "]" || t.text == "}" || t.text == "?" || t.text == "$") return true
        if (i in lambdaCloses) return false
        return when (t.kind) {
            YmirToken.Kind.KEYWORD -> isValueKeyword(t.text)
            YmirToken.Kind.PUNCTUATION -> false
            else -> true
        }
    }

    private fun isKeyword(i: Int): Boolean {
        val t = code[i].text
        return code[i].kind == YmirToken.Kind.KEYWORD && t.isNotEmpty() && (t[0] in 'a'..'z' || t[0] == '_' || t[0] == '!')
    }

    private companion object {
        val UNARIES = setOf("-", "&", "*", "!", "&?", "..")

        fun isTightKeyword(t: String): Boolean =
            t == "assert" || t == "typeof" || t == "sizeof" || t == "panic" || t == "self" || t == "super" || t == "__pragma"

        fun isBangKeyword(t: String): Boolean = t == "cast" || t == "__version" || t == "__pragma" || t == "template"

        fun isValueKeyword(t: String): Boolean =
            t == "self" || t == "super" || t == "true" || t == "false" || t == "null" || t == "none" || t == "_"
    }
}
