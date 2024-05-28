package org.bibletranslationtools.versificationspec.entity

data class ChapterNode(
    val chapters: MutableMap<String, VerseNode> = mutableMapOf()
)