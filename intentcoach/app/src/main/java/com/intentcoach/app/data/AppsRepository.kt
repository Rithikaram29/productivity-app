package com.intentcoach.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.intentcoach.app.domain.AppCategorizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An app the picker can offer. */
data class InstalledApp(val packageName: String, val label: String, val category: AppCategory)

/**
 * Which apps exist on the phone, and which ones the user wants to be asked about.
 *
 * Listing apps: we ask the PackageManager for activities that answer the launcher intent
 * (MAIN + LAUNCHER). Since API 30 that only works because the manifest declares the same
 * intent in `<queries>` — an intent-scoped visibility declaration, which is NOT the
 * restricted QUERY_ALL_PACKAGES permission and needs no Play Console declaration.
 * Apps without a launcher icon (system services) are never shown.
 */
class AppsRepository(
    private val context: Context,
    private val dao: MonitoredAppDao,
    scope: CoroutineScope
) {
    private val _monitored = MutableStateFlow<Set<String>>(emptySet())

    /**
     * In-memory mirror of the enabled packages. The service checks this on every poll;
     * reading a StateFlow is free, a DB query every 800 ms would not be.
     */
    val monitoredPackages: StateFlow<Set<String>> = _monitored.asStateFlow()

    init {
        scope.launch {
            migrateLegacySelection()
            dao.observeEnabled().collect { list -> _monitored.value = list.map { it.packageName }.toSet() }
        }
    }

    fun observeAll(): Flow<List<MonitoredApp>> = dao.observeAll()

    // DEPRECATION: the ResolveInfoFlags overload of queryIntentActivities is API 33+; the
    // int-flags one used here works on every version from 26 up.
    @Suppress("DEPRECATION")
    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val browsers = browserPackages(pm)
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    category = AppCategorizer.categorize(info.packageName, info.category, info.packageName in browsers)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun setMonitored(app: InstalledApp, enabled: Boolean) {
        // Update first; only insert if the app has never been in the table.
        if (dao.setEnabled(app.packageName, enabled) == 0 && enabled) {
            dao.insert(
                MonitoredApp(packageName = app.packageName, appName = app.label, isEnabled = true, category = app.category)
            )
        }
    }

    /** Forget the in-memory mirror. Part of "Delete all local data". */
    fun resetCache() {
        _monitored.value = emptySet()
    }

    @Suppress("DEPRECATION")
    private fun browserPackages(pm: PackageManager): Set<String> {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return pm.queryIntentActivities(view, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    /**
     * One-time carry-over of a v0.1 user's watch list (SharedPreferences) into Room.
     * Only an explicit selection is carried; the old defaults are not.
     */
    private suspend fun migrateLegacySelection() {
        if (Settings.isLegacyAppsMigrated(context)) return
        if (WatchedApps.hasExplicitSelection(context)) {
            val pm = context.packageManager
            for (pkg in WatchedApps.get(context)) {
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    WatchedApps.displayNameFor(pkg) ?: pkg
                }
                val category = AppCategorizer.categorize(pkg, AppCategorizer.CATEGORY_UNDEFINED, false)
                dao.insert(MonitoredApp(packageName = pkg, appName = label, isEnabled = true, category = category))
            }
        }
        Settings.setLegacyAppsMigrated(context)
    }
}
