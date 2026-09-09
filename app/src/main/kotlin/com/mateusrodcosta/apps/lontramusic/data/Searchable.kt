package com.mateusrodcosta.apps.lontramusic.data

import androidx.compose.runtime.Stable
import com.ibm.icu.text.RuleBasedCollator
import com.ibm.icu.text.StringSearch
import java.text.StringCharacterIterator

interface Searchable {
    val searchableStrings: List<String>
}

@Stable
fun <T : Searchable> Iterable<T>.search(query: String, collator: RuleBasedCollator): List<T> {
    return if (query.isEmpty()) {
        this.toList()
    } else {
        filter { searchable ->
            searchable.searchableStrings.any {
                if (it.isEmpty()) false
                else
                    StringSearch(query, StringCharacterIterator(it), collator).first() !=
                        StringSearch.DONE
            }
        }
    }
}

@Stable
fun <T> Iterable<T>.search(
    query: String,
    collator: RuleBasedCollator,
    selector: (T) -> Searchable,
): List<T> {
    return if (query.isEmpty()) {
        this.toList()
    } else {
        filter { item ->
            selector(item).searchableStrings.any {
                if (it.isEmpty()) false
                else
                    StringSearch(query, StringCharacterIterator(it), collator).first() !=
                        StringSearch.DONE
            }
        }
    }
}

@Stable
fun <T> Iterable<T>.searchIndices(
    query: String,
    collator: RuleBasedCollator,
    selector: (T) -> Searchable,
): Set<Int> {
    return if (query.isEmpty()) {
        emptySet()
    } else {
        mapIndexedNotNull { index, item ->
            if (
                selector(item).searchableStrings.any {
                    if (it.isEmpty()) false
                    else
                        StringSearch(query, StringCharacterIterator(it), collator).first() !=
                            StringSearch.DONE
                }
            )
                index
            else null
        }
            .toSet()
    }
}
