package com.heuristq.dinein_kiosk

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * Hosts the Flutter kiosk. Keeps the screen on and pins the app (Android lock task mode) so customers cannot
 * leave it. Staff can lift the lock from the app's PIN-protected service menu; it comes back the next time
 * the app starts or is brought to the front.
 */
class MainActivity : FlutterActivity() {

    private var lockAllowed = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        if (lockAllowed) enterLockTask()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "enter" -> {
                    lockAllowed = true
                    enterLockTask()
                    result.success(null)
                }
                "exit" -> {
                    lockAllowed = false
                    leaveLockTask()
                    result.success(null)
                }
                "isLocked" -> result.success(isLocked())
                else -> result.notImplemented()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isLocked(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.isInLockTaskMode
    }

    /**
     * As device owner (see docs/KIOSK_SETUP.md) this pins silently. Otherwise Android shows its own
     * screen-pinning prompt the first time. Failures are ignored: the lock is a hardening layer only.
     */
    private fun enterLockTask() {
        try {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setLockTaskPackages(
                    ComponentName(this, KioskDeviceAdminReceiver::class.java),
                    arrayOf(packageName),
                )
            }
            if (!isLocked()) startLockTask()
        } catch (_: Exception) {
        }
    }

    private fun leaveLockTask() {
        try {
            if (isLocked()) stopLockTask()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL = "com.heuristq.dinein_kiosk/lockdown"
    }
}
