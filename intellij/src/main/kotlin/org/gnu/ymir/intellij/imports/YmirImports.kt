package org.gnu.ymir.intellij.imports

import org.gnu.ymir.intellij.format.YmirLexer
import org.gnu.ymir.intellij.format.YmirToken

/**
 * The top-level `use` declarations of a Ymir source in their canonical form,
 * as yr-mode's `yr-optimize-imports` and `ymir-lsp` write it: the paths
 * merged, each once, the roots sorted with `std` last and separated by an
 * empty line.
 */
object YmirImports {

    /** A replacement of the text from `start` to `end`. */
    data class Edit(val start: Int, val end: Int, val text: String)

    // The top-level `use` declarations following each other, from the offset of the first to the end of the last,
    // the paths of each, null if it is not a plain path, and whether a comment is between them
    private class Run(val start: Int, val end: Int, val paths: List<List<String>?>, val commented: Boolean)

    /**
     * The edits rewriting the runs of top-level `use` declarations of `text`
     * in their canonical form, without the paths of `remove`, a run of only
     * those being removed. A run holding a comment, or a declaration that is
     * not a plain path, is left as it is.
     */
    fun optimize(text: String, remove: Set<List<String>> = emptySet()): List<Edit> {
        val res = ArrayList<Edit>()
        for (r in runs(YmirLexer.tokens(text))) {
            if (r.commented || r.paths.any { it == null }) continue
            val kept = r.paths.filterNotNull().filter { it !in remove }
            if (kept.isNotEmpty()) {
                val canonical = canonical(kept)
                if (canonical != text.substring(r.start, r.end)) res.add(Edit(r.start, r.end, canonical))
            } else {
                var end = r.end
                while (end < text.length && text[end].isWhitespace()) end++
                res.add(Edit(r.start, end, ""))
            }
        }

        return res
    }

    /** The declarations importing `paths`. */
    fun canonical(paths: List<List<String>>): String {
        val roots = keys(paths)
        return (roots.filter { it != "std" } + roots.filter { it == "std" })
            .joinToString("\n\n") { declarations(it, child(paths, it)).joinToString("\n") }
    }

    private fun runs(tokens: List<YmirToken>): List<Run> {
        val res = ArrayList<Run>()
        var start = -1
        var end = -1
        var paths = ArrayList<List<String>?>()
        var commented = false
        var depth = 0
        var declStart = true
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.isComment) {
                if (start >= 0) commented = true
                i++
                continue
            }

            val protected = declStart && depth == 0 && t.text in PROTECTIONS && tokens.getOrNull(i + 1)?.text == "use"
            if (declStart && depth == 0 && (t.text == "use" || protected)) {
                var j = if (protected) i + 1 else i
                while (j < tokens.size && !tokens[j].isPunctuation(";")) j++
                val parsed = if (protected) null else parse(tokens.subList(i + 1, j))
                if (start < 0) start = t.start
                if (parsed == null) paths.add(null) else paths.addAll(parsed)
                end = if (j < tokens.size) tokens[j].end else tokens.last().end
                i = j + 1
                continue
            }

            if (start >= 0) {
                res.add(Run(start, end, paths, commented && tokens.any { it.isComment && it.start in start until end }))
                start = -1
                paths = ArrayList()
                commented = false
            }

            if (t.isOpening()) depth++
            if (t.isClosing() && depth > 0) depth--
            declStart = depth == 0 && (t.isPunctuation(";") || t.isPunctuation("}"))
            i++
        }

        if (start >= 0) res.add(Run(start, end, paths, commented && tokens.any { it.isComment && it.start in start until end }))
        return res
    }

    /** The paths the tokens following a `use` import, null if they are not plain paths. */
    private fun parse(tokens: List<YmirToken>): List<List<String>>? {
        val res = ArrayList<List<String>>()
        var i = 0

        fun segment(): String? = tokens.getOrNull(i)?.takeIf {
            it.kind == YmirToken.Kind.IDENTIFIER || it.text == "_" || it.kind == YmirToken.Kind.KEYWORD
        }?.text?.also { i++ }

        fun path(prefix: List<String>): Boolean {
            val first = segment() ?: return false
            var p = prefix + first
            while (i < tokens.size && tokens[i].isPunctuation("::")) {
                i++
                if (i < tokens.size && tokens[i].isPunctuation("{")) {
                    i++
                    while (true) {
                        if (!path(p)) return false
                        if (i < tokens.size && tokens[i].isPunctuation(",")) {
                            i++
                            continue
                        }

                        if (i < tokens.size && tokens[i].isPunctuation("}")) {
                            i++
                            return true
                        }

                        return false
                    }
                }

                p = p + (segment() ?: return false)
            }

            res.add(p)
            return true
        }

        if (!path(emptyList()) || i != tokens.size) return null
        return res
    }

    private fun declarations(prefix: String, paths: List<List<String>>): List<String> {
        val leaf = isLeaf(paths)
        val ks = keys(paths)
        if (!leaf && ks.size == 1) return declarations(prefix + "::" + ks[0], child(paths, ks[0]))

        val res = ArrayList<String>()
        if (leaf) res.add("use $prefix;")

        val entries = ArrayList<String>()
        val nested = ArrayList<String>()
        for (k in ks) {
            val c = child(paths, k)
            val direct = directEntries(c)
            if (isLeafNode(c)) {
                entries.add(k)
            } else if (direct != null) {
                if (isLeaf(c)) entries.add(k)
                for (d in direct) entries.add("$k::$d")
            } else {
                nested.addAll(declarations("$prefix::$k", c))
            }
        }

        if (entries.size == 1) res.add("use $prefix::${entries[0]};")
        else if (entries.size > 1) res.add("use $prefix::{${entries.joinToString(", ")}};")
        return res + nested
    }

    /** The keys of `paths`, if it imports `_` and each of its keys is a leaf, null otherwise. */
    private fun directEntries(paths: List<List<String>>): List<String>? {
        val ks = keys(paths)
        if ("_" !in ks || !isLeafNode(child(paths, "_"))) return null
        return ks.takeIf { k -> k.all { isLeafNode(child(paths, it)) } }
    }

    private fun isLeaf(paths: List<List<String>>): Boolean = paths.any { it.isEmpty() }

    private fun isLeafNode(paths: List<List<String>>): Boolean = isLeaf(paths) && keys(paths).isEmpty()

    /** The paths of `paths` starting with `key`, without it. */
    private fun child(paths: List<List<String>>, key: String): List<List<String>> =
        paths.filter { it.isNotEmpty() && it[0] == key }.map { it.drop(1) }

    /** The first segments of `paths`, sorted, each once. */
    private fun keys(paths: List<List<String>>): List<String> = paths.filter { it.isNotEmpty() }.map { it[0] }.distinct().sorted()

    private val PROTECTIONS = setOf("pub", "prv", "prot")
}
