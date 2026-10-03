package com.xbox123z.zlib

import android.app.Application
import com.xbox123z.zlib.data.Prefs
import com.xbox123z.zlib.data.ZLibClient
import com.xbox123z.zlib.dl.Downloads

class ZLibApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var client: ZLibClient
        private set
    lateinit var downloads: Downloads
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        client = ZLibClient(prefs)
        downloads = Downloads(this)
    }
}
