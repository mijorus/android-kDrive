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
package com.infomaniak.drive.ui.fileList.preview

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.getPageNumberChip
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.toggleFullscreen
import com.infomaniak.drive.utils.PreviewPDFUtils
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.fragment_preview_others.*
import kotlinx.android.synthetic.main.fragment_preview_pdf.container
import kotlinx.android.synthetic.main.fragment_preview_pdf.downloadLayout
import kotlinx.android.synthetic.main.fragment_preview_pdf.pdfView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewPDFFragment : PreviewFragment() {

    private val previewPDFViewModel by viewModels<PreviewPDFViewModel>()

    private var pdfFile: java.io.File? = null
    private var isDownloading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preview_pdf, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile()) return

        container?.layoutTransition?.setAnimateParentHierarchy(false)

        fileIcon.setImageResource(file.getFileType().icon)
        fileName.text = file.name
        downloadProgress.isVisible = true

        previewDescription.apply {
            setText(R.string.previewDownloadIndication)
            isVisible = true
        }

        downloadLayout.isVisible = true
        pdfView.isGone = true

        bigOpenWithButton.apply {
            isGone = true
            setOnClickListener { openWithClicked() }
        }

        previewPDFViewModel.downloadProgress.observe(viewLifecycleOwner) { progress ->
            if (progress >= 100 && previewPDFViewModel.pdfJob.isCancelled) downloadPdf()
            downloadProgress.progress = progress
        }

        pdfView.setOnClickListener { toggleFullscreen() }
        downloadLayout.setOnClickListener { toggleFullscreen() }
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        if (menuVisible) {
            if (!isDownloading) downloadPdf() else previewSliderViewModel.pdfIsDownloading.value = isDownloading
        }
    }

    override fun onPause() {
        previewPDFViewModel.cancelJobs()
        super.onPause()
    }

    private fun showPdf(file: java.io.File) = lifecycleScope.launchWhenResumed {
        withContext(Dispatchers.Main) {
            downloadLayout.isGone = true
            pdfView.isVisible = true
            pdfView.fromFile(file)
                .enableAnnotationRendering(true)
                .scrollHandle(DefaultScrollHandle(requireContext()))
                .spacing(16)
                .swipeHorizontal(false)
                .onLoad { pageCount -> updatePageNumber(totalPage = pageCount) }
                .onPageChange { currentPage, pageCount ->
                    updatePageNumber(currentPage = currentPage, totalPage = pageCount)
                }
                .load()

            getPageNumberChip()?.isVisible = true
        }
    }

    private fun updatePageNumber(currentPage: Int = 1, totalPage: Int) {
        if (currentPage >= 1) {
            getPageNumberChip()?.text = getString(R.string.previewPdfPages, currentPage, totalPage)
        }
    }

    private fun downloadPdf() {
        if (pdfFile == null) {
            previewSliderViewModel.pdfIsDownloading.value = true
            isDownloading = true
            previewPDFViewModel.downloadPdfFile(requireContext(), file, previewSliderViewModel.userDrive)
                .observe(viewLifecycleOwner) { apiResponse ->
                    apiResponse.data?.let { pdfFile ->
                        this.pdfFile = pdfFile
                        showPdf(pdfFile)
                    } ?: run {
                        downloadProgress.isGone = true
                        previewDescription.setText(R.string.previewNoPreview)
                        bigOpenWithButton.isVisible = true
                    }
                    previewSliderViewModel.pdfIsDownloading.value = false
                    isDownloading = false
                }
        }
    }

    class PreviewPDFViewModel(app: Application) : AndroidViewModel(app) {
        var pdfJob = Job()
        val downloadProgress = MutableLiveData<Int>()

        fun downloadPdfFile(context: Context, file: File, userDrive: UserDrive): LiveData<ApiResponse<java.io.File>> {
            pdfJob.cancel()
            pdfJob = Job()
            return liveData(Dispatchers.IO + pdfJob) {
                val pdfCore = PreviewPDFUtils.convertPdfFileToPdfCore(context, file, userDrive) {
                    viewModelScope.launch(Dispatchers.Main) {
                        downloadProgress.value = it
                    }
                }
                emit(pdfCore)
            }
        }

        fun cancelJobs() {
            pdfJob.cancel()
        }

        override fun onCleared() {
            cancelJobs()
            super.onCleared()
        }
    }
}
