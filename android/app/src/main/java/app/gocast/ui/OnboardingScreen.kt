package app.gocast.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gocast.R

internal object WalkthroughColors {
    val Background = Color(0xFF111317)
    val Surface = Color(0xFF202329)
    val Text = Color(0xFFF4F5F7)
    val Muted = Color(0xFFA9AFBA)
    val Blue = Color(0xFF8AB6FF)
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    BackHandler(onBack = onFinished)
    Column(
        Modifier.fillMaxSize().background(WalkthroughColors.Background).safeDrawingPadding(),
    ) {
        Image(
            painterResource(R.drawable.gocast_logo),
            contentDescription = "gocast",
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).width(152.dp).height(44.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.walkthrough_connect_title),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                ),
                color = WalkthroughColors.Text,
            )
            Text(
                stringResource(R.string.walkthrough_connect_body),
                style = MaterialTheme.typography.bodyLarge,
                color = WalkthroughColors.Muted,
            )
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                ConnectionIllustration(Modifier.fillMaxWidth(.9f).aspectRatio(1.7f))
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 12.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                contentPadding = PaddingValues(vertical = 18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WalkthroughColors.Text, contentColor = WalkthroughColors.Background,
                ),
            ) {
                Text(stringResource(R.string.intro_get_started), style = MaterialTheme.typography.labelLarge)
            }
            Text(stringResource(R.string.intro_footer), color = WalkthroughColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
