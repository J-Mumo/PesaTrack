package com.pesatrack.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.CategoryGroup
import com.pesatrack.presentation.theme.getCategoryColor
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Grouped category picker dialog
 * Shows categories organized in expandable groups.
 *
 * When [onCreateCategory] is provided, the picker also shows an inline
 * "Add sub-category" entry at the bottom of every expanded group and an
 * "Add new category group" entry at the very end of the list. Newly-created
 * sub-categories are auto-selected; newly-created groups are auto-expanded so
 * the user can immediately add a sub-category to them.
 *
 * Signature of [onCreateCategory]: `(name, icon, color, parentId, onCreated)`.
 * Pass `parentId = null` for a top-level group; otherwise the new category is
 * created under that parent. The implementation must invoke `onCreated` with
 * the resulting [Category] once the insert completes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupedCategoryPicker(
    categoryGroups: List<CategoryGroup>,
    selectedCategoryId: Long?,
    onCategorySelected: (Category) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onCreateCategory: ((name: String, icon: String, color: String, parentId: Long?, onCreated: (Category) -> Unit) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedGroups by remember { mutableStateOf(setOf<Long>()) }
    // null = no form; (parent, label) = form for sub-category under parent;
    //  Pair(null, "") = form for new top-level group.
    var pendingCreate by remember { mutableStateOf<CreateTarget?>(null) }

    // Find currently selected category's parent to auto-expand
    LaunchedEffect(selectedCategoryId) {
        selectedCategoryId?.let { selectedId ->
            categoryGroups.forEach { group ->
                if (group.children.any { it.id == selectedId }) {
                    expandedGroups = expandedGroups + group.parent.id
                }
            }
        }
    }
    
    // Filter categories based on search
    val filteredGroups = remember(categoryGroups, searchQuery) {
        if (searchQuery.isBlank()) {
            categoryGroups
        } else {
            categoryGroups.mapNotNull { group ->
                val matchingChildren = group.children.filter { 
                    it.name.contains(searchQuery, ignoreCase = true) 
                }
                if (matchingChildren.isNotEmpty() || group.parent.name.contains(searchQuery, ignoreCase = true)) {
                    group.copy(children = matchingChildren.ifEmpty { group.children })
                } else null
            }
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Category",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                
                // Search bar
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                )
                
                // Category list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    filteredGroups.forEach { group ->
                        // Group header
                        item(key = "group_${group.parent.id}") {
                            CategoryGroupHeader(
                                group = group.parent,
                                isExpanded = expandedGroups.contains(group.parent.id),
                                childCount = group.children.size,
                                onClick = {
                                    expandedGroups = if (expandedGroups.contains(group.parent.id)) {
                                        expandedGroups - group.parent.id
                                    } else {
                                        expandedGroups + group.parent.id
                                    }
                                }
                            )
                        }
                        
                        // Children (expanded)
                        item(key = "children_${group.parent.id}") {
                            AnimatedVisibility(
                                visible = expandedGroups.contains(group.parent.id) || searchQuery.isNotBlank(),
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    group.children.forEach { category ->
                                        CategoryChildItem(
                                            category = category,
                                            isSelected = category.id == selectedCategoryId,
                                            onClick = { onCategorySelected(category) }
                                        )
                                    }
                                    // Inline "Add sub-category" entry under each expanded group.
                                    // Hidden while searching to keep results focused.
                                    if (onCreateCategory != null && searchQuery.isBlank()) {
                                        AddCategoryRow(
                                            label = "Add sub-category to ${group.parent.name}",
                                            indented = true,
                                            onClick = {
                                                pendingCreate = CreateTarget(
                                                    parentId = group.parent.id,
                                                    parentName = group.parent.name
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Inline "Add new category group" at the very end of the list.
                    if (onCreateCategory != null && searchQuery.isBlank()) {
                        item(key = "add_group") {
                            AddCategoryRow(
                                label = "Add new category group",
                                indented = false,
                                onClick = { pendingCreate = CreateTarget(parentId = null, parentName = null) }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingCreate?.let { target ->
        CategoryFormDialog(
            title = if (target.parentId == null) "Add Category Group" else "Add Sub-Category",
            subtitle = if (target.parentName != null) "Under ${target.parentName}" else "New top-level group",
            onDismiss = { pendingCreate = null },
            onConfirm = { name, icon, color ->
                val cb = onCreateCategory ?: return@CategoryFormDialog
                pendingCreate = null
                cb(name, icon, color, target.parentId) { created ->
                    if (created.parentId != null) {
                        // Auto-select newly created sub-category and dismiss the picker.
                        onCategorySelected(created)
                    } else {
                        // Newly created group: expand it so the user can add sub-categories.
                        expandedGroups = expandedGroups + created.id
                    }
                }
            }
        )
    }
}

private data class CreateTarget(val parentId: Long?, val parentName: String?)

@Composable
private fun AddCategoryRow(
    label: String,
    indented: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (indented) 24.dp else 0.dp,
                top = 2.dp,
                bottom = 2.dp,
                end = 4.dp
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search categories...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryGroupHeader(
    group: Category,
    isExpanded: Boolean,
    childCount: Int,
    onClick: () -> Unit
) {
    val categoryColor = getCategoryColor(group.color)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(categoryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(group.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Name and count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$childCount categories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Expand/collapse icon
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryChildItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val categoryColor = getCategoryColor(category.color)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 1.dp, bottom = 1.dp, end = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = if (isSelected) {
            categoryColor.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(categoryColor)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Name
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // Selected indicator
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Compact category display button that opens the picker
 */
@Composable
fun CategorySelector(
    selectedCategory: Category?,
    categoryGroups: List<CategoryGroup>,
    onCategorySelected: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    
    // Find parent name for display
    val parentName = remember(selectedCategory, categoryGroups) {
        selectedCategory?.parentId?.let { parentId ->
            categoryGroups.find { it.parent.id == parentId }?.parent?.name
        }
    }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showPicker = true },
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedCategory != null) {
                val categoryColor = getCategoryColor(selectedCategory.color)
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(categoryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(selectedCategory.icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedCategory.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (parentName != null) {
                        Text(
                            text = parentName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Filled.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "Select a category",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    if (showPicker) {
        GroupedCategoryPicker(
            categoryGroups = categoryGroups,
            selectedCategoryId = selectedCategory?.id,
            onCategorySelected = { category ->
                onCategorySelected(category)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}
