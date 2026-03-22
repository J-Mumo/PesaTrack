package com.pesatrack.presentation.screens.category_management

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.data.local.database.entities.CategoryRuleEntity
import com.pesatrack.data.local.database.entities.RuleMatchType
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.CategoryGroup
import com.pesatrack.presentation.components.getCategoryIcon
import com.pesatrack.presentation.theme.getCategoryColor

// ==================== Preset color palette ====================

private val PRESET_COLORS = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
    "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
    "#8BC34A", "#CDDC39", "#FFC107", "#FF9800", "#FF5722",
    "#795548", "#607D8B", "#9E9E9E", "#006064", "#1B5E20"
)

// ==================== Preset icon names ====================

private val PRESET_ICONS = listOf(
    "category", "label", "star", "favorite", "shopping_bag",
    "restaurant", "local_cafe", "fitness_center", "work", "business",
    "home", "apartment", "school", "local_hospital", "pets",
    "directions_car", "flight", "commute", "account_balance", "savings",
    "volunteer_activism", "sports_esports", "music_note", "palette", "build",
    "local_gas_station", "local_shipping", "phone_android", "computer", "cloud"
)

// ==================== Main Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Show snackbar for messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Categories") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) viewModel.showAddGroup()
                    else viewModel.showAddRule()
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab row: Categories | Rules
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Categories") },
                        icon = { Icon(Icons.Filled.Category, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Auto-Rules (${uiState.rules.size})") },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) }
                    )
                }

                when (selectedTab) {
                    0 -> CategoryListTab(
                        groups = uiState.categoryGroups,
                        onAddSubCategory = viewModel::showAddSubCategory,
                        onEditCategory = viewModel::showEditCategory,
                        onDeleteCategory = viewModel::showDeleteCategory
                    )
                    1 -> RulesListTab(
                        rules = uiState.rules,
                        allCategories = uiState.allCategories,
                        onEditRule = viewModel::showEditRule,
                        onDeleteRule = viewModel::showDeleteRule
                    )
                }
            }
        }
    }

    // Dialogs
    when (val dialog = uiState.dialogState) {
        is CategoryDialogState.Hidden -> { /* no dialog */ }

        is CategoryDialogState.AddSubCategory -> {
            CategoryFormDialog(
                title = "Add Sub-Category",
                subtitle = "Under ${dialog.parentGroup.name}",
                onDismiss = viewModel::dismissDialog,
                onConfirm = { name, icon, color ->
                    viewModel.addSubCategory(name, icon, color, dialog.parentGroup.id)
                }
            )
        }

        is CategoryDialogState.AddGroup -> {
            CategoryFormDialog(
                title = "Add Category Group",
                subtitle = "New top-level group",
                onDismiss = viewModel::dismissDialog,
                onConfirm = { name, icon, color ->
                    viewModel.addGroup(name, icon, color)
                }
            )
        }

        is CategoryDialogState.EditCategory -> {
            CategoryFormDialog(
                title = if (dialog.category.isGroup) "Edit Group" else "Edit Category",
                subtitle = dialog.category.name,
                initialName = dialog.category.name,
                initialIcon = dialog.category.icon,
                initialColor = dialog.category.color,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { name, icon, color ->
                    viewModel.updateCategory(dialog.category.id, name, icon, color)
                }
            )
        }

        is CategoryDialogState.ConfirmDelete -> {
            DeleteConfirmDialog(
                categoryName = dialog.category.name,
                isGroup = dialog.category.isGroup,
                isDefault = dialog.category.isDefault,
                expenseCount = dialog.expenseCount,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { viewModel.deleteCategory(dialog.category.id) }
            )
        }

        is CategoryDialogState.AddRule -> {
            RuleFormDialog(
                title = "Add Auto-Rule",
                categoryGroups = uiState.categoryGroups,
                preSelectedCategoryId = dialog.preSelectedCategoryId,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { pattern, matchType, categoryId, priority ->
                    viewModel.addRule(pattern, matchType, categoryId, priority)
                }
            )
        }

        is CategoryDialogState.EditRule -> {
            RuleFormDialog(
                title = "Edit Auto-Rule",
                categoryGroups = uiState.categoryGroups,
                initialPattern = dialog.rule.pattern,
                initialMatchType = try { RuleMatchType.valueOf(dialog.rule.matchType) } catch (_: Exception) { RuleMatchType.CONTAINS },
                preSelectedCategoryId = dialog.rule.categoryId,
                initialPriority = dialog.rule.priority,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { pattern, matchType, categoryId, priority ->
                    viewModel.updateRule(
                        dialog.rule.id, pattern, matchType, categoryId, priority,
                        dialog.rule.isActive
                    )
                }
            )
        }

        is CategoryDialogState.ConfirmDeleteRule -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text("Delete Rule?") },
                text = {
                    Text("Delete auto-categorization rule for \"${dialog.rule.pattern}\"?")
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deleteRule(dialog.rule.id) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// ==================== Categories Tab ====================

@Composable
private fun CategoryListTab(
    groups: List<CategoryGroup>,
    onAddSubCategory: (Long) -> Unit,
    onEditCategory: (Long) -> Unit,
    onDeleteCategory: (Long) -> Unit
) {
    var expandedGroups by remember { mutableStateOf(setOf<Long>()) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        groups.forEach { group ->
            // Group header
            item(key = "group_${group.parent.id}") {
                CategoryGroupRow(
                    group = group.parent,
                    childCount = group.children.size,
                    isExpanded = expandedGroups.contains(group.parent.id),
                    onToggle = {
                        expandedGroups = if (expandedGroups.contains(group.parent.id)) {
                            expandedGroups - group.parent.id
                        } else {
                            expandedGroups + group.parent.id
                        }
                    },
                    onEdit = { onEditCategory(group.parent.id) },
                    onDelete = if (!group.parent.isDefault) {
                        { onDeleteCategory(group.parent.id) }
                    } else null
                )
            }

            // Children
            item(key = "children_${group.parent.id}") {
                AnimatedVisibility(
                    visible = expandedGroups.contains(group.parent.id),
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        group.children.forEach { child ->
                            CategoryChildRow(
                                category = child,
                                onEdit = { onEditCategory(child.id) },
                                onDelete = if (!child.isDefault) {
                                    { onDeleteCategory(child.id) }
                                } else null
                            )
                        }
                        // Add sub-category button
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 40.dp, top = 2.dp, bottom = 2.dp, end = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAddSubCategory(group.parent.id) },
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier 
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.AddCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Add sub-category",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryGroupRow(
    group: Category,
    childCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val categoryColor = getCategoryColor(group.color)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onToggle,
                onLongClick = onEdit
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!group.isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.height(16.dp)
                        ) {
                            Text(
                                text = "CUSTOM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "$childCount categories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Edit button
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Delete button (only for custom groups)
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryChildRow(
    category: Category,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val categoryColor = getCategoryColor(category.color)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 1.dp, bottom = 1.dp, end = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onEdit,
                onLongClick = { onDelete?.invoke() }
            ),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(categoryColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (!category.isDefault) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.height(16.dp)
                ) {
                    Text(
                        text = "CUSTOM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ==================== Rules Tab ====================

@Composable
private fun RulesListTab(
    rules: List<CategoryRuleEntity>,
    allCategories: List<Category>,
    onEditRule: (Long) -> Unit,
    onDeleteRule: (Long) -> Unit
) {
    if (rules.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No auto-categorization rules yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap + to create a rule that automatically\nassigns categories to recipients",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(rules, key = { it.id }) { rule ->
                val categoryName = allCategories.find { it.id == rule.categoryId }?.name ?: "Unknown"
                val parentName = allCategories.find { it.id == rule.categoryId }?.parentId?.let { pid ->
                    allCategories.find { it.id == pid }?.name
                }
                RuleCard(
                    rule = rule,
                    categoryName = categoryName,
                    groupName = parentName,
                    onEdit = { onEditRule(rule.id) },
                    onDelete = { onDeleteRule(rule.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleCard(
    rule: CategoryRuleEntity,
    categoryName: String,
    groupName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEdit,
                onLongClick = onDelete
            )
            .then(if (!rule.isActive) Modifier.alpha(0.5f) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\"${rule.pattern}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(18.dp)
                    ) {
                        Text(
                            text = rule.matchType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "→ ${groupName?.let { "$it > " } ?: ""}$categoryName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (rule.priority > 0) {
                    Text(
                        text = "Priority: ${rule.priority}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ==================== Category Form Dialog ====================

@Composable
private fun CategoryFormDialog(
    title: String,
    subtitle: String,
    initialName: String = "",
    initialIcon: String = "category",
    initialColor: String = "#9E9E9E",
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Icon picker button
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showIconPicker = !showIconPicker }
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getCategoryIcon(selectedIcon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(selectedIcon, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        if (showIconPicker) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Icon grid
                AnimatedVisibility(visible = showIconPicker) {
                    IconPickerGrid(
                        selectedIcon = selectedIcon,
                        onIconSelected = {
                            selectedIcon = it
                            showIconPicker = false
                        }
                    )
                }

                // Color picker button
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showColorPicker = !showColorPicker }
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(getCategoryColor(selectedColor))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(selectedColor, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        if (showColorPicker) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Color palette
                AnimatedVisibility(visible = showColorPicker) {
                    ColorPickerGrid(
                        selectedColor = selectedColor,
                        onColorSelected = {
                            selectedColor = it
                            showColorPicker = false
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), selectedIcon, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==================== Icon Picker ====================

@Composable
private fun IconPickerGrid(
    selectedIcon: String,
    onIconSelected: (String) -> Unit
) {
    Column {
        PRESET_ICONS.chunked(6).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { iconName ->
                    val isSelected = iconName == selectedIcon
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { onIconSelected(iconName) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = getCategoryIcon(iconName),
                                contentDescription = iconName,
                                modifier = Modifier.size(22.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                // Fill remaining slots with empty space
                repeat(6 - row.size) {
                    Spacer(modifier = Modifier.size(44.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ==================== Color Picker ====================

@Composable
private fun ColorPickerGrid(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    Column {
        PRESET_COLORS.chunked(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { colorHex ->
                    val color = getCategoryColor(colorHex)
                    val isSelected = colorHex == selectedColor
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorSelected(colorHex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ==================== Delete Confirm Dialog ====================

@Composable
private fun DeleteConfirmDialog(
    categoryName: String,
    isGroup: Boolean,
    isDefault: Boolean,
    expenseCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${if (isGroup) "Group" else "Category"}?") },
        text = {
            Column {
                if (isDefault) {
                    Text(
                        text = "\"$categoryName\" is a default category and cannot be deleted.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (expenseCount > 0) {
                    Text(
                        text = "\"$categoryName\" has $expenseCount expense${if (expenseCount != 1) "s" else ""} assigned. " +
                                "You must re-categorize those expenses before deleting.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Delete \"$categoryName\"? ${if (isGroup) "This will also delete all its sub-categories." else ""}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            if (!isDefault && expenseCount == 0) {
                TextButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isDefault || expenseCount > 0) "OK" else "Cancel")
            }
        }
    )
}

// ==================== Rule Form Dialog ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleFormDialog(
    title: String,
    categoryGroups: List<CategoryGroup>,
    initialPattern: String = "",
    initialMatchType: RuleMatchType = RuleMatchType.CONTAINS,
    preSelectedCategoryId: Long? = null,
    initialPriority: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (pattern: String, matchType: RuleMatchType, categoryId: Long, priority: Int) -> Unit
) {
    var pattern by remember { mutableStateOf(initialPattern) }
    var matchType by remember { mutableStateOf(initialMatchType) }
    var selectedCategoryId by remember { mutableStateOf(preSelectedCategoryId) }
    var priority by remember { mutableStateOf(initialPriority.toString()) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var expandedMatchType by remember { mutableStateOf(false) }

    // Find selected category name for display
    val selectedCategoryName = remember(selectedCategoryId, categoryGroups) {
        selectedCategoryId?.let { id ->
            categoryGroups.flatMap { it.children }.find { it.id == id }?.name ?: "Unknown"
        } ?: "Select category..."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Pattern field
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Recipient Pattern") },
                    placeholder = { Text("e.g. JAVA HOUSE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Match type dropdown
                Text("Match Type", style = MaterialTheme.typography.labelMedium)
                ExposedDropdownMenuBox(
                    expanded = expandedMatchType,
                    onExpandedChange = { expandedMatchType = it }
                ) {
                    OutlinedTextField(
                        value = matchType.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMatchType) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedMatchType,
                        onDismissRequest = { expandedMatchType = false }
                    ) {
                        RuleMatchType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(type.name)
                                        Text(
                                            text = when (type) {
                                                RuleMatchType.EXACT -> "Full name must match"
                                                RuleMatchType.CONTAINS -> "Name contains pattern"
                                                RuleMatchType.STARTS_WITH -> "Name starts with pattern"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    matchType = type
                                    expandedMatchType = false
                                }
                            )
                        }
                    }
                }

                // Category picker
                Text("Assign to Category", style = MaterialTheme.typography.labelMedium)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showCategoryPicker = true },
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedCategoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedCategoryId != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Priority field
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it.filter { c -> c.isDigit() } },
                    label = { Text("Priority (0 = default)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedCategoryId?.let { catId ->
                        onConfirm(
                            pattern.trim(),
                            matchType,
                            catId,
                            priority.toIntOrNull() ?: 0
                        )
                    }
                },
                enabled = pattern.isNotBlank() && selectedCategoryId != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Category picker dialog
    if (showCategoryPicker) {
        RuleCategoryPickerDialog(
            categoryGroups = categoryGroups,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = { cat ->
                selectedCategoryId = cat.id
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

// ==================== Category Picker for Rules ====================

@Composable
private fun RuleCategoryPickerDialog(
    categoryGroups: List<CategoryGroup>,
    selectedCategoryId: Long?,
    onCategorySelected: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var expandedGroups by remember { mutableStateOf(setOf<Long>()) }

    // Auto-expand parent of selected category
    LaunchedEffect(selectedCategoryId) {
        selectedCategoryId?.let { id ->
            categoryGroups.forEach { group ->
                if (group.children.any { it.id == id }) {
                    expandedGroups = expandedGroups + group.parent.id
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                categoryGroups.forEach { group ->
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    expandedGroups = if (expandedGroups.contains(group.parent.id)) {
                                        expandedGroups - group.parent.id
                                    } else {
                                        expandedGroups + group.parent.id
                                    }
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = group.parent.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    if (expandedGroups.contains(group.parent.id)) Icons.Filled.ExpandLess
                                    else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    if (expandedGroups.contains(group.parent.id)) {
                        items(group.children) { child ->
                            val isSelected = child.id == selectedCategoryId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onCategorySelected(child) },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = child.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
