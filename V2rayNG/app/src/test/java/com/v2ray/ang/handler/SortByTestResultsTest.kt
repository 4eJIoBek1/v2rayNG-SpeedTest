package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SortByTestResultsTest {

    private fun aff(speed: Float = 0f, delay: Long = 0L) =
        ServerAffiliationInfo(testDelayMillis = delay, testSpeedMbps = speed)

    @Test
    fun `speed profiles come first in descending order`() {
        val affiliations = mapOf(
            "slow" to aff(speed = 5f, delay = 50L),
            "fast" to aff(speed = 100f, delay = 200L),
            "mid" to aff(speed = 20f, delay = 20L)
        )

        val result = AngConfigManager.sortGuidsByTestResults(
            guids = listOf("slow", "fast", "mid"),
            affiliationOf = affiliations::get
        )

        assertEquals(listOf("fast", "mid", "slow"), result)
    }

    @Test
    fun `delay-only profiles follow in ascending order`() {
        val affiliations = mapOf(
            "slow-ping" to aff(delay = 300L),
            "fast-ping" to aff(delay = 50L),
            "with-speed" to aff(speed = 1f, delay = 999L)
        )

        val result = AngConfigManager.sortGuidsByTestResults(
            guids = listOf("slow-ping", "fast-ping", "with-speed"),
            affiliationOf = affiliations::get
        )

        assertEquals(listOf("with-speed", "fast-ping", "slow-ping"), result)
    }

    @Test
    fun `untested and failed profiles go last`() {
        val affiliations = mapOf(
            "untested" to null,
            "failed-ping" to aff(delay = -1L),
            "ok" to aff(delay = 10L)
        )

        val result = AngConfigManager.sortGuidsByTestResults(
            guids = listOf("untested", "failed-ping", "ok"),
            affiliationOf = affiliations::get
        )

        assertEquals("ok", result.first())
        assertEquals(setOf("untested", "failed-ping"), result.drop(1).toSet())
    }

    @Test
    fun `empty input returns empty list`() {
        val result = AngConfigManager.sortGuidsByTestResults(
            guids = emptyList(),
            affiliationOf = { null }
        )

        assertEquals(emptyList<String>(), result)
    }
}
