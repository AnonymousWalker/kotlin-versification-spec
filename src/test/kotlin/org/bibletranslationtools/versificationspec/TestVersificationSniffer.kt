package org.bibletranslationtools.versificationspec

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.bibletranslationtools.versificationspec.entity.Rule
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
            getRules()
        ).sniff("test_versification")

        val expectedFile = getResource("versification/vers.json")
        val expectedVersification = ObjectMapper(JsonFactory())
            .registerKotlinModule()
            .readValue<Versification>(expectedFile)

        assertEquals(expectedVersification, versification)
    }

    private fun getRules(): List<Rule> {
        val rules: List<Rule> = VersificationSniffer::class.java.classLoader
            .getResourceAsStream("rules/merged_rules.json")!!
            .use {
                ObjectMapper(JsonFactory())
                    .registerKotlinModule()
                    .readValue(it)
            }

        return rules
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