package com.pesatrack.presentation.screens.category_management

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.database.entities.RuleMatchType
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.CategoryRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Category Management screen.
 * Handles CRUD for custom categories and user-defined auto-categorization rules.
 */
@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val categoryRuleRepository: CategoryRuleRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CategoryMgmtVM"
    }

    private val _uiState = MutableStateFlow(CategoryManagementUiState())
    val uiState: StateFlow<CategoryManagementUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                categoryRepository.getCategoryGroups(),
                categoryRuleRepository.getAllRules(),
                categoryRepository.getAllCategories()
            ) { groups, rules, allCats ->
                Triple(groups, rules, allCats)
            }.collect { (groups, rules, allCats) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categoryGroups = groups,
                        rules = rules,
                        allCategories = allCats
                    )
                }
            }
        }
    }

    // ==================== Dialog State ====================

    fun showAddSubCategory(parentGroupId: Long) {
        val group = _uiState.value.categoryGroups.find { it.parent.id == parentGroupId }?.parent ?: return
        _uiState.update { it.copy(dialogState = CategoryDialogState.AddSubCategory(group)) }
    }

    fun showAddGroup() {
        _uiState.update { it.copy(dialogState = CategoryDialogState.AddGroup) }
    }

    fun showEditCategory(categoryId: Long) {
        val cat = _uiState.value.allCategories.find { it.id == categoryId } ?: return
        _uiState.update { it.copy(dialogState = CategoryDialogState.EditCategory(cat)) }
    }

    fun showDeleteCategory(categoryId: Long) {
        viewModelScope.launch {
            val cat = _uiState.value.allCategories.find { it.id == categoryId } ?: return@launch
            val count = if (cat.isGroup) {
                categoryRepository.getExpenseCountForGroup(categoryId)
            } else {
                categoryRepository.getExpenseCountForCategory(categoryId)
            }
            _uiState.update {
                it.copy(dialogState = CategoryDialogState.ConfirmDelete(cat, count))
            }
        }
    }

    fun showAddRule(preSelectedCategoryId: Long? = null) {
        _uiState.update { it.copy(dialogState = CategoryDialogState.AddRule(preSelectedCategoryId)) }
    }

    fun showEditRule(ruleId: Long) {
        val rule = _uiState.value.rules.find { it.id == ruleId } ?: return
        _uiState.update { it.copy(dialogState = CategoryDialogState.EditRule(rule)) }
    }

    fun showDeleteRule(ruleId: Long) {
        val rule = _uiState.value.rules.find { it.id == ruleId } ?: return
        _uiState.update { it.copy(dialogState = CategoryDialogState.ConfirmDeleteRule(rule)) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = CategoryDialogState.Hidden) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ==================== Category CRUD ====================

    fun addSubCategory(name: String, icon: String, color: String, parentId: Long) {
        viewModelScope.launch {
            try {
                categoryRepository.addCategory(name = name, icon = icon, color = color, parentId = parentId)
                _uiState.update {
                    it.copy(
                        dialogState = CategoryDialogState.Hidden,
                        message = "Category \"$name\" added"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add sub-category", e)
                _uiState.update { it.copy(message = "Failed to add category: ${e.message}") }
            }
        }
    }

    fun addGroup(name: String, icon: String, color: String) {
        viewModelScope.launch {
            try {
                categoryRepository.addCategoryGroup(name = name, icon = icon, color = color)
                _uiState.update {
                    it.copy(
                        dialogState = CategoryDialogState.Hidden,
                        message = "Group \"$name\" added"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add group", e)
                _uiState.update { it.copy(message = "Failed to add group: ${e.message}") }
            }
        }
    }

    fun updateCategory(id: Long, name: String, icon: String, color: String) {
        viewModelScope.launch {
            try {
                categoryRepository.updateCategory(id = id, name = name, icon = icon, color = color)
                _uiState.update {
                    it.copy(
                        dialogState = CategoryDialogState.Hidden,
                        message = "Category updated"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update category", e)
                _uiState.update { it.copy(message = "Failed to update: ${e.message}") }
            }
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            try {
                val cat = _uiState.value.allCategories.find { it.id == id }
                val success = if (cat?.isGroup == true) {
                    categoryRepository.deleteGroup(id)
                } else {
                    categoryRepository.deleteCategory(id)
                }
                if (success) {
                    _uiState.update {
                        it.copy(
                            dialogState = CategoryDialogState.Hidden,
                            message = "Category deleted"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            dialogState = CategoryDialogState.Hidden,
                            message = "Cannot delete — category has expenses assigned"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete category", e)
                _uiState.update { it.copy(message = "Failed to delete: ${e.message}") }
            }
        }
    }

    // ==================== Rule CRUD ====================

    fun addRule(pattern: String, matchType: RuleMatchType, categoryId: Long, priority: Int) {
        viewModelScope.launch {
            try {
                categoryRuleRepository.addRule(
                    pattern = pattern,
                    matchType = matchType,
                    categoryId = categoryId,
                    priority = priority
                )
                _uiState.update {
                    it.copy(
                        dialogState = CategoryDialogState.Hidden,
                        message = "Rule added: \"$pattern\""
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add rule", e)
                _uiState.update { it.copy(message = "Failed to add rule: ${e.message}") }
            }
        }
    }

    fun updateRule(
        id: Long,
        pattern: String,
        matchType: RuleMatchType,
        categoryId: Long,
        priority: Int,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            try {
                categoryRuleRepository.updateRule(
                    id = id,
                    pattern = pattern,
                    matchType = matchType,
                    categoryId = categoryId,
                    priority = priority,
                    isActive = isActive
                )
                _uiState.update {
                    it.copy(
                        dialogState = CategoryDialogState.Hidden,
                        message = "Rule updated"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update rule", e)
                _uiState.update { it.copy(message = "Failed to update rule: ${e.message}") }
            }
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            try {
                categoryRuleRepository.deleteRule(id)
                _uiState.update {
                    it.copy(
                        dialogState = CategoryDialogState.Hidden,
                        message = "Rule deleted"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete rule", e)
                _uiState.update { it.copy(message = "Failed to delete rule: ${e.message}") }
            }
        }
    }
}
