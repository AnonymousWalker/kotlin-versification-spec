package org.wycliffeassociates.versificationspec.entity

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Versification (
    var shortname: String? = null,
    var maxVerses: MutableMap<String, List<Int>>,
    var partialVerses: MutableMap<String, MutableList<String>> = mutableMapOf(),
    @JsonAlias("verseMappings")
    var mappedVerses: MutableMap<String, String> = mutableMapOf(),
    var excludedVerses: MutableList<String> = mutableListOf()
)