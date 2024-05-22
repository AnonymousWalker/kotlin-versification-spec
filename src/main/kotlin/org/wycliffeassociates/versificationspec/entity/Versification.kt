package org.wycliffeassociates.versificationspec.entity

data class Versification (
    var shortname: String,
    var maxVerses: MutableMap<String, List<Int>>,
    var partialVerses: Map<String, MutableList<String>> = emptyMap(),
    var verseMappings: Map<String, String>,
    var excludedVerses: List<String> = listOf()
)