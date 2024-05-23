package org.wycliffeassociates.versificationspec.entity

data class Versification (
    var shortname: String,
    var maxVerses: MutableMap<String, List<Int>>,
    var partialVerses: MutableMap<String, MutableList<String>> = mutableMapOf(),
    var verseMappings: MutableMap<String, String> = mutableMapOf(),
    var excludedVerses: List<String> = listOf()
)