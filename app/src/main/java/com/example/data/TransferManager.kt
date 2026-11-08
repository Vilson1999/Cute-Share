package com.example.data

import com.example.model.FileCategory
import com.example.model.SharedFile
import com.example.model.TransferFileItem
import com.example.model.TransferSession
import com.example.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

class TransferManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _currentSession = MutableStateFlow<TransferSession?>(null)
    val currentSession: StateFlow<TransferSession?> = _currentSession.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferSession>>(emptyList())
    val transferHistory: StateFlow<List<TransferSession>> = _transferHistory.asStateFlow()

    private var transferJob: Job? = null

    init {
        // Starts clean with no demo transfer history
        _transferHistory.value = emptyList()
    }

    fun startTransfer(
        files: List<SharedFile>,
        isOutgoing: Boolean,
        peerName: String,
        avatarRes: Int
    ) {
        transferJob?.cancel()

        val items = files.map { file ->
            TransferFileItem(
                id = UUID.randomUUID().toString(),
                name = file.name,
                sizeBytes = file.sizeBytes,
                transferredBytes = 0L,
                progress = 0f,
                status = TransferStatus.TRANSFERRING,
                category = file.category
            )
        }

        val session = TransferSession(
            id = UUID.randomUUID().toString(),
            isOutgoing = isOutgoing,
            peerName = peerName,
            avatarRes = avatarRes,
            files = items,
            isPaused = false,
            isCancelled = false,
            isCompleted = false,
            currentSpeedBytesPerSec = 35_000_000L,
            remainingSeconds = calculateRemainingSeconds(items.sumOf { it.sizeBytes }, 0L, 35_000_000L)
        )

        _currentSession.value = session
        startTransferLoop()
    }

    private fun startTransferLoop() {
        transferJob = scope.launch {
            val intervalMs = 120L

            while (isActive) {
                delay(intervalMs)
                val session = _currentSession.value ?: break
                if (session.isPaused || session.isCancelled || session.isCompleted) {
                    continue
                }

                // Simulate high-speed transfer with subtle fluctuation (30 to 45 MB/s)
                val speedMb = 32.0 + Random.nextDouble(-4.0, 8.0)
                val speedBytesPerSec = (speedMb * 1024 * 1024).toLong()
                val bytesThisTick = (speedBytesPerSec * (intervalMs.toDouble() / 1000.0)).toLong()

                var remainingBytesToDistribute = bytesThisTick
                var allDone = true

                val updatedFiles = session.files.map { item ->
                    if (item.status == TransferStatus.TRANSFERRING) {
                        val needed = item.sizeBytes - item.transferredBytes
                        if (needed > 0) {
                            val add = remainingBytesToDistribute.coerceAtMost(needed)
                            remainingBytesToDistribute -= add
                            val newTransferred = item.transferredBytes + add
                            val isItemComplete = newTransferred >= item.sizeBytes
                            if (!isItemComplete) allDone = false

                            item.copy(
                                transferredBytes = newTransferred,
                                progress = if (item.sizeBytes > 0) (newTransferred.toFloat() / item.sizeBytes).coerceIn(0f, 1f) else 1f,
                                status = if (isItemComplete) TransferStatus.COMPLETED else TransferStatus.TRANSFERRING
                            )
                        } else {
                            item.copy(status = TransferStatus.COMPLETED)
                        }
                    } else {
                        if (item.status == TransferStatus.PAUSED) {
                            allDone = false
                        }
                        item
                    }
                }

                val totalBytes = updatedFiles.sumOf { it.sizeBytes }
                val transferredBytes = updatedFiles.sumOf { it.transferredBytes }
                val remainingBytes = (totalBytes - transferredBytes).coerceAtLeast(0L)
                val remainingSecs = if (speedBytesPerSec > 0 && remainingBytes > 0) {
                    (remainingBytes / speedBytesPerSec).coerceAtLeast(1L)
                } else {
                    0L
                }

                val isFinished = allDone && updatedFiles.all { it.isFinished }

                val updatedSession = session.copy(
                    files = updatedFiles,
                    currentSpeedBytesPerSec = if (isFinished) 0L else speedBytesPerSec,
                    remainingSeconds = remainingSecs,
                    isCompleted = isFinished
                )

                _currentSession.value = updatedSession

                if (isFinished) {
                    _transferHistory.update { list ->
                        listOf(updatedSession) + list.filter { it.id != updatedSession.id }
                    }
                    break
                }
            }
        }
    }

    fun addFilesToActiveSession(newFiles: List<SharedFile>) {
        if (newFiles.isEmpty()) return

        val newItems = newFiles.map { file ->
            TransferFileItem(
                id = UUID.randomUUID().toString(),
                name = file.name,
                sizeBytes = file.sizeBytes,
                transferredBytes = 0L,
                progress = 0f,
                status = TransferStatus.TRANSFERRING,
                category = file.category
            )
        }

        _currentSession.update { session ->
            if (session == null) return@update null
            session.copy(
                files = session.files + newItems,
                isCompleted = false,
                isCancelled = false,
                currentSpeedBytesPerSec = 35_000_000L
            )
        }

        if (transferJob == null || transferJob?.isActive != true) {
            startTransferLoop()
        }
    }

    fun toggleFilePause(fileId: String) {
        _currentSession.update { session ->
            if (session == null) return@update null
            val updated = session.files.map { item ->
                if (item.id == fileId) {
                    when (item.status) {
                        TransferStatus.TRANSFERRING -> item.copy(status = TransferStatus.PAUSED)
                        TransferStatus.PAUSED -> item.copy(status = TransferStatus.TRANSFERRING)
                        else -> item
                    }
                } else item
            }
            session.copy(files = updated)
        }
    }

    fun cancelFile(fileId: String) {
        _currentSession.update { session ->
            if (session == null) return@update null
            val updated = session.files.map { item ->
                if (item.id == fileId) {
                    item.copy(status = TransferStatus.CANCELLED)
                } else item
            }
            val allDone = updated.all { it.isFinished }
            session.copy(
                files = updated,
                isCompleted = allDone
            )
        }
    }

    fun toggleSessionPause() {
        _currentSession.update { session ->
            if (session == null) return@update null
            val newPaused = !session.isPaused
            val updatedFiles = session.files.map { item ->
                if (item.status != TransferStatus.COMPLETED && item.status != TransferStatus.CANCELLED) {
                    item.copy(status = if (newPaused) TransferStatus.PAUSED else TransferStatus.TRANSFERRING)
                } else item
            }
            session.copy(
                isPaused = newPaused,
                files = updatedFiles
            )
        }
    }

    fun cancelSession() {
        transferJob?.cancel()
        _currentSession.update { session ->
            if (session == null) return@update null
            val updatedFiles = session.files.map {
                if (!it.isFinished) it.copy(status = TransferStatus.CANCELLED) else it
            }
            session.copy(
                isCancelled = true,
                currentSpeedBytesPerSec = 0L,
                remainingSeconds = 0L,
                files = updatedFiles
            )
        }
    }

    fun clearSession() {
        val session = _currentSession.value
        if (session != null) {
            _transferHistory.update { listOf(session) + it }
        }
        _currentSession.value = null
    }

    private fun calculateRemainingSeconds(total: Long, done: Long, speed: Long): Long {
        if (speed <= 0) return 0L
        return ((total - done).coerceAtLeast(0L) / speed).coerceAtLeast(1L)
    }
}
