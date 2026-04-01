package com.form1.musicplayer.onedrive

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Background download queue for OneDrive files.
 *
 * Two concurrent downloaders:
 *
 *   **Immediate worker** — handles a single "play next" request. Calling [requestImmediate]
 *   overwrites the current slot; if a download is already in progress it is cancelled and
 *   restarted with the new item. This ensures that the song the user is about to play is
 *   fetched as quickly as possible.
 *
 *   **Background worker** — processes items enqueued via [enqueue] one at a time (FIFO).
 *   It pauses while the immediate worker is active and resumes once the immediate slot is
 *   cleared, so background downloads never compete with playback-critical downloads.
 *
 * Both workers use [OneDriveCacheManager.getFileContent] so downloaded bytes are
 * automatically saved to the disk cache and served on future requests without re-downloading.
 *
 * Completed downloads emit a [DownloadEvent] on [events] so UI can update cached indicators.
 */
class OneDriveDownloadQueue private constructor(context: Context) {

    private val cache = OneDriveCacheManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Immediate slot ────────────────────────────────────────────────────────

    @Volatile private var immediateItemId: String? = null
    @Volatile private var immediateDisplayName: String = ""
    private var immediateJob: Job? = null

    // ── Background queue ──────────────────────────────────────────────────────

    data class DownloadTask(val itemId: String, val displayName: String)

    private val backgroundChannel = Channel<DownloadTask>(capacity = Channel.UNLIMITED)

    // ── Events ────────────────────────────────────────────────────────────────

    data class DownloadEvent(
        val itemId: String,
        val displayName: String,
        val success: Boolean
    )

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 64)
    /** Emits a [DownloadEvent] each time a download completes (success or failure). */
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    companion object {
        private const val TAG = "OneDriveDownloadQueue"

        @Volatile private var instance: OneDriveDownloadQueue? = null

        fun getInstance(context: Context): OneDriveDownloadQueue =
            instance ?: synchronized(this) {
                instance ?: OneDriveDownloadQueue(context.applicationContext).also { instance = it }
            }
    }

    init {
        startBackgroundWorker()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Queue [itemId] for background download.
     * If the file is already cached, the task is skipped immediately.
     */
    fun enqueue(itemId: String, displayName: String) {
        if (cache.isCached(itemId)) {
            Log.d(TAG, "Skipping already-cached: $displayName")
            return
        }
        scope.launch { backgroundChannel.send(DownloadTask(itemId, displayName)) }
    }

    /**
     * Request an immediate (high-priority) download of [itemId].
     *
     * The immediate slot is overwritten; if a previous immediate download is running it is
     * cancelled so the new request starts right away.
     */
    fun requestImmediate(itemId: String, displayName: String) {
        if (cache.isCached(itemId)) {
            Log.d(TAG, "Immediate request skipped (already cached): $displayName")
            _events.tryEmit(DownloadEvent(itemId, displayName, success = true))
            return
        }
        immediateItemId = itemId
        immediateDisplayName = displayName
        immediateJob?.cancel()
        immediateJob = scope.launch { downloadImmediate(itemId, displayName) }
    }

    // ── Workers ───────────────────────────────────────────────────────────────

    private suspend fun downloadImmediate(itemId: String, displayName: String) {
        Log.d(TAG, "Immediate download: $displayName")
        val result = cache.getFileContent(itemId, isImmutable = true)
        val success = result.isSuccess
        if (!success) Log.w(TAG, "Immediate download failed: $displayName", result.exceptionOrNull())
        immediateItemId = null
        _events.emit(DownloadEvent(itemId, displayName, success))
    }

    private fun startBackgroundWorker() {
        scope.launch {
            for (task in backgroundChannel) {
                // Wait while an immediate download is in progress
                while (immediateItemId != null) {
                    kotlinx.coroutines.delay(200)
                }
                if (cache.isCached(task.itemId)) {
                    Log.d(TAG, "Background skip (already cached): ${task.displayName}")
                    continue
                }
                Log.d(TAG, "Background download: ${task.displayName}")
                val result = cache.getFileContent(task.itemId, isImmutable = true)
                val success = result.isSuccess
                if (!success) Log.w(TAG, "Background download failed: ${task.displayName}", result.exceptionOrNull())
                _events.emit(DownloadEvent(task.itemId, task.displayName, success))
            }
        }
    }
}
