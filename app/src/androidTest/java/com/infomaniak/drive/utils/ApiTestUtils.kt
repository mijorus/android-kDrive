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

import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.CreateFile
import com.infomaniak.drive.data.models.File
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.parcel.RawValue
import org.junit.Assert
import java.util.*

object ApiTestUtils {

    fun assertApiResponse(response: ApiResponse<*>, dataShouldNotBeNull: Boolean = true) {
        Assert.assertTrue("This should succeed", response.isSuccess())
        Assert.assertNull("There should be no error", response.error)
        if (dataShouldNotBeNull) Assert.assertNotNull("The data cannot be null", response.data)
    }

    fun deleteTestFile(remoteFile: File) {
        val deleteResponse = ApiRepository.deleteFile(remoteFile)
        Assert.assertTrue("created file couldn't be deleted from the remote", deleteResponse.isSuccess())
    }

    fun createFileForTest(): @RawValue File {
        val createFile = CreateFile("offline doc ${UUID.randomUUID()}", File.Office.DOCS.extension)
        val apiResponse = ApiRepository.createOfficeFile(Env.DRIVE_ID, Utils.ROOT_ID, createFile)
        assertApiResponse(apiResponse)
        return apiResponse.data!!
    }
}