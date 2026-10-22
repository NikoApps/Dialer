package io.github.nikoapps.dialer.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every translated strings file has the same strings as the English one, with the same
 * placeholders, so no screen falls back to English halfway and no name or number goes missing.
 */
class TranslationsTest {

    private val res = File("src/main/res")
    private val english = read(File(res, "values/strings.xml"))
    private val translations = res.listFiles { f -> f.name.startsWith("values-") }!!
        .map { File(it, "strings.xml") }
        .filter { it.exists() }
        .sortedBy { it.path }

    @Test
    fun `there are translations`() {
        assertTrue(translations.size >= 9)
    }

    @Test
    fun `each language has the same strings as English`() {
        for (file in translations) {
            assertEquals(file.parentFile.name, english.keys, read(file).keys)
        }
    }

    @Test
    fun `each string keeps the same placeholders`() {
        for (file in translations) {
            val translated = read(file)
            for ((key, texts) in translated) {
                val expected = placeholders(english[key]?.first() ?: continue)
                for (text in texts) {
                    assertEquals("${file.parentFile.name} $key", expected, placeholders(text))
                }
            }
        }
    }

    @Test
    fun `no exclamation marks`() {
        for (file in translations + File(res, "values/strings.xml")) {
            for ((key, texts) in read(file)) {
                assertTrue("${file.parentFile.name} $key", texts.none { '!' in it || '¡' in it })
            }
        }
    }

    /** "%1$s", "%2$s" and "%d", sorted, so a placeholder may move within the sentence. */
    private fun placeholders(text: String): List<String> =
        Regex("""%(\d+\$)?[sd]""").findAll(text).map { it.value }.sorted().toList()

    /**
     * Strings and plurals by name. A string has one text; a plural has one per quantity, and each
     * is compared with English's "other". Strings marked translatable="false" are left out.
     */
    private fun read(file: File): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = sortedMapOf<String, List<String>>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            if (e.getAttribute("translatable") == "false") continue
            out[e.getAttribute("name")] = listOf(e.textContent)
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val e = plurals.item(i) as Element
            val items = e.getElementsByTagName("item")
            val texts = (0 until items.length).map { items.item(it) as Element }
            // English's first text is its "other", which the translations are checked against.
            out[e.getAttribute("name")] = texts.sortedByDescending { it.getAttribute("quantity") == "other" }.map { it.textContent }
        }
        return out
    }
}
