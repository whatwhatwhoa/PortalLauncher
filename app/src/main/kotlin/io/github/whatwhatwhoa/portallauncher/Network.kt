package io.github.whatwhatwhoa.portallauncher

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.UnknownHostException

/**
 * Words for a request that failed (the SteamGridDB key's check, Updates). Offline is the usual
 * reason, but LineageOS's Restricted networking mode blocks a newly installed app by itself:
 * Wi-Fi and the browser work, and every lookup of ours fails as UnknownHostException
 * (gotchas.md). "Offline" beside a full Wi-Fi wedge sends the owner hunting, so that case names
 * the switch instead.
 */
class Connectivity(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** The note for [e]; asks Android about the networks, so off the main thread. */
    fun failure(e: Exception): String {
        // Android drops the stack of an UnknownHostException from the log, so its text goes too.
        Log.w(TAG, "Request failed: $e", e)
        return when {
            !online() -> OFFLINE
            e is UnknownHostException -> BLOCKED
            else -> NO_ANSWER
        }
    }

    // Every network, not the default one: Android hides that from an app it blocks, so a blocked
    // Portal would look offline. One look, not a watch, so the old list call is enough.
    @Suppress("DEPRECATION")
    private fun online(): Boolean = try {
        manager.allNetworks.any { manager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true }
    } catch (e: SecurityException) {
        false
    }

    companion object {
        private const val TAG = "Connectivity"
        const val OFFLINE = "Offline, try again"
        const val BLOCKED = "Blocked: App info → Network access"
        const val NO_ANSWER = "No answer, try again"
    }
}
