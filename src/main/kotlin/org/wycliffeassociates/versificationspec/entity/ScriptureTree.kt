package org.wycliffeassociates.versificationspec.entity

data class ScriptureTree(
    val books: MutableMap<String, ChapterNode> = mutableMapOf()
)