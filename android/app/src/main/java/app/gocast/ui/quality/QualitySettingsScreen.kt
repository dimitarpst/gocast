package app.gocast.ui.quality

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gocast.capture.CaptureService
import app.gocast.settings.CastQualityProfile
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.CastQualityStore
import app.gocast.settings.CostChip
import app.gocast.settings.QualityRecommendation
import app.gocast.settings.QualityRecommender
import app.gocast.settings.SettingHelp
import app.gocast.ui.quality.components.AccordionSection
import app.gocast.ui.quality.components.CostChipRow
import app.gocast.ui.quality.components.ProfileCard
import app.gocast.ui.quality.components.costColor
import app.gocast.ui.quality.sections.BitrateSettingsSection
import app.gocast.ui.quality.sections.CaptureSettingsSection
import app.gocast.ui.quality.sections.CodecSettingsSection
import app.gocast.ui.quality.sections.EncoderSettingsSection
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
    var expandedSection by remember { mutableStateOf<String?>(null) }
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
        onCostTap = { weightPopup = it },
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Cast quality", style = MaterialTheme.typography.headlineSmall)
            when (autosaveStatus) {
                AutosaveStatus.Idle -> Unit
                AutosaveStatus.Saving -> Text(
                    "Saving…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AutosaveStatus.Autosaved -> Text(
                    "Autosaved",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            "Like game graphics presets — pick a profile, or tune and save your own. " +
                "Changes apply while casting (codec/encoder may briefly reconnect).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (!detecting) {
                    recommendation = null
                    detecting = true
                }
            },
            enabled = !detecting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (detecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("Checking your phone…")
            } else {
                Text("Auto-detect best profile")
            }
        }

        AnimatedVisibility(visible = recommendation != null && !detecting) {
            val tip = recommendation
            if (tip != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Recommended: ${tip.profile.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tip.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Profiles", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        profiles.forEach { profile ->
            val selected = when {
                activeProfileId != null -> activeProfileId == profile.id
                else -> settings == profile.settings
            }
            ProfileCard(
                profile = profile,
                selected = selected,
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

        if (activeProfileId == null) {
            Text(
                "Unsaved custom mix — save it as a profile to reuse later.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                saveName = ""
                showSaveDialog = true
            }) {
                Text("Save as profile")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Detailed settings", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap a section to expand it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        AccordionSection(
            title = "Capture",
            expanded = expandedSection == "capture",
            onToggle = {
                expandedSection = if (expandedSection == "capture") null else "capture"
            },
        ) {
            CaptureSettingsSection(settings = settings, actions = actions)
        }

        AccordionSection(
            title = "Codec",
            expanded = expandedSection == "codec",
            onToggle = {
                expandedSection = if (expandedSection == "codec") null else "codec"
            },
        ) {
            CodecSettingsSection(settings = settings, actions = actions)
        }

        AccordionSection(
            title = "Bitrate & framerate",
            expanded = expandedSection == "bitrate",
            onToggle = {
                expandedSection = if (expandedSection == "bitrate") null else "bitrate"
            },
        ) {
            BitrateSettingsSection(settings = settings, actions = actions)
        }

        AccordionSection(
            title = "Encoder",
            expanded = expandedSection == "encoder",
            onToggle = {
                expandedSection = if (expandedSection == "encoder") null else "encoder"
            },
        ) {
            EncoderSettingsSection(settings = settings, actions = actions)
        }

        Spacer(Modifier.height(24.dp))
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
