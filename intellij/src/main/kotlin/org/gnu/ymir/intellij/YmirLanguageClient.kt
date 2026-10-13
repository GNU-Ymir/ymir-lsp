package org.gnu.ymir.intellij

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.gnu.ymir.intellij.imports.YmirUnusedUses

/**
 * The client of `ymir-lsp`, recording the unused `use` declarations its
 * diagnostics report before LSP4IJ shows them.
 */
class YmirLanguageClient(project: Project) : LanguageClientImpl(project) {

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        YmirUnusedUses.getInstance(project).update(params.uri, params.diagnostics)
        super.publishDiagnostics(params)
    }
}
