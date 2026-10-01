# ymir-lsp

The language server of Ymir, built on the `ymirc` frontend (`libymirc`): diagnostics, hover,
definition, references, outline and completion over the Language Server Protocol, on stdio. The
repository also holds the IntelliJ plugin using it, in `intellij/`.

## The server

```sh
gyllir build      # ./ymir-lsp
gyllir test
```

`ymirc` is the `master` branch of [bootstrap](https://github.com/GNU-Ymir/bootstrap), pinned to a
commit by `gyllir.lock` (`gyllir update ymirc` moves it). It is built by the same `gyc`, against
the same std, as this package.

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

The build downloads the IDE of `platformVersion` (`gradle.properties`). To build against an
installed one instead, set `platformLocalPath` to its directory, e.g. in
`~/.gradle/gradle.properties`. Gradle needs a JDK 21 to compile the plugin, and fetches one when
none is installed.
