package com.heuristq.dinein_kiosk

import android.app.admin.DeviceAdminReceiver

/**
 * Lets the app be provisioned as device owner (`dpm set-device-owner`), which is what allows silent lock task
 * mode with no pinning prompt. It declares no policies of its own.
 */
class KioskDeviceAdminReceiver : DeviceAdminReceiver()
