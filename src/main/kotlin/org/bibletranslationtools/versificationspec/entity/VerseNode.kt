package org.bibletranslationtools.versificationspec.entity

data class VerseNode(
    val verses: MutableMap<String, String> = mutableMapOf()
)
