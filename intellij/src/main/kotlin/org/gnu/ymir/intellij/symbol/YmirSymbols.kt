package org.gnu.ymir.intellij.symbol

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.util.indexing.FindSymbolParameters
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.ServerStatus
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.gnu.ymir.intellij.YmirLanguageServerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

/**
 * The declarations of the workspace, asked to the running `ymir-lsp` by its
 * `workspace/symbol`, read by lsp4j as `SymbolInformation`s or as
 * `WorkspaceSymbol`s, their JSON being the same.
 */
object YmirSymbols {

    /** The declarations whose name matches `pattern`, none while the server is not running. */
    fun find(project: Project, pattern: String): List<YmirSymbol> {
        val manager = LanguageServerManager.getInstance(project)
        if (manager.getServerStatus(YmirLanguageServerFactory.SERVER_ID) != ServerStatus.started) return emptyList()

        val params = WorkspaceSymbolParams(query(pattern))
        val result = manager.getLanguageServer(YmirLanguageServerFactory.SERVER_ID).thenCompose { item ->
            item?.workspaceService?.symbol(params) ?: CompletableFuture.completedFuture(null)
        }

        val symbols = try {
            ProgressIndicatorUtils.awaitWithCheckCanceled(result)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: ExecutionException) {
            null
        } catch (_: CompletionException) {
            null
        }

        return read(project, symbols)
    }

    /** The declarations of an answer to `workspace/symbol`. */
    @Suppress("DEPRECATION")
    fun read(project: Project, symbols: Either<List<SymbolInformation>, List<WorkspaceSymbol>>?): List<YmirSymbol> = when {
        symbols == null -> emptyList()
        symbols.isLeft -> symbols.left.map { YmirSymbol.of(project, it) }
        else -> symbols.right.map { YmirSymbol.of(project, it) }
    }

    /** The query of the pattern of a search, without the line and column it may end with. */
    fun query(pattern: String): String =
        pattern.trim().trimStart('*').replace(LINE_SUFFIX, "")

    /**
     * Whether `symbol` is to be listed by a search of `parameters`: one of the
     * project in its scope, one out of it only with the non-project items.
     */
    fun isListed(symbol: YmirSymbol, parameters: FindSymbolParameters): Boolean {
        val file = symbol.file ?: return false
        return runReadActionBlocking {
            parameters.searchScope.contains(file) ||
                parameters.isSearchInLibraries && !ProjectFileIndex.getInstance(parameters.project).isInContent(file)
        }
    }

    private val LINE_SUFFIX = Regex("""(?<!:):\d+(:\d+)?$""")
}
