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

import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.infomaniak.drive.KDriveTest
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.fileList.FileViewHolder
import de.mannodermaus.junit5.ActivityScenarioExtension
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension

open class KDriveUiTest : KDriveTest() {

    @JvmField
    @RegisterExtension
    val activityExtension = ActivityScenarioExtension.launch<MainActivity>()

    var device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun getViewIdentifier(id: String) = "$APP_PACKAGE:id/$id"

    @BeforeEach
    open fun startApp() {
        // Close the bottomSheetModal displayed because it's the user's first connection
        closeBottomSheetInfoModalIfDisplayed()
    }

    fun createPrivateFolder(folderName: String) {
        getDeviceViewById("mainFab").clickAndWaitForNewWindow()

        UiCollection(UiSelector().resourceId(getViewIdentifier("addFileBottomSheetLayout"))).getChildByText(
            UiSelector().resourceId(getViewIdentifier("folderCreateText")),
            context.getString(R.string.allFolder)
        ).click()

        UiCollection(UiSelector().resourceId(getViewIdentifier("newFolderLayout"))).getChildByText(
            UiSelector().resourceId(getViewIdentifier("privateFolder")), context.getString(R.string.allFolder)
        ).click()

        UiCollection(UiSelector().resourceId(getViewIdentifier("createFolderLayout"))).apply {
            getChildByInstance(UiSelector().resourceId(getViewIdentifier("folderNameValueInput")), 0).text = folderName
            val permissionsRecyclerView = UiScrollable(UiSelector().resourceId(getViewIdentifier("permissionsRecyclerView")))
            permissionsRecyclerView.getChildByText(
                UiSelector().resourceId(getViewIdentifier("permissionCard")),
                context.getString(R.string.createFolderMeOnly)
            ).run {
                permissionsRecyclerView.scrollIntoView(this)
                click()
            }
            getChildByText(
                UiSelector().resourceId(getViewIdentifier("createFolderButton")),
                context.getString(R.string.buttonCreateFolder)
            ).clickAndWaitForNewWindow()
        }
    }

    fun deleteFile(fileName: String) {
        openFileListItemMenu(fileName)
        UiScrollable(UiSelector().resourceId(getViewIdentifier("scrollView"))).apply {
            scrollForward()
            getChild(UiSelector().resourceId(getViewIdentifier("deleteFile"))).click()
        }
        device.findObject(UiSelector().text(context.getString(R.string.buttonMove))).clickAndWaitForNewWindow()
    }

    fun openFileShareDetails(fileName: String) {
        openFileListItemMenu(fileName)
        getDeviceViewById("fileRights").clickAndWaitForNewWindow()
    }

    fun getDeviceViewById(id: String): UiObject = device.findObject(UiSelector().resourceId(getViewIdentifier(id)))

    fun findFileIfInList(fileName: String, mustBeInList: Boolean) {
        if (mustBeInList) {
            // Try to scroll to the file
            onView(withResourceName("fileRecyclerView")).perform(
                RecyclerViewActions.scrollTo<FileViewHolder>(hasDescendant(withText(fileName)))
            )
            // Assert the file is displayed
            onView(withText(fileName)).check(matches(isDisplayed()))
        } else {
            // Assert the file does not exists in view hierarchy
            onView(withText(fileName)).check(doesNotExist())
        }
    }

    fun createPublicShareLink(recyclerView: UiScrollable) {
        recyclerView.getChildByText(
            UiSelector().resourceId(getViewIdentifier("permissionTitle")),
            context.getString(R.string.shareLinkPublicRightTitle)
        ).click()
        device.apply {
            findObject(UiSelector().resourceId(getViewIdentifier("saveButton"))).clickAndWaitForNewWindow()
            findObject(UiSelector().resourceId(getViewIdentifier("shareLinkButton"))).clickAndWaitForNewWindow()
        }
    }

    fun selectDriveInList(instance: Int) {
        UiCollection(UiSelector().resourceId(getViewIdentifier("selectRecyclerView"))).getChildByInstance(
            UiSelector().resourceId(getViewIdentifier("itemSelectText")), instance
        ).clickAndWaitForNewWindow()
    }

    fun switchToDriveInstance(instanceNumero: Int) {
        getDeviceViewById("homeFragment").clickAndWaitForNewWindow()
        if (!DriveInfosController.hasSingleDrive(AccountUtils.currentUserId)) {
            getDeviceViewById("switchDriveButton").clickAndWaitForNewWindow()
            selectDriveInList(instanceNumero) // Switch to dev test drive
            // Close the bottomSheet modal displayed to have info on categories
            closeBottomSheetInfoModalIfDisplayed()
        }
    }


    fun closeBottomSheetInfoModalIfDisplayed() {
        try {
            onView(withId(R.id.secondaryActionButton)).perform(click())
        } catch (exception: NoMatchingViewException) {
            try {
                onView(withId(R.id.actionButton)).perform(click())
            } catch (noMatchingException: NoMatchingViewException) {
                // Continue if bottomSheet are not displayed
            }
        } catch (loseFocusException: RuntimeException) {
            // if the focus was lost by root
            pressBack()
        }
    }

    /**
     * Checks if a view is visible by the user
     * Search for the first view matching the given [viewId], [stringRes] (or both)
     *
     * @param isVisible True if the view must be displayed to the user
     * @param viewId Id of the targeted view
     * @param stringRes Id of the text inside the targeted view
     */
    fun checkViewVisibility(isVisible: Boolean, @IdRes viewId: Int? = null, @StringRes stringRes: Int? = null) {
        val matchers = allOf(arrayListOf<Matcher<View>>().apply {
            viewId?.let { add(withId(it)) }
            stringRes?.let { add(withText(it)) }
            Assertions.assertFalse(isEmpty())
        })
        onView(matchers.first()).check(matches(withEffectiveVisibility(if (isVisible) Visibility.VISIBLE else Visibility.GONE)))
    }

    private fun Matcher<View?>.first(): Matcher<View?> {
        return object : TypeSafeMatcher<View?>() {
            var isFirst = true

            override fun describeTo(description: Description) {
                this@first.describeTo(description.appendText(" at first index"))
            }

            override fun matchesSafely(view: View?): Boolean =
                (this@first.matches(view) && isFirst).also { if (it) isFirst = false }
        }
    }

    private fun openFileListItemMenu(fileName: String) {
        findFileIfInList(fileName, true)
        onView(
            allOf(
                withResourceName("menuButton"),
                isDescendantOfA(allOf(withResourceName("endIconLayout"), hasSibling(hasDescendant(withText(fileName)))))
            )
        ).perform(click())
    }
}