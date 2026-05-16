package com.assigntask.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class AssignTaskApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAgtUjdkU3UtPYpNHCGh4nYX_VOIsTA-R8")
                .setApplicationId("1:90081570769:android:0000000000000001")
                .setProjectId("assigntask-51813")
                .setStorageBucket("assigntask-51813.firebasestorage.app")
                .setGcmSenderId("90081570769")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
