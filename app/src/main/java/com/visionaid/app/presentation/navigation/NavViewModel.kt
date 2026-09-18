package com.visionaid.app.presentation.navigation

import androidx.lifecycle.ViewModel
import com.visionaid.app.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Tiny ViewModel that gives the NavGraph access to the settings store
 * (needed to pick the start destination: setup vs. main).
 */
@HiltViewModel
class NavViewModel @Inject constructor(
    val preferences: AppPreferences
) : ViewModel()
