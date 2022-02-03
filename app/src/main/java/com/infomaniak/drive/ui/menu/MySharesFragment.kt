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
package com.infomaniak.drive.ui.menu

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_file_list.*

class MySharesFragment : FileSubTypeListFragment() {

    override var hideBackButtonWhenRoot: Boolean = false
    override var allowCancellation: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (folderId == ROOT_ID) {
            downloadFiles = DownloadFiles()
            folderId = OTHER_ROOT_ID
        }
        setNoFilesLayout = SetNoFilesLayout()
        super.onViewCreated(view, savedInstanceState)

        collapsingToolbarLayout.title = getString(R.string.mySharesTitle)

        fileAdapter.onFileClicked = { file ->
            fileListViewModel.cancelDownloadFiles()
            if (file.isFolder()) safeNavigate(
                MySharesFragmentDirections.actionMySharesFragmentSelf(
                    file.id,
                    file.name
                )
            ) else {
                val fileList = fileAdapter.getFileObjectsList(mainViewModel.realm)
                Utils.displayFile(mainViewModel, findNavController(), file, fileList)
            }
        }
    }

    private inner class SetNoFilesLayout : () -> Unit {
        override fun invoke() {
            noFilesLayout.setup(
                icon = R.drawable.ic_share,
                title = R.string.mySharesNoFile,
                initialListView = fileRecyclerView
            )
        }
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            fileAdapter.isComplete = false

            fileListViewModel.getMySharedFiles(fileListViewModel.sortType).observe(viewLifecycleOwner) {
                // forceClean because myShares is not paginated
                populateFileList(
                    files = it?.first ?: ArrayList(),
                    folderId = FileController.MY_SHARES_FILE_ID,
                    forceClean = true,
                    isComplete = true,
                    realm = mainViewModel.realm,
                    isNewSort = isNewSort
                )
            }
        }
    }
}
