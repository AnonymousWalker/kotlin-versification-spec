package org.wycliffeassociates.versificationspec.usfm

class UsfmTree(
    val book: Book,
    val chapters: List<Chapter>
) {

    fun addChapter(chapter: Chapter) {
        chapters as MutableList
        chapters.add(chapter)
    }

    fun addVerse(chapter: Chapter, verse: Verse) {
        chapter.contents as MutableList
        chapter.contents.add(verse)
    }
}