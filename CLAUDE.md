# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`ymir-lsp`, the language server of Ymir, written in Ymir on top of the `ymirc` frontend library
(a gyllir dependency on the `master` branch of GNU-Ymir/bootstrap, pinned by `gyllir.lock`), and
`intellij/`, the IntelliJ plugin starting it through LSP4IJ. See `README.md` for the builds.

## Issue tracking (Plane)

Project **LSP** (key `LSP`), workspace `ymir-bootstrap`. Use the plane MCP tools.

## Pull request titles

PR titles must read `[LSP-XXX][kind] Log`, `[kind]` being optional. The Plane sync
(`.github/workflows/plane-sync.yml`) attaches a PR to the work item of the leading `[LSP-XXX]`
tag of its title, and of nothing else: a PR without it is never mirrored onto Plane.

## Policy

Concise comments:
- only describe what the functions do, not what the current work is adding
- don't write comments inside code unless absolutely necessary for understanding
- a comment of more than 3 lines is generally too verbose

Commits:
- split work in logical commits
- a commit is one piece: one module, or one behavior. A feature spread over several modules is
  one commit per module, never a single commit adding them all
- order the commits like the modules depend on each other: chore, then the modules of the server
  (`sys`, `transport`, `document`, `unit`, `server`, then `main`), then the IntelliJ plugin, then
  tests, then docs
- a commit never uses what a later one declares, even though compilation within the branch is
  not required: what is shared comes before what uses it, and a dispatch comes after everything
  it dispatches to
- rewrite history when a new commit modifies something introduced by another commit of the same
  branch
- tests don't need to pass, nor the code to compile, between commits, as long as the last commit
  of the branch compiles and its tests succeed
- commit messages are one line long
- don't add co-authors
