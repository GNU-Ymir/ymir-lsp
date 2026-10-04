# ymir-lsp

The language server of Ymir, built on the `ymirc` frontend (`libymirc`): diagnostics, hover,
definition, references, highlights, outline, workspace symbols, completion, quick fixes and
formatting over the Language Server Protocol, on stdio or TCP. The repository also holds the
IntelliJ plugin using it, in `intellij/`.

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
is checked alone. A document
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
offers to raise the budget or to restart it.
In IntelliJ IDEA, *File > New > Project… > Ymir* creates a gyllir package, an executable or a
library, as `gyllir init` lays it out.

A *Gyllir* run configuration runs `gyllir run`, `build` or `test` in a package, the locations of
the diagnostics of gyc linked to their file. A project rooted at a package gets its configurations
when first opened (*Run* and *Test*, or *Test* and *Build* for a library), and the gutter of
`fn main` and of each `__test` runs or debugs the package or its tests.

Debugging a *Run* or *Test* configuration builds the program, or its tests with `gyllir test
--dry`, and runs it under gdb, 14 or later, through its Debug Adapter Protocol and the client of
LSP4IJ: the breakpoints of the gutter of the `.yr` files stop it, and *Frames* and *Variables*
show its stack and the values of each frame. The arguments of a package of several targets name
the one to debug first. gdb is `gdb` from the PATH by default, set under *Settings > Languages &
Frameworks > Ymir*, and loads `intellij/gdb/ymir.py`, naming the frames by their demangled Ymir
function, eliding the frames of the runtime, leaving the temporaries of the compiler out of the
locals, and printing the slices, options, maps and objects of Ymir.

The documentation popup of a symbol, on hover or Ctrl+Q, is the hover of the server, rendered by
LSP4IJ, its code blocks highlighted with the grammar.

Alt+Enter on a diagnostic lists its quick fixes, along with the generic actions of the IDE.

*Find Usages* (Alt+F7), *Show Usages* (Ctrl+Alt+F7), and Ctrl+click on a declaration list the
usages of a symbol in the packages of the workspace, and the ones of the file are highlighted
under the caret.

*Go to Class* lists the structs, classes, enums, traits and type `def`s of the workspace symbols
of the server, and *Go to Symbol* all of them, with the module declaring them. Those of the std
and of `.deps`, which is excluded from a project rooted at a package, are listed with the
non-project items.

*Code > Reformat Ymir Code* (Meta+Alt+L, Ctrl+Alt+L being the screen lock of most Linux
desktops) runs *Reformat Code* in a `.yr` file, which the server formats, or only the declarations
of the selection.

The build downloads the IDE of `platformVersion` (`gradle.properties`). To build against an
installed one instead, set `platformLocalPath` to its directory, e.g. in
`~/.gradle/gradle.properties`. Gradle needs a JDK 21 to compile the plugin, and fetches one when
none is installed.
