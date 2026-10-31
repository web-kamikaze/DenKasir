package com.denkasir

import android.app.Application
import com.denkasir.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DenKasirApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getDatabase(this@DenKasirApp).ensureSeeded(this@DenKasirApp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}