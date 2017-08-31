package com.r2dkennobi.nsdhelper

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Created by Kenny Yokoyama on 8/8/16.
 *
 * Initialize NSD Manager and search for devices advertising provided service type
 * on the local network. Also performs device IP resolution after discovery.
 */
internal class NsdDiscoverHelper (ctx: Context, private val mServiceType: String, private val mCallback: NsdDiscoverCallback?) {
    private val mNsdManager: NsdManager = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mNsdItems = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val mResolvedItems = ArrayList<NsdServiceInfo>()
    private val mHandler: Handler = Handler(Looper.getMainLooper())
    private val mDiscoveryListener: NsdManager.DiscoveryListener

    /**
     * NsdDiscoverCallback interface
     */
    internal interface NsdDiscoverCallback {
        fun finishedResolving(resolved: List<NsdServiceInfo>)
    }

    init {
        this.mDiscoveryListener = object : NsdManager.DiscoveryListener {
            internal val mItems: MutableList<NsdServiceInfo> = ArrayList()

            override fun onStartDiscoveryFailed(s: String, i: Int) {
                Log.e(TAG, "Start Discovery failed: Error code: " + i)
                stopDroneDiscovery()
            }

            override fun onStopDiscoveryFailed(s: String, i: Int) {
                Log.e(TAG, "Stop Discovery failed: Error code: " + i)
                stopDroneDiscovery()
            }

            override fun onDiscoveryStarted(s: String) {
                Log.d(TAG, "Discovery started")
                mHandler.postDelayed({
                    try {
                        mNsdManager.stopServiceDiscovery(this)
                    } catch (e: IllegalArgumentException) {
                        Log.d(TAG, "No need to stop NSD service")
                    }
                }, OPERATION_TIMEOUT.toLong())
            }

            override fun onDiscoveryStopped(s: String) {
                Log.d(TAG, "Discovery stopped")
                initResolving(mItems)
            }

            override fun onServiceFound(nsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service discovery success: " + nsdServiceInfo)
                if (!mItems.contains(nsdServiceInfo)) {
                    mItems.add(nsdServiceInfo)
                }
            }

            override fun onServiceLost(nsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service Lost: " + nsdServiceInfo)
                if (mItems.contains(nsdServiceInfo)) {
                    mItems.remove(nsdServiceInfo)
                }
            }
        }
    }

    /**
     * Start network service discovery
     */
    fun startDroneDiscovery() {
        this.mNsdManager.discoverServices(this.mServiceType, NsdManager.PROTOCOL_DNS_SD, this.mDiscoveryListener)
    }

    /**
     * Stop network discovery and peer queue
     */
    fun stopDroneDiscovery() {
        try {
            this.mNsdManager.stopServiceDiscovery(this.mDiscoveryListener)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "No need to stop NSD service")
        }

        this.mNsdItems.clear()
        if (this.mCallback != null) {
            this.mCallback.finishedResolving(this.mResolvedItems)
        }
    }

    /**
     * Start resolving IPs for discovered peers
     * @param mItems - List of discovered peers
     */
    private fun initResolving(mItems: List<NsdServiceInfo>) {
        this.mNsdItems.addAll(mItems)
        startResolving()
    }

    /**
     * Resolve IP for a single peer
     */
    private fun startResolving() {
        this.mHandler.post {
            try {
                if (!mNsdItems.isEmpty()) {
                    val item = mNsdItems.remove()
                    mNsdManager.resolveService(item, initResolveListener())
                } else {
                    Log.d(TAG, "Empty NSD Item list!!! Finishing NsdDiscoverHelper.")
                    stopDroneDiscovery()
                }
            } catch (e: NoSuchElementException) {
                Log.e(TAG, "Accidentally attempted to remove from empty queue...")
                e.printStackTrace()
            }
        }
    }

    /**
     * Initialize resolver listener
     * @return New resolver listener
     */
    private fun initResolveListener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, i: Int) {
                Log.e(TAG, "Resolve failed: Device" + nsdServiceInfo.serviceName + ", Error code - " + i)
                startResolving()
            }

            override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "Resolve Succeeded: " + nsdServiceInfo)
                mResolvedItems.add(nsdServiceInfo)
                startResolving()
            }
        }
    }

    companion object {
        private val TAG = NsdDiscoverHelper::class.java.simpleName
        private const val OPERATION_TIMEOUT = 1000  // ms
    }
}
