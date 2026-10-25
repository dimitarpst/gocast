package app.gocast.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gocast.R
import kotlinx.coroutines.launch

internal object WalkthroughColors {
    val Background = Color(0xFF111317)
    val Surface = Color(0xFF202329)
    val Text = Color(0xFFF4F5F7)
    val Muted = Color(0xFFA9AFBA)
    val Blue = Color(0xFF8AB6FF)
}

private enum class WalkthroughPage(@StringRes val title: Int, @StringRes val body: Int) {
    Connect(R.string.walkthrough_connect_title, R.string.walkthrough_connect_body),
    Share(R.string.walkthrough_share_title, R.string.walkthrough_share_body),
    Quality(R.string.walkthrough_quality_title, R.string.walkthrough_quality_body),
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pages = WalkthroughPage.entries
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    fun goTo(page: Int) { scope.launch { pager.animateScrollToPage(page) } }

    BackHandler {
        if (pager.currentPage > 0) goTo(pager.currentPage - 1) else onFinished()
    }
    Column(
        Modifier.fillMaxSize().background(WalkthroughColors.Background)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.gocast_logo),
                contentDescription = "gocast",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(152.dp).height(44.dp),
            )
            TextButton(onClick = onFinished) {
                Text(stringResource(R.string.walkthrough_skip), color = WalkthroughColors.Muted)
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                    ),
                    color = WalkthroughColors.Text,
                )
                Text(
                    stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = WalkthroughColors.Muted,
                )
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    when (page) {
                        WalkthroughPage.Connect -> ConnectionIllustration(Modifier.fillMaxWidth(.9f).aspectRatio(1.7f))
                        WalkthroughPage.Share -> SharingPreview()
                        WalkthroughPage.Quality -> QualityPreview()
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 12.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val progress = stringResource(R.string.walkthrough_progress, pager.currentPage + 1, pages.size)
            Row(
                modifier = Modifier.semantics { contentDescription = progress },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier.width(if (index == pager.currentPage) 24.dp else 8.dp).height(8.dp)
                            .background(if (index == pager.currentPage) WalkthroughColors.Blue else Color(0xFF424751), CircleShape),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pager.currentPage > 0) {
                    OutlinedButton(
                        onClick = { goTo(pager.currentPage - 1) },
                        enabled = !pager.isScrollInProgress,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(vertical = 18.dp),
                    ) { Text(stringResource(R.string.back), color = WalkthroughColors.Text) }
                }
                Button(
                    onClick = {
                        if (pager.currentPage == pages.lastIndex) onFinished()
                        else goTo(pager.currentPage + 1)
                    },
                    enabled = !pager.isScrollInProgress,
                    modifier = Modifier.weight(2f),
                    shape = CircleShape,
                    contentPadding = PaddingValues(vertical = 18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WalkthroughColors.Text, contentColor = WalkthroughColors.Background,
                    ),
                ) {
                    Text(
                        stringResource(if (pager.currentPage == pages.lastIndex) R.string.intro_get_started else R.string.walkthrough_next),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(stringResource(R.string.intro_footer), color = WalkthroughColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
