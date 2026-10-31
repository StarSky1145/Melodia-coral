package com.lin0721.linmusic.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AndroidOnlineState"

// 只看 INTERNET 能力，不要求 VALIDATED：部分地区/ROM 的连通性校验会误判，导致在线时被当成离线
class AndroidOnlineStateProvider(context: Context) : OnlineStateProvider {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networks = HashSet<Network>()

    private val _online = MutableStateFlow(initialOnline())
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(networks) {
                networks.add(network)
                _online.value = true
            }
        }

        override fun onLost(network: Network) {
            synchronized(networks) {
                networks.remove(network)
                _online.value = networks.isNotEmpty()
            }
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager?.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            AppLogger.w(TAG, "注册网络回调失败，按在线处理", e)
            _online.value = true
        }
    }

    private fun initialOnline(): Boolean {
        val manager = connectivityManager ?: return true
        return try {
            val active = manager.activeNetwork ?: return false
            manager.getNetworkCapabilities(active)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            AppLogger.w(TAG, "初始网络状态检测异常，按在线处理", e)
            true
        }
    }
}
