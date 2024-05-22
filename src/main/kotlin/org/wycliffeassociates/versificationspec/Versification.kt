package org.wycliffeassociates.org.wycliffeassociates.versificationspec

data class Versification (
    var shortname: String,
    var maxVerses: Map<String, List<Int>>,
    var partialVerses: Map<String, Any> = emptyMap(),
    var verseMappings: Map<String, String>,
    var excludedVerses: Map<String, Any> = emptyMap(),
    var unexcludedVerses: Map<String, Any> = emptyMap()
)