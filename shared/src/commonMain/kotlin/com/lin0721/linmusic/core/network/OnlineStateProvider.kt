package com.lin0721.linmusic.core.network

import kotlinx.coroutines.flow.StateFlow

// 本机是否存在可用网络连接，离线时数据层改读本地缓存
interface OnlineStateProvider {
    val online: StateFlow<Boolean>

    fun isOnline(): Boolean = online.value
}
