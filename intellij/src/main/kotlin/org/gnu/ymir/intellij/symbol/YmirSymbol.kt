package org.gnu.ymir.intellij.symbol

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.ui.IconMapper
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceSymbol
import javax.swing.Icon

/**
 * A top-level declaration `ymir-lsp` found, presented with the path of the
 * module declaring it, and opening its file at it.
 */
class YmirSymbol(
    private val project: Project,
    private val name: String,
    val kind: SymbolKind,
    private val container: String?,
    private val uri: String,
    private val position: Position?,
) : NavigationItem, ItemPresentation {

    val file: VirtualFile? by lazy { LSPIJUtils.findResourceFor(uri) }

    override fun getName(): String = name

    override fun getPresentation(): ItemPresentation = this

    override fun getPresentableText(): String = name

    override fun getLocationString(): String? = container

    override fun getIcon(unused: Boolean): Icon? = IconMapper.getIcon(kind)

    override fun navigate(requestFocus: Boolean) {
        LSPIJUtils.openInEditor(uri, position, requestFocus, project)
    }

    override fun canNavigate(): Boolean = true

    override fun canNavigateToSource(): Boolean = true

    companion object {

        @Suppress("DEPRECATION")
        fun of(project: Project, info: SymbolInformation): YmirSymbol =
            YmirSymbol(project, info.name, info.kind, info.containerName, info.location.uri, info.location.range.start)

        fun of(project: Project, symbol: WorkspaceSymbol): YmirSymbol {
            val location = symbol.location
            return if (location.isLeft) {
                YmirSymbol(project, symbol.name, symbol.kind, symbol.containerName, location.left.uri, location.left.range.start)
            } else {
                YmirSymbol(project, symbol.name, symbol.kind, symbol.containerName, location.right.uri, null)
            }
        }
    }
}
