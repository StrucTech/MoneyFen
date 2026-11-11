package com.moneyfen.app.ui

import androidx.compose.runtime.Composable
import com.moneyfen.app.data.Snapshot

/**
 * Simple home (shown when prefs.simpleUi is on). The old [HomeScreen] stays untouched next to it.
 * Phase 1 of docs/SIMPLIFY.md: still the same as the old one; phase 2 replaces the body.
 */
@Composable
fun HomeScreenSimple(snap: Snapshot) {
    HomeScreen(snap)
}
