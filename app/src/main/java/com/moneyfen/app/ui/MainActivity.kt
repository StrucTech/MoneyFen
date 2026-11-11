package com.moneyfen.app.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moneyfen.app.app
import com.moneyfen.app.data.Repository
import com.moneyfen.app.data.Snapshot

val LocalRepo = staticCompositionLocalOf<Repository> { error("no repo") }
val LocalNav = staticCompositionLocalOf<NavHostController> { error("no nav") }
val LocalSnap = androidx.compose.runtime.compositionLocalOf { Snapshot() }

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = app.repo
        setContent {
            MoneyFenTheme {
                val snap by repo.snapshot.collectAsStateWithLifecycle(Snapshot())
                val prefsVersion by repo.prefs.version.collectAsStateWithLifecycle()
                val nav = rememberNavController()
                CompositionLocalProvider(LocalRepo provides repo, LocalNav provides nav, LocalSnap provides snap, LocalHide provides (prefsVersion >= 0 && repo.prefs.hideAmounts)) {
                    Surface(Modifier.fillMaxSize()) {
                        if (!repo.prefs.onboarded) OnboardingScreen(snap) else AppScaffold(nav, snap)
                    }
                }
            }
        }
    }
}

private data class Tab(val route: String, val icon: ImageVector, val en: String, val ar: String)

private val tabs = listOf(
    Tab("home", Icons.Default.Home, "Home", "الرئيسية"),
    Tab("txs", Icons.AutoMirrored.Filled.ReceiptLong, "Transactions", "المعاملات"),
    Tab("analytics", Icons.Default.PieChart, "Analytics", "التحليل"),
    Tab("plan", Icons.Default.Savings, "Plan", "الخطة"),
    Tab("settings", Icons.Default.Settings, "Settings", "الإعدادات"),
)

@Composable
private fun AppScaffold(nav: NavHostController, snap: Snapshot) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.navigate(t.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(t.icon, null) },
                        label = { Text(tr(t.en, t.ar), maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        val mod = Modifier.padding(pad).consumeWindowInsets(pad).statusBarsPadding()
            .then(if (showBar) Modifier else Modifier.navigationBarsPadding()).imePadding()
        NavHost(nav, startDestination = "home", modifier = mod) {
            composable("home") { if (LocalRepo.current.prefs.simpleUi) HomeScreenSimple(snap) else HomeScreen(snap) }
            composable("txs") { TransactionsScreen(snap, null) }
            composable("analytics") { AnalyticsScreen(snap) }
            composable("plan") { PlanScreen(snap) }
            composable("settings") { SettingsScreen(snap) }
            composable("account/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                AccountScreen(snap, it.arguments?.getLong("id") ?: 0L)
            }
            composable("accountTxs/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                TransactionsScreen(snap, it.arguments?.getLong("id"))
            }
            composable("tx/{id}?account={account}", listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("account") { type = NavType.LongType; defaultValue = 0L },
            )) {
                TxEditScreen(snap, it.arguments?.getLong("id") ?: 0L, it.arguments?.getLong("account") ?: 0L)
            }
            composable("categories") { CategoriesScreen(snap) }
            composable("rules") { RulesScreen(snap) }
            composable("patterns") { PatternsScreen(snap) }
            composable("backup") { BackupScreen() }
            composable("sources") { IncomeSourcesScreen(snap) }
            composable("pickprice/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                PricePickerScreen(snap, it.arguments?.getLong("id") ?: 0L)
            }
            composable("goal/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                GoalEditScreen(snap, it.arguments?.getLong("id") ?: 0L)
            }
        }
    }
}
