package org.bibletranslationtools.versificationspec

import org.bibletranslationtools.versificationspec.entity.Range

fun mapFromRange(range: Range): List<String> {
    // Using when statement to check each field
    return when {
        range.mergedVerses != null -> range.mergedVerses
        range.oneToOne != null -> range.oneToOne
        range.textMayBeMissing != null -> range.textMayBeMissing
        range.longVerseLVElsewhere != null -> range.longVerseLVElsewhere
        range.longVerseElsewhere != null -> range.longVerseElsewhere
        range.subdividedVerse != null -> range.subdividedVerse
        range.longVerseElsewhereJoin != null -> range.longVerseElsewhereJoin
        range.duplicateTarget != null -> range.duplicateTarget
        range.longVerseDuplicated != null -> range.longVerseDuplicated
        range.longVerseLVDuplicated != null -> range.longVerseLVDuplicated
        range.longVerseLVExtra != null -> range.longVerseLVExtra
        range.passageMissing != null -> range.passageMissing
        else -> listOf()
    }
}