package com.aar.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BilingualStringsParityTest {

    private fun parseStringKeys(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()

        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val key = node.attributes.getNamedItem("name")?.nodeValue ?: continue
            val value = node.textContent.trim()
            result[key] = value
        }
        return result
    }

    private fun findResourceFile(relPath: String): File {
        val candidates = listOf(
            File(relPath),
            File("app/$relPath"),
            File("../app/$relPath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Resource file not found: $relPath")
    }

    @Test
    fun `all string resources must have identical keys across English and Traditional Chinese`() {
        val enFile = findResourceFile("src/main/res/values/strings.xml")
        val zhFile = findResourceFile("src/main/res/values-zh-rTW/strings.xml")

        val enStrings = parseStringKeys(enFile)
        val zhStrings = parseStringKeys(zhFile)

        assertFalse("Base strings.xml must not be empty", enStrings.isEmpty())
        assertFalse("Traditional Chinese strings.xml must not be empty", zhStrings.isEmpty())

        // Ensure every key in English is also in Traditional Chinese
        val missingInZh = enStrings.keys - zhStrings.keys
        assertTrue("Keys missing in values-zh-rTW/strings.xml: $missingInZh", missingInZh.isEmpty())

        // Ensure every key in Traditional Chinese is also in English
        val missingInEn = zhStrings.keys - enStrings.keys
        assertTrue("Keys missing in values/strings.xml: $missingInEn", missingInEn.isEmpty())

        // Ensure no empty values
        for ((key, value) in enStrings) {
            assertFalse("English string '$key' must not be blank", value.isBlank())
        }
        for ((key, value) in zhStrings) {
            assertFalse("Traditional Chinese string '$key' must not be blank", value.isBlank())
        }
    }
}
