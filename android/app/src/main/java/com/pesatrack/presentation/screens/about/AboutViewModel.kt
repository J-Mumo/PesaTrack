package com.pesatrack.presentation.screens.about

import androidx.lifecycle.ViewModel
import com.pesatrack.utils.UsageSummaryGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    val usageSummaryGenerator: UsageSummaryGenerator
) : ViewModel()
