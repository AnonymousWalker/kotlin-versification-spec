package org.wycliffeassociates.versificationspec

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.wycliffeassociates.versificationspec.entity.Address
import org.wycliffeassociates.versificationspec.entity.ParsedTest
import org.wycliffeassociates.versificationspec.entity.Rule
import org.wycliffeassociates.versificationspec.entity.Versification
import org.wycliffeassociates.versificationspec.entity.canonBookIds
import java.io.File
import java.io.FileWriter
import java.util.logging.Logger
import java.util.regex.Pattern
import kotlin.collections.HashMap

class Sniffer(
    books: Map<String, Map<String, Map<String, String>>>,
    outdir: String = "../../data/output/",
    vrs: Boolean = false,
    mappings: String = "../../versification-mappings/standard-mappings",
    rules: String = "../rules/merged_rules.json"
) {
    private val _books = HashMap<String, MutableMap<Int, MutableMap<String, String>>>()
    private lateinit var versification: Versification
    private val verseIndex = HashMap<String, Any>()
    private val args = hashMapOf("outdir" to outdir, "vrs" to vrs, "mappings" to mappings, "rules" to rules)
    private val sidTemplate = "\$book \$chapter:\$verse"
    private val bcvPattern = Pattern.compile("""((\w+)\.(\d+):(\d+)\.?(\d+)?\*?(\d+)?)""")
    private val factorPattern = Pattern.compile("""\*?(\d+)""")

    init {
        // Ensure all chapters are int and verses are str
        for ((b, bookMap) in books) {
            val chapterMap = HashMap<Int, MutableMap<String, String>>()
            for ((c, chapterMapRaw) in bookMap) {
                val verseMap = HashMap<String, String>()
                for ((v, verse) in chapterMapRaw) {
                    verseMap[v.toString()] = verse
                }
                chapterMap[c.toInt()] = verseMap
            }
            this._books[b] = chapterMap
        }

        // Ensure output path is present
        val outputDir = File(outdir)
        if (!outputDir.isDirectory) {
            outputDir.mkdir()
        }
    }

    fun sniff(versificationName: String = "custom_versification"): Versification {
        versification = Versification(
            shortname = versificationName,
            maxVerses = mutableMapOf(),
            partialVerses = mutableMapOf(),
            verseMappings = mutableMapOf(),
            excludedVerses = mutableListOf()
        )
        maxVerses()
        mappedVerses()
//        versification.partialVerses.forEach { key, value ->
//            (value as MutableList<*>).sort()
//        }
//        val outfile = "${args["outdir"]}/$versificationName.json"
//        FileWriter(outfile).use { it.write(versification.toString()) }
        return versification
    }

    private fun maxVerses() {
        for (book in canonBookIds) {
            if (_books.containsKey(book)) {
                val maxVerses = HashMap<Int, Int>()
                for ((chapter, verses) in _books[book]!!) {
                    for (verse in verses.keys) {
                        partial(book, chapter, verse)
                        val verseNum = Pattern.compile("\\d+").matcher(verse).let { matcher ->
                            var maxNum = 0
                            while (matcher.find()) {
                                val num = matcher.group().toInt()
                                if (num > maxNum) maxNum = num
                            }
                            maxNum
                        }
                        maxVerses[chapter] = maxVerses.getOrDefault(chapter, 0).coerceAtLeast(verseNum)
                    }
                }
                versification.maxVerses[book] = maxVerses.toSortedMap().values.toMutableList()
            }
        }
    }

    private fun partial(book: String, chapter: Int, verse: String) {
        val verses = verse.split(Regex("""[-,]"""))
        for (pv in verses) {
            val segment = Regex("""\D+""").find(pv)
            if (segment != null) {
                val numeric = Regex("""\d+""").find(pv)!!.value
                val id = sidTemplate.replace("\$book", book).replace("\$chapter", chapter.toString()).replace("\$verse", verse)
                if (!versification.partialVerses.containsKey(id)) {
                    (versification.partialVerses as HashMap<String, MutableList<String>>)[id] = mutableListOf()
                    if (findVerse(book, chapter, numeric) != null) {
                        versification.partialVerses[id]?.add("-")
                    }
                }
                versification.partialVerses[id]?.add(segment.value)
            }
        }
    }

    private fun mappedVerses() {
        val rules: List<Rule> = File(args["rules"].toString()).readText().let { text ->
            val mapper = ObjectMapper(JsonFactory()).registerKotlinModule()
            mapper.readValue(text)
        }
        for (rule in rules) {
            Logger.getLogger(Sniffer::class.java.name).info("-------------------------")
            Logger.getLogger(Sniffer::class.java.name).info("Rule: " + rule.name)
            val fromColumn = mapFrom(rule)
            if (fromColumn != null) {
                val toColumn = mapTo(rule)
                if (fromColumn != toColumn) {
                    createMappings(rule, fromColumn, toColumn!!)
                }
            }
        }
    }

    private fun doTest(parsedTest: ParsedTest): Boolean {
        Logger.getLogger(Sniffer::class.java.name).info("doTest()")
        val left = parsedTest.left.parsed
        val right = parsedTest.right.parsed
        val op = parsedTest.op

        val keyword = right["keyword"]
        Logger.getLogger(Sniffer::class.java.name).info(left.toString())
        Logger.getLogger(Sniffer::class.java.name).info(op)
        Logger.getLogger(Sniffer::class.java.name).info(right.toString())

        val book = left["book"]!!
        val chapter = left["chapter"]!!.toInt()
        val verse = left["verse"]!!.toInt()

        if (!bookExists(book)) {
            Logger.getLogger(Sniffer::class.java.name).info(left["book"] + " not found in books")
            return false
        } else if (!chapterExists(book, chapter)) {
            Logger.getLogger(Sniffer::class.java.name).info("Chapter " + left["chapter"] + " not found in " + left["book"])
            return false
        } else {
            Logger.getLogger(Sniffer::class.java.name).info(left["book"] + " found in books")
        }

        when {
            op == "=" && keyword == "Last" -> if (!isLastInChapter(book, chapter, verse)) {
                return false
            }
            op == "=" && keyword == "Exist" -> if (findVerse(book, chapter, verse.toString()) == null) {
                return false
            }
            op == "=" && keyword == "NotExist" -> if (findVerse(book, chapter, verse.toString()) != null) {
                return false
            }
            op == "<" && right.containsKey("chapter") -> if (hasMoreWords(left, right)) {
                return false
            }
            op == ">" && right.containsKey("chapter") -> if (hasFewerWords(left, right)) {
                return false
            }
            else -> {
                Logger.getLogger(Sniffer::class.java.name).info("Error in test!  (not implemented?)")
                return false
            }
        }
        return true
    }

    private fun createSid(bb: String, cc: Int, vv: Int): String {
        return sidTemplate.replace("\$book", bb).replace("\$chapter", cc.toString()).replace("\$verse", vv.toString())
    }

    private fun findVerse(bb: String, cc: Int, vv: String): Any? {
        return if (_books.containsKey(bb) && _books[bb]!!.containsKey(cc) && _books[bb]!![cc]!!.containsKey(vv)) {
            _books[bb]!![cc]!![vv]
        } else {
            null
        }
    }

    private fun isLastInChapter(book: String, chapter: Int, verse: Int): Boolean {
        Logger.getLogger(Sniffer::class.java.name).info("isLastInChapter()")
        Logger.getLogger(Sniffer::class.java.name).info(createSid(book, chapter, verse) + " => " + versification.maxVerses[book]!![chapter - 1])
        return if (verse == versification.maxVerses[book]!![chapter - 1]) {
            Logger.getLogger(Sniffer::class.java.name).info("Last in chapter")
            true
        } else {
            Logger.getLogger(Sniffer::class.java.name).info("Not last in chapter")
            false
        }
    }

    private fun hasMoreWords(ref: Map<String, String>, comparison: Map<String, String>): Boolean {
        val refBook = ref["book"]!!
        val refChapter = ref["chapter"]!!.toInt()
        val refVerse = ref["verse"]!!

        val refString = findVerse(
            refBook,
            refChapter,
            refVerse
        ).toString()

        val comparisonString = findVerse(
            comparison["book"]!!,
            comparison["chapter"]!!.toInt(),
            comparison["verse"]!!
        ).toString()

        val refFactor = ref.getOrDefault("factor", "1").toInt()
        val comparisonFactor = comparison.getOrDefault("factor", "1").toInt()
        return refString.length * refFactor > comparisonString.length * comparisonFactor
    }

    private fun hasFewerWords(ref: Map<String, String>, comparison: Map<String, String>): Boolean {
        return hasMoreWords(comparison, ref)
    }

    private fun bookExists(book: String): Boolean {
        return versification.maxVerses.containsKey(book)
    }

    private fun chapterExists(book: String, chapter: Int): Boolean {
        return bookExists(book) && chapter <= (versification.maxVerses[book] as List<*>).size
    }

    private fun mapFrom(rule: Rule): Int? {
        val tests = rule.tests
        for (columnIndex in tests.indices) {
            if (allTestsPass(tests[columnIndex])) {
                return columnIndex
            }
        }
        return null
    }

    private fun allTestsPass(tests: List<String>): Boolean {
        for (test in tests) {
            val pt = parseTest(test)
            if (pt == null || !doTest(pt)) {
                Logger.getLogger(Sniffer::class.java.name).info("doTest() returns false")
                return false
            }
        }
        Logger.getLogger(Sniffer::class.java.name).info("doTest() returns true")
        return true
    }

    private fun parseTest(test: String): ParsedTest? {
        val regex = Regex("([<=>])")
        val groups = regex.split(test)

        return if (groups.size == 2 && test.contains(regex)) {
            val left = Address(
                text = groups[0],
                parsed = parseRef(groups[0])
            )
            val right = Address(
                text = groups[1],
                parsed = parseRef(groups[1])
            )
            ParsedTest(
                left = left,
                right = right,
                op = regex.find(test)!!.value
            )
        } else {
            Logger.getLogger(Sniffer::class.java.name).info("ERROR: Does not parse: $test")
            null
        }
    }

    private fun parseRef(ref: String): Map<String, String> {
        val m = bcvPattern.matcher(ref)
        return if (m.find()) {
            mutableMapOf(
                "book" to m.group(2).uppercase(),
                "chapter" to m.group(3),
                "verse" to m.group(4)
            ).also { map ->
                if (m.group(5) != null) map["words"] = m.group(5)
                if (m.group(6) != null) map["factor"] = m.group(6)
                if (!canonBookIds.contains(m.group(2).uppercase())) {
                    Logger.getLogger(Sniffer::class.java.name).info("ERROR: ${m.group(2).uppercase()} is not a valid USFM book name")
                }
            }
        } else {
            mapOf("keyword" to ref.trim())
        }
    }

    private fun mapTo(rule: Rule): Int? {
        var to: Int? = null
        val name = rule.name
        val columns = rule.columns
        for (c in columns) {
            if (c.contains("Hebrew")) {
                return columns.indexOf(c)
            } else if (c.contains("Greek")) {
                to = columns.indexOf(c)
            }
        }
        return to
    }

    private fun createMappings(rule: Rule, fromColumn: Int, toColumn: Int) {
        Logger.getLogger(Sniffer::class.java.name).info("createMappings(), rule=${rule.name}")
        Logger.getLogger(Sniffer::class.java.name).info("Map from column $fromColumn to column $toColumn")
        for (range in rule.ranges) {
            val actualRange = mapFromRange(range)
            if (maxOf(fromColumn, toColumn) <= actualRange.size - 1) {
                val frum = actualRange[fromColumn].uppercase().replace(".", " ", true)
                val to = actualRange[toColumn].uppercase().replace(".", " ", true)
                Logger.getLogger(Sniffer::class.java.name).info("$frum : $to")
                if (frum != to && to != "NOVERSE") {
                    versification.verseMappings[frum] = to
                }
            } else {
                Logger.getLogger(Sniffer::class.java.name).info("### Error: missing column in mapping")
            }
        }
    }
}
