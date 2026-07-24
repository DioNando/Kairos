package com.kairos.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.Quadrant
import com.kairos.app.ui.components.quadrantVisual
import kotlinx.coroutines.launch

/**
 * Accueil au premier lancement : trois écrans expliquant la matrice d'Eisenhower et la prise en
 * main. « Passer » ou terminer appellent [onDone] (le réglage `onboardingDone` est alors posé).
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pageCount - 1

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Bouton « Passer » en haut à droite.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.onb_skip)) }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> QuadrantsPage()
                    else -> StartPage()
                }
            }

            // Indicateur de page.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pageCount) { index ->
                    val selected = pagerState.currentPage == index
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        label = "onb-dot",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (selected) 10.dp else 8.dp)
                            .background(color, CircleShape),
                    )
                }
            }

            Button(
                onClick = {
                    if (isLast) onDone() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp),
            ) {
                Text(stringResource(if (isLast) R.string.onb_start else R.string.onb_next))
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    PageScaffold(
        title = stringResource(R.string.onb_welcome_title),
        body = stringResource(R.string.onb_welcome_body),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun QuadrantsPage() {
    PageScaffold(
        title = stringResource(R.string.onb_quadrants_title),
        body = stringResource(R.string.onb_quadrants_body),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                Quadrant.DO to Quadrant.DELEGATE,
                Quadrant.SCHEDULE to Quadrant.ELIMINATE,
            ).forEach { (left, right) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuadrantMini(left, Modifier.weight(1f))
                    QuadrantMini(right, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuadrantMini(quadrant: Quadrant, modifier: Modifier = Modifier) {
    val visual = quadrantVisual(quadrant)
    Column(
        modifier = modifier
            .height(96.dp)
            .background(visual.accent.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(visual.icon, contentDescription = null, tint = visual.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(visual.verb, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = visual.accent)
        Text(
            visual.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StartPage() {
    PageScaffold(
        title = stringResource(R.string.onb_start_title),
        body = stringResource(R.string.onb_start_body),
    ) {
        Text("✦", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PageScaffold(title: String, body: String, illustration: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 160.dp)) { illustration() }
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
