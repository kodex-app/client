package dev.icedtea.kodex.ui.reader.ebook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reader page and the policy it is served with must agree: the page's one inline script carries
 * the nonce the policy allows, and the policy lets no other script run — that is what keeps a book's
 * own scripts (foliate's chapter iframes inherit the page's policy) from executing.
 */
class EbookHostPageTest {

    private val nonce = "0123456789abcdef0123456789abcdef"

    @Test
    fun theBootScriptCarriesTheNonceThePolicyAllows() {
        val html = readerHtml("""{"format":"epub"}""", nonce)
        val csp = readerContentSecurityPolicy(nonce)

        assertTrue("""<script nonce="$nonce">window.KDX_CONFIG = {"format":"epub"};</script>""" in html)
        assertTrue("'nonce-$nonce'" in csp)
        // Exactly one inline script, and the module entry point comes from the host itself.
        assertEquals(1, Regex("<script(?![^>]*\\bsrc=)").findAll(html).count())
        assertTrue("""<script type="module" src="./reader.js"></script>""" in html)
    }

    @Test
    fun thePolicyLetsNoForeignScriptRun() {
        val scriptSrc = readerContentSecurityPolicy(nonce).split(';').map { it.trim() }
            .single { it.startsWith("script-src ") }

        assertEquals("script-src 'self' 'nonce-$nonce'", scriptSrc)
        // None of the escapes that would let a book's markup run code again.
        for (escape in listOf("'unsafe-inline'", "'unsafe-eval'", "blob:", "data:", "*", "http:", "https:")) {
            assertFalse(escape in scriptSrc.split(' '), "script-src must not allow $escape")
        }
    }
}
