package com.lin0721.linmusic.core.download

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveTaskStatusTest {

    @Test
    fun `WorkManager 实时状态优先`() {
        assertEquals(DownloadTaskStatus.RUNNING, resolveTaskStatus(WorkInfo.State.RUNNING, null))
        assertEquals(DownloadTaskStatus.WAITING, resolveTaskStatus(WorkInfo.State.ENQUEUED, null))
        assertEquals(DownloadTaskStatus.WAITING, resolveTaskStatus(WorkInfo.State.BLOCKED, null))
        assertEquals(DownloadTaskStatus.SUCCEEDED, resolveTaskStatus(WorkInfo.State.SUCCEEDED, PersistedTaskState.PAUSED))
        assertEquals(DownloadTaskStatus.FAILED, resolveTaskStatus(WorkInfo.State.FAILED, null))
    }

    @Test
    fun `已启动但仍在闸门前排队的任务显示为等待`() {
        assertEquals(DownloadTaskStatus.WAITING, resolveTaskStatus(WorkInfo.State.RUNNING, null, started = false))
        assertEquals(DownloadTaskStatus.RUNNING, resolveTaskStatus(WorkInfo.State.RUNNING, null, started = true))
    }

    @Test
    fun `暂停的任务被取消后仍显示为已暂停`() {
        assertEquals(DownloadTaskStatus.PAUSED, resolveTaskStatus(WorkInfo.State.CANCELLED, PersistedTaskState.PAUSED))
    }

    @Test
    fun `用户取消的任务不展示`() {
        assertNull(resolveTaskStatus(WorkInfo.State.CANCELLED, null))
    }

    @Test
    fun `WorkManager 清理后按持久化状态展示`() {
        assertEquals(DownloadTaskStatus.SUCCEEDED, resolveTaskStatus(null, PersistedTaskState.SUCCEEDED))
        assertEquals(DownloadTaskStatus.FAILED, resolveTaskStatus(null, PersistedTaskState.FAILED))
        assertEquals(DownloadTaskStatus.PAUSED, resolveTaskStatus(null, PersistedTaskState.PAUSED))
    }

    @Test
    fun `既没有任务也没有持久化状态的记录不展示`() {
        assertNull(resolveTaskStatus(null, null))
    }
}
