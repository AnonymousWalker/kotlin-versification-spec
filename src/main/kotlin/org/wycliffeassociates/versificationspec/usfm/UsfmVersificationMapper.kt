package org.wycliffeassociates.versificationspec.usfm

import org.wycliffeassociates.usfmtools.USFMParser
import org.wycliffeassociates.usfmtools.models.markers.CMarker
import org.wycliffeassociates.usfmtools.models.markers.IDMarker
import org.wycliffeassociates.usfmtools.models.markers.TextBlock
import org.wycliffeassociates.usfmtools.models.markers.VMarker
import org.wycliffeassociates.versificationspec.entity.ChapterNode
import org.wycliffeassociates.versificationspec.entity.ScriptureTree
import org.wycliffeassociates.versificationspec.entity.VerseNode
import java.io.File

class UsfmVersificationMapper {

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

    fun verseListToTree(verseList: List<ContentRow>): ScriptureTree {
        val tree = ScriptureTree()

        for (row in verseList) {

            val rowBook = row.book
            val rowChapter = row.chapter
            val rowVerseNumber = row.verseNumber
            val rowText = row.verseText

            if (!tree.books.containsKey(rowBook)) {
                tree.books[rowBook] = ChapterNode()
            }
            if (tree.books[rowBook]?.chapters?.containsKey(rowChapter) == false) {
                tree.books[rowBook]!!.chapters[rowChapter] = VerseNode()
            }
            if (tree.books[rowBook]?.chapters?.get(rowChapter)?.verses?.containsKey(rowVerseNumber) == true) {
                // verse already exists for the given book-chapter-verse
                throw Exception("Verse already exists")
            }

            tree.books[rowBook]!!.chapters[rowChapter]!!.verses[rowVerseNumber] = rowText
        }

        return tree
    }
}