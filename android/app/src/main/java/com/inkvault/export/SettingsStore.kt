package com.inkvault.export

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** App theme preference: follow the system, or force one. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System default"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        val DEFAULT = SYSTEM
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** The sync targets the export pipeline knows how to write to today (cloud pending — DESIGN.md). */
enum class SyncMethod(val key: String, val label: String) {
    LOCAL_FOLDER("local_folder", "Local folder (SAF)"),
    TAILSCALE_PUSH("tailscale_push", "Direct push (Tailscale)"),
    LOCAL_ONLY("local_only", "Local only");

    companion object {
        val DEFAULT = LOCAL_FOLDER
        fun fromKey(key: String?): SyncMethod = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Runtime settings persisted with Jetpack DataStore (Preferences) per the brief — NOT
 * SharedPreferences. Changing a value takes effect on the next export (the worker reads it fresh),
 * no rebuild/restart. Only the keys Phase 3 needs live here; Phase 4 adds the OCR-trigger keys.
 */
class SettingsStore(private val context: Context) {
    private val syncMethodKey = stringPreferencesKey("sync.method")
    private val localFolderUriKey = stringPreferencesKey("sync.local_folder.uri")
    private val tailscaleEndpointKey = stringPreferencesKey("sync.tailscale.endpoint")
    private val themeModeKey = stringPreferencesKey("ui.theme")
    private val rememberPasswordKey = booleanPreferencesKey("pen.remember_password")
    private val onDeviceOcrAckKey = booleanPreferencesKey("ocr.on_device.acknowledged")
    private val onDeviceOcrEnabledKey = booleanPreferencesKey("ocr.on_device.enabled")
    private val bgCaptureNudgeDismissedKey = booleanPreferencesKey("capture.bg_nudge_dismissed")
    private val calendarTargetIdKey = longPreferencesKey("calendar.target_id")
    private val translateEndpointKey = stringPreferencesKey("translate.endpoint")
    private val translateModelKey = stringPreferencesKey("translate.model")

    val themeMode: Flow<ThemeMode> =
        context.settingsDataStore.data.map { ThemeMode.fromKey(it[themeModeKey]) }
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[themeModeKey] = mode.key }

    /** Whether to remember the pen unlock password (for ~30 days) vs. ask every connect. Default off. */
    val rememberPassword: Flow<Boolean> =
        context.settingsDataStore.data.map { it[rememberPasswordKey] ?: false }
    suspend fun setRememberPassword(on: Boolean) = edit { it[rememberPasswordKey] = on }

    /**
     * Whether the user has acknowledged that on-device OCR (ML Kit Digital Ink) downloads a one-time
     * model from Google on first use — outbound to a target the user didn't select. We ask once via a
     * disclosure, then never again. Default off, so the first "Transcribe on device" prompts.
     */
    val onDeviceOcrAcknowledged: Flow<Boolean> =
        context.settingsDataStore.data.map { it[onDeviceOcrAckKey] ?: false }
    suspend fun acknowledgeOnDeviceOcr() = edit { it[onDeviceOcrAckKey] = true }

    /**
     * Master switch for on-device (ML Kit) transcription. Default ON — the per-use disclosure still
     * gates first use; turn OFF to keep transcription on the NAS/OCR host only.
     */
    val onDeviceOcrEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[onDeviceOcrEnabledKey] ?: true }
    suspend fun setOnDeviceOcrEnabled(on: Boolean) = edit { it[onDeviceOcrEnabledKey] = on }

    /** Re-arm the first-use on-device OCR disclosure so it prompts again. */
    suspend fun resetOnDeviceOcrDisclosure() = edit { it[onDeviceOcrAckKey] = false }

    /** Whether the first-connect "allow background capture" nudge has already been shown/dismissed. */
    val bgCaptureNudgeDismissed: Flow<Boolean> =
        context.settingsDataStore.data.map { it[bgCaptureNudgeDismissedKey] ?: false }
    suspend fun dismissBgCaptureNudge() = edit { it[bgCaptureNudgeDismissedKey] = true }

    /** The device calendar new events are added to (-1 = none chosen yet). */
    val calendarTargetId: Flow<Long> =
        context.settingsDataStore.data.map { it[calendarTargetIdKey] ?: -1L }
    suspend fun setCalendarTargetId(id: Long) = edit { it[calendarTargetIdKey] = id }

    val syncMethod: Flow<SyncMethod> =
        context.settingsDataStore.data.map { SyncMethod.fromKey(it[syncMethodKey]) }
    val localFolderUri: Flow<String> =
        context.settingsDataStore.data.map { it[localFolderUriKey] ?: "" }
    val tailscaleEndpoint: Flow<String> =
        context.settingsDataStore.data.map { it[tailscaleEndpointKey] ?: "" }

    /**
     * High-quality translation engine: an OpenAI-compatible endpoint (Ollama/vLLM on the user's GPU
     * box) running a translation-grade LLM — one model covers every language, no per-language
     * downloads, and traffic stays on the user's own tailnet. Blank → fall back to on-device ML Kit.
     */
    val translateEndpoint: Flow<String> =
        context.settingsDataStore.data.map { it[translateEndpointKey] ?: "" }
    val translateModel: Flow<String> =
        context.settingsDataStore.data.map { it[translateModelKey] ?: "" }
    suspend fun setTranslateEndpoint(endpoint: String) = edit { it[translateEndpointKey] = endpoint }
    suspend fun setTranslateModel(model: String) = edit { it[translateModelKey] = model }

    // Per-notebook-product type assignment (the new-notebook dialog). Keyed by Ncode book id, so the
    // user designates a product once and every physical copy of it auto-resolves afterward.
    private fun notebookTypeKey(book: Int) = stringPreferencesKey("notebook.type.$book")

    /** Persist the user's type choice for a book id. */
    suspend fun assignNotebookType(book: Int, typeId: String) =
        edit { it[notebookTypeKey(book)] = typeId }

    /** The user's assigned type id for a book id (null = not assigned), observed for the dialog. */
    fun assignedTypeId(book: Int): Flow<String?> =
        context.settingsDataStore.data.map { it[notebookTypeKey(book)] }

    /** Resolve a book id to its type at export time: user assignment wins, else the built-in default. */
    suspend fun notebookType(book: Int?): NotebookType? {
        book ?: return null
        val assignedId = context.settingsDataStore.data.first()[notebookTypeKey(book)]
        return NotebookType.resolve(assignedId, book)
    }

    // Notebooks the user has already been prompted to set up (type + label), so the dialog asks once.
    private val acknowledgedNotebooksKey = stringSetPreferencesKey("notebook.setup.done")

    val acknowledgedNotebooks: Flow<Set<String>> =
        context.settingsDataStore.data.map { it[acknowledgedNotebooksKey] ?: emptySet() }

    /** Mark a notebook as set up / dismissed, so the new-notebook dialog won't ask again. */
    suspend fun acknowledgeNotebook(id: String) = edit { prefs ->
        prefs[acknowledgedNotebooksKey] = (prefs[acknowledgedNotebooksKey] ?: emptySet()) + id
    }

    suspend fun setSyncMethod(method: SyncMethod) =
        edit { it[syncMethodKey] = method.key }
    suspend fun setLocalFolderUri(uri: String) =
        edit { it[localFolderUriKey] = uri }
    suspend fun setTailscaleEndpoint(endpoint: String) =
        edit { it[tailscaleEndpointKey] = endpoint }

    /** One-shot read for the WorkManager job, which needs the current target at export time. */
    suspend fun snapshot(): Triple<SyncMethod, String, String> {
        val prefs = context.settingsDataStore.data.first()
        return Triple(
            SyncMethod.fromKey(prefs[syncMethodKey]),
            prefs[localFolderUriKey] ?: "",
            prefs[tailscaleEndpointKey] ?: "",
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
