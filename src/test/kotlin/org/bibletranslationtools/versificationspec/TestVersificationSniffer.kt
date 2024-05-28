package org.bibletranslationtools.versificationspec

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.bibletranslationtools.versificationspec.VersificationSniffer
import org.bibletranslationtools.versificationspec.entity.Versification
import org.bibletranslationtools.versificationspec.usfm.ContentRow
import org.bibletranslationtools.versificationspec.usfm.UsfmVersificationMapper
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class TestVersificationSniffer {

    private val verseList = mutableListOf<ContentRow>()
    private val mapper = UsfmVersificationMapper()
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
    fun testSniff() {
        getResource("usfm/01-GEN.usfm").let { processFile(it) }
        getResource("usfm/19-PSA.usfm").let { processFile(it) }
        getResource("usfm/66-JUD.usfm").let { processFile(it) }

        val tree = mapper.verseListToTree(verseList)
        val versification = VersificationSniffer(
            tree
        ).sniff("test_versification")

        val expectedFile = getResource("versification/vers.json")
        val expectedVersification = ObjectMapper(JsonFactory())
            .registerKotlinModule()
            .readValue<Versification>(expectedFile)

        assertEquals(expectedVersification, versification)
    }

    private fun processFile(input: File) {
        val vers = mapper.parse(input)

        val bookCode = vers.book.bookCode
        vers.chapters.forEach { ch ->
            val chapterNumber = ch.chapterNumber
            ch.contents.forEach { v ->
                val verseNumber = v.verseNumber
                val verseText = v.verseText
                verseList.add(
                    ContentRow(bookCode, chapterNumber, verseNumber, verseText)
                )
            }
        }
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