package app.gocast.ui.quality

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gocast.R
import app.gocast.capture.CaptureService
import app.gocast.settings.CastQualityProfile
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.CastQualityStore
import app.gocast.settings.CostChip
import app.gocast.settings.QualityRecommendation
import app.gocast.settings.QualityRecommender
import app.gocast.settings.SettingHelp
import app.gocast.ui.quality.components.CostChipRow
import app.gocast.ui.quality.components.ProfileRow
import app.gocast.ui.quality.components.SettingsGroup
import app.gocast.ui.quality.components.costColor
import app.gocast.ui.quality.sections.BitrateSettingsSection
import app.gocast.ui.quality.sections.CaptureSettingsSection
import app.gocast.ui.quality.sections.CodecSettingsSection
import app.gocast.ui.quality.sections.EncoderSettingsSection
import app.gocast.ui.quality.sections.StreamingSettingsSection
import kotlinx.coroutines.delay

private enum class AutosaveStatus {
    Idle,
    Saving,
    Autosaved,
}

@Composable
fun QualitySettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(CastQualityStore.load(context)) }
    var profiles by remember { mutableStateOf(CastQualityStore.allProfiles(context)) }
    var activeProfileId by remember {
        mutableStateOf(CastQualityStore.activeProfileId(context))
    }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var helpTarget by remember { mutableStateOf<SettingHelp?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var detecting by remember { mutableStateOf(false) }
    var recommendation by remember { mutableStateOf<QualityRecommendation?>(null) }
    var weightPopup by remember { mutableStateOf<CostChip?>(null) }
    var autosaveStatus by remember { mutableStateOf(AutosaveStatus.Idle) }
    var autosaveToken by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)

    fun refreshProfiles() {
        profiles = CastQualityStore.allProfiles(context)
        activeProfileId = CastQualityStore.activeProfileId(context)
    }

    fun notifyCastingSession() {
        CaptureService.applyQualityIfCasting(context)
    }

    fun markAutosaving() {
        autosaveStatus = AutosaveStatus.Saving
        autosaveToken++
    }

    LaunchedEffect(autosaveToken) {
        if (autosaveToken == 0) return@LaunchedEffect
        delay(350)
        autosaveStatus = AutosaveStatus.Autosaved
    }

    LaunchedEffect(detecting) {
        if (!detecting) return@LaunchedEffect
        delay(700)
        val tip = QualityRecommender.recommend(context)
        settings = tip.profile.settings
        CastQualityStore.save(context, tip.profile.settings)
        val exactBuiltIn = CastQualityProfile.BuiltIns
            .firstOrNull { it.settings == tip.profile.settings }
        CastQualityStore.setActiveProfileId(context, exactBuiltIn?.id)
        activeProfileId = exactBuiltIn?.id
        recommendation = tip
        detecting = false
        refreshProfiles()
        notifyCastingSession()
        markAutosaving()
    }

    fun persistSettings(next: CastQualitySettings) {
        settings = next
        CastQualityStore.save(context, next)
        // Tweaking knobs leaves the named profile unless values still match.
        val stillMatching = profiles.firstOrNull { it.id == activeProfileId }?.settings == next
        if (!stillMatching) {
            CastQualityStore.setActiveProfileId(context, null)
            activeProfileId = null
        }
        notifyCastingSession()
        markAutosaving()
    }

    fun selectProfile(profile: CastQualityProfile) {
        CastQualityStore.applyProfile(context, profile)
        settings = profile.settings
        activeProfileId = profile.id
        refreshProfiles()
        notifyCastingSession()
        markAutosaving()
    }

    val actions = QualityUiActions(
        onChange = ::persistSettings,
        onInfo = { helpTarget = it },
    )
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(
                "Quality",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            when (autosaveStatus) {
                AutosaveStatus.Idle -> Unit
                AutosaveStatus.Saving -> Text(
                    "Saving…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AutosaveStatus.Autosaved -> Text(
                    "Saved",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        SettingsGroup(title = "Profile") {
            profiles.forEach { profile ->
                val selected = when {
                    activeProfileId != null -> activeProfileId == profile.id
                    else -> settings == profile.settings
                }
                ProfileRow(
                    profile = profile,
                    selected = selected,
                    recommended = !detecting && recommendation?.profile?.id == profile.id,
                    onSelect = { selectProfile(profile) },
                    onDelete = if (!profile.builtIn) {
                        {
                            CastQualityStore.deleteUserProfile(context, profile.id)
                            refreshProfiles()
                            settings = CastQualityStore.load(context)
                            activeProfileId = CastQualityStore.activeProfileId(context)
                        }
                    } else {
                        null
                    },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    if (!detecting) {
                        recommendation = null
                        detecting = true
                    }
                },
                enabled = !detecting,
                shape = CircleShape,
                modifier = Modifier.weight(1f),
            ) {
                if (detecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Text("Auto-detect")
                }
            }
            FilledTonalButton(
                onClick = {
                    saveName = ""
                    showSaveDialog = true
                },
                shape = CircleShape,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.quality_save_profile))
            }
        }

        DetailedSettingsToggle(
            open = detailsOpen,
            onToggle = { detailsOpen = !detailsOpen },
        )
        AnimatedVisibility(visible = detailsOpen) {
            Column {
                SettingsGroup(title = "Picture") {
                    CaptureSettingsSection(settings = settings, actions = actions)
                }
                SettingsGroup(title = "Bitrate") {
                    BitrateSettingsSection(settings = settings, actions = actions)
                }
                SettingsGroup(title = "Streaming") {
                    StreamingSettingsSection(settings = settings, actions = actions)
                }
                SettingsGroup(title = "Video format") {
                    CodecSettingsSection(settings = settings, actions = actions)
                }
                SettingsGroup(title = "Compatibility") {
                    EncoderSettingsSection(settings = settings, actions = actions)
                }
            }
        }
    }

    helpTarget?.let { help ->
        AlertDialog(
            onDismissRequest = { helpTarget = null },
            title = { Text(help.title) },
            text = {
                Column {
                    CostChipRow(help.costs, onCostTap = { weightPopup = it })
                    Spacer(Modifier.height(12.dp))
                    Text(help.body)
                }
            },
            confirmButton = {
                TextButton(onClick = { helpTarget = null }) { Text("Got it") }
            },
        )
    }

    weightPopup?.let { chip ->
        AlertDialog(
            onDismissRequest = { weightPopup = null },
            title = { Text(chip.axisLabel) },
            text = {
                Text(
                    chip.weightLabel,
                    color = costColor(chip.weight),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { weightPopup = null }) { Text("OK") }
            },
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save profile") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val profile = CastQualityProfile.createUser(saveName, settings)
                        CastQualityStore.upsertUserProfile(context, profile)
                        CastQualityStore.applyProfile(context, profile)
                        activeProfileId = profile.id
                        refreshProfiles()
                        showSaveDialog = false
                        notifyCastingSession()
                        markAutosaving()
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** One row that reveals every detailed setting; the chevron turns to point down when open. */
@Composable
private fun DetailedSettingsToggle(open: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (open) 90f else 0f, label = "chevron")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
    ) {
        Row(
            Modifier
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Detailed settings",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}
