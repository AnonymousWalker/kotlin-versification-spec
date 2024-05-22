package org.wycliffeassociates.versificationspec

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.util.JSONPObject
import org.wycliffeassociates.org.wycliffeassociates.versificationspec.Versification
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.logging.Logger
import java.util.regex.Pattern
import kotlin.collections.HashMap

object canons {
    val bookIds = listOf<String>()
}

class Sniffer(
    books: Map<String, Map<String, Map<String, Any>>>,
    outdir: String = "../../data/output/",
    vrs: Boolean = false,
    mappings: String = "../../versification-mappings/standard-mappings",
    rules: String = "../rules/merged_rules.json"
) {
    private val books = HashMap<String, MutableMap<Int, MutableMap<String, Any>>>()
    private lateinit var versification: Versification
    private val verseIndex = HashMap<String, Any>()
    private val args = hashMapOf("outdir" to outdir, "vrs" to vrs, "mappings" to mappings, "rules" to rules)
    private val sidTemplate = "\$book \$chapter:\$verse"
    private val bcvPattern = Pattern.compile("""((\w+)\.(\d+):(\d+)\.?(\d+)?\*?(\d+)?)""")
    private val factorPattern = Pattern.compile("""\*?(\d+)""")

    init {
        // Ensure all chapters are int and verses are str
        for ((b, bookMap) in books) {
            val chapterMap = HashMap<Int, MutableMap<String, Any>>()
            for ((c, chapterMapRaw) in bookMap) {
                val verseMap = HashMap<String, Any>()
                for ((v, verse) in chapterMapRaw) {
                    verseMap[v.toString()] = verse
                }
                chapterMap[c.toInt()] = verseMap
            }
            this.books[b] = chapterMap
        }

        // Ensure output path is present
        val outputDir = File(outdir)
        if (!outputDir.isDirectory) {
            outputDir.mkdir()
        }
    }

    fun sniff(name: String? = null) {
        val versificationName = name ?: "custom_versification"
        versification.shortname = versificationName
        maxVerses()
        mappedVerses()
        versification.partialVerses.forEach { key, value ->
            (value as MutableList<*>).sort()
        }
        val outfile = "${args["outdir"]}/$versificationName.json"
        FileWriter(outfile).use { it.write(versification.toString()) }
    }

    private fun maxVerses() {
        versification.maxVerses = HashMap<String, MutableList<Int>>()
        versification.partialVerses = HashMap<String, MutableList<String>>()
        versification.verseMappings = HashMap<String, String>()
        versification.excludedVerses = HashMap<String, String>()
        versification.unexcludedVerses = HashMap<String, String>()

        for (book in canons.bookIds) {
            if (books.containsKey(book)) {
                val maxVerses = HashMap<Int, Int>()
                for ((chapter, verses) in books[book]!!) {
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
                        versification.partialVerses[id]!!.add("-")
                    }
                }
                versification.partialVerses[id]!!.add(segment.value)
            }
        }
    }

    private fun mappedVerses() {
        val rules = File(args["rules"]!!).readText().let { text ->
            val jsonParser = JsonParser()
            jsonParser.parse(text).asJsonArray
        }
        for (rule in rules) {
            val ruleObject = rule.asJsonObject
            Logger.getLogger(Sniffer::class.java.name).info("-------------------------")
            Logger.getLogger(Sniffer::class.java.name).info("Rule: " + ruleObject["name"].asString)
            val fromColumn = mapFrom(ruleObject)
            if (fromColumn != null) {
                val toColumn = mapTo(ruleObject)
                if (fromColumn != toColumn) {
                    createMappings(ruleObject, fromColumn, toColumn)
                }
            }
        }
    }

    private fun doTest(parsedTest: JsonObject): Boolean {
        Logger.getLogger(Sniffer::class.java.name).info("doTest()")
        val left = parsedTest["left"].asJsonObject["parsed"].asJsonObject
        val right = parsedTest["right"].asJsonObject["parsed"].asJsonObject
        val op = parsedTest["op"].asString
        val keyword = if (right.has("keyword")) right["keyword"].asString else null

        Logger.getLogger(Sniffer::class.java.name).info(left.toString())
        Logger.getLogger(Sniffer::class.java.name).info(op)
        Logger.getLogger(Sniffer::class.java.name).info(right.toString())

        if (!bookExists(left["book"].asString)) {
            Logger.getLogger(Sniffer::class.java.name).info(left["book"].asString + " not found in books")
            return false
        } else if (!chapterExists(left["book"].asString, left["chapter"].asInt)) {
            Logger.getLogger(Sniffer::class.java.name).info("Chapter " + left["chapter"].asInt + " not found in " + left["book"].asString)
            return false
        } else {
            Logger.getLogger(Sniffer::class.java.name).info(left["book"].asString + " found in books")
        }

        when {
            op == "=" && keyword == "Last" -> if (!isLastInChapter(left["book"].asString, left["chapter"].asInt, left["verse"].asInt)) {
                return false
            }
            op == "=" && keyword == "Exist" -> if (findVerse(left["book"].asString, left["chapter"].asInt, left["verse"].asInt) == null) {
                return false
            }
            op == "=" && keyword == "NotExist" -> if (findVerse(left["book"].asString, left["chapter"].asInt, left["verse"].asInt) != null) {
                return false
            }
            op == "<" && right.has("chapter") -> if (hasMoreWords(left, right)) {
                return false
            }
            op == ">" && right.has("chapter") -> if (hasFewerWords(left, right)) {
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
        return if (books.containsKey(bb) && books[bb]!!.containsKey(cc) && books[bb]!![cc]!!.containsKey(vv)) {
            books[bb]!![cc]!![vv]
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

    private fun hasMoreWords(ref: JsonObject, comparison: JsonObject): Boolean {
        val refString = findVerse(ref["book"].asString, ref["chapter"].asInt, ref["verse"].asString).toString()
        val comparisonString = findVerse(comparison["book"].asString, comparison["chapter"].asInt, comparison["verse"].asString).toString()
        Logger.getLogger(Sniffer::class.java.name).info("hasMoreWords()")
        Logger.getLogger(Sniffer::class.java.name).info(ref.toString())
        Logger.getLogger(Sniffer::class.java.name).info(comparison.toString())
        Logger.getLogger(Sniffer::class.java.name).info(refString)
        Logger.getLogger(Sniffer::class.java.name).info(comparisonString)
        Logger.getLogger(Sniffer::class.java.name).info(refString.length > comparisonString.length)
        val refFactor = if (ref.has("factor")) ref["factor"].asInt else 1
        val comparisonFactor = if (comparison.has("factor")) comparison["factor"].asInt else 1
        return refString.length * refFactor > comparisonString.length * comparisonFactor
    }

    private fun hasFewerWords(ref: JsonObject, comparison: JsonObject) {
        hasMoreWords(comparison, ref)
    }

    private fun bookExists(book: String): Boolean {
        return versification.maxVerses.containsKey(book)
    }

    private fun chapterExists(book: String, chapter: Int): Boolean {
        return bookExists(book) && chapter <= (versification.maxVerses[book] as List<*>).size
    }

    private fun mapFrom(rule: JsonObject): Int? {
        val tests = rule["tests"].asJsonArray
        for (column in 0 until tests.size()) {
            if (allTestsPass(tests[column].asJsonArray)) {
                return column
            }
        }
        return null
    }

    private fun allTestsPass(tests: JsonArray): Boolean {
        for (test in tests) {
            val pt = parseTest(test.asString)
            if (!doTest(pt)) {
                Logger.getLogger(Sniffer::class.java.name).info("doTest() returns false")
                return false
            }
        }
        Logger.getLogger(Sniffer::class.java.name).info("doTest() returns true")
        return true
    }

    private fun parseTest(test: String): JsonObject {
        val d = JsonObject()
        val t = Pattern.compile("""([<=>])""")
        val triple = t.split(test)
        if (triple.size != 3) {
            Logger.getLogger(Sniffer::class.java.name).info("ERROR: Does not parse: $test")
        } else {
            d.add("left", JsonObject().apply {
                addProperty("text", triple[0])
                add("parsed", parseRef(triple[0]))
            })
            d.addProperty("op", triple[1])
            d.add("right", JsonObject().apply {
                addProperty("text", triple[2])
                add("parsed", parseRef(triple[2]))
            })
        }
        return d
    }

    private fun parseRef(ref: String): JsonObject {
        val m = bcvPattern.matcher(ref)
        return if (m.find()) {
            JsonObject().apply {
                addProperty("book", m.group(2).uppercase())
                addProperty("chapter", m.group(3).toInt())
                addProperty("verse", m.group(4).toInt())
                if (m.group(5) != null) addProperty("words", m.group(5).toInt())
                if (m.group(6) != null) addProperty("factor", m.group(6).toInt())
                if (!canons.bookIds.contains(m.group(2).uppercase())) {
                    Logger.getLogger(Sniffer::class.java.name).info("ERROR: ${m.group(2).uppercase()} is not a valid USFM book name")
                }
            }
        } else {
            JsonObject().apply { addProperty("keyword", ref.trim()) }
        }
    }

    private fun mapTo(rule: JsonObject): Int? {
        var to: Int? = null
        val name = rule["name"].asString
        val columns = rule["columns"].asJsonArray
        for (c in 0 until columns.size()) {
            if (columns[c].asString.contains("Hebrew")) {
                return c
            } else if (columns[c].asString.contains("Greek")) {
                to = c
            }
        }
        return to
    }

    private fun createMappings(rule: JsonNode, fromColumn: Int, toColumn: Int) {
        Logger.getLogger(Sniffer::class.java.name).info("createMappings(), rule=${rule["name"]}")
        Logger.getLogger(Sniffer::class.java.name).info("Map from column $fromColumn to column $toColumn")
        for (r in rule["ranges"].asJsonArray) {
            for (k in r.asJsonObject.keySet()) {
                val ranges = r.asJsonObject[k].asJsonArray
                if (maxOf(fromColumn, toColumn) <= ranges.size() - 1) {
                    val frum = ranges[fromColumn].asString.uppercase().replace(".", " ", true)
                    val to = ranges[toColumn].asString.uppercase().replace(".", " ", true)
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
}
