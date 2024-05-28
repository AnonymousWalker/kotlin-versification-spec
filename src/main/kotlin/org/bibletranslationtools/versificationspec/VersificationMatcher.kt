package org.bibletranslationtools.versificationspec

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.bibletranslationtools.versificationspec.entity.StandardMapping
import org.bibletranslationtools.versificationspec.entity.Versification

object VersificationMatcher {
    private val standardVersifications: List<Versification>

    init {
        standardVersifications = listOf(
            getStandardMapping(StandardMapping.ORG),
            getStandardMapping(StandardMapping.ENG),
            getStandardMapping(StandardMapping.LXX),
            getStandardMapping(StandardMapping.RSC),
            getStandardMapping(StandardMapping.RSO),
            getStandardMapping(StandardMapping.VUL)
        )
    }

    fun match(spec: Versification): Versification {
        val matches = standardVersifications.filter { standard ->
            compareMaxVerses(spec.maxVerses, standard.maxVerses) &&
            compareMappedVerses(spec.mappedVerses, standard.mappedVerses) &&
            comparePartialVerses(spec.partialVerses, standard.partialVerses) &&
            compareExcludedVerses(spec.excludedVerses, standard.excludedVerses)
        }

        return matches.firstOrNull() ?: spec.copy(shortname = "custom_versification")
    }

    private fun compareMaxVerses(
        a: Map<String, List<Int>>,
        b: Map<String, List<Int>>
    ): Boolean {
        return a.all { (book, v) ->
            a[book] == b[book] // all books in `a` exist in `b`
        }
    }

    private fun comparePartialVerses(
        a: Map<String, MutableList<String>>,
        b: Map<String, MutableList<String>>
    ): Boolean {
        return a.all { (key, value) ->
            a[key] == b[key]
        }
    }

    private fun compareMappedVerses(
        a: Map<String, String>,
        b: Map<String, String>
    ): Boolean {
        return a.all { (key, value) ->
            a[key] == b[key]
        }
    }

    private fun compareExcludedVerses(a: List<String>, b: List<String>): Boolean {
        return a == b
    }

    private fun getStandardMapping(mapping: StandardMapping): Versification {
        val name = mapping.fileName
        return javaClass.classLoader.getResourceAsStream("standard-mappings/$name")!!
            .use { stream ->
                ObjectMapper(JsonFactory())
                    .registerKotlinModule()
                    .readValue<Versification>(stream)
                    .let {
                        it.copy(shortname = mapping.name)
                    }
            }
    }
}