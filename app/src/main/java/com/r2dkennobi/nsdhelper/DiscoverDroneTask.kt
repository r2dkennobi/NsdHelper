package com.r2dkennobi.nsdhelper

import android.content.Context
import android.net.nsd.NsdServiceInfo
import android.os.AsyncTask
import android.os.CountDownTimer
import android.util.Log
import java.util.*

/**
 * Created by Kenny Yokoyama on 11/9/16.
 */
class DiscoverDroneTask (ctx: Context, serviceType: String, private val mCallback: DiscoverDroneInterface) : AsyncTask<Void, Void, List<NsdServiceInfo>>() {
    private val mNsdDiscoverHelper: NsdDiscoverHelper
    private val mList = ArrayList<NsdServiceInfo>()
    private val mTimer = object : CountDownTimer(OPERATION_TIMEOUT.toLong(), 1000) {
        override fun onTick(l: Long) {}

        override fun onFinish() {
            mRunning = false
        }
    }
    private var mRunning: Boolean = false

    /**
     * Discover Drone Interface callback
     */
    interface DiscoverDroneInterface {
        fun handlePostExecute(items: List<NsdServiceInfo>)
    }

    init {
        this.mNsdDiscoverHelper = NsdDiscoverHelper(ctx, serviceType, object : NsdDiscoverHelper.NsdDiscoverCallback {
            override fun finishedResolving(resolved: List<NsdServiceInfo>) {
                Log.d(TAG, "Finished resolving")
                mRunning = false
                mList.addAll(resolved)
            }
        })
        this.mRunning = true
    }

    override fun doInBackground(vararg voids: Void): List<NsdServiceInfo> {
        Log.d(TAG, "Running discovery for DiscoverDroneTask")
        this.mNsdDiscoverHelper.startDroneDiscovery()
        this.mTimer.start()
        while (this.mRunning) {
            if (isCancelled) {
                this.mNsdDiscoverHelper.stopDroneDiscovery()
                this.mTimer.cancel()
                this.mRunning = false
            }
        }
        return mList
    }

    override fun onPostExecute(droneListItems: List<NsdServiceInfo>) {
        super.onPostExecute(droneListItems)
        Log.d(TAG, "done DiscoverDroneTask: " + droneListItems.size)
        this.mCallback.handlePostExecute(droneListItems)
    }

    companion object {
        private val TAG = DiscoverDroneTask::class.java.simpleName
        private const val OPERATION_TIMEOUT = 2000  // ms
    }
}
