package com.demostenes.tvhome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    val allApps = state.apps
    val hiddenSet = state.hidden
    val showHidden = remember { mutableStateOf(false) }

    val visibleApps = remember(allApps, hiddenSet, showHidden.value) {
        if (showHidden.value) allApps else allApps.filter { it.packageName !in hiddenSet }
    }
    val appByPkg = remember(allApps) { allApps.associateBy { it.packageName } }
    val dockApps = remember(state.favorites, appByPkg, hiddenSet) {
        state.favorites.mapNotNull { pkg ->
            if (pkg in hiddenSet) null else appByPkg[pkg]
        }
    }

    var focusedApp by remember { mutableStateOf<AppInfo?>(null) }
    var contextTarget by remember { mutableStateOf<AppInfo?>(null) }
    var inputsDialog by remember { mutableStateOf(false) }
    var movingPkg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dockApps, visibleApps) {
        if (focusedApp == null) {
            focusedApp = dockApps.firstOrNull() ?: visibleApps.firstOrNull()
        }
    }

    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.loaded) {
        if (state.loaded) {
            delay(80)
            runCatching { firstFocus.requestFocus() }
        }
    }

    LaunchedEffect(state.homeTick) {
        if (state.homeTick > 0) {
            contextTarget = null
            inputsDialog = false
            movingPkg = null
            scope.launch { listState.animateScrollToItem(0) }
            runCatching { firstFocus.requestFocus() }
        }
    }

    BackHandler(enabled = true) {
        when {
            contextTarget != null -> contextTarget = null
            inputsDialog -> inputsDialog = false
            movingPkg != null -> movingPkg = null
            showHidden.value -> showHidden.value = false
            else -> scope.launch {
                listState.animateScrollToItem(0)
                runCatching { firstFocus.requestFocus() }
            }
        }
    }

    val accentColor by animateColorAsState(
        targetValue = focusedApp?.color ?: Color(0xFF1E293B),
        animationSpec = tween(550),
        label = "accent",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
            .onPreviewKeyEvent { ev ->
                if (movingPkg != null && ev.type == KeyEventType.KeyDown) {
                    val pkg = movingPkg!!
                    when (ev.key) {
                        Key.DirectionLeft -> {
                            state.moveFavorite(pkg, -1)
                            true
                        }
                        Key.DirectionRight -> {
                            state.moveFavorite(pkg, +1)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.Back, Key.Escape -> {
                            movingPkg = null
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        // Fundo atmosferico estilo tvOS (Crossfade suave do blur do app focado + gradiente escuro)
        AmbientBackdrop(focusedApp = focusedApp, accentColor = accentColor)

        val rows = remember(visibleApps) { visibleApps.chunked(5) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 64.dp),
        ) {
            item(key = "top_bar") {
                TopHeader(
                    movingPkg = movingPkg,
                    showingHidden = showHidden.value,
                    hiddenCount = hiddenSet.size,
                    onInputs = { inputsDialog = true },
                    onToggleHidden = { showHidden.value = !showHidden.value },
                    onSettings = { actions.openSettings() },
                )
            }

            item(key = "hero_stage") {
                TopShelfStage(
                    app = focusedApp,
                    isFavorite = focusedApp?.packageName in state.favorites,
                    isMoving = movingPkg != null,
                )
            }

            if (dockApps.isNotEmpty()) {
                item(key = "dock_section") {
                    DockShelf(
                        apps = dockApps,
                        firstFocus = firstFocus,
                        movingPkg = movingPkg,
                        onFocus = { focusedApp = it },
                        onClick = { app ->
                            if (movingPkg != null) movingPkg = null
                            else actions.launch(app.packageName)
                        },
                        onMenu = { app -> contextTarget = app },
                    )
                }
            }

            item(key = "grid_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp, end = 56.dp, top = 26.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (showHidden.value) "Todos os Aplicativos (incluindo ocultos)" else "Todos os Aplicativos",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${visibleApps.size}",
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 14.sp,
                    )
                }
            }

            items(rows.size, key = { idx -> "row_$idx" }) { rowIdx ->
                val rowApps = rows[rowIdx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 56.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    for ((colIdx, app) in rowApps.withIndex()) {
                        val assignFirst = dockApps.isEmpty() && rowIdx == 0 && colIdx == 0
                        AppTile(
                            app = app,
                            width = 162.dp,
                            height = 94.dp,
                            isHidden = app.packageName in hiddenSet,
                            isMoving = false,
                            modifier = if (assignFirst) Modifier.focusRequester(firstFocus) else Modifier,
                            onFocus = { focusedApp = app },
                            onClick = { actions.launch(app.packageName) },
                            onMenu = { contextTarget = app },
                        )
                    }
                }
            }
        }

        if (contextTarget != null) {
            val target = contextTarget!!
            AppOptionsOverlay(
                app = target,
                isFavorite = target.packageName in state.favorites,
                isHidden = target.packageName in hiddenSet,
                onDismiss = { contextTarget = null },
                onOpen = {
                    contextTarget = null
                    actions.launch(target.packageName)
                },
                onToggleFavorite = {
                    state.toggleFavorite(target.packageName)
                    contextTarget = null
                },
                onStartMove = {
                    if (target.packageName !in state.favorites) {
                        state.toggleFavorite(target.packageName)
                    }
                    movingPkg = target.packageName
                    contextTarget = null
                },
                onToggleHidden = {
                    state.toggleHidden(target.packageName)
                    contextTarget = null
                },
                onAppInfo = {
                    contextTarget = null
                    actions.appInfo(target.packageName)
                },
                onUninstall = {
                    contextTarget = null
                    actions.uninstall(target.packageName)
                },
            )
        }

        if (inputsDialog) {
            InputsOverlay(
                inputs = remember { actions.inputs() },
                onDismiss = { inputsDialog = false },
                onSelect = { item ->
                    inputsDialog = false
                    actions.openInput(item)
                },
            )
        }
    }
}

@Composable
private fun AmbientBackdrop(focusedApp: AppInfo?, accentColor: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.52f),
                        accentColor.copy(alpha = 0.18f),
                        Color(0xFF07090E),
                    ),
                    center = Offset(size.width * 0.68f, size.height * 0.24f),
                    radius = size.width * 0.72f,
                )
            )
        }

        Crossfade(
            targetState = focusedApp,
            animationSpec = tween(420),
            label = "backdrop_xfade",
        ) { app ->
            if (app != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Blur leve usando o bitmap reduzido com interpolacao bilinear
                    Image(
                        bitmap = app.blur,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.30f },
                    )
                    // Vinheta cinematografica estilo Apple TV
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color(0xFF07090E).copy(alpha = 0.35f),
                                    0.48f to Color(0xFF07090E).copy(alpha = 0.72f),
                                    1.0f to Color(0xFF07090E).copy(alpha = 0.96f),
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TopHeader(
    movingPkg: String?,
    showingHidden: Boolean,
    hiddenCount: Int,
    onInputs: () -> Unit,
    onToggleHidden: () -> Unit,
    onSettings: () -> Unit,
) {
    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("EEE, d MMM   HH:mm", Locale.forLanguageTag("pt-BR"))
        while (true) {
            clockText = fmt.format(Date()).replaceFirstChar { it.uppercase() }
            delay(15_000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, top = 28.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "TV Home",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (movingPkg != null) {
                Spacer(Modifier.width(18.dp))
                Text(
                    text = "Modo organizar: use ◀ ▶ para mover e OK para concluir",
                    color = Color(0xFFFBBF24),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PillButton(
                iconRes = R.drawable.ic_input,
                label = "Entradas",
                onClick = onInputs,
            )
            if (hiddenCount > 0 || showingHidden) {
                PillButton(
                    iconRes = R.drawable.ic_edit,
                    label = if (showingHidden) "Ocultar ($hiddenCount)" else "Ocultos ($hiddenCount)",
                    onClick = onToggleHidden,
                )
            }
            PillButton(
                iconRes = R.drawable.ic_settings,
                label = "Ajustes",
                onClick = onSettings,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = clockText,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PillButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.07f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pill_scale",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(
                if (focused) Color.White else Color.White.copy(alpha = 0.11f)
            )
            .border(
                width = 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50),
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .focusable(interactionSource = interaction)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (focused) Color(0xFF0B0F17) else Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = if (focused) Color(0xFF0B0F17) else Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TopShelfStage(
    app: AppInfo?,
    isFavorite: Boolean,
    isMoving: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(horizontal = 56.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (isFavorite) "NO DOCK • APPLE TV STYLE" else "APLICATIVO",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = app?.label ?: "TV Home",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isMoving) {
                    "Use as setas ◀ ▶ para reposicionar no Dock • Pressione OK para salvar"
                } else {
                    "Pressione OK para abrir  •  Segure OK ou aperte MENU para opções"
                },
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 14.sp,
            )
        }

        if (app != null) {
            Crossfade(
                targetState = app,
                animationSpec = tween(320),
                label = "stage_card",
            ) { current ->
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .aspectRatio(16f / 9f)
                        .shadow(24.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(current.color.copy(alpha = 0.85f))
                        .border(
                            BorderStroke(1.5.dp, Color.White.copy(alpha = 0.22f)),
                            RoundedCornerShape(22.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (current.hasBanner) {
                        Image(
                            bitmap = current.image,
                            contentDescription = current.label,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Image(
                            bitmap = current.image,
                            contentDescription = current.label,
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.Medium,
                            modifier = Modifier.size(96.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockShelf(
    apps: List<AppInfo>,
    firstFocus: FocusRequester,
    movingPkg: String?,
    onFocus: (AppInfo) -> Unit,
    onClick: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 42.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(vertical = 18.dp),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(apps, key = { "dock_${it.packageName}" }) { app ->
                    val isFirst = app == apps.first()
                    AppTile(
                        app = app,
                        width = 176.dp,
                        height = 102.dp,
                        isHidden = false,
                        isMoving = movingPkg == app.packageName,
                        modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier,
                        onFocus = { onFocus(app) },
                        onClick = { onClick(app) },
                        onMenu = { onMenu(app) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppInfo,
    width: Dp,
    height: Dp,
    isHidden: Boolean,
    isMoving: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.13f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "tile_scale",
    )
    val elevation by animateDpAsState(
        targetValue = if (focused) 22.dp else 4.dp,
        animationSpec = tween(180),
        label = "tile_elev",
    )
    val shape = RoundedCornerShape(20.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(width),
    ) {
        Box(
            modifier = Modifier
                .size(width, height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = if (focused) -6f else 0f
                    alpha = if (isHidden) 0.45f else 1f
                }
                .shadow(elevation, shape)
                .clip(shape)
                .background(app.color)
                .border(
                    width = if (isMoving) 3.dp else if (focused) 2.5.dp else 1.dp,
                    color = when {
                        isMoving -> Color(0xFFFBBF24)
                        focused -> Color.White
                        else -> Color.White.copy(alpha = 0.14f)
                    },
                    shape = shape,
                )
                .onFocusChanged { if (it.isFocused) onFocus() }
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown &&
                        (ev.key == Key.Menu || ev.key == Key.Bookmark || ev.key == Key.Guide || ev.key == Key.Info)
                    ) {
                        onMenu()
                        true
                    } else {
                        false
                    }
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onMenu,
                )
                .focusable(interactionSource = interaction)
                .drawWithContent {
                    drawContent()
                    // Brilho especular superior tipo tvOS quando em foco
                    if (focused) {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.24f),
                                    Color.Transparent,
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width * 0.7f, size.height * 0.8f),
                            )
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (app.hasBanner) {
                Image(
                    bitmap = app.image,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    bitmap = app.image,
                    contentDescription = app.label,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.size(58.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = app.label,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppOptionsOverlay(
    app: AppInfo,
    isFavorite: Boolean,
    isHidden: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStartMove: () -> Unit,
    onToggleHidden: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(40)
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF151922))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))

            DialogActionItem(
                label = "Abrir aplicativo",
                modifier = Modifier.focusRequester(firstFocus),
                onClick = onOpen,
            )
            DialogActionItem(
                label = if (isFavorite) "Remover do Dock (Favoritos)" else "Fixar no Dock (Favoritos)",
                onClick = onToggleFavorite,
            )
            if (isFavorite) {
                DialogActionItem(
                    label = "Mover posição no Dock",
                    onClick = onStartMove,
                )
            }
            DialogActionItem(
                label = if (isHidden) "Mostrar novamente na grade" else "Ocultar da tela inicial",
                onClick = onToggleHidden,
            )
            DialogActionItem(
                label = "Informações do sistema (forçar parada / cache)",
                onClick = onAppInfo,
            )
            DialogActionItem(
                label = "Desinstalar",
                onClick = onUninstall,
            )
            DialogActionItem(
                label = "Cancelar",
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun InputsOverlay(
    inputs: List<InputItem>,
    onDismiss: () -> Unit,
    onSelect: (InputItem) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(40)
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF151922))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Entradas da TV e HDMI",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            inputs.forEachIndexed { idx, item ->
                DialogActionItem(
                    label = item.label,
                    modifier = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onSelect(item) },
                )
            }
            DialogActionItem(
                label = "Fechar",
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun DialogActionItem(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.07f))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = if (focused) Color(0xFF0B0F17) else Color.White,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
