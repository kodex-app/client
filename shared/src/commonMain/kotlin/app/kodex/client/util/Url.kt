package app.kodex.client.util

/**
 * Normalizes user-typed server addresses into a base URL with no trailing slash. Defaults a
 * missing scheme to https (Kodex is typically fronted by TLS); users can type `http://` explicitly
 * for a LAN server.
 */
fun normalizeBaseUrl(raw: String): String {
    var s = raw.trim().removeSuffix("/")
    if (s.isEmpty()) return s
    if (!s.startsWith("http://") && !s.startsWith("https://")) {
        s = "https://$s"
    }
    return s
}
