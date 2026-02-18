package com.pesatrack.domain.models

/**
 * Domain model for an expense category
 * Supports hierarchical parent-child relationships
 */
data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val color: String,
    val parentId: Long? = null,
    val isGroup: Boolean = false,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0
)

/**
 * Category with its children for hierarchical display
 */
data class CategoryGroup(
    val parent: Category,
    val children: List<Category>
)
