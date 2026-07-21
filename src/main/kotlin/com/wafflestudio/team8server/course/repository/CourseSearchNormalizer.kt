package com.wafflestudio.team8server.course.repository

object CourseSearchNormalizer {
    private val whitespace = Regex("\\s+")
    private const val LIKE_ESCAPE = '\\'

    fun normalize(query: String?): NormalizedCourseQuery? {
        val trimmed = query?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val keywords =
            trimmed
                .split(whitespace)
                .mapNotNull { keyword ->
                    keyword
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let(::normalizeKeyword)
                }

        return NormalizedCourseQuery(
            trimmed = trimmed,
            withoutSpaces = trimmed.replace(" ", ""),
            keywords = keywords,
        )
    }

    private fun normalizeKeyword(keyword: String): NormalizedCourseKeyword {
        val withoutSpaces = keyword.replace(" ", "")
        return NormalizedCourseKeyword(
            raw = keyword,
            withoutSpaces = withoutSpaces,
            containsPattern = containsPattern(keyword),
            noSpaceContainsPattern = containsPattern(withoutSpaces),
            fuzzyPattern = fuzzyPattern(keyword),
            noSpaceFuzzyPattern = fuzzyPattern(withoutSpaces),
            departmentPrefixFuzzyPattern = departmentPrefixFuzzyPattern(keyword),
            semanticRule = SemanticCourseSearchRule.from(keyword),
        )
    }

    private fun containsPattern(value: String): String = "%${escapeLike(value)}%"

    private fun fuzzyPattern(value: String): String? =
        value
            .takeIf { it.length >= 2 }
            ?.map { escapeLike(it.toString()) }
            ?.joinToString("%", prefix = "%", postfix = "%")

    private fun departmentPrefixFuzzyPattern(value: String): String? {
        val keyword =
            when (value.lastOrNull()) {
                '\uACFC', '\uBD80' -> value.dropLast(1)
                else -> value
            }

        return keyword
            .takeIf { it.length >= 2 && it.hasKorean() && it.lastOrNull() != '\uD559' }
            ?.map { escapeLike(it.toString()) }
            ?.joinToString("%", prefix = "", postfix = "%")
    }

    private fun String.hasKorean(): Boolean = any { it in '\uAC00'..'\uD7A3' }

    private fun escapeLike(value: String): String =
        buildString {
            value.forEach { char ->
                if (char == LIKE_ESCAPE || char == '%' || char == '_') {
                    append(LIKE_ESCAPE)
                }
                append(char)
            }
        }
}

data class NormalizedCourseQuery(
    val trimmed: String,
    val withoutSpaces: String,
    val keywords: List<NormalizedCourseKeyword>,
)

data class NormalizedCourseKeyword(
    val raw: String,
    val withoutSpaces: String,
    val containsPattern: String,
    val noSpaceContainsPattern: String,
    val fuzzyPattern: String?,
    val noSpaceFuzzyPattern: String?,
    val departmentPrefixFuzzyPattern: String?,
    val semanticRule: SemanticCourseSearchRule?,
)

enum class SemanticCourseSearchRule {
    MAJOR,
    GRADUATE,
    UNDERGRADUATE,
    ;

    companion object {
        fun from(keyword: String): SemanticCourseSearchRule? =
            when (keyword) {
                "\uC804\uACF5" -> MAJOR
                "\uC11D\uBC15", "\uB300\uD559\uC6D0" -> GRADUATE
                "\uD559\uBD80", "\uD559\uC0AC" -> UNDERGRADUATE
                else -> null
            }
    }
}
