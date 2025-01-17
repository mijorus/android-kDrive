/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.drive.utils

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.fragment.app.FragmentActivity
import androidx.work.*
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.services.PeriodicUploadWorker
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.sync.MediaObserverService
import com.infomaniak.drive.data.sync.MediaObserverWorker
import com.infomaniak.lib.core.utils.SentryLog
import java.util.Date

object SyncUtils {

    val DATE_TAKEN: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.DATE_TAKEN
        else "datetaken"

    inline val Context.uploadFolder get() = java.io.File(cacheDir, UploadWorker.UPLOAD_FOLDER).apply { if (!exists()) mkdirs() }

    fun getFileDates(cursor: Cursor): Pair<Date?, Date> {
        val dateTakenIndex = cursor.getColumnIndex(DATE_TAKEN)
        val dateAddedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

        val lastModifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val dateModifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

        val fileCreatedAt = when {
            cursor.isValidDate(dateTakenIndex) -> Date(cursor.getLong(dateTakenIndex))
            cursor.isValidDate(dateAddedIndex) -> Date(cursor.getLong(dateAddedIndex) * 1000)
            else -> null
        }

        var fileModifiedAt = when {
            cursor.isValidDate(lastModifiedIndex) -> Date(cursor.getLong(lastModifiedIndex))
            cursor.isValidDate(dateModifiedIndex) -> Date(cursor.getLong(dateModifiedIndex) * 1000)
            fileCreatedAt != null -> fileCreatedAt
            else -> null
        }

        if (fileModifiedAt == null || fileModifiedAt.time == 0L) {
            fileModifiedAt = Date()
        }

        return Pair(fileCreatedAt, fileModifiedAt)
    }

    private fun Cursor.isValidDate(index: Int) = index != -1 && this.getLong(index) > 0

    fun FragmentActivity.launchAllUpload(drivePermissions: DrivePermissions) {
        if (AccountUtils.isEnableAppSync() &&
            drivePermissions.checkSyncPermissions(false) &&
            UploadFile.getAllPendingUploads().isNotEmpty()
        ) {
            syncImmediately()
        }
    }

    fun Context.syncImmediately(data: Data = Data.EMPTY, force: Boolean = false) {
        if (!isSyncActive() || force) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(UploadWorker.workConstraints())
                .setExpeditedIfAvailable()
                .setInputData(data)
                .build()
            WorkManager.getInstance(this).enqueueUniqueWork(UploadWorker.TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun Context.isSyncActive(isRunning: Boolean = true): Boolean {
        return WorkManager.getInstance(this).getWorkInfos(
            UploadWorker.uploadWorkerQuery(listOf(if (isRunning) WorkInfo.State.RUNNING else WorkInfo.State.ENQUEUED))
        ).get()?.isNotEmpty() == true
    }

    fun Context.isSyncScheduled(): Boolean {
        return WorkManager.getInstance(this).getWorkInfos(
            UploadWorker.uploadWorkerQuery(listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED))
        ).get()?.isNotEmpty() == true
    }

    private fun Context.isAutoSyncActive(): Boolean {
        return WorkManager.getInstance(this).getWorkInfos(
            WorkQuery.Builder.fromUniqueWorkNames(listOf(PeriodicUploadWorker.TAG))
                .addStates(listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED))
                .build()
        ).get()?.isNotEmpty() == true
    }

    private fun Context.startPeriodicSync(syncInterval: Long) {
        if (!isSyncActive()) {
            PeriodicUploadWorker.scheduleWork(this, syncInterval)
        }
    }

    private fun Context.cancelPeriodicSync() {
        WorkManager.getInstance(this).cancelUniqueWork(UploadWorker.TAG)
        WorkManager.getInstance(this).cancelUniqueWork(PeriodicUploadWorker.TAG)
    }

    fun Context.activateSyncIfNeeded() {
        UploadFile.getAppSyncSettings()?.let { syncSettings ->
            if (!isAutoSyncActive()) {
                // Cancel old period periodic worker
                WorkManager.getInstance(this).cancelUniqueWork(UploadWorker.PERIODIC_TAG)
                // Enable periodic sync
                activateAutoSync(syncSettings)
            }
        }
    }

    fun Context.startContentObserverService() {
        if (UploadFile.getAppSyncSettings()?.syncImmediately == true) {
            SentryLog.d("kDrive", "start content observer!")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) MediaObserverWorker.scheduleWork(this)
            else startService(Intent(applicationContext, MediaObserverService::class.java))
        }
    }

    private fun Context.cancelContentObserver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            MediaObserverWorker.cancelWork(applicationContext)
        } else {
            applicationContext.stopService(Intent(applicationContext, MediaObserverService::class.java))
        }
    }

    fun Context.activateAutoSync(syncSettings: SyncSettings) {
        cancelContentObserver()
        if (syncSettings.syncImmediately) startContentObserverService()
        startPeriodicSync(syncSettings.syncInterval)
    }

    fun Context.disableAutoSync() {
        UploadFile.removeAppSyncSettings()
        cancelContentObserver()
        cancelPeriodicSync()
    }

}
