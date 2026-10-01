package com.fairshare.android.core.domain.rounding

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundingEngineTest {

    @Test
    fun testDistributeEqualExact() {
        val splits = RoundingEngine.distributeEqual(300L, listOf("A", "B", "C"))
        assertEquals(100L, splits["A"])
        assertEquals(100L, splits["B"])
        assertEquals(100L, splits["C"])
        assertEquals(300L, splits.values.sum())
    }

    @Test
    fun testDistributeEqualWithRemainder() {
        // ₹1.00 (100 paise) among 3 people -> 34, 33, 33 (deterministic tie-breaker: sorted IDs)
        val splits = RoundingEngine.distributeEqual(100L, listOf("A", "B", "C"))
        assertEquals(34L, splits["A"])
        assertEquals(33L, splits["B"])
        assertEquals(33L, splits["C"])
        assertEquals(100L, splits.values.sum())
    }

    @Test
    fun testDistributeWeightedLargestRemainder() {
        // 100 paise split 50%, 30%, 20%
        val participants = listOf(
            RoundingEngine.WeightedParticipant("A", 5000L),
            RoundingEngine.WeightedParticipant("B", 3000L),
            RoundingEngine.WeightedParticipant("C", 2000L)
        )
        val splits = RoundingEngine.distribute(100L, participants)
        assertEquals(50L, splits["A"])
        assertEquals(30L, splits["B"])
        assertEquals(20L, splits["C"])
        assertEquals(100L, splits.values.sum())
    }

    @Test
    fun testDistributeWeightedUnevenRemainder() {
        // 100 paise split into 3 equal shares of weight 1
        val participants = listOf(
            RoundingEngine.WeightedParticipant("A", 1L),
            RoundingEngine.WeightedParticipant("B", 1L),
            RoundingEngine.WeightedParticipant("C", 1L)
        )
        val splits = RoundingEngine.distribute(100L, participants)
        assertEquals(100L, splits.values.sum())
        // Remainder 1 goes to "A" due to deterministic tie-breaker ID ascending
        assertEquals(34L, splits["A"])
        assertEquals(33L, splits["B"])
        assertEquals(33L, splits["C"])
    }

    @Test
    fun testNoLostOrDuplicatedMinorUnitsAcrossLargeRange() {
        val ids = (1..7).map { "user_$it" }
        for (amount in 1L..500L) {
            val splits = RoundingEngine.distributeEqual(amount, ids)
            assertEquals(amount, splits.values.sum())
        }
    }
}
