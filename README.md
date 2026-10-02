# ymir-lsp

The language server of Ymir, built on the `ymirc` frontend (`libymirc`): diagnostics, hover,
definition, references, outline, completion and quick fixes over the Language Server Protocol, on
stdio or TCP. The repository also holds the IntelliJ plugin using it, in `intellij/`.

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
gyllir builds it: in the package of the closest `gyllir.toml`, a file of its source directory is
the module of the root file of the package it is (`main.yr`, `__lib__.yr`, or `package-root`), and
a file of `test/` a module of `test/__test__.yr`, with the packages of `.deps/` it depends on, the
std its `[std]` resolved there if not the one of gyc, the `-fversion=` of its `flags`, and the
`__test` blocks. Any other file is checked alone. A document
is checked once the client stopped changing it for 250ms, the unsaved buffers of the client
standing in for their files, and its warnings are reported as such, as with `gyllir build`.

## The IntelliJ plugin

```sh
cd intellij
./gradlew buildPlugin   # build/distributions/ymir-intellij-<version>.zip, to install from disk
./gradlew runIde        # an IDE with the plugin installed, in a sandbox
```

It runs in IntelliJ-based IDEs from 2026.1, and depends on
[LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij), which starts `ymir-lsp` for the
`.yr` files and maps its features onto the IDE, and on the bundled TextMate plugin, which
highlights them with the grammar of `intellij/textmate/ymir`. The command starting the server is
`ymir-lsp` from the PATH by default, and set under *Settings > Languages & Frameworks > Ymir*.
In IntelliJ IDEA, *File > New > Project… > Ymir* creates a gyllir package, an executable or a
library, as `gyllir init` lays it out.

A *Gyllir* run configuration runs `gyllir run`, `build` or `test` in a package, the locations of
the diagnostics of gyc linked to their file. A project rooted at a package gets its configurations
when first opened (*Run* and *Test*, or *Test* and *Build* for a library), and the gutter of
`fn main` and of each `__test` runs the package or its tests.

The build downloads the IDE of `platformVersion` (`gradle.properties`). To build against an
installed one instead, set `platformLocalPath` to its directory, e.g. in
`~/.gradle/gradle.properties`. Gradle needs a JDK 21 to compile the plugin, and fetches one when
none is installed.
