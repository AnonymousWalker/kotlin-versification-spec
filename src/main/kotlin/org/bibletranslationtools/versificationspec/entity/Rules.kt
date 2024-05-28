package org.bibletranslationtools.versificationspec.entity

import com.fasterxml.jackson.annotation.JsonAlias

data class Rule(
    val name: String,
    val columns: List<List<String>>,
    val tests: List<List<String>>,
    val ranges: List<Range>
)

data class Range(
    @JsonAlias(
        "MergedVerses",
        "OneToOne",
        "TextMayBeMissing",
        "LongVerse/LVElsewhere",
        "LongVerseElsewhere",
        "SubdividedVerse",
        "LongVerseElsewhereJoin",
        "DuplicateTarget",
        "LongVerseDuplicated",
        "LongVerse/LVDuplicated",
        "LongVerse/LVExtra",
        "PassageMissing"
    )
    val values: List<String>
)
