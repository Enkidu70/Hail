package com.aistra.hail.utils

import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyEvent
import com.aistra.hail.BuildConfig
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object HShizuku {
    private val isRoot get() = Shizuku.getUid() == 0
    private val userId get() = if (isRoot) android.os.Process.myUserHandle().hashCode() else 0
    private val callerPackage get() = if (isRoot) BuildConfig.APPLICATION_ID else "com.android.shell"

    private fun asInterface(className: String, serviceName: String): Any =
        Class.forName("$className\$Stub").getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(SystemServiceHelper.getSystemService(serviceName)))!!

    val lockScreen
        get() = try {
            val input = asInterface("android.hardware.input.IInputManager", "input")
            val inject = input::class.java.getMethod(
                "injectInputEvent", InputEvent::class.java, Int::class.java
            )
            val now = SystemClock.uptimeMillis()
            inject.invoke(
                input, KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER, 0), 0
            )
            inject.invoke(
                input, KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_POWER, 0), 0
            )
            true
        } catch (t: Throwable) {
            HLog.e(t)
            false
        }

    fun setAppDisabled(packageName: String, disabled: Boolean): Boolean {
        HPackages.getPackageInfoOrNull(packageName) ?: return false
        try {
            val pm = asInterface("android.content.pm.IPackageManager", "package")
            val newState = when {
                !disabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                isRoot -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }
            pm::class.java.getMethod(
                "setApplicationEnabledSetting",
                String::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                String::class.java
            ).invoke(pm, packageName, newState, 0, userId, callerPackage)
        } catch (t: Throwable) {
            HLog.e(t)
        }
        return HPackages.isAppDisabled(packageName) == disabled
    }

    fun isAppHidden(packageName: String): Boolean {
        HPackages.getPackageInfoOrNull(packageName) ?: return false
        return try {
            val pm = asInterface("android.content.pm.IPackageManager", "package")
            HiddenApiBypass.invoke(
                pm::class.java, pm, "getApplicationHiddenSettingAsUser", packageName, userId
            ) as Boolean
        } catch (t: Throwable) {
            HLog.e(t)
            false
        }
    }

    fun setAppHidden(packageName: String, hidden: Boolean): Boolean {
        HPackages.getPackageInfoOrNull(packageName) ?: return false
        return try {
            val pm = asInterface("android.content.pm.IPackageManager", "package")
            pm::class.java.getMethod(
                "setApplicationHiddenSettingAsUser",
                String::class.java,
                Boolean::class.java,
                Int::class.java
            ).invoke(pm, packageName, hidden, userId) as Boolean
        } catch (t: Throwable) {
            HLog.e(t)
            false
        }
    }

    fun setAppSuspended(packageName: String, suspended: Boolean): Boolean {
        HPackages.getPackageInfoOrNull(packageName) ?: return false
        return try {
            val pm = asInterface("android.content.pm.IPackageManager", "package")
            (HiddenApiBypass.invoke(
                pm::class.java,
                pm,
                "setPackagesSuspendedAsUser",
                arrayOf(packageName),
                suspended,
                null,
                null,
                null,
                callerPackage,
                userId
            ) as Array<*>).isEmpty()
        } catch (t: Throwable) {
            HLog.e(t)
            false
        }
    }

    fun uninstallApp(packageName: String): Boolean = false // Not yet implemented
}