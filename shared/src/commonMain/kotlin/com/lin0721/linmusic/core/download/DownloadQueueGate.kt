package com.lin0721.linmusic.core.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// 下载并发闸门：同时最多 maxConcurrent 个任务在下载，其余按排队顺序等待。
// WorkManager 无法限制 CoroutineWorker 的并发，也没有优先级，由此统一调度；
// 每次放行时重新读取排队顺序，用户调整的「优先下载」会在下一个空位生效
class DownloadQueueGate(
    private val maxConcurrent: Int,
    private val orderOf: suspend (workIds: Collection<String>) -> Map<String, Long>
) {
    private class Waiter(val workId: String, val granted: CompletableDeferred<Unit> = CompletableDeferred())

    private val mutex = Mutex()
    private var running = 0
    private val waiters = mutableListOf<Waiter>()

    suspend fun <T> withPermit(workId: String, block: suspend () -> T): T {
        acquire(workId)
        try {
            return block()
        } finally {
            withContext(NonCancellable) { release() }
        }
    }

    private suspend fun acquire(workId: String) {
        val waiter = mutex.withLock {
            if (running < maxConcurrent && waiters.isEmpty()) {
                running++
                null
            } else {
                Waiter(workId).also { waiters += it }
            }
        } ?: return
        try {
            waiter.granted.await()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock {
                    // 仍在排队则直接退出；已被放行则把名额交给下一个
                    if (!waiters.remove(waiter)) handOff()
                }
            }
            throw e
        }
    }

    private suspend fun release() {
        mutex.withLock { handOff() }
    }

    // 持有一个名额的一方让出：交给排队最靠前的等待者，没有则名额空出
    private suspend fun handOff() {
        if (waiters.isEmpty()) {
            running--
            return
        }
        val orders = runCatching { orderOf(waiters.map { it.workId }) }.getOrDefault(emptyMap())
        val next = waiters.minBy { orders[it.workId] ?: Long.MAX_VALUE }
        waiters.remove(next)
        next.granted.complete(Unit)
    }
}
