package com.jupiter.filemanager.feature.search

import com.jupiter.filemanager.domain.model.FileItem
import java.util.Locale

/**
 * Honest presentation sections for the search reference layout.
 *
 * Both sections contain only repository/index results. "Recent" means a direct
 * filename match ordered by the item's real modification timestamp, while
 * "Suggested" means a weaker match found in the filename, parent path,
 * extension or MIME metadata. No generated snippets or synthetic files are
 * introduced here.
 */
internal data class SearchResultSections(
    val recentResults: List<FileItem>,
    val suggestedMatches: List<FileItem>,
)

internal fun sectionSearchResults(
    query: String,
    results: List<FileItem>,
): SearchResultSections {
    if (results.isEmpty()) return SearchResultSections(emptyList(), emptyList())

    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isEmpty()) {
        return SearchResultSections(
            recentResults = results.distinctBy(FileItem::path).sortedByDescending(FileItem::lastModified),
            suggestedMatches = emptyList(),
        )
    }

    val unique = results.distinctBy(FileItem::path)
    val direct = unique
        .filter { normalizeSearchText(it.name).contains(normalizedQuery) }
        .sortedWith(compareByDescending<FileItem> { it.lastModified }.thenBy { it.name.lowercase(Locale.ROOT) })

    // Natural-language and type-only searches can legitimately return items
    // without the literal raw query in their filename. Keep a small, real
    // recent section so those results are never hidden from the user.
    val recent = if (direct.isNotEmpty()) direct else unique
        .sortedByDescending(FileItem::lastModified)
        .take(4)

    // The reference shows a four-row recent preview. Additional direct matches
    // are useful, real suggestions until the user expands that preview.
    val recentPaths = recent.asSequence().take(4).map(FileItem::path).toHashSet()
    val suggested = unique
        .asSequence()
        .filterNot { it.path in recentPaths }
        .map { it to searchMetadataScore(it, normalizedQuery) }
        .sortedWith(
            compareByDescending<Pair<FileItem, Int>> { it.second }
                .thenByDescending { it.first.lastModified }
                .thenBy { it.first.name.lowercase(Locale.ROOT) },
        )
        .map { it.first }
        .toList()

    return SearchResultSections(recentResults = recent, suggestedMatches = suggested)
}

private fun normalizeSearchText(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")

/** Score only verifiable [FileItem] metadata; higher values are stronger matches. */
internal fun searchMetadataScore(item: FileItem, normalizedQuery: String): Int {
    if (normalizedQuery.isBlank()) return 0

    val name = item.name.lowercase(Locale.ROOT)
    val parent = item.parentPath.orEmpty().lowercase(Locale.ROOT)
    val extension = item.extension.lowercase(Locale.ROOT)
    val mime = item.mimeType.orEmpty().lowercase(Locale.ROOT)
    val queryTokens = normalizedQuery.split(Regex("\\s+")).filter(String::isNotBlank)

    var score = 0
    if (name == normalizedQuery) score += 300
    if (name.contains(normalizedQuery)) score += 180
    if (parent.contains(normalizedQuery)) score += 100
    if (extension == normalizedQuery.removePrefix(".")) score += 80
    if (mime.contains(normalizedQuery)) score += 60

    queryTokens.forEach { token ->
        if (name.contains(token)) score += 35
        if (parent.contains(token)) score += 20
        if (name.isOrderedSubsequenceOf(token)) score += 8
    }
    return score
}

private fun String.isOrderedSubsequenceOf(query: String): Boolean {
    if (query.isEmpty()) return false
    var queryIndex = 0
    for (character in this) {
        if (character == query[queryIndex]) {
            queryIndex += 1
            if (queryIndex == query.length) return true
        }
    }
    return false
}
