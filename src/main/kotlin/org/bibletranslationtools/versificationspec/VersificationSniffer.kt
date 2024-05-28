package org.bibletranslationtools.versificationspec

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.bibletranslationtools.versificationspec.entity.Address
import org.bibletranslationtools.versificationspec.entity.ScriptureTree
import org.bibletranslationtools.versificationspec.entity.ParsedTest
import org.bibletranslationtools.versificationspec.entity.Rule
import org.bibletranslationtools.versificationspec.entity.Versification
import org.bibletranslationtools.versificationspec.entity.canonBookIds
import java.io.File
import java.util.logging.Logger
import java.util.regex.Pattern
import kotlin.collections.HashMap

class VersificationSniffer(
    tree: ScriptureTree,
    rules: String = "../rules/merged_rules.json"
) {
    private val _books = HashMap<String, MutableMap<Int, MutableMap<String, String>>>()
    private lateinit var versification: Versification
    private val verseIndex = HashMap<String, Any>()
    private val sidTemplate = "\$book \$chapter:\$verse"
    private val bcvPattern = Pattern.compile("""((\w+)\.(\d+):(\d+)\.?(\d+)?\*?(\d+)?)""")
    private val factorPattern = Pattern.compile("""\*?(\d+)""")
    private val rules: List<Rule> = File(rules).readText().let { text ->
        ObjectMapper(JsonFactory())
            .registerKotlinModule()
            .readValue(text)
    }

    init {
        // Ensure all chapters are int and verses are str
        for ((b, bookMap) in tree.books) {
            val chapterMap = HashMap<Int, MutableMap<String, String>>()
            for ((c, chapterMapRaw) in bookMap.chapters) {
                val verseMap = HashMap<String, String>()
                for ((v, verse) in chapterMapRaw.verses) {
                    verseMap[v] = verse
                }
                chapterMap[c.toInt()] = verseMap
            }
            this._books[b] = chapterMap
        }
    }

    fun sniff(versificationName: String = "custom_versification"): Versification {
        versification = Versification(
            shortname = versificationName,
            maxVerses = mutableMapOf(),
            partialVerses = mutableMapOf(),
            mappedVerses = mutableMapOf(),
            excludedVerses = mutableListOf()
        )
        maxVerses()
        mappedVerses()
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
        for (rule in rules) {
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
        val left = parsedTest.left.parsed
        val right = parsedTest.right.parsed
        val op = parsedTest.op

        val keyword = right["keyword"]
        val book = left["book"]!!
        val chapter = left["chapter"]!!.toInt()
        val verse = left["verse"]!!.toInt()

        if (!bookExists(book) || !chapterExists(book, chapter)) {
            return false
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
        return verse == versification.maxVerses[book]!![chapter - 1]
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
                return false
            }
        }
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
                    Logger.getLogger(VersificationSniffer::class.java.name).info("ERROR: ${m.group(2).uppercase()} is not a valid USFM book name")
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
        for (range in rule.ranges) {
            val actualRange = range.values
            if (maxOf(fromColumn, toColumn) <= actualRange.size - 1) {
                val fr = actualRange[fromColumn].uppercase().replaceFirst(".", " ")
                val to = actualRange[toColumn].uppercase().replaceFirst(".", " ")
                if (fr != to && to != "NOVERSE") {
                    versification.mappedVerses[fr] = to
                }
            } else {
                Logger.getLogger(VersificationSniffer::class.java.name).info("### Error: missing column in mapping")
            }
        }
    }
}
