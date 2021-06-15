/*
 * Aurora Store
 *  Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
 *
 *  Aurora Store is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Aurora Store is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.aurora.store.data.installer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.aurora.services.IPrivilegedCallback
import com.aurora.services.IPrivilegedService
import com.aurora.store.AuroraApplication
import com.aurora.store.BuildConfig
import com.aurora.store.R
import com.aurora.store.data.event.BusEvent
import com.aurora.store.data.event.InstallerEvent
import com.aurora.store.util.Log
import com.aurora.store.util.PackageUtil
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ServiceInstaller(context: Context) : InstallerBase(context) {

    private lateinit var serviceConnection: ServiceConnection

    companion object {
        const val ACTION_INSTALL_REPLACE_EXISTING = 2
        const val PRIVILEGED_EXTENSION_PACKAGE_NAME = "com.aurora.services"
        const val PRIVILEGED_EXTENSION_SERVICE_INTENT = "com.aurora.services.IPrivilegedService"
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun install(packageName: String, files: List<Any>) {

        when {
            isAlreadyQueued(packageName) -> {
                Log.i("$packageName already queued")
            }

            PackageUtil.isInstalled(context, PRIVILEGED_EXTENSION_PACKAGE_NAME) -> {
                Log.i("Received service install request for $packageName")
                val uriList = files.map {
                    when (it) {
                        is File -> getUri(it)
                        is String -> getUri(File(it))
                        else -> {
                            throw Exception("Invalid data, expecting listOf() File or String")
                        }
                    }
                }
                val fileList = files.map {
                    when (it) {
                        is File -> it.absolutePath
                        is String -> File(it).absolutePath
                        else -> {
                            throw Exception("Invalid data, expecting listOf() File or String")
                        }
                    }
                }

                xInstall(packageName, uriList, fileList)
            }
            else -> {
                postError(
                    packageName,
                    context.getString(R.string.installer_status_failure),
                    context.getString(R.string.installer_service_unavailable)
                )
            }
        }
    }

    override fun uninstall(packageName: String) {
        val attachedToServiceTimeout = AtomicBoolean(false)

        AuroraApplication.enqueuedInstalls.add(packageName)

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                attachedToServiceTimeout.set(true)
                val service = IPrivilegedService.Stub.asInterface(binder)

                if (service.hasPrivilegedPermissions()) {
                    Log.i(context.getString(R.string.installer_service_available))

                    val callback = object : IPrivilegedCallback.Stub() {

                        override fun handleResult(packageName: String, returnCode: Int) {

                        }

                        override fun handleResultX(
                            packageName: String,
                            returnCode: Int,
                            extra: String?
                        ) {
                            removeFromInstallQueue(packageName)
                            handleCallbackUninstall(packageName, returnCode, extra)
                        }
                    }

                    try {
                        service.deletePackageX(
                            packageName,
                            2,
                            BuildConfig.APPLICATION_ID,
                            callback
                        )
                    } catch (e: RemoteException) {
                        Log.e("Failed to connect Aurora Services")
                        removeFromInstallQueue(packageName)
                    }
                } else {
                    removeFromInstallQueue(packageName)
                    postError(
                        packageName,
                        context.getString(R.string.installer_status_failure),
                        context.getString(R.string.installer_service_misconfigured)
                    )
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                removeFromInstallQueue(packageName)
                Log.e("Disconnected from Aurora Services")
            }
        }

        val intent = Intent(PRIVILEGED_EXTENSION_SERVICE_INTENT)
        intent.setPackage(PRIVILEGED_EXTENSION_PACKAGE_NAME)

        context.bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        Handler(Looper.getMainLooper()).postDelayed({
            if (!attachedToServiceTimeout.get()) {
                removeFromInstallQueue(packageName)
            }
        }, 25 * 1000)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun xInstall(packageName: String, uriList: List<Uri>, fileList: List<String>) {
        val attachedToServiceTimeout = AtomicBoolean(false)

        AuroraApplication.enqueuedInstalls.add(packageName)

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                attachedToServiceTimeout.set(true)
                val service = IPrivilegedService.Stub.asInterface(binder)

                if (service.hasPrivilegedPermissions()) {
                    Log.i(context.getString(R.string.installer_service_available))

                    val callback = object : IPrivilegedCallback.Stub() {

                        override fun handleResult(packageName: String, returnCode: Int) {

                        }

                        override fun handleResultX(
                            packageName: String,
                            returnCode: Int,
                            extra: String?
                        ) {
                            removeFromInstallQueue(packageName)
                            handleCallback(packageName, returnCode, extra)
                        }
                    }

                    try {
                        if (service.isMoreMethodImplemented) {
                            try {
                                service.installSplitPackageMore(
                                    packageName,
                                    uriList,
                                    ACTION_INSTALL_REPLACE_EXISTING,
                                    BuildConfig.APPLICATION_ID,
                                    callback,
                                    fileList
                                )
                            } catch (e: RemoteException) {
                                removeFromInstallQueue(packageName)
                                postError(packageName, e.localizedMessage, e.stackTraceToString())
                            }
                        } else {
                            throw Exception("New method not implemented")
                        }
                    } catch (th: Throwable) {
                        th.printStackTrace()
                        try {
                            service.installSplitPackageX(
                                packageName,
                                uriList,
                                ACTION_INSTALL_REPLACE_EXISTING,
                                BuildConfig.APPLICATION_ID,
                                callback
                            )
                        } catch (e: RemoteException) {
                            removeFromInstallQueue(packageName)
                            postError(packageName, e.localizedMessage, e.stackTraceToString())
                        }
                    }
                } else {
                    removeFromInstallQueue(packageName)
                    postError(
                        packageName,
                        context.getString(R.string.installer_status_failure),
                        context.getString(R.string.installer_service_misconfigured)
                    )
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                removeFromInstallQueue(packageName)
                Log.e("Disconnected from Aurora Services")
            }
        }

        val intent = Intent(PRIVILEGED_EXTENSION_SERVICE_INTENT)
        intent.setPackage(PRIVILEGED_EXTENSION_PACKAGE_NAME)

        context.bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        Handler(Looper.getMainLooper()).postDelayed({
            if (!attachedToServiceTimeout.get()) {
                removeFromInstallQueue(packageName)
            }
        }, 25 * 1000)
    }

    private fun handleCallbackUninstall(packageName: String, returnCode: Int, extra: String?) {
        Log.i("Services Callback : $packageName $returnCode $extra")

        when (returnCode) {
            PackageInstaller.STATUS_SUCCESS -> {
                EventBus.getDefault().post(
                    BusEvent.UninstallEvent(
                        packageName,
                        context.getString(R.string.installer_status_success)
                    )
                )
            }
            else -> {
                val error = AppInstaller.getErrorString(
                    context,
                    returnCode
                )

                postError(packageName, error, extra)
            }
        }
        if (::serviceConnection.isInitialized) {
            context.unbindService(serviceConnection)
        }
    }

    private fun handleCallback(packageName: String, returnCode: Int, extra: String?) {
        Log.i("Services Callback : $packageName $returnCode $extra")

        when (returnCode) {
            PackageInstaller.STATUS_SUCCESS -> {
                EventBus.getDefault().post(
                    InstallerEvent.Success(
                        packageName,
                        context.getString(R.string.installer_status_success)
                    )
                )
            }
            else -> {
                val error = AppInstaller.getErrorString(
                    context,
                    returnCode
                )

                postError(packageName, error, extra)
            }
        }
        if (::serviceConnection.isInitialized) {
            context.unbindService(serviceConnection)
        }
    }

    override fun postError(packageName: String, error: String?, extra: String?) {
        super.postError(packageName, error, extra)
        if (::serviceConnection.isInitialized) {
            context.unbindService(serviceConnection)
        }
    }

    override fun getUri(file: File): Uri {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileProvider",
            file
        )

        context.grantUriPermission(
            PRIVILEGED_EXTENSION_PACKAGE_NAME,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        return uri
    }
}
