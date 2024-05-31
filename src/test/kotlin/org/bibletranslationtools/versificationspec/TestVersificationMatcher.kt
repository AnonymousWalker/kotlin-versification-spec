package org.bibletranslationtools.versificationspec

import org.bibletranslationtools.versificationspec.entity.StandardMapping
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class TestVersificationMatcher {
    private lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory().toFile()
    }

    @AfterEach
    fun cleanUp() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testMatchUsfm() {
        var usfm = getResource("usfm/01-GEN.usfm")
        var vers = VersificationSniffer.sniff(listOf(usfm))
        var result = VersificationMatcher.match(vers)

        assertEquals(StandardMapping.ENG.name, result.shortname)

        usfm = getResource("usfm/66-JUD.usfm")
        vers = VersificationSniffer.sniff(listOf(usfm))
        result = VersificationMatcher.match(vers)

        assertEquals(StandardMapping.ORG.name, result.shortname)
    }

    private fun getResource(path: String): File {
        val name = File(path).name
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: throw NullPointerException("Test resource not found: $path")

        stream.use { input ->
            val file = tempDir.resolve(name)

            file.outputStream().use { out ->
                input.copyTo(out)
            }

            return file
        }
    }
}