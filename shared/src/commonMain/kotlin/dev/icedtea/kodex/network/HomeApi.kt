package dev.icedtea.kodex.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter

// Home screen and global search. Extensions on [KodexApi]; see that file for the auth model.

// ── Home ──────────────────────────────────────────────────────────────────────────────────────

/**
 * The whole Home screen in one request. The server applies the user's hidden-library and
 * hidden-source preferences itself, so nothing here has to fetch those preferences or drop rows
 * afterwards — filtering a fetched page client-side returned short rails whose counts disagreed
 * with the server's.
 */
suspend fun KodexApi.home(baseUrl: String, apiKey: String): HomeDto =
    client.get("$baseUrl/api/v1/home") {
        header(HEADER_API_KEY, apiKey)
        parameter("size", HOME_ROW_SIZE)
    }.body()

/** The full continue-reading list — Home's "See all" for that rail, scoped exactly like the rail. */
suspend fun KodexApi.homeKeepReading(baseUrl: String, apiKey: String, limit: Int = 50): List<KeepReadingDto> =
    client.get("$baseUrl/api/v1/home/keep-reading") {
        header(HEADER_API_KEY, apiKey)
        parameter("limit", limit)
    }.body()

/** One of Home's series rails expanded: [row] is `RECENT` (newest) or `UPDATED` (newest content). */
suspend fun KodexApi.homeSeries(baseUrl: String, apiKey: String, row: String, size: Int = 300): List<SeriesDto> =
    client.get("$baseUrl/api/v1/home/series") {
        header(HEADER_API_KEY, apiKey)
        parameter("row", row)
        parameter("size", size)
    }.body<PageResponse<SeriesDto>>().content

/** Home's recently-added books rail expanded. */
suspend fun KodexApi.homeBooks(baseUrl: String, apiKey: String, size: Int = 300): List<BookDto> =
    client.get("$baseUrl/api/v1/home/books") {
        header(HEADER_API_KEY, apiKey)
        parameter("size", size)
    }.body<PageResponse<BookDto>>().content

// ── Global search ─────────────────────────────────────────────────────────────────────────────

/** Full-text series search (library mode of the web's global search). */
suspend fun KodexApi.searchSeries(baseUrl: String, apiKey: String, query: String): List<SeriesDto> =
    client.get("$baseUrl/api/v1/series") {
        header(HEADER_API_KEY, apiKey)
        parameter("search", query)
        parameter("size", SEARCH_SIZE)
        parameter("sort", "name,asc")
    }.body<PageResponse<SeriesDto>>().content

/** Full-text book search. */
suspend fun KodexApi.searchBooks(baseUrl: String, apiKey: String, query: String): List<BookDto> =
    client.get("$baseUrl/api/v1/books") {
        header(HEADER_API_KEY, apiKey)
        parameter("search", query)
        parameter("size", SEARCH_SIZE)
    }.body<PageResponse<BookDto>>().content
