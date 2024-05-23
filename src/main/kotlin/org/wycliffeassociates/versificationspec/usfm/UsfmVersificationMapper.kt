package org.wycliffeassociates.versificationspec.usfm

import org.wycliffeassociates.usfmtools.USFMParser
import org.wycliffeassociates.usfmtools.models.markers.CMarker
import org.wycliffeassociates.usfmtools.models.markers.IDMarker
import org.wycliffeassociates.usfmtools.models.markers.TextBlock
import org.wycliffeassociates.usfmtools.models.markers.VMarker
import java.io.File

class UsfmVersificationMapper {
    private val verseList = mutableListOf<ContentRow>()

    fun parse(input: File): UsfmVersification {
        val parser = USFMParser()
        val parsedDoc = parser.parseFromString(input.readText())

        val (bookCode, description) = parsedDoc
            .contents
            .filterIsInstance<IDMarker>()
            .singleOrNull()
            ?.let {
                it.textIdentifier.split(" ", limit = 2).let { list ->
                    when (list.size) {
                        2 -> list
                        1 -> listOf(list.first(), "Unknown Book")
                        else -> listOf("unknown", "Unknown Book")
                    }
                }
            } ?: listOf("unknown", "Unknown Book")

        val spec = UsfmVersification(
            Book(bookCode, description),
            arrayListOf()
        )

        parsedDoc.contents.filterIsInstance<CMarker>().forEach { marker ->
            val chapter = Chapter(marker.number.toString(), arrayListOf())
            spec.addChapter(chapter)

            marker.getChildMarkers(VMarker::class.java).forEach { verseMarker ->
                val text = verseMarker.getChildMarkers(TextBlock::class.java)
                    .joinToString(" ") { it.text }

                val verse = Verse(
                    verseMarker.verseNumber,
                    text
                )

                spec.addVerse(chapter, verse)
            }
        }

        return spec
    }

    fun verseListToDict(verseList: List<ContentRow>): Map<String, Map<String, Map<String, String>>> {
        val books = mutableMapOf<String, MutableMap<String, MutableMap<String, String>>>()

        for (row in verseList) {
            val verse = Verse(
                verseNumber = row.verseNumber,
                verseText = row.verseText
            )

            val rowBook = row.book
            val rowChapter = row.chapter
            val rowVerseNumber = row.verseNumber
            val rowText = row.verseText

            if (!books.containsKey(rowBook)) {
                books[rowBook] = mutableMapOf()
            }
            if (!books[rowBook]!!.containsKey(rowChapter)) {
                books[rowBook]!![rowChapter] = mutableMapOf()
            }
            if (books[rowBook]!![rowChapter]!!.containsKey(rowVerseNumber)) {
                // verse already exists for the given book-chapter-verse
                throw Exception("Verse already exists")
            }

            books[rowBook]!![rowChapter]!![rowVerseNumber] = rowText
        }

        return books
    }
}