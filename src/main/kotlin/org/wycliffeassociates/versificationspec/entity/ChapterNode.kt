package org.wycliffeassociates.versificationspec.entity

data class ChapterNode(
    val chapters: MutableMap<String, VerseNode> = mutableMapOf()
)