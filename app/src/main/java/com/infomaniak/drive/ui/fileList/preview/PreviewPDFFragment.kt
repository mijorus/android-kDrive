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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewPdfBinding
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.getPageNumberChip
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.toggleFullscreen
import com.infomaniak.drive.utils.PdfCore
import com.infomaniak.drive.utils.PreviewPDFUtils
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.safeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewPDFFragment : PreviewFragment() {

    private var binding: FragmentPreviewPdfBinding by safeBinding()

    private var previewPDFAdapter: PreviewPDFAdapter? = null
    private val previewPDFViewModel by viewModels<PreviewPDFViewModel>()

    private var pdfCore: PdfCore? = null
    private var isDownloading = false


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewPdfBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.downloadLayout) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile()) return@with

        container.layoutTransition?.setAnimateParentHierarchy(false)

        fileIcon.setImageResource(file.getFileType().icon)
        fileName.text = file.name
        downloadProgress.isVisible = true

        previewDescription.apply {
            setText(R.string.previewDownloadIndication)
            isVisible = true
        }

        root.apply {
            isVisible = true
            setOnClickListener { toggleFullscreen() }
        }

        binding.pdfViewRecycler.apply {
            isGone = true
            onClicked = { toggleFullscreen() }
        }

        bigOpenWithButton.apply {
            isGone = true
            setOnClickListener { openWithClicked() }
        }

        previewPDFViewModel.downloadProgress.observe(viewLifecycleOwner) { progress ->
            if (progress >= 100 && previewPDFViewModel.pdfJob.isCancelled) downloadPdf()
            downloadProgress.progress = progress
        }
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

    override fun onDestroy() {
        pdfCore?.clear()
        super.onDestroy()
    }

    private fun showPdf(pdfCore: PdfCore) = with(binding) {
        lifecycleScope.launchWhenResumed {
            withContext(Dispatchers.Main) {
                downloadLayout.root.isGone = true
                pdfViewRecycler.isVisible = true
                getPageNumberChip()?.isVisible = true
                previewPDFAdapter = PreviewPDFAdapter(pdfCore)
                pdfViewRecycler.adapter = previewPDFAdapter
                previewPDFAdapter?.itemCount?.let { updatePageNumber(totalPage = it) }

                pdfViewRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        val currentPage =
                            (pdfViewRecycler.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition() + 1
                        previewPDFAdapter?.itemCount?.let { updatePageNumber(currentPage = currentPage, totalPage = it) }
                    }
                })
            }
        }
    }

    fun updatePageNumber(currentPage: Int = 1, totalPage: Int) {
        if (currentPage >= 1) {
            getPageNumberChip()?.text = getString(R.string.previewPdfPages, currentPage, totalPage)
        }
    }

    private fun downloadPdf() = with(binding.downloadLayout) {
        if (previewPDFAdapter == null || previewPDFAdapter?.itemCount == 0) {
            previewSliderViewModel.pdfIsDownloading.value = true
            isDownloading = true
            previewPDFViewModel.downloadPdfFile(requireContext(), file, previewSliderViewModel.userDrive)
                .observe(viewLifecycleOwner) { apiResponse ->
                    apiResponse.data?.let { pdfCore ->
                        this@PreviewPDFFragment.pdfCore = pdfCore
                        showPdf(pdfCore)
                    } ?: run {
                        downloadProgress.isGone = true
                        previewDescription.setText(apiResponse.translatedError)
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

        fun downloadPdfFile(context: Context, file: File, userDrive: UserDrive): LiveData<ApiResponse<PdfCore>> {
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
