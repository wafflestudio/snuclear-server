package com.wafflestudio.team8server.course.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CourseSearchNormalizerTest {
    @Test
    fun `normalize trims query and removes spaces`() {
        val normalized = CourseSearchNormalizer.normalize(" \uC790\uB8CC \uAD6C\uC870 ")

        assertThat(normalized).isNotNull
        assertThat(normalized!!.trimmed).isEqualTo("\uC790\uB8CC \uAD6C\uC870")
        assertThat(normalized.withoutSpaces).isEqualTo("\uC790\uB8CC\uAD6C\uC870")
        assertThat(normalized.keywords.map { it.raw }).containsExactly("\uC790\uB8CC", "\uAD6C\uC870")
    }

    @Test
    fun `normalize removes spaces from trimmed query`() {
        val normalized = CourseSearchNormalizer.normalize("\uC790\uB8CC \uAD6C\uC870")

        assertThat(normalized).isNotNull
        assertThat(normalized!!.withoutSpaces).isEqualTo("\uC790\uB8CC\uAD6C\uC870")
    }

    @Test
    fun `normalize builds fuzzy pattern for compact shorthand`() {
        val normalized = CourseSearchNormalizer.normalize("\uBB3C2")

        assertThat(normalized).isNotNull
        assertThat(normalized!!.keywords).hasSize(1)
        assertThat(normalized.keywords.first().fuzzyPattern).isEqualTo("%\uBB3C%2%")
    }

    @Test
    fun `normalize builds department prefix pattern for department shorthand`() {
        val normalized = CourseSearchNormalizer.normalize("\uCEF4\uACF5\uACFC")

        assertThat(normalized).isNotNull
        assertThat(normalized!!.keywords.first().departmentPrefixFuzzyPattern).isEqualTo("\uCEF4%\uACF5%")
    }

    @Test
    fun `normalize maps snutt semantic keywords`() {
        assertThat(
            CourseSearchNormalizer
                .normalize("\uC804\uACF5")!!
                .keywords
                .first()
                .semanticRule,
        ).isEqualTo(SemanticCourseSearchRule.MAJOR)
        assertThat(
            CourseSearchNormalizer
                .normalize("\uB300\uD559\uC6D0")!!
                .keywords
                .first()
                .semanticRule,
        ).isEqualTo(SemanticCourseSearchRule.GRADUATE)
        assertThat(
            CourseSearchNormalizer
                .normalize("\uD559\uBD80")!!
                .keywords
                .first()
                .semanticRule,
        ).isEqualTo(SemanticCourseSearchRule.UNDERGRADUATE)
    }

    @Test
    fun `normalize escapes like wildcards`() {
        val normalized = CourseSearchNormalizer.normalize("100%")

        assertThat(normalized).isNotNull
        assertThat(normalized!!.keywords.first().containsPattern).isEqualTo("%100\\%%")
    }

    @Test
    fun `normalize returns null for null or blank query`() {
        assertThat(CourseSearchNormalizer.normalize(null)).isNull()
        assertThat(CourseSearchNormalizer.normalize("   ")).isNull()
    }
}
