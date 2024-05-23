package org.wycliffeassociates.versificationspec.usfm

data class Chapter(
    val chapterNumber: String,
    val contents: List<Verse>
)