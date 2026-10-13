package app.delayd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.delayd.ClaimStatus
import app.delayd.R

val MaxContentWidth = 640.dp

/** App bar + centred, inset-aware list: the frame every screen shares. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)), Alignment.TopCenter) {
            val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
            LazyColumn(
                Modifier.widthIn(max = MaxContentWidth).fillMaxHeight(),
                contentPadding = PaddingValues(start = Space.l, end = Space.l, top = Space.s, bottom = bottom + Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.m),
                content = content,
            )
        }
    }
}

/** Tonal card on the page background; a hairline keeps it visible without shadows. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    padding: Dp = Space.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), MaterialTheme.shapes.medium, container, contentColor,
        border = if (container == MaterialTheme.colorScheme.surface) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(Space.s), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = Space.m, vertical = Space.xs), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Text always carries the minutes, colour only adds weight: 60+ amber (claimable), 120+ red (half the fare). */
@Composable
fun DelayBadge(minutes: Long) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when {
        minutes >= 120 -> c.errorContainer to c.onErrorContainer
        minutes >= 60 -> c.tertiaryContainer to c.onTertiaryContainer
        else -> c.surfaceVariant to c.onSurfaceVariant
    }
    Pill(stringResource(R.string.delay_badge, minutes), bg, fg)
}

@Composable
fun StatusChip(status: ClaimStatus) {
    val c = MaterialTheme.colorScheme
    val x = LocalExtra.current
    val (bg, fg) = when (status) {
        ClaimStatus.OPEN -> c.primaryContainer to c.onPrimaryContainer
        ClaimStatus.CLAIMED -> c.secondaryContainer to c.onSecondaryContainer
        ClaimStatus.PAID -> x.successContainer to x.onSuccessContainer
        ClaimStatus.DISMISSED -> c.surfaceVariant to c.onSurfaceVariant
    }
    Pill(stringResource(statusLabel(status)), bg, fg)
}

fun statusLabel(s: ClaimStatus) = when (s) {
    ClaimStatus.OPEN -> R.string.status_open
    ClaimStatus.CLAIMED -> R.string.status_claimed
    ClaimStatus.PAID -> R.string.status_paid
    ClaimStatus.DISMISSED -> R.string.status_dismissed
}

@Composable
fun BigMoney(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) =
    Text(text, modifier, style = MaterialTheme.typography.displaySmall, color = color, fontWeight = FontWeight.SemiBold)

/** Buttons that wrap under each other at large font scales instead of clipping. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Actions(content: @Composable FlowRowScope.() -> Unit) =
    FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(Space.s), Arrangement.spacedBy(Space.s), content = content)

/** The launcher-icon ticket as a tintable glyph (decorative). */
@Composable
fun TicketGlyph(modifier: Modifier = Modifier, tint: Color) =
    Icon(painterResource(R.drawable.ic_stat_delayd), null, modifier, tint)
