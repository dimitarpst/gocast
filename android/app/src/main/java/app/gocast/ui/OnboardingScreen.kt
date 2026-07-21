package app.gocast.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gocast.R
import kotlinx.coroutines.launch

/**
 * First-open orientation — brand-first, almost no reading.
 * Research (and common practice): keep it skippable, visual, and under ~2 beats.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF12141A), Color(0xFF1B2030), Color(0xFF0E1016)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!lastPage) {
                    TextButton(onClick = onFinished) {
                        Text("Skip", color = Color(0xFF9AA3B5))
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> OnboardingHero()
                    else -> OnboardingConnectHint()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(2) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) Color(0xFF6EA8FE)
                                else Color(0xFF3A4558),
                            ),
                    )
                }
            }

            Button(
                onClick = {
                    if (lastPage) {
                        onFinished()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6EA8FE),
                    contentColor = Color(0xFF0E1016),
                ),
            ) {
                Text(
                    text = if (lastPage) "Get started" else "Next",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun OnboardingHero() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.gocast_logo),
            contentDescription = "gocast",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(72.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Your phone screen,\non your PC.",
            color = Color(0xFFE8ECF5),
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Same Wi-Fi. Nothing else.",
            color = Color(0xFF9AA3B5),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnboardingConnectHint() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Quiet visual stand-in for “look at the PC” — not a wall of instructions.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1B2030)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8ECF5)),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "12  34",
                    color = Color(0xFF6EA8FE),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Open gocast on the PC.\nScan the QR — or type the code.",
            color = Color(0xFFE8ECF5),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
        )
    }
}
