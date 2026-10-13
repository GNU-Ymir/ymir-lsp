# ymir-lsp

The language server of Ymir, built on the `ymirc` frontend (`libymirc`): diagnostics, hover,
definition, references, highlights, semantic tokens, outline, workspace symbols, completion,
quick fixes and formatting over the Language Server Protocol, on stdio or TCP. The repository also
holds the IntelliJ plugin using it, in `intellij/`.

## The server

```sh
gyllir build      # ./ymir-lsp
gyllir test
```

`ymirc` is the `master` branch of [bootstrap](https://github.com/GNU-Ymir/bootstrap), pinned to a
commit by `gyllir.lock` (`gyllir update ymirc` moves it). It is built by the same `gyc`, against
the same std, as this package.

```sh
ymir-lsp                  # on stdio, how an editor starts it
ymir-lsp --listen=PORT    # waits for the client to connect on PORT
ymir-lsp --socket=PORT    # connects to the client listening on PORT
```

`--host=HOST` sets the address of the last two, `127.0.0.1` by default. A file is checked as
gyllir builds it: in the package of the closest `gyllir.toml`, a file of the source directory of
a target is a module of its root file (`main.yr`, `__lib__.yr`, or `package-root`), and a file of
its test directory a module of its `test-root`, `__test__.yr` by default, a package declaring no
`[targets.<name>]` being a single target of its top-level fields. It is compiled against the
library targets of the packages of `.deps/` the target selects, and the targets of its package it
links, with the std its `[std]` resolved there if not the one of gyc, the `-I`, `-iprefix`,
`-nostdinc` and `-fversion=` of the `flags` of the target, and the `__test` blocks. Any other file
is checked alone. The open documents of a target are checked together, by one compilation of
their modules as `-fmodule` selects them: each is given the diagnostics of its file, and the ones
of the other files at its start. Only the 5 most recently opened, edited or queried documents are
compiled, the `compiledDocuments` of the `initializationOptions`, `0` for all of them: the others
keep the diagnostics of their latest check, and a query about one compiles it again. A closed
document leaves the compilation of its target. A document
is checked once the client stopped changing it for 250ms, the unsaved buffers of the client
standing in for their files, and its warnings are reported as such, as with `gyllir build`. A
save checks again the open documents reading a document changed since their latest check.

A hover shows the signature of the entity under the cursor, followed by the documentation comment
of a symbol, in Markdown when the client renders it: its paragraphs, its tags (`@params:`,
`@returns:`, `@example:`...) as labelled sections, their `- name: text` items as lists, and its
code blocks and HTML as they are written.

The references of a symbol are its usages in the compilation of the document, and in the ones of
the packages of the workspace folders, sources and tests, compiled again first when a document
they read changed since: the modules the document does not read are searched as well. The
highlights of a symbol are its usages in the document.

The semantic tokens of a document are its tokens naming an entity in its compilation, a token
once: typed by the kind of the entity (`namespace`, `class`, `struct`, `enum`, `interface`, `type`,
`function`, `method`, `macro`, `variable`, `parameter`, or `property` for a field), a declaration
carrying the `declaration` modifier. A name resolving to nothing has none.

A diagnostic carries its quick fixes: the edits ymirc proposes, preceded by the ones of the
server. An undefined symbol is fixed by the `use` of each module declaring it publicly, among the
modules of the workspace symbols, a `use` resolving no symbol by its removal, and a local variable
written to while immutable by its `mut`, or its `dmut` when a part of it is written to. The `use`
declarations are rewritten in their canonical form, as the formatting writes them.

The workspace symbols are the top-level declarations of the modules compiled, and of the modules
of the std and of `.deps` they use, as of their latest compilation: the packages of the workspace
folders are compiled whole, sources and tests, once the server is idle after the initialization,
and after a save for the ones a document they read changed in since, giving way to any message
of the client, and a checked document replaces the declarations of its file. A query matches
the names starting with its first character and holding the others in order.

A document is formatted in its canonical form: its tokens on lines of at most 120 characters,
indented by 4 spaces, the lists that do not fit broken one element a line, aligned on their
opening parenthesis, the chains of at least two `.` or `:.` calls that do not fit broken before
each call, indented once, the consecutive `let`, `def`, enum fields and `match` arms aligned on their
`=` or `=>`, and its top-level `use` declarations merged and sorted, `std` last, as yr-mode's
`yr-optimize-imports` writes them. A range formats the declarations it is in, and a document
that does not parse is left as it is.

## The IntelliJ plugin

```sh
cd intellij
./gradlew buildPlugin   # build/distributions/ymir-intellij-<version>.zip, to install from disk
./gradlew runIde        # an IDE with the plugin installed, in a sandbox
```

It runs in IntelliJ-based IDEs from 2026.1, and depends on
[LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij), which starts `ymir-lsp` for the
`.yr` files and maps its features onto the IDE, and on the bundled TextMate plugin, which
highlights them with the grammar of `intellij/textmate/ymir`, `self` and `super` in a color of
their own, set under *Settings > Editor > Color Scheme > Ymir*. The command starting the server is
`ymir-lsp` from the PATH by default, and set under *Settings > Languages & Frameworks > Ymir*.
It runs under a memory budget set there, 2048 MiB by default and `0` for none: in a systemd user
scope killing it past the budget without letting it swap (`systemd-run --user --scope -p
MemoryMax=<budget>M -p MemorySwapMax=0`), or else with the heap of its GC capped by
`GC_MAXIMUM_HEAP_SIZE`. A server stopped on its budget is not restarted, and a notification
offers to raise the budget or to restart it. The number of open documents the server compiles,
5 by default, is set there as well.
In IntelliJ IDEA, *File > New > Project… > Ymir* creates a gyllir package, an executable or a
library, as `gyllir init` lays it out.

A *Gyllir* run configuration runs `gyllir run`, `build` or `test` in a package, the locations of
the diagnostics of gyc linked to their file. A project rooted at a package gets its configurations
when first opened (*Run* and *Test*, or *Test* and *Build* for a library), and the gutter of
`fn main` runs or debugs the package. A `test` configuration takes the `--filter` of the tests it
runs, `module::test` glob segments, and an environment file of `KEY=VALUE` lines set in the
environment of the tests (`--env-file`). The gutter of a `__test` runs or debugs this test alone,
in a configuration filtering it by its module, the path of its file under the source or test
directory, and its name, its rank among the tests of the module when unnamed, `name[*]` for the
cases of a parameterized one: it takes the arguments and the environment file of the
configuration running the tests of the package.

Debugging a *Run* or *Test* configuration builds the program, or its tests with `gyllir test
--dry`, and runs it under gdb, 14 or later, through its Debug Adapter Protocol and the client of
LSP4IJ: the breakpoints of the gutter of the `.yr` files stop it, and *Frames* and *Variables*
show its stack and the values of each frame. The arguments of a package of several targets name
the one to debug first, and the tests run on the filter of the configuration, in the variables of
its environment file. gdb is `gdb` from the PATH by default, set under *Settings > Languages &
Frameworks > Ymir*, and loads `intellij/gdb/ymir.py`, naming the frames by their demangled Ymir
function, eliding the frames of the runtime, leaving the temporaries of the compiler out of the
locals, and printing the slices, options, maps and objects of Ymir.

The documentation popup of a symbol, on hover or Ctrl+Q, is the hover of the server, rendered by
LSP4IJ, its code blocks highlighted with the grammar.

Alt+Enter on a diagnostic lists its quick fixes, along with the generic actions of the IDE.

*Find Usages* (Alt+F7), *Show Usages* (Ctrl+Alt+F7), and Ctrl+click on a declaration list the
usages of a symbol in the packages of the workspace, and the ones of the file are highlighted
under the caret.
LSP4IJ cuts a `.yr` file into the semantic tokens of the server, Ctrl+click going to the declaration
of a reference and showing the usages of a declaration, while a name without a token, one the server
resolves to nothing, is neither highlighted nor navigated from.

*Go to Class* lists the structs, classes, enums, traits and type `def`s of the workspace symbols
of the server, and *Go to Symbol* all of them, with the module declaring them. Those of the std
and of `.deps`, which is excluded from a project rooted at a package, are listed with the
non-project items.

Enter, and a closing bracket typed at the start of a line, indent the line as the formatting does,
without the server: a block, and a list opened at the end of its line, once more than the line
opening it, a list aligned on its first element, the value of its named element `name-> value` on
this value, and once more the body of an `if`, a loop or an `else` without braces, the value of a
`=` or a `=>` ending a line, and a line starting with an operator. Enter between two brackets puts
the closing one on a line of its own, and an opening bracket typed is closed once, at the end of
the file as elsewhere.

*Code > Reformat Ymir Code* (Meta+Alt+L, Ctrl+Alt+L being the screen lock of most Linux
desktops) runs *Reformat Code* in a `.yr` file, or on the lines of the selection, formatted by the
plugin rather than by the server: each line indented as on Enter, a line continuing the expression
of the line before it moved along with it, the tokens of a line spaced as the server spaces them,
the spaces aligning a `=`, a `=>`, a `{` or a comment kept, the trailing spaces removed and the
empty lines following each other merged. Only the whitespace changes: no line is broken or joined,
and the `use` declarations are left in their order. *Code > Reformat Ymir Code with ymir-lsp*
rewrites the file, or the declarations of the selection, in the canonical form of the server.

*Code > Optimize Imports* (Ctrl+Alt+O) rewrites the top-level `use` declarations of a `.yr` file in
their canonical form, as the formatting of the server does, without the server, and removes the
ones the latest diagnostics of the session report unused (E3038). A run of declarations holding a
comment, or one that is not a plain path, is left as it is.

The build downloads the IDE of `platformVersion` (`gradle.properties`). To build against an
installed one instead, set `platformLocalPath` to its directory, e.g. in
`~/.gradle/gradle.properties`. Gradle needs a JDK 21 to compile the plugin, and fetches one when
none is installed.
