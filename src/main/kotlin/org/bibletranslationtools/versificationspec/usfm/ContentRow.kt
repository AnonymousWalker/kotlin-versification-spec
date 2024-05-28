package org.bibletranslationtools.versificationspec.usfm

data class ContentRow(
    val book: String,
    val chapter: String,
    val verseNumber: String,
    val verseText: String
)
