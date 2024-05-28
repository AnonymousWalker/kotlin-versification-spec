package org.bibletranslationtools.versificationspec.entity

data class ScriptureTree(
    val books: MutableMap<String, ChapterNode> = mutableMapOf()
)