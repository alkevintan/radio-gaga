package com.radio.player.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import java.net.InetAddress

/**
 * Wrapper around Android NSD for advertising and discovering Radio Gaga pairing endpoints.
 *
 * Service type: `_radiogaga._tcp.`
 * TXT records carry the local device id so a peer can match a discovered host against
 * a previously paired identity.
 */
class PairingNsd(private val context: Context) {

    companion object {
        private const val TAG = "PairingNsd"
        const val SERVICE_TYPE = "_radiogaga._tcp."
        const val ATTR_DEVICE_ID = "did"
        const val ATTR_NAME = "nm"
    }

    private val nsd: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registered = false

    data class DiscoveredHost(
        val deviceId: String,
        val name: String,
        val host: InetAddress,
        val port: Int
    )

    fun advertise(localDeviceId: String, localName: String, port: Int) {
        if (registered) return
        val info = NsdServiceInfo().apply {
            // Service name must be unique on the network. Suffix with a short id slice.
            serviceName = "RadioGaga-${localDeviceId.take(8)}"
            serviceType = SERVICE_TYPE
            this.port = port
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute(ATTR_DEVICE_ID, localDeviceId)
                setAttribute(ATTR_NAME, localName)
            }
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                registered = true
                Log.i(TAG, "registered ${serviceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "register failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                registered = false
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "unregister failed: $errorCode")
            }
        }
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.w(TAG, "registerService threw", e)
        }
    }

    fun stopAdvertise() {
        registrationListener?.let {
            try { nsd.unregisterService(it) } catch (_: Exception) {}
        }
        registrationListener = null
        registered = false
    }

    /**
     * Browse for the first matching host whose TXT `did` equals [targetDeviceId], then
     * resolve it to an [InetAddress] + port and invoke [onFound] once.
     *
     * Discovery keeps running until [stopDiscovery] is called.
     */
    fun discover(targetDeviceId: String, onFound: (DiscoveredHost) -> Unit, onLost: () -> Unit = {}) {
        stopDiscovery()
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolve(serviceInfo) { resolved ->
                    val did = readAttr(resolved, ATTR_DEVICE_ID) ?: return@resolve
                    if (did != targetDeviceId) return@resolve
                    val host = resolved.host ?: return@resolve
                    onFound(
                        DiscoveredHost(
                            deviceId = did,
                            name = readAttr(resolved, ATTR_NAME) ?: resolved.serviceName,
                            host = host,
                            port = resolved.port
                        )
                    )
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                onLost()
            }
        }
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "discoverServices threw", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        discoveryListener = null
    }

    private fun resolve(info: NsdServiceInfo, cb: (NsdServiceInfo) -> Unit) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "resolve failed: $errorCode")
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                cb(serviceInfo)
            }
        }
        try {
            nsd.resolveService(info, resolveListener)
        } catch (e: Exception) {
            Log.w(TAG, "resolveService threw", e)
        }
    }

    private fun readAttr(info: NsdServiceInfo, key: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        val map = info.attributes ?: return null
        val bytes = map[key] ?: return null
        return String(bytes, Charsets.UTF_8)
    }
}
