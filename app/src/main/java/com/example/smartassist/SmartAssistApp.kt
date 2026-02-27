package com.example.smartassist

import android.app.Application

class SmartAssistApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global initialization point
        // (Keep lightweight — no heavy loading here)
    }
}