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
package com.infomaniak.drive.ui.fileList.categories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.shape.CornerFamily
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.bottomSheetDialogs.CategoryInfoActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter.UICategory
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.DebouncingTextWatcher
import kotlinx.android.synthetic.main.fragment_select_categories.*
import kotlinx.android.synthetic.main.item_search_view.*

class SelectCategoriesFragment : Fragment() {

    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()
    private val navigationArgs: SelectCategoriesFragmentArgs by navArgs()

    private lateinit var categoriesAdapter: CategoriesAdapter
    private lateinit var file: File

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_select_categories, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        file = FileController.getFileById(navigationArgs.fileId) ?: run {
            findNavController().popBackStack()
            return
        }

        DriveInfosController.getCategoryRights().let {
            setCategoriesAdapter(it?.canEditCategory == true, it?.canDeleteCategory == true)
            setAddCategoryButton(it?.canCreateCategory == true)
        }

        searchView.hint = getString(R.string.searchTitle)

        setListeners()
        setBackActionHandlers()
    }

    private fun setCategoriesAdapter(canEditCategory: Boolean, canDeleteCategory: Boolean) {
        categoriesAdapter = CategoriesAdapter(
            onCategoryChanged = { categoryId, isSelected ->
                if (isSelected) addCategory(categoryId) else removeCategory(categoryId)
            }
        ).apply {
            this.canEditCategory = canEditCategory
            this.canDeleteCategory = canDeleteCategory

            val uiCategories = DriveInfosController.getCurrentDriveCategories().map { category ->
                val fileCategory = file.categories.find { it.id == category.id }
                UICategory(
                    id = category.id,
                    name = category.getName(requireContext()),
                    color = category.color,
                    isPredefined = category.isPredefined,
                    isSelected = fileCategory != null,
                    userUsageCount = category.userUsageCount,
                    addedToFileAt = fileCategory?.addedToFileAt,
                )
            }
            setItems(uiCategories.sortCategoriesList())

            onMenuClicked = { category ->
                safeNavigate(
                    R.id.categoryInfoActionsBottomSheetDialog, bundleOf(
                        "fileId" to file.id,
                        "categoryId" to category.id,
                        "categoryName" to category.name,
                        "categoryColor" to category.color,
                        "categoryIsPredefined" to category.isPredefined,
                    )
                )
            }
        }

        categoriesRecyclerView.adapter = categoriesAdapter
    }

    private fun setAddCategoryButton(canCreateCategory: Boolean) {
        toolbar.menu.findItem(R.id.addCategory).isVisible = canCreateCategory
    }

    private fun setListeners() {
        toolbar.apply {
            setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == R.id.addCategory) {
                    navigateToCreateCategory()
                    true
                } else false
            }
            setNavigationOnClickListener {
                setBackNavigationResult(
                    SELECT_CATEGORIES_NAV_KEY,
                    categoriesAdapter.allCategories.filter { it.isSelected }.map { it.id },
                )
            }
        }

        createCategoryRow.setOnClickListener { navigateToCreateCategory() }

        searchView.apply {
            clearButton.setOnClickListener { text = null }
            addTextChangedListener(DebouncingTextWatcher(lifecycle) {
                if (isAtLeastResumed()) {
                    clearButton.isInvisible = it.isNullOrEmpty()
                    categoriesAdapter.updateFilter(text.toString())
                    handleCreateCategoryRow(it?.trim())
                }
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (EditorInfo.IME_ACTION_SEARCH == actionId) {
                    categoriesAdapter.updateFilter(text.toString())
                    true
                } else false
            }
        }
    }

    private fun setBackActionHandlers() {
        getBackNavigationResult<Int>(CategoryInfoActionsBottomSheetDialog.DELETE_CATEGORY_NAV_KEY) { categoryId ->
            categoriesAdapter.deleteCategory(categoryId)
        }
    }

    private fun navigateToCreateCategory() {
        safeNavigate(
            SelectCategoriesFragmentDirections.actionSelectCategoriesFragmentToCreateOrEditCategoryFragment(
                fileId = file.id,
                categoryId = CreateOrEditCategoryFragment.CREATE_CATEGORY_ID,
                categoryName = searchView.text.toString(),
                categoryColor = null,
            )
        )
    }

    private fun handleCreateCategoryRow(categoryName: String?) {
        val text = getString(R.string.manageCategoriesCreateTitle, "<b>$categoryName</b>")
        addCategoryTitle.text = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)

        createCategoryRow.apply {
            val bottomCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)
            var topCornerRadius = 0.0f

            if (categoriesAdapter.filteredCategories.isEmpty()) {
                topCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)
                createCategoryRowSeparator.isGone = true
            } else createCategoryRowSeparator.isVisible = true

            shapeAppearanceModel = shapeAppearanceModel
                .toBuilder()
                .setTopLeftCorner(CornerFamily.ROUNDED, topCornerRadius)
                .setTopRightCorner(CornerFamily.ROUNDED, topCornerRadius)
                .setBottomLeftCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                .setBottomRightCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                .build()

            isVisible = categoryName?.isNotBlank() == true && !categoriesAdapter.doesCategoryExist(categoryName)
        }
    }

    private fun addCategory(categoryId: Int) {
        selectCategoriesViewModel.addCategory(file, categoryId).observe(viewLifecycleOwner) { apiResponse ->
            val isSelected = if (apiResponse.isSuccess()) {
                true
            } else {
                Utils.showSnackbar(requireView(), apiResponse.translateError())
                false
            }
            categoriesAdapter.selectCategory(categoryId, isSelected)
        }
    }

    private fun removeCategory(categoryId: Int) {
        selectCategoriesViewModel.removeCategory(file, categoryId).observe(viewLifecycleOwner) { apiResponse ->
            val isSelected = if (apiResponse.isSuccess()) {
                false
            } else {
                Utils.showSnackbar(requireView(), apiResponse.translateError())
                true
            }
            categoriesAdapter.selectCategory(categoryId, isSelected)
        }
    }

    private fun isAtLeastResumed(): Boolean {
        return lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    companion object {
        const val SELECT_CATEGORIES_NAV_KEY = "select_categories_nav_key"
    }
}