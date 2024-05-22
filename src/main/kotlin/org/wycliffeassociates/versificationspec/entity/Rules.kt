package org.wycliffeassociates.versificationspec.entity

import com.fasterxml.jackson.annotation.JsonProperty

data class Rule(
    val name: String,
    val columns: List<List<String>>,
    val tests: List<List<String>>,
    val ranges: List<Range>
)

data class Range(
    @JsonProperty("MergedVerses")
    val mergedVerses: List<String>?,
    @JsonProperty("OneToOne")
    val oneToOne: List<String>?,
    @JsonProperty("TextMayBeMissing")
    val textMayBeMissing: List<String>?,
    @JsonProperty("LongVerse/LVElsewhere")
    val longVerseLVElsewhere: List<String>?,
    @JsonProperty("LongVerseElsewhere")
    val longVerseElsewhere: List<String>?,
    @JsonProperty("SubdividedVerse")
    val subdividedVerse: List<String>?,
    @JsonProperty("LongVerseElsewhereJoin")
    val longVerseElsewhereJoin: List<String>?,
    @JsonProperty("DuplicateTarget")
    val duplicateTarget: List<String>?,
    @JsonProperty("LongVerseDuplicated")
    val longVerseDuplicated: List<String>?,
    @JsonProperty("LongVerse/LVDuplicated"
    ) val longVerseLVDuplicated: List<String>?,
    @JsonProperty("LongVerse/LVExtra"
    ) val longVerseLVExtra: List<String>?,
    @JsonProperty("PassageMissing")
    val passageMissing: List<String>?
)
