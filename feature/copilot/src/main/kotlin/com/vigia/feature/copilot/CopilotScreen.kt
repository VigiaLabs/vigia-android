package com.vigia.feature.copilot

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vigia.core.model.ChatMessage
import com.vigia.core.model.ChatSession
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.MessageRole
import com.vigia.core.model.MessageStatus
import com.vigia.core.network.search.SearchEvent
import com.vigia.core.network.stripe.PayoutStatus
import com.vigia.feature.copilot.orb.AiOrb
import com.vigia.feature.copilot.theme.AnswerBodyStyle
import com.vigia.feature.copilot.theme.VigiaMotion
import com.vigia.feature.copilot.theme.VigiaTheme
import com.vigia.feature.copilot.theme.pressScale
import com.vigia.feature.copilot.theme.vigiaColors
import com.vigia.feature.copilot.voice.VoiceCallOverlay
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Stateless Copilot screen — Perplexity-style two-state layout.
 *
 * Landing mode (no active session):
 *   LandingTopBar (hamburger | nav pill | avatar) + orb wordmark + LandingChatBox
 *
 * Chat mode (active session):
 *   ChatTopBar (X | Answers/Sources/Map tabs | overflow) + thread + FollowUpBar
 *
 * Compose Expert gates: stable params, keyed lazy items, animateItem(), zero-
 * recomposition orb, Crossfade caption in a fixed-height slot.
 * UI/UX Pro Max gates: ≥48dp touch targets, contentDescription on icon-only
 * controls, token-only colours, edge-to-edge insets.
 */
@Composable
internal fun CopilotScreen(
    uiState: CopilotUiState,
    sessions: List<ChatSession>,
    sessionMessages: List<ChatMessage>,
    activeSessionId: String?,
    onSendMessage: (String) -> Unit,
    onCancelSearch: () -> Unit,
    onNewChat: () -> Unit,
    onLoadSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSignOut: () -> Unit = {},
    accountName: String? = null,
    accountEmail: String? = null,
) {
    VigiaTheme {
        when (uiState) {
            CopilotUiState.Loading -> LoadingPane()
            is CopilotUiState.Error -> ErrorPane(uiState.message)
            is CopilotUiState.Active -> ActiveShell(
                state           = uiState,
                sessions        = sessions,
                sessionMessages = sessionMessages,
                activeSessionId = activeSessionId,
                onSendMessage   = onSendMessage,
                onCancelSearch  = onCancelSearch,
                onNewChat       = onNewChat,
                onLoadSession   = onLoadSession,
                onDeleteSession = onDeleteSession,
                onSignOut       = onSignOut,
                accountName     = accountName,
                accountEmail    = accountEmail,
            )
        }
    }
}

// ── Loading / Error ───────────────────────────────────────────────────────────

@Composable
private fun LoadingPane() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorPane(message: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text     = message,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

// ── Shell ─────────────────────────────────────────────────────────────────────

private enum class ChatTab { Answers, Sources, Map }

/** Landing-page top-bar destinations: Ask VIGIA · Alerts · Maps · Wallet. */
private enum class LandingTab { Ask, Alerts, Map, Wallet }

@Composable
private fun ActiveShell(
    state: CopilotUiState.Active,
    sessions: List<ChatSession>,
    sessionMessages: List<ChatMessage>,
    activeSessionId: String?,
    onSendMessage: (String) -> Unit,
    onCancelSearch: () -> Unit,
    onNewChat: () -> Unit,
    onLoadSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSignOut: () -> Unit,
    accountName: String?,
    accountEmail: String?,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()
    val inSession   = activeSessionId != null

    // Pager state is the single source of truth for the active tab — the top bar
    // reads its fractional offset to slide the selection bubble, and content
    // swipes drive it directly.
    val landingPager = rememberPagerState(pageCount = { LandingTab.entries.size })
    val chatPager    = rememberPagerState(pageCount = { ChatTab.entries.size })

    // Hoisted so the suggestion pills can prefill the landing input box.
    var landingInput by remember { mutableStateOf("") }
    val landingFocus = remember { FocusRequester() }

    // Voice call surface (mic button → full-screen Gemini-style voice UI).
    var showVoice by remember { mutableStateOf(false) }

    // Reset to Answers whenever a new session is activated (new or loaded from drawer).
    LaunchedEffect(activeSessionId) { chatPager.scrollToPage(ChatTab.Answers.ordinal) }
    // Returning to landing always lands on Ask VIGIA.
    LaunchedEffect(inSession) { if (!inSession) landingPager.scrollToPage(LandingTab.Ask.ordinal) }

    // Derive sources for the Sources tab: live stream sources while streaming,
    // or the last assistant message's sources after the stream completes.
    val displaySources = remember(sessionMessages, state.searchSources, state.isSearchStreaming) {
        if (state.isSearchStreaming && state.searchSources.isNotEmpty()) {
            state.searchSources
        } else {
            sessionMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
                ?.sources
                ?.map { SearchEvent.Source(it.id, it.label, it.trustLevel, it.url) }
                ?: emptyList()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            VigiaDrawer(
                sessions        = sessions,
                activeSessionId = activeSessionId,
                onNewChat = {
                    scope.launch { drawerState.close() }
                    onNewChat()
                },
                onLoadSession = { id ->
                    scope.launch { drawerState.close() }
                    onLoadSession(id)
                },
                onDeleteSession = onDeleteSession,
                onSignOut = {
                    scope.launch { drawerState.close() }
                    onSignOut()
                },
                accountName  = accountName,
                accountEmail = accountEmail,
            )
        },
    ) {
        val hazeState = remember { HazeState() }

      Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (inSession) {
                    ChatTopBar(
                        pagerState = chatPager,
                        onClose    = onNewChat,
                        hazeState  = hazeState,
                    )
                } else {
                    LandingTopBar(
                        pagerState  = landingPager,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        hazeState   = hazeState,
                    )
                }
            },
        ) { scaffoldPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
                // hazeSource: content scrolls through this layer; the bottom bars
                // float above it and blur whatever is behind them.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                ) {
                    Atmosphere()

                    // iOS-style content swap: fade + micro-scale-in on enter,
                    // quick fade-out on exit. Spring for scale so it's interruptible.
                    AnimatedContent(
                        targetState = inSession,
                        transitionSpec = {
                            (fadeIn(tween(VigiaMotion.ENTER_MS, delayMillis = 30)) +
                             scaleIn(initialScale = 0.97f, animationSpec = spring(
                                 dampingRatio = VigiaMotion.gentle.dampingRatio,
                                 stiffness    = VigiaMotion.gentle.stiffness,
                             ))).togetherWith(fadeOut(tween(VigiaMotion.EXIT_MS)))
                        },
                        label = "landing_chat_switch",
                        modifier = Modifier.fillMaxSize(),
                    ) { session ->
                        if (session) {
                            ChatContent(
                                pagerState      = chatPager,
                                state           = state,
                                sessionMessages = sessionMessages,
                                displaySources  = displaySources,
                                modifier        = Modifier.fillMaxSize(),
                            )
                        } else {
                            LandingPager(
                                pagerState = landingPager,
                                state      = state,
                                modifier   = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // Bottom input — floats over scrolling content with glass blur.
                if (inSession) {
                    FollowUpBar(
                        hazeState   = hazeState,
                        isStreaming = state.isSearchStreaming,
                        onSubmit    = onSendMessage,
                        onCancel    = onCancelSearch,
                        modifier    = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                } else {
                    // Chatbox + suggestion pills live ONLY on the Ask page — the
                    // Alerts / Map / Wallet pages get the full canvas with no input.
                    val onAskPage = landingPager.currentPage == LandingTab.Ask.ordinal
                    AnimatedVisibility(
                        visible = onAskPage,
                        enter   = fadeIn(tween(VigiaMotion.ENTER_MS)),
                        exit    = fadeOut(tween(VigiaMotion.EXIT_MS)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Column {
                            // Compact suggestion pills above the input — tapping prefills
                            // the box (Perplexity style). Hidden once the user has text.
                            AnimatedVisibility(
                                visible = landingInput.isBlank(),
                                enter   = fadeIn(tween(VigiaMotion.ENTER_MS)),
                                exit    = fadeOut(tween(VigiaMotion.EXIT_MS)),
                            ) {
                                SuggestionPillRow(
                                    onPrefill = { query ->
                                        landingInput = query
                                        landingFocus.requestFocus()
                                    },
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                            }
                            LandingChatBox(
                                hazeState      = hazeState,
                                text           = landingInput,
                                onTextChange   = { landingInput = it },
                                onSubmit       = onSendMessage,
                                onMicClick     = { showVoice = true },
                                focusRequester = landingFocus,
                            )
                        }
                    }
                }
            }
        }

        // Full-screen voice surface — slides up over everything when the mic is tapped.
        AnimatedVisibility(
            visible = showVoice,
            enter   = fadeIn(tween(VigiaMotion.ENTER_MS)) +
                      slideInVertically(spring(dampingRatio = VigiaMotion.gentle.dampingRatio,
                                               stiffness = VigiaMotion.gentle.stiffness)) { it / 6 },
            exit    = fadeOut(tween(VigiaMotion.EXIT_MS)) +
                      slideOutVertically(tween(VigiaMotion.EXIT_MS)) { it / 6 },
        ) {
            VoiceCallOverlay(onEnd = { showVoice = false })
        }
      }
    }
}

// ── Top bars ──────────────────────────────────────────────────────────────────

/**
 * Landing page top bar: hamburger (opens session drawer) | navigation pill
 * (Ask VIGIA · Alerts · Maps · Wallet) | profile avatar.
 */
@Composable
private fun LandingTopBar(
    pagerState: PagerState,
    onMenuClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TopBarIconButton(
            icon               = Icons.Filled.Menu,
            contentDescription = "Open session history",
            onClick            = onMenuClick,
        )

        SlidingTabPill(
            pagerState = pagerState,
            tabs = listOf(
                TabIcon(Icons.Filled.Search, "Ask VIGIA"),
                TabIcon(Icons.Filled.Notifications, "Alerts"),
                TabIcon(Icons.Filled.Place, "VigiaMaps"),
                TabIcon(Icons.Filled.AccountBalanceWallet, "Wallet"),
            ),
        )

        TopBarIconButton(
            icon               = Icons.Filled.Person,
            contentDescription = "Open profile",
            onClick            = {},
            containerColor     = MaterialTheme.colorScheme.primaryContainer,
            iconTint           = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Active chat top bar: X (back to landing) | Answers/Sources/Map tab pill |
 * overflow menu. Mirrors Perplexity's three-slot structure.
 */
@Composable
private fun ChatTopBar(
    pagerState: PagerState,
    onClose: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TopBarIconButton(
            icon               = Icons.Filled.Close,
            contentDescription = "New chat",
            onClick            = onClose,
        )

        SlidingTabPill(
            pagerState = pagerState,
            tabs = listOf(
                TabIcon(Icons.Filled.Home, "Answers"),
                TabIcon(Icons.Filled.Info, "Sources"),
                TabIcon(Icons.Filled.Place, "Map"),
            ),
        )

        TopBarIconButton(
            icon               = Icons.Filled.MoreVert,
            contentDescription = "More options",
            onClick            = {},
        )
    }
}

private data class TabIcon(val icon: ImageVector, val contentDescription: String)

/**
 * Segmented pill whose selection bubble SLIDES with the pager. The bubble offset
 * reads [PagerState.currentPageOffsetFraction] inside the layout-phase `offset`
 * lambda, so it tracks finger-drag every frame with zero recompositions. Tapping
 * an icon animates the pager to that page.
 */
@Composable
private fun SlidingTabPill(
    pagerState: PagerState,
    tabs: List<TabIcon>,
    modifier: Modifier = Modifier,
) {
    val scope      = rememberCoroutineScope()
    val scheme     = MaterialTheme.colorScheme
    val itemSize   = 40.dp
    val itemSizePx = with(LocalDensity.current) { itemSize.toPx() }

    Surface(
        color    = scheme.surfaceVariant,
        shape    = CircleShape,
        border   = BorderStroke(1.dp, scheme.outline),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.padding(4.dp)) {
            // Sliding selection bubble (drawn behind the icons).
            Box(
                modifier = Modifier
                    .offset {
                        val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        IntOffset((position * itemSizePx).roundToInt(), 0)
                    }
                    .size(itemSize)
                    .clip(CircleShape)
                    .background(scheme.secondaryContainer)
                    .border(BorderStroke(1.dp, scheme.outline), CircleShape),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    val selected    = pagerState.currentPage == index
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(itemSize)
                            .pressScale(interaction, pressedScale = 0.88f)
                            .clip(CircleShape)
                            .clickable(interactionSource = interaction, indication = null) {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                            .semantics { contentDescription = tab.contentDescription },
                    ) {
                        Icon(
                            imageVector        = tab.icon,
                            contentDescription = null,
                            tint = if (selected) scheme.onSecondaryContainer
                                   else scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Bordered circular icon button used for the top-bar side actions. */
@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        color    = containerColor,
        shape    = CircleShape,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(44.dp)
            .pressScale(interaction, pressedScale = 0.90f)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = iconTint,
            modifier           = Modifier.size(44.dp).padding(11.dp),
        )
    }
}

// ── Drawer ────────────────────────────────────────────────────────────────────

@Composable
private fun VigiaDrawer(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    onNewChat: () -> Unit,
    onLoadSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSignOut: () -> Unit,
    accountName: String?,
    accountEmail: String?,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(24.dp))

            Text(
                text     = "VIGIA",
                style    = MaterialTheme.typography.displaySmall,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Text(
                text     = accountName ?: accountEmail ?: "Your road copilot",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 28.dp),
            )

            Spacer(Modifier.height(20.dp))

            NavigationDrawerItem(
                label    = { Text("New chat", style = MaterialTheme.typography.titleSmall) },
                icon     = { Icon(Icons.Filled.Add, contentDescription = null) },
                selected = false,
                onClick  = onNewChat,
                colors   = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor      = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedTextColor      = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(
                color    = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Spacer(Modifier.height(8.dp))

            if (sessions.isEmpty()) {
                Text(
                    text     = "Your conversations will appear here",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
            } else {
                val now     = System.currentTimeMillis()
                val dayMs   = 86_400_000L
                val weekMs  = 7 * dayMs
                val today    = remember(sessions) { sessions.filter { now - it.updatedAt < dayMs } }
                val thisWeek = remember(sessions) { sessions.filter { now - it.updatedAt in dayMs until weekMs } }
                val older    = remember(sessions) { sessions.filter { now - it.updatedAt >= weekMs } }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier       = Modifier.weight(1f),
                ) {
                    if (today.isNotEmpty()) {
                        item(key = "header_today") { SessionGroupHeader("Today") }
                        items(today, key = { it.id }) { session ->
                            SessionRow(
                                session  = session,
                                isActive = session.id == activeSessionId,
                                onSelect = { onLoadSession(session.id) },
                                onDelete = { onDeleteSession(session.id) },
                            )
                        }
                    }
                    if (thisWeek.isNotEmpty()) {
                        item(key = "header_week") { SessionGroupHeader("This week") }
                        items(thisWeek, key = { it.id }) { session ->
                            SessionRow(
                                session  = session,
                                isActive = session.id == activeSessionId,
                                onSelect = { onLoadSession(session.id) },
                                onDelete = { onDeleteSession(session.id) },
                            )
                        }
                    }
                    if (older.isNotEmpty()) {
                        item(key = "header_older") { SessionGroupHeader("Older") }
                        items(older, key = { it.id }) { session ->
                            SessionRow(
                                session  = session,
                                isActive = session.id == activeSessionId,
                                onSelect = { onLoadSession(session.id) },
                                onDelete = { onDeleteSession(session.id) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color    = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Spacer(Modifier.height(8.dp))

            DrawerFooterItem(icon = Icons.Filled.Settings, label = "Settings")
            DrawerFooterItem(icon = Icons.Filled.Info, label = "Help & feedback")
            DrawerFooterItem(
                icon    = Icons.AutoMirrored.Filled.Logout,
                label   = "Sign out",
                showSoon = false,
                onClick = onSignOut,
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SessionGroupHeader(label: String) {
    Text(
        text     = label,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
    )
}

@Composable
private fun SessionRow(
    session: ChatSession,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Column {
                Text(
                    text     = session.title.ifBlank { "New conversation" },
                    style    = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = relativeTime(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        selected = isActive,
        onClick  = onSelect,
        badge = {
            IconButton(
                onClick  = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Close,
                    contentDescription = "Delete this chat",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(16.dp),
                )
            }
        },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

private fun relativeTime(epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    return when {
        diff < 60_000L      -> "Just now"
        diff < 3_600_000L   -> "${diff / 60_000}m ago"
        diff < 86_400_000L  -> "${diff / 3_600_000}h ago"
        diff < 604_800_000L -> "${diff / 86_400_000}d ago"
        else                -> "${diff / 604_800_000}w ago"
    }
}

@Composable
private fun DrawerFooterItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    showSoon: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (showSoon) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            ) {
                Text(
                    text     = "Soon",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

// ── Atmosphere ────────────────────────────────────────────────────────────────

@Composable
private fun Atmosphere(modifier: Modifier = Modifier) {
    val sky    = MaterialTheme.vigiaColors.atmosphereSky
    val peach  = MaterialTheme.vigiaColors.atmospherePeach
    val violet = MaterialTheme.vigiaColors.atmosphereViolet
    Canvas(modifier = modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(sky, Color.Transparent),
                center = Offset(size.width * 0.10f, size.height * 0.05f),
                radius = size.width * 0.95f,
            ),
            center = Offset(size.width * 0.10f, size.height * 0.05f),
            radius = size.width * 0.95f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(peach, Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.22f),
                radius = size.width * 0.80f,
            ),
            center = Offset(size.width * 0.95f, size.height * 0.22f),
            radius = size.width * 0.80f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(violet, Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.92f),
                radius = size.width * 0.85f,
            ),
            center = Offset(size.width * 0.15f, size.height * 0.92f),
            radius = size.width * 0.85f,
        )
    }
}

// ── Landing content ───────────────────────────────────────────────────────────

/**
 * Landing/home pane: centered wordmark + orb + status, with alert cards and
 * suggestion chips that scroll freely above the bottom chat box.
 */
/**
 * Switches the landing body between the four top-bar destinations. Backed by the
 * shared [HorizontalPager] state so swiping slides between pages and the top-bar
 * bubble tracks the drag.
 */
@Composable
private fun LandingPager(
    pagerState: PagerState,
    state: CopilotUiState.Active,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        when (LandingTab.entries[page]) {
            LandingTab.Ask -> LandingContent(
                state    = state,
                modifier = Modifier.fillMaxSize(),
            )
            LandingTab.Alerts -> AlertsPane(
                alerts   = state.pendingAlerts,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            )
            LandingTab.Map -> MapPane(
                markers  = state.spatialMarkers,
                modifier = Modifier.fillMaxSize(),
            )
            LandingTab.Wallet -> WalletPane(
                payoutStatus = state.payoutStatus,
                modifier     = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LandingContent(
    state: CopilotUiState.Active,
    modifier: Modifier = Modifier,
) {
    // Hero is vertically centred and the orb is large — the suggestion pills now
    // live in the bottom bar, freeing this space (Perplexity-style empty canvas).
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            // leave room for the bottom pills + input box
            .padding(bottom = 150.dp),
    ) {
        Text(
            text  = "vigia",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = if (state.devicePresent) "Blackbox linked" else "Your road copilot",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        AiOrb(state = state.orbState, size = 168.dp)
        Spacer(Modifier.height(20.dp))
        StatusCaption(state = state)

        // Up to two alert cards beneath the orb, if any are active.
        if (state.pendingAlerts.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            state.pendingAlerts.take(2).forEach { alert ->
                AlertCard(
                    alert    = alert,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusCaption(state: CopilotUiState.Active, modifier: Modifier = Modifier) {
    val caption = when {
        state.isSearchStreaming && state.searchStep.isNotEmpty() -> state.searchStep
        state.isSearchStreaming            -> "Thinking…"
        state.orbState == OrbState.Alert   -> "Hazard detected"
        state.orbState == OrbState.Offline -> "Reconnecting to Blackbox…"
        state.devicePresent                -> "All clear — monitoring"
        else                               -> "Ready"
    }
    // Fixed height — caption swaps never shift the layout below (CLS guard).
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        Crossfade(targetState = caption, label = "status_caption") { text ->
            Text(
                text     = text,
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Chat content (Answers / Sources / Map) ────────────────────────────────────

@Composable
private fun ChatContent(
    pagerState: PagerState,
    state: CopilotUiState.Active,
    sessionMessages: List<ChatMessage>,
    displaySources: List<SearchEvent.Source>,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        when (ChatTab.entries[page]) {
            ChatTab.Answers -> AnswersPane(
                state           = state,
                sessionMessages = sessionMessages,
                modifier        = Modifier.fillMaxSize(),
            )
            ChatTab.Sources -> SourcesPane(
                sources  = displaySources,
                modifier = Modifier.fillMaxSize(),
            )
            ChatTab.Map -> MapPane(
                markers  = state.spatialMarkers,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Thread view for the Answers tab: user bubbles (right) + assistant bubbles
 * (left/full) + skeleton shimmer while VIGIA is thinking + streaming tail.
 */
@Composable
private fun AnswersPane(
    state: CopilotUiState.Active,
    sessionMessages: List<ChatMessage>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding      = PaddingValues(
            start  = 16.dp,
            end    = 16.dp,
            top    = 8.dp,
            bottom = 100.dp,  // clear the FollowUpBar
        ),
        modifier = modifier,
    ) {
        items(
            items       = sessionMessages,
            key         = { it.id },
            contentType = { it.role.name },
        ) { message ->
            if (message.role == MessageRole.USER) {
                UserBubble(message = message, modifier = Modifier.animateItem())
            } else {
                AssistantBubble(message = message, modifier = Modifier.animateItem())
            }
        }

        // Skeleton shimmer: no tokens yet but the stream is active
        if (state.isSearchStreaming && state.searchAnswer.isEmpty()) {
            item(key = "skeleton", contentType = "skeleton") {
                SkeletonThinkingCard(modifier = Modifier.animateItem())
            }
        }

        // Streaming tail: tokens are arriving — show live AnswerCard
        // Condition is isSearchStreaming-only (not searchAnswer.isNotEmpty()) so
        // it disappears the instant Done fires: Room has already written the
        // ASSISTANT row by then, giving a clean handoff with no blank window.
        if (state.isSearchStreaming && state.searchAnswer.isNotEmpty()) {
            item(key = "streaming_tail", contentType = "streaming") {
                AnswerCard(
                    answer      = state.searchAnswer,
                    sources     = state.searchSources,
                    steps       = state.completedSteps,
                    latencyMs   = state.totalLatencyMs,
                    isStreaming = true,
                    isPartial   = false,
                    modifier    = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * Three-bar shimmer card shown while VIGIA is thinking but has not yet emitted
 * any tokens — mirrors the Perplexity skeleton loading animation.
 */
@Composable
private fun SkeletonThinkingCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
        ),
        label = "shimmer_offset",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(bottom = 14.dp),
        ) {
            CircularProgressIndicator(
                modifier    = Modifier.size(16.dp),
                color       = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text  = "Thinking…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val shimmerColors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant,
        )

        repeat(3) { index ->
            val widthFraction = if (index == 2) 0.65f else 1f
            Box(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .fillMaxWidth(widthFraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.linearGradient(
                            colors     = shimmerColors,
                            start      = Offset(shimmer * 800f - 400f, 0f),
                            end        = Offset(shimmer * 800f, 0f),
                        ),
                    ),
            )
        }
    }
}

// ── Sources pane ─────────────────────────────────────────────────────────────

@Composable
private fun SourcesPane(
    sources: List<SearchEvent.Source>,
    modifier: Modifier = Modifier,
) {
    if (sources.isEmpty()) {
        EmptyState(
            icon     = Icons.Filled.Info,
            title    = "No sources yet",
            body     = "VIGIA's answer sources will appear here once it responds.",
            modifier = modifier,
        )
    } else {
        LazyColumn(
            contentPadding      = PaddingValues(
                start  = 16.dp,
                end    = 16.dp,
                top    = 8.dp,
                bottom = 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier            = modifier,
        ) {
            item(key = "sources_header") {
                Text(
                    text     = "${sources.size} source${if (sources.size == 1) "" else "s"}",
                    style    = MaterialTheme.typography.labelLarge,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            items(sources, key = { it.id }) { source ->
                SourceCard(source = source, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: SearchEvent.Source,
    modifier: Modifier = Modifier,
) {
    Surface(
        color           = MaterialTheme.vigiaColors.glassSurface,
        shape           = MaterialTheme.shapes.large,
        shadowElevation = 1.dp,
        modifier        = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(16.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector        = Icons.Filled.Info,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(36.dp).padding(9.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = source.label.ifBlank { domainOf(source) },
                    style    = MaterialTheme.typography.titleSmall,
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text     = domainOf(source),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (source.trustLevel.isNotEmpty()) {
                    Text(
                        text  = source.trustLevel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ── Bottom input — landing ────────────────────────────────────────────────────

/**
 * Full-width chat box for the landing page. Matches Perplexity's large rounded-
 * rectangle input at the bottom: multiline text area + action row with send circle.
 */
@Composable
private fun LandingChatBox(
    hazeState: HazeState,
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onMicClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val onSend: () -> Unit = {
        if (text.isNotBlank()) {
            onSubmit(text.trim())
            onTextChange("")
            keyboardController?.hide()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .hazeEffect(hazeState, rememberGlassStyle())
            .border(
                BorderStroke(1.dp, MaterialTheme.vigiaColors.glassBorder),
                RoundedCornerShape(20.dp),
            ),
    ) {
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            TextField(
                value         = text,
                onValueChange = onTextChange,
                placeholder   = {
                    Text(
                        text  = "Talk to VIGIA",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine      = false,
                maxLines        = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor  = Color.Transparent,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor        = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
                    cursorColor             = MaterialTheme.colorScheme.primary,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier  = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            ) {
                // Attach placeholder (non-functional — future UX hookup)
                IconButton(
                    onClick  = {},
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "Attach file" },
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Add,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                // Mic — opens the full-screen voice surface.
                val micInteraction = remember { MutableInteractionSource() }
                Surface(
                    color    = Color.Transparent,
                    shape    = CircleShape,
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(40.dp)
                        .pressScale(micInteraction, pressedScale = 0.88f)
                        .clip(CircleShape)
                        .clickable(interactionSource = micInteraction, indication = null) { onMicClick() }
                        .semantics { contentDescription = "Voice input" },
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Mic,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(40.dp).padding(10.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Send button — filled circle when there is text, bordered.
                val sendInteraction = remember { MutableInteractionSource() }
                val hasText = text.isNotBlank()
                IconButton(
                    onClick           = onSend,
                    enabled           = hasText,
                    interactionSource = sendInteraction,
                    modifier = Modifier
                        .size(40.dp)
                        .pressScale(sendInteraction, pressedScale = 0.88f)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Send message" },
                ) {
                    Surface(
                        color  = if (hasText) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.surfaceVariant,
                        shape  = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = if (hasText) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Bottom input — follow-up ──────────────────────────────────────────────────

/**
 * Persistent follow-up bar for the active chat view. Pill-shaped, glass-blurred.
 * While streaming: tapping the orb cancels the request.
 * While idle with text: tapping the arrow sends the follow-up.
 */
@Composable
private fun FollowUpBar(
    hazeState: HazeState,
    isStreaming: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val onSend = remember(onSubmit, keyboardController) {
        {
            if (text.isNotBlank()) {
                onSubmit(text.trim())
                text = ""
                keyboardController?.hide()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .hazeEffect(hazeState, rememberGlassStyle())
            .border(
                BorderStroke(1.dp, MaterialTheme.vigiaColors.glassBorder),
                RoundedCornerShape(28.dp),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            TextField(
                value         = text,
                onValueChange = { text = it },
                placeholder   = {
                    Text(
                        text  = if (isStreaming) "VIGIA is responding…" else "Ask follow-up…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                enabled         = !isStreaming,
                singleLine      = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor    = Color.Transparent,
                    unfocusedContainerColor  = Color.Transparent,
                    disabledContainerColor   = Color.Transparent,
                    focusedIndicatorColor    = Color.Transparent,
                    unfocusedIndicatorColor  = Color.Transparent,
                    disabledIndicatorColor   = Color.Transparent,
                    focusedTextColor         = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor       = MaterialTheme.colorScheme.onSurface,
                    cursorColor              = MaterialTheme.colorScheme.primary,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier  = Modifier.weight(1f),
            )

            // Right control: orb (stop) while streaming; send arrow when text present
            AnimatedContent(
                targetState = isStreaming,
                transitionSpec = {
                    (fadeIn(tween(VigiaMotion.ENTER_MS)) +
                     scaleIn(initialScale = 0.75f, animationSpec = spring(
                         dampingRatio = VigiaMotion.snappy.dampingRatio,
                         stiffness    = VigiaMotion.snappy.stiffness,
                     ))).togetherWith(fadeOut(tween(VigiaMotion.EXIT_MS)))
                },
                label = "followup_send_swap",
            ) { streaming ->
                if (streaming) {
                    IconButton(
                        onClick  = onCancel,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = "Stop response" },
                    ) {
                        AiOrb(state = OrbState.Searching, size = 40.dp)
                    }
                } else {
                    val sendInteraction = remember { MutableInteractionSource() }
                    val hasText = text.isNotBlank()
                    IconButton(
                        onClick           = onSend,
                        enabled           = hasText,
                        interactionSource = sendInteraction,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .pressScale(sendInteraction, pressedScale = 0.88f)
                            .semantics { contentDescription = "Send follow-up" },
                    ) {
                        Surface(
                            color = if (hasText) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = if (hasText) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Thread bubbles ────────────────────────────────────────────────────────────

@Composable
private fun UserBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.End,
        modifier              = modifier.fillMaxWidth(),
    ) {
        Surface(
            color  = MaterialTheme.vigiaColors.glassSurface,
            shape  = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.vigiaColors.glassBorder),
        ) {
            Text(
                text     = message.body,
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun AssistantBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    AnswerCard(
        answer      = message.body,
        sources     = message.sources.map {
            SearchEvent.Source(
                id         = it.id,
                label      = it.label,
                trustLevel = it.trustLevel,
                url        = it.url,
            )
        },
        steps       = message.reasoningSteps,
        latencyMs   = message.latencyMs,
        isStreaming = false,
        isPartial   = message.status == MessageStatus.Partial,
        modifier    = modifier,
    )
}

// ── Alerts ────────────────────────────────────────────────────────────────────

@Composable
private fun AlertsPane(alerts: List<HazardAlert>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Alerts",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        if (alerts.isEmpty()) {
            EmptyState(
                icon  = Icons.Filled.CheckCircle,
                title = "All clear",
                body  = "No active hazards on your route. VIGIA will alert you the moment something changes.",
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding      = PaddingValues(bottom = 100.dp),
            ) {
                items(alerts, key = { it.id }) { alert ->
                    AlertCard(alert = alert, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: HazardAlert, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ext    = MaterialTheme.vigiaColors

    val (container, content, accent) = when (alert.severity) {
        HazardAlert.Severity.CRITICAL ->
            Triple(scheme.errorContainer, scheme.onErrorContainer, scheme.error)
        HazardAlert.Severity.HIGH ->
            Triple(ext.warningContainer, ext.onWarningContainer, ext.warningAccent)
        HazardAlert.Severity.MEDIUM ->
            Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary)
        HazardAlert.Severity.LOW ->
            Triple(scheme.surfaceVariant, scheme.onSurfaceVariant, scheme.outline)
    }
    val title = when (alert.severity) {
        HazardAlert.Severity.CRITICAL -> "Critical hazard"
        HazardAlert.Severity.HIGH     -> "Caution ahead"
        HazardAlert.Severity.MEDIUM   -> "Heads up"
        HazardAlert.Severity.LOW      -> "Notice"
    }
    val icon = if (alert.severity == HazardAlert.Severity.CRITICAL ||
        alert.severity == HazardAlert.Severity.HIGH
    ) Icons.Filled.Warning else Icons.Filled.Info

    Surface(
        color    = container,
        shape    = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${alert.severity.name} alert: ${alert.messageText}" },
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = accent,
                modifier           = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = alert.messageText,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = content,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Map pane ──────────────────────────────────────────────────────────────────

@Composable
private fun MapPane(markers: List<SearchEvent.SpatialMarker>, modifier: Modifier = Modifier) {
    if (markers.isEmpty()) {
        EmptyState(
            icon     = Icons.Filled.Place,
            title    = "No mapped hazards yet",
            body     = "Ask VIGIA about your route — detected hazards and points of interest will appear here.",
            modifier = modifier,
        )
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding      = PaddingValues(
                start  = 16.dp,
                end    = 16.dp,
                top    = 8.dp,
                bottom = 100.dp,
            ),
            modifier = modifier,
        ) {
            items(markers, key = { it.id }) { marker ->
                MarkerCard(marker = marker, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun MarkerCard(marker: SearchEvent.SpatialMarker, modifier: Modifier = Modifier) {
    Surface(
        color           = MaterialTheme.vigiaColors.glassSurface,
        shape           = MaterialTheme.shapes.large,
        shadowElevation = 1.dp,
        modifier        = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector        = Icons.Filled.Place,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text  = marker.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (marker.summary.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = marker.summary,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = remember(marker) { "%.4f, %.4f".format(marker.lat, marker.lng) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Profile pane ──────────────────────────────────────────────────────────────

@Composable
private fun ProfilePane(state: CopilotUiState.Active, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(24.dp))

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
        ) {
            Icon(
                imageVector        = Icons.Filled.Person,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier           = Modifier
                    .size(88.dp)
                    .padding(20.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text  = "VIGIA Rider",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))

        ProfileRow(
            label    = "Blackbox",
            value    = if (state.devicePresent) "Linked" else "Not linked",
            dotColor = if (state.devicePresent) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        ProfileRow(
            label    = "Active alerts",
            value    = state.pendingAlerts.size.toString(),
            dotColor = if (state.pendingAlerts.isEmpty()) MaterialTheme.colorScheme.outline
                       else MaterialTheme.vigiaColors.warningAccent,
        )
        Spacer(Modifier.height(12.dp))
        ProfileRow(label = "Build", value = "VIGIA Copilot · Demo", dotColor = null)
    }
}

@Composable
private fun ProfileRow(label: String, value: String, dotColor: Color?) {
    Surface(
        color           = MaterialTheme.vigiaColors.glassSurface,
        shape           = MaterialTheme.shapes.large,
        shadowElevation = 1.dp,
        modifier        = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text  = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Wallet pane ───────────────────────────────────────────────────────────────

@Composable
private fun WalletPane(payoutStatus: PayoutStatus, modifier: Modifier = Modifier) {
    val (headline, detail, accentDot) = when (payoutStatus) {
        PayoutStatus.Idle ->
            Triple("Wallet ready", "No pending payouts. Earnings from verified hazard reports land here.", false)
        PayoutStatus.AwaitingOnboarding ->
            Triple("Finish setup", "Connect a payout account to start receiving earnings.", true)
        PayoutStatus.OnboardingInProgress ->
            Triple("Setup in progress", "We're verifying your payout details — this usually takes a moment.", true)
        is PayoutStatus.OnboardingComplete ->
            Triple("Payouts enabled", "Account ${payoutStatus.accountId.take(12)}… is ready to receive funds.", false)
        is PayoutStatus.PaymentPending ->
            Triple(
                "Payout pending",
                "%.2f %s on its way to your account.".format(
                    payoutStatus.amountCents / 100.0,
                    payoutStatus.currency.uppercase(),
                ),
                true,
            )
        is PayoutStatus.PaymentSucceeded ->
            Triple("Payout sent", "Charge ${payoutStatus.chargeId.take(12)}… completed successfully.", false)
        is PayoutStatus.Failed ->
            Triple("Payout issue", payoutStatus.userMessage, true)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text  = "Wallet",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        Surface(
            color           = MaterialTheme.vigiaColors.glassSurface,
            shape           = MaterialTheme.shapes.extraLarge,
            shadowElevation = 2.dp,
            modifier        = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(20.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier           = Modifier.size(48.dp).padding(12.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (accentDot) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.vigiaColors.warningAccent),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text  = headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp),
    ) {
        Surface(
            color           = MaterialTheme.vigiaColors.glassSurface,
            shape           = CircleShape,
            shadowElevation = 1.dp,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier
                    .size(64.dp)
                    .padding(16.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text      = title,
            style     = MaterialTheme.typography.headlineSmall,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = body,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Answer card ───────────────────────────────────────────────────────────────

@Composable
private fun AnswerCard(
    answer: String,
    sources: List<SearchEvent.Source>,
    steps: List<String>,
    latencyMs: Long,
    isStreaming: Boolean,
    isPartial: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val formatted = remember(answer) { formatAnswer(answer) }
    var stepsExpanded by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val context   = LocalContext.current
    val onCopy = remember(answer, clipboard) {
        { clipboard.setText(AnnotatedString(answer)) }
    }
    val onShare = remember(answer, context) {
        {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, answer)
            }
            context.startActivity(Intent.createChooser(sendIntent, null))
        }
    }

    // Flat layout — assistant answers render directly on the canvas (Perplexity
    // style), no card surface. Only user messages get a pill.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
            ReasoningHeader(
                steps       = steps,
                latencyMs   = latencyMs,
                isStreaming = isStreaming,
                expanded    = stepsExpanded,
                onToggle    = { stepsExpanded = !stepsExpanded },
            )

            AnimatedVisibility(
                visible = stepsExpanded && steps.isNotEmpty(),
                enter   = fadeIn(tween(VigiaMotion.ENTER_MS)) +
                          expandVertically(spring(dampingRatio = 0.85f, stiffness = 400f)),
                exit    = fadeOut(tween(VigiaMotion.EXIT_MS)) +
                          shrinkVertically(tween(VigiaMotion.EXIT_MS)),
            ) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    steps.forEach { step ->
                        Text(
                            text     = "•  $step",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text  = formatted,
                style = AnswerBodyStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (isPartial) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Filled.Warning,
                        contentDescription = null,
                        tint               = MaterialTheme.vigiaColors.warningAccent,
                        modifier           = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = "Connection lost — partial response",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.vigiaColors.warningAccent,
                    )
                }
            }

            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = remember(sources.size) {
                        "Reviewed ${sources.size} source" + if (sources.size == 1) "" else "s"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sources, key = { it.id }) { source ->
                        SourceChip(label = remember(source) { domainOf(source) })
                    }
                }
            }

            if (!isStreaming) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill(label = "Copy", onClick = onCopy)
                    GlassPill(label = "Share", onClick = onShare)
                }
            }
    }
}

@Composable
private fun ReasoningHeader(
    steps: List<String>,
    latencyMs: Long,
    isStreaming: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val canExpand        = !isStreaming && steps.isNotEmpty()
    val toggleInteraction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(
                enabled           = canExpand,
                interactionSource = toggleInteraction,
                indication        = null,
            ) { onToggle() }
            .semantics {
                contentDescription =
                    if (expanded) "Hide reasoning steps" else "Show reasoning steps"
            },
    ) {
        if (isStreaming) {
            CircularProgressIndicator(
                modifier    = Modifier.size(16.dp),
                color       = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text     = steps.lastOrNull() ?: "Thinking…",
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Icon(
                imageVector        = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint               = MaterialTheme.vigiaColors.success,
                modifier           = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            val summary = remember(steps.size, latencyMs) {
                buildString {
                    append(steps.size)
                    append(" reasoning step")
                    if (steps.size != 1) append('s')
                    append(" completed")
                    if (latencyMs > 0) {
                        append(" · ")
                        append("%.1fs".format(latencyMs / 1000.0))
                    }
                }
            }
            Text(
                text     = summary,
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (canExpand) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                                  else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun domainOf(source: SearchEvent.Source): String =
    source.url
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore("/")
        .ifEmpty { source.label }

@Composable
private fun SourceChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GlassPill(label: String, onClick: () -> Unit) {
    val pressInteraction = remember { MutableInteractionSource() }
    Surface(
        color    = MaterialTheme.colorScheme.surfaceVariant,
        shape    = CircleShape,
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .pressScale(pressInteraction, pressedScale = 0.94f)
            .clip(CircleShape)
            .clickable(
                interactionSource = pressInteraction,
                indication        = null,
            ) { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text     = label,
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }
}

private fun formatAnswer(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.lines()
    lines.forEachIndexed { index, line ->
        val text = if (line.startsWith("- ")) "•  " + line.removePrefix("- ") else line
        var rest = text
        while (true) {
            val open = rest.indexOf("**")
            if (open < 0) { append(rest); break }
            val close = rest.indexOf("**", open + 2)
            if (close < 0) { append(rest); break }
            append(rest.substring(0, open))
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(rest.substring(open + 2, close))
            }
            rest = rest.substring(close + 2)
        }
        if (index < lines.lastIndex) append('\n')
    }
}

// ── Suggestions ───────────────────────────────────────────────────────────────

private data class Suggestion(
    val title: String,
    val query: String,
    val icon: ImageVector,
)

private val Suggestions = listOf(
    Suggestion(
        title = "Road conditions",
        query = "What are the road conditions ahead on my route?",
        icon  = Icons.Filled.Place,
    ),
    Suggestion(
        title = "Hazards near me",
        query = "Any hazards reported near my location?",
        icon  = Icons.Filled.Warning,
    ),
    Suggestion(
        title = "Weather on route",
        query = "What's the weather along my route for the next hour?",
        icon  = Icons.Filled.Cloud,
    ),
)

/** Horizontally scrollable row of compact suggestion pills (Perplexity style). */
@Composable
private fun SuggestionPillRow(
    onPrefill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding        = PaddingValues(horizontal = 4.dp),
        modifier              = modifier.fillMaxWidth(),
    ) {
        items(Suggestions, key = { it.query }) { suggestion ->
            SuggestionPill(
                label   = suggestion.title,
                icon    = suggestion.icon,
                onClick = { onPrefill(suggestion.query) },
            )
        }
    }
}

@Composable
private fun SuggestionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        color    = MaterialTheme.vigiaColors.glassSurface,
        shape    = CircleShape,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text  = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── Glass style ───────────────────────────────────────────────────────────────

@Composable
private fun rememberGlassStyle(): HazeStyle {
    val surface = MaterialTheme.colorScheme.surface
    val tint    = MaterialTheme.vigiaColors.glassTint
    return remember(surface, tint) {
        HazeStyle(
            backgroundColor = surface,
            tint            = HazeTint(tint),
            blurRadius      = 24.dp,
            noiseFactor     = 0.04f,
        )
    }
}
