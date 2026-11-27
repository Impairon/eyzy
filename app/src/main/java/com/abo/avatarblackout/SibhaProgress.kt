package com.abo.avatarblackout

/**
 * Progress through a traditional 33-count tasbih cycle.
 * The stored counter remains cumulative; this only derives the visible cycle progress.
 */
object SibhaProgress {
    const val CYCLE_SIZE = 33

    fun progress(totalCount: Int): Int {
        if (totalCount <= 0) return 0
        return ((totalCount - 1) % CYCLE_SIZE) + 1
    }
}
