package dev.icedtea.kodex.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// Ebook manifests, fonts and per-user settings (reader prefs). Extensions on [KodexApi]; see that file for the auth model.

// ── Ebook reading (EPUB streamed by entry; MOBI/KF8/FB2 parsed from the whole file) ───────────

/**
 * EPUB manifest: the spine (reading order) plus every zip entry with its size. foliate resolves
 * hrefs against the OPF and asks for entries by exact name, which `/resource` serves — so the
 * whole file never has to be downloaded.
 */
suspend fun KodexApi.bookManifest(baseUrl: String, apiKey: String, bookId: String): EbookManifestDto =
    client.get("$baseUrl/api/v1/books/$bookId/manifest") { header(HEADER_API_KEY, apiKey) }.body()

/** The same manifest for a BOOK-kind source chapter, which the core exposes as an ephemeral EPUB. */
suspend fun KodexApi.sourceChapterManifest(baseUrl: String, apiKey: String, providerId: String, chapterId: String): EbookManifestDto =
    client.get("$baseUrl/api/v1/content-sources/$providerId/chapter-manifest") {
        header(HEADER_API_KEY, apiKey)
        parameter("chapterId", chapterId)
    }.body()

/** Fonts the user uploaded, offered alongside the shipped ones in the ebook font picker. */
suspend fun KodexApi.customFonts(baseUrl: String, apiKey: String): List<CustomFontDto> =
    client.get("$baseUrl/api/v1/fonts") { header(HEADER_API_KEY, apiKey) }.body()

/**
 * The OFL reader fonts the server ships. Asked for rather than hard-coded so the app offers the
 * same list the web reader does — including their @font-face descriptors, which is what lets the
 * real font file be fetched instead of guessing at a family name the device probably lacks.
 */
suspend fun KodexApi.bundledFonts(baseUrl: String, apiKey: String): List<BundledFontDto> =
    client.get("$baseUrl/api/v1/fonts/bundled") { header(HEADER_API_KEY, apiKey) }.body()

// ── Per-user settings (reader prefs live here) ────────────────────────────────────────────────

/** All generic per-user settings as a JSON object (e.g. `reader.comic`, `reader.comic.series.<id>`). */
suspend fun KodexApi.userSettings(baseUrl: String, apiKey: String): JsonObject {
    val text = client.get("$baseUrl/api/v1/users/me/settings") {
        header(HEADER_API_KEY, apiKey)
    }.bodyAsText()
    return if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text).jsonObject
}

/** Upsert one opaque JSON setting (pass a JSON `null` value to clear it). */
suspend fun KodexApi.saveUserSetting(baseUrl: String, apiKey: String, key: String, value: JsonElement) {
    client.put("$baseUrl/api/v1/users/me/settings/${key.encodeURLQueryComponent()}") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(value)
    }
}
