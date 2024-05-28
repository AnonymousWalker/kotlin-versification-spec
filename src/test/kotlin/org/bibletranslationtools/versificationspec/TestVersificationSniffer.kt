package org.bibletranslationtools.versificationspec

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.bibletranslationtools.versificationspec.entity.Versification
import org.bibletranslationtools.versificationspec.usfm.UsfmContentMapper
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class TestVersificationSniffer {

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
    fun testSniffUsfm() {
        val files = listOf(
            getResource("usfm/01-GEN.usfm"),
            getResource("usfm/19-PSA.usfm"),
            getResource("usfm/66-JUD.usfm")
        )
        val scriptureTree = UsfmContentMapper.mapToTree(files)
        val versification = VersificationSniffer(
            scriptureTree,
            """D:\Projects\kotlin-versification-spec\src\main\resources\rules\merged_rules.json"""
        ).sniff("test_versification")

        val expectedFile = getResource("versification/vers.json")
        val expectedVersification = ObjectMapper(JsonFactory())
            .registerKotlinModule()
            .readValue<Versification>(expectedFile)

        assertEquals(expectedVersification, versification)
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