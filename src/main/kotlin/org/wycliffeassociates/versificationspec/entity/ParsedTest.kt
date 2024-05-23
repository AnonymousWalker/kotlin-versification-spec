package org.wycliffeassociates.versificationspec.entity

data class ParsedTest(
    val left: Address,
    val right: Address,
    val op: String
)

data class Address (
    val text: String,
    val parsed: Map<String, String>
)
