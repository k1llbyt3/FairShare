package com.fairshare.android.core.domain.rounding

/**
 * Deterministic Rounding Engine.
 * Implements the Largest Remainder Method (Hare-Niemeyer / Hamilton method)
 * with a deterministic tie-breaker (participant ID ascending).
 * Guarantees:
 * 1. Zero lost minor units.
 * 2. Zero duplicated minor units.
 * 3. Deterministic and reproducible across all devices and runs.
 * 4. Sum of allocated minor units is strictly equal to totalMinor.
 */
object RoundingEngine {

    data class WeightedParticipant(
        val id: String,
        val weight: Long
    )

    /**
     * Distributes [totalMinor] proportionally according to the positive weights in [participants].
     * If weights sum to 0, throws IllegalArgumentException.
     * Guarantees that the sum of returned values equals [totalMinor].
     */
    fun distribute(
        totalMinor: Long,
        participants: List<WeightedParticipant>
    ): Map<String, Long> {
        if (participants.isEmpty()) {
            return emptyMap()
        }
        if (totalMinor == 0L) {
            return participants.associate { it.id to 0L }
        }

        val totalWeight = participants.sumOf { it.weight }
        require(totalWeight > 0L) { "Total weight must be strictly positive" }

        data class Allocation(
            val id: String,
            val floorAmount: Long,
            val remainder: Long
        )

        val allocations = ArrayList<Allocation>(participants.size)
        var sumFloor = 0L

        for (p in participants) {
            val numerator = totalMinor * p.weight
            val floorShare = numerator / totalWeight
            val rem = numerator % totalWeight
            allocations.add(Allocation(p.id, floorShare, rem))
            sumFloor += floorShare
        }

        var unallocated = totalMinor - sumFloor

        // Sort by remainder descending, then by id ascending as deterministic tie-breaker
        allocations.sortWith(
            compareByDescending<Allocation> { it.remainder }
                .thenBy { it.id }
        )

        val resultMap = HashMap<String, Long>(participants.size)
        for (alloc in allocations) {
            val extra = if (unallocated > 0L) {
                unallocated--
                1L
            } else {
                0L
            }
            resultMap[alloc.id] = alloc.floorAmount + extra
        }

        return resultMap
    }

    /**
     * Splits [totalMinor] equally among [participantIds].
     * Distributes remainder deterministically to participants sorted by ID ascending.
     */
    fun distributeEqual(
        totalMinor: Long,
        participantIds: List<String>
    ): Map<String, Long> {
        if (participantIds.isEmpty()) {
            return emptyMap()
        }
        val count = participantIds.size
        val baseShare = totalMinor / count
        val remainder = (totalMinor % count).toInt()

        // Deterministic sort by ID
        val sortedIds = participantIds.sorted()
        val resultMap = HashMap<String, Long>(count)

        for (i in sortedIds.indices) {
            val extra = if (i < remainder) 1L else 0L
            resultMap[sortedIds[i]] = baseShare + extra
        }

        return resultMap
    }
}
