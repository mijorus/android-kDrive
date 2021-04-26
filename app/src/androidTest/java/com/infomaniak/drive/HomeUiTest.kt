/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
package com.infomaniak.drive

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import com.infomaniak.drive.UiTestHelper.APP_PACKAGE
import com.infomaniak.drive.UiTestHelper.DEFAULT_DRIVE_ID
import com.infomaniak.drive.UiTestHelper.DEFAULT_DRIVE_NAME
import com.infomaniak.drive.UiTestHelper.LAUNCH_TIMEOUT
import com.infomaniak.drive.utils.AccountUtils
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeUiTest {

    private lateinit var device: UiDevice

    @Before
    fun startApp() {
        device = UiTestHelper.getDevice()
        device.pressHome()

        val launcherPackage: String = device.launcherPackageName
        assertThat(launcherPackage, notNullValue())
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), 3000)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT)
    }

    @Test
    fun testSwitchDrive() {
        val switchDrive = device.findObject(UiSelector().resourceId("$APP_PACKAGE:id/driveInfos"))
        switchDrive.clickAndWaitForNewWindow()

        val driveRecyclerView = UiCollection(UiSelector().resourceId("$APP_PACKAGE:id/selectionRecyclerView"))

        driveRecyclerView.getChildByInstance(
            UiSelector().resourceId("$APP_PACKAGE:id/driveCard"),
            driveRecyclerView.childCount - 1
        ).clickAndWaitForNewWindow()

        switchDrive.clickAndWaitForNewWindow()

        driveRecyclerView.getChildByText(
            UiSelector().resourceId("$APP_PACKAGE:id/driveCard"),
            DEFAULT_DRIVE_NAME
        ).clickAndWaitForNewWindow()

        assert(AccountUtils.currentDriveId == DEFAULT_DRIVE_ID)
    }
}