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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.databinding.ItemPdfViewBinding
import com.infomaniak.drive.ui.fileList.preview.PreviewPDFAdapter.PreviewPDFViewHolder
import com.infomaniak.drive.utils.PdfCore
import com.infomaniak.lib.core.views.ViewHolder

class PreviewPDFAdapter(private val pdfCore: PdfCore) : RecyclerView.Adapter<PreviewPDFViewHolder>() {

    private var whiteBitmap: Bitmap

    init {
        whiteBitmap = createWhiteBitmap()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewPDFViewHolder {
        return PreviewPDFViewHolder(ItemPdfViewBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: PreviewPDFViewHolder, position: Int): Unit = with(holder.binding) {
        imageView.setImageBitmap(whiteBitmap)
        pdfCore.renderPage(position) { bitmap -> imageView.setImageBitmap(bitmap) }
    }

    private fun createWhiteBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(pdfCore.bitmapWidth, pdfCore.bitmapHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)

        return bitmap
    }

    override fun getItemCount() = pdfCore.getPdfPages()

    class PreviewPDFViewHolder(val binding: ItemPdfViewBinding) : ViewHolder(binding.root)
}
