/** mobile/app/src/main/java/com/kp/filemanager/domain/ProgressReporter.kt: Emits one immutable pipeline progress model with exact counters and smoothed operation ETA; added for media workflow optimization. */
package com.kp.filemanager.domain

class ProgressReporter(
    private val emit: (OrganizationProgress) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
) {
    private var state = OrganizationProgress()
    private var currentItemStartedNanos: Long? = null
    private val recentDurationsNanos = ArrayDeque<Long>()

    fun scanning(
        filesDiscovered: Int,
        directoriesDiscovered: Int,
        currentItem: String,
        moviesDetected: Int = 0,
        episodesDetected: Int = 0,
        subtitlesDetected: Int = 0,
    ) {
        state = state.copy(
            phase = OrganizationPhase.SCANNING,
            isIndeterminate = true,
            processedItems = filesDiscovered,
            totalItems = 0,
            remainingItems = 0,
            percent = null,
            currentItem = currentItem,
            filesDiscovered = filesDiscovered,
            directoriesDiscovered = directoriesDiscovered,
            moviesDetected = moviesDetected,
            episodesDetected = episodesDetected,
            subtitlesDetected = subtitlesDetected,
            etaMillis = null,
            startedAtMillis = state.startedAtMillis ?: wallClockMillis(),
        )
        emit(state)
    }

    fun begin(phase: OrganizationPhase, totalItems: Int) {
        state = state.copy(
            phase = phase,
            isIndeterminate = false,
            processedItems = 0,
            totalItems = totalItems,
            remainingItems = totalItems,
            percent = if (totalItems == 0) 100 else 0,
            currentItem = "",
            etaMillis = null,
            startedAtMillis = state.startedAtMillis ?: wallClockMillis(),
        )
        currentItemStartedNanos = null
        recentDurationsNanos.clear()
        emit(state)
    }

    fun currentItem(value: String) {
        currentItemStartedNanos = nanoTime()
        state = state.copy(currentItem = value)
        emit(state)
    }

    fun completeItem(
        operationType: OperationType,
        failed: Boolean = false,
        skipped: Boolean = false,
    ) {
        currentItemStartedNanos?.let { started ->
            recentDurationsNanos += (nanoTime() - started).coerceAtLeast(0)
            while (recentDurationsNanos.size > ETA_SAMPLE_WINDOW) recentDurationsNanos.removeFirst()
        }
        val processed = (state.processedItems + 1).coerceAtMost(state.totalItems)
        val remaining = (state.totalItems - processed).coerceAtLeast(0)
        val moved = state.movedCount + if (
            !failed && !skipped && operationType in setOf(OperationType.MOVE, OperationType.MOVE_AND_RENAME)
        ) {
            1
        } else {
            0
        }
        val renamed = state.renamedCount + if (
            !failed && !skipped && operationType in setOf(OperationType.RENAME, OperationType.MOVE_AND_RENAME)
        ) {
            1
        } else {
            0
        }
        state = state.copy(
            processedItems = processed,
            remainingItems = remaining,
            percent = if (state.totalItems == 0) 100 else (processed * 100 / state.totalItems),
            movedCount = moved,
            renamedCount = renamed,
            skippedCount = state.skippedCount + if (skipped) 1 else 0,
            failedCount = state.failedCount + if (failed) 1 else 0,
            etaMillis = estimatedRemainingMillis(remaining),
        )
        currentItemStartedNanos = null
        emit(state)
    }

    fun completePhaseItem(currentItem: String = state.currentItem) {
        val processed = (state.processedItems + 1).coerceAtMost(state.totalItems)
        state = state.copy(
            processedItems = processed,
            remainingItems = (state.totalItems - processed).coerceAtLeast(0),
            percent = if (state.totalItems == 0) 100 else (processed * 100 / state.totalItems),
            currentItem = currentItem,
        )
        emit(state)
    }

    fun completeCleanupCandidate(path: String, deleted: Boolean) {
        val processed = (state.processedItems + 1).coerceAtMost(state.totalItems)
        state = state.copy(
            processedItems = processed,
            remainingItems = (state.totalItems - processed).coerceAtLeast(0),
            percent = if (state.totalItems == 0) 100 else (processed * 100 / state.totalItems),
            currentItem = path,
            cleanedFolderCount = state.cleanedFolderCount + if (deleted) 1 else 0,
        )
        emit(state)
    }

    fun complete() {
        state = state.copy(
            phase = OrganizationPhase.COMPLETE,
            isIndeterminate = false,
            processedItems = state.totalItems,
            remainingItems = 0,
            percent = 100,
            currentItem = "",
            etaMillis = null,
        )
        emit(state)
    }

    private fun estimatedRemainingMillis(remaining: Int): Long? {
        if (recentDurationsNanos.size < MINIMUM_ETA_SAMPLES || remaining == 0) return null
        val smoothedNanos = recentDurationsNanos.foldIndexed(0.0) { index, average, duration ->
            if (index == 0) duration.toDouble() else ETA_SMOOTHING * duration + (1 - ETA_SMOOTHING) * average
        }
        return (smoothedNanos * remaining / NANOS_PER_MILLISECOND).toLong().coerceAtLeast(1)
    }

    private companion object {
        const val MINIMUM_ETA_SAMPLES = 3
        const val ETA_SAMPLE_WINDOW = 8
        const val ETA_SMOOTHING = 0.35
        const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}
