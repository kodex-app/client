package dev.icedtea.kodex.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Mihon-style source filter model, mirroring `dev.kodex.spi.content.filter.Filter`. The server
 * uses Jackson `@JsonTypeInfo(property = "type")` with the subtype names below; kotlinx's default
 * class discriminator is also `type`, so these serialize/deserialize compatibly in both directions:
 * GET the source's default [FilterListDto], edit [state] values, POST the list back with the search.
 */
@Serializable
sealed interface SourceFilter {
    val name: String
}

@Serializable
@SerialName("header")
data class HeaderFilter(override val name: String) : SourceFilter

@Serializable
@SerialName("separator")
data class SeparatorFilter(override val name: String = "") : SourceFilter

@Serializable
@SerialName("text")
data class TextFilterDto(override val name: String, val state: String = "") : SourceFilter

@Serializable
@SerialName("checkbox")
data class CheckBoxFilter(override val name: String, val state: Boolean = false) : SourceFilter

/** Three-state: 0 ignore · 1 include · 2 exclude. */
@Serializable
@SerialName("tristate")
data class TriStateFilter(override val name: String, val state: Int = 0) : SourceFilter {
    companion object {
        const val IGNORE = 0
        const val INCLUDE = 1
        const val EXCLUDE = 2
    }
}

/** Single-choice; [state] indexes into [values]. */
@Serializable
@SerialName("select")
data class SelectFilter(override val name: String, val values: List<String> = emptyList(), val state: Int = 0) : SourceFilter

/** Nested filters (usually a genre list of checkboxes/tri-states). */
@Serializable
@SerialName("group")
data class GroupFilter(override val name: String, val state: List<SourceFilter> = emptyList()) : SourceFilter

/** Sort key picker with a direction. */
@Serializable
@SerialName("sort")
data class SortFilter(override val name: String, val values: List<String> = emptyList(), val state: SortSelection? = null) : SourceFilter

@Serializable
data class SortSelection(val index: Int, val ascending: Boolean)

/** The `FilterList` wrapper (`{ "filters": [...] }`). */
@Serializable
data class FilterListDto(val filters: List<SourceFilter> = emptyList())

/** Body of `POST /content-sources/{id}/search`. */
@Serializable
data class SourceSearchRequest(
    val query: String,
    val page: Int,
    val filters: FilterListDto,
)
