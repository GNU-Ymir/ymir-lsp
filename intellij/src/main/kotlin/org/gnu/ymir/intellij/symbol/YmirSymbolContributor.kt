package org.gnu.ymir.intellij.symbol

import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import org.eclipse.lsp4j.SymbolKind

/**
 * The Ymir declarations of a Go to search, of the kinds it lists, the names
 * matching the pattern typed being asked to the server, indexing or not.
 */
abstract class YmirSymbolContributor : ChooseByNameContributorEx, DumbAware {

    protected abstract fun lists(kind: SymbolKind): Boolean

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val project = scope.project ?: return
        val pattern = project.getUserData(ChooseByNamePopup.CURRENT_SEARCH_PATTERN) ?: ""
        YmirSymbols.find(project, pattern)
            .filter { lists(it.kind) }
            .map { it.name }
            .distinct()
            .all { processor.process(it) }
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        YmirSymbols.find(parameters.project, name)
            .filter { it.name == name && lists(it.kind) && YmirSymbols.isListed(it, parameters) }
            .all { processor.process(it) }
    }
}

/** The types of Go to Class: the structs, classes, enums, traits and the `def` of a type. */
class YmirGotoClassContributor : YmirSymbolContributor() {

    override fun lists(kind: SymbolKind): Boolean = kind in TYPES

    companion object {
        val TYPES = setOf(SymbolKind.Class, SymbolKind.Struct, SymbolKind.Enum, SymbolKind.Interface)
    }
}

/** Every top-level declaration, in Go to Symbol. */
class YmirGotoSymbolContributor : YmirSymbolContributor() {

    override fun lists(kind: SymbolKind): Boolean = true
}
