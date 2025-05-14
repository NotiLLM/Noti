package org.muilab.notigpt.view.component.notification

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.service.NotiListenerService
import org.muilab.notigpt.util.hasTransparentPixels
import org.muilab.notigpt.util.replaceChars
import org.muilab.notigpt.view.component.notification.action.NotiActionBar
import org.muilab.notigpt.view.component.notification.action.NotiFeedbackDropdown
import org.muilab.notigpt.view.component.notification.info.ExpandedNotiInfo
import org.muilab.notigpt.view.utils.NotiExpandState
import org.muilab.notigpt.viewModel.DrawerViewModel
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotiCard(context: Context, notiUnit: NotiUnit, drawerViewModel: DrawerViewModel, notiViewed: MutableState<Boolean>, viewedInfos: MutableSet<Long>) {

    var notiTopViewed by remember { mutableStateOf(false) }
    var notiBottomViewed by remember { mutableStateOf(false) }

    val notiKey = notiUnit.notiKey
    val pinned = notiUnit.isPinned
    val wholeNotiRead = notiUnit.wholeNotiRead
    val notiOverallTitle = notiUnit.title
    val isPeople = notiUnit.isPeople
    val pkgName = notiUnit.pkgName
    val appName = notiUnit.appName
    val bitmap = notiUnit.bitmap
    val largeBitmap = notiUnit.largeBitmap

    val summary = notiUnit.summary
    val hasSummary = summary.isNotEmpty()

    val backgroundColor = when {
        pinned -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val timeColor = when {
        !wholeNotiRead -> MaterialTheme.colorScheme.error
        pinned -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val notiBody = notiUnit.getNotiBody().toCollection(ArrayList())
    var requiresExpansion by remember { mutableStateOf(
        notiBody.size > 1 || hasSummary ||
                (notiBody.size == 1 && notiBody[0].getDisplayedTitle(pkgName, isPeople)
                    .let { notiOverallTitle.isNotBlank() && notiOverallTitle != it }))
    }


    var contentHeightPx by remember { mutableIntStateOf(0) }
    var latestMessageHeightPx by remember { mutableIntStateOf(0) }
    var maxContentHeightPx by remember { mutableFloatStateOf(0f) }
    val maxHeightDp = 200.dp
    val notiInfoGapDp = 4.dp

    val expansionProgress: (Float, Float) -> Float = { offset, maxHeight ->
        offset.coerceIn(0F, maxHeight) / maxOf(maxHeight, 1F)
    }
    val COLLAPSE_THRESHOLD = 20f

    val coroutineScope = rememberCoroutineScope()

    val density = LocalDensity.current
    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = NotiExpandState.Collapsed,
            anchors = DraggableAnchors {
                NotiExpandState.Collapsed at 0f
                NotiExpandState.Opened at maxContentHeightPx
            },
            positionalThreshold = { distance: Float -> distance * 0.5f },
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            decayAnimationSpec = exponentialDecay(),
            velocityThreshold = { with(density) { 80.dp.toPx() } }
        )
    }

    var isInitialDragDownward by remember { mutableStateOf(false) }
    var hasStartedDragging by remember { mutableStateOf(false) }

    val customDragHandler = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                isInitialDragDownward = false
                hasStartedDragging = false
            },
            onDrag = { change, dragAmount ->
                if (!hasStartedDragging) {
                    hasStartedDragging = true
                    isInitialDragDownward = if (abs(dragAmount.y) > abs(dragAmount.x)) {
                        dragAmount.y > 0
                    } else {
                        false
                    }
                }

                if (isInitialDragDownward)
                    anchoredDraggableState.dispatchRawDelta(dragAmount.y)
                change.consume()
            },
            onDragEnd = {
                if (hasStartedDragging && isInitialDragDownward) {
                    coroutineScope.launch {
                        anchoredDraggableState.animateTo(
                            if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD) {
                                Log.d("Expand", "Open on Drag")
                                NotiExpandState.Opened
                            } else {
                                Log.d("Expand", "Close on Drag")
                                NotiExpandState.Collapsed
                            }
                        )
                    }
                }
                hasStartedDragging = false
            }
        )
    }

    val isDropdownMenuExpanded = remember { mutableStateOf(false) }

    val showSummary = {
        anchoredDraggableState.offset < COLLAPSE_THRESHOLD && hasSummary
    }

    Card(
        modifier = Modifier
            .padding(vertical = 1.dp, horizontal = 20.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = {

                    val contentIntent = NotiListenerService.getContentIntent(context, notiUnit)

                    if (contentIntent != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 = API 34
                            val options = ActivityOptions.makeBasic().apply {
                                pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            }

                            try {
                                contentIntent.send(
                                    /* context = */ context,
                                    /* code = */ 0,
                                    /* intent = */ null,
                                    /* onFinished = */ null,
                                    /* handler = */ null,
                                    /* requiredPermission = */ null,
                                    /* options = */ options.toBundle()
                                )
                            } catch (e: Exception) {
                                Log.e("AccessNotification", "PendingIntent send failed", e)
                                // Fallback launch
                            }
                        } else {
                            try {
                                contentIntent.send()
                            } catch (e: Exception) {
                                // Fallback launch
                            }
                        }
                    }


                    Log.d("NotiListenerService", "Sent intent")
                    if (!pinned)
                        drawerViewModel.actOnNoti(notiKey, "access_click")
                },
                onLongClick = {
                    isDropdownMenuExpanded.value = true
                }
            )
            .onGloballyPositioned { coordinates ->
                val windowBounds = coordinates.boundsInWindow()
                notiTopViewed =
                    notiTopViewed || windowBounds.top >= 0 && windowBounds.top < windowBounds.height
            }
            .onGloballyPositioned { coordinates ->
                val windowBounds = coordinates.boundsInWindow()
                notiBottomViewed =
                    notiBottomViewed || windowBounds.bottom > 0 && windowBounds.bottom <= windowBounds.height
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        NotiFeedbackDropdown(context, notiUnit, isDropdownMenuExpanded)

        val progress = expansionProgress(anchoredDraggableState.offset, maxContentHeightPx)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
        ) {
            Column(
                Modifier.padding(start = 2.dp, end = 3.dp),
            ) {
                // Use remember to optimize bitmap selection logic
                val imageToDisplay = remember(bitmap, largeBitmap, anchoredDraggableState.offset) {
                    if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD && largeBitmap != null) {
                        largeBitmap.asImageBitmap()
                    } else {
                        bitmap?.asImageBitmap()
                    }
                }

                // Use remember to optimize transparent pixel check
                val hasTransparency = remember(bitmap) {
                    bitmap != null && hasTransparentPixels(bitmap, 0.1f)
                }

                if (showSummary())
                    Spacer(Modifier.size(3.dp))

                if (imageToDisplay != null) {
                    if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD && largeBitmap != null) {
                        // Render the large image when expanded
                        Image(
                            bitmap = imageToDisplay,
                            contentDescription = "Notification Icon",
                            modifier = Modifier
                                .size((35 + 15 * progress).dp)
                                .padding(vertical = 3.dp, horizontal = 6.dp)
                        )
                    } else {
                        // Render the icon if bitmap has transparency, otherwise render the image
                        if (hasTransparency) {
                            Icon(
                                bitmap = imageToDisplay,
                                contentDescription = "Notification Icon",
                                modifier = Modifier
                                    .size((35 + 15 * progress).dp)
                                    .padding(vertical = 3.dp, horizontal = 6.dp),
                                tint = contentColorFor(backgroundColor)
                            )
                        } else {
                            Image(
                                bitmap = imageToDisplay,
                                contentDescription = "Notification Icon",
                                modifier = Modifier
                                    .size((35 + 15 * progress).dp)
                                    .padding(vertical = 3.dp, horizontal = 6.dp)
                            )
                        }
                    }
                }
                if (progress == 0f && notiUnit.outcome.similarityScore > 0)
                    Text(
                        String.format("%.3f", notiUnit.outcome.similarityScore),
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(0.dp),
                        fontSize = 8.sp,
                    )
            }

            Column (Modifier.align(Alignment.TopEnd)) {

                Row(
                    Modifier
                        .wrapContentHeight()
                        .padding(start = 35.dp)) {
                    if (showSummary()) {
                        Text(
                            summary,
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 5.dp)
                                .align(Alignment.CenterVertically),
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Column(
                            Modifier
                                .padding(start = (5 + 15 * progress).dp, end = 5.dp)
                                .weight(1f)
                        ) {
                            Row(Modifier.fillMaxWidth()) {
                                if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD) {
                                    Text(
                                        text = appName,
                                        fontSize = (10 + progress * 4).sp
                                    )
                                    Spacer(Modifier.weight(1F))
                                } else {
                                    Text(
                                        modifier = Modifier
                                            .background(Color.Transparent)
                                            .weight(1F),
                                        text = if (notiOverallTitle == "null") appName else notiOverallTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 14.sp,
                                        onTextLayout = { textLayoutResult ->
                                            if (textLayoutResult.hasVisualOverflow)
                                                requiresExpansion = true
                                        }
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(timeColor, RoundedCornerShape(16.dp))
                                ) {
                                    Text(
                                        modifier = Modifier.padding(horizontal = 5.dp),
                                        text = notiUnit.getRelLatestTimeStr(),
                                        maxLines = 1,
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = contentColorFor(timeColor)
                                    )
                                }
                            }

                            Row {
                                if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD) {
                                    Text(
                                        modifier = Modifier
                                            .background(Color.Transparent),
                                        text = if (notiOverallTitle == "null") "" else notiOverallTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        maxLines = if (anchoredDraggableState.offset < COLLAPSE_THRESHOLD) 1 else Int.MAX_VALUE,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = (14 + progress * 2).sp,
                                        onTextLayout = { textLayoutResult ->
                                            if (textLayoutResult.hasVisualOverflow)
                                                requiresExpansion = true
                                        }
                                    )
                                } else {
                                    val notiContent = notiBody.last().content
                                    Text(
                                        modifier = Modifier.background(Color.Transparent),
                                        text = if (notiContent == "null") "" else replaceChars(
                                            notiContent
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        onTextLayout = { textLayoutResult ->
                                            if (textLayoutResult.hasVisualOverflow)
                                                requiresExpansion = true
                                        },
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    Column {
                        if (pinned) {
                            Icon(
                                painter = painterResource(R.drawable.pin_no),
                                "Pin",
                                Modifier
                                    .size(15.dp)
                                    .align(Alignment.End)
                                    .rotate(45f)
                            )
                        } else {
                            Spacer(Modifier.size(7.dp))
                        }
                        if (requiresExpansion) {
                            Icon(
                                painter = if (progress < 0.5f)
                                    painterResource(R.drawable.expand_circle_down)
                                else
                                    painterResource(R.drawable.expand_circle_up),
                                "Expand",
                                Modifier
                                    .size(25.dp)
                                    .align(Alignment.CenterHorizontally)
                                    .then(customDragHandler)
                                    .clickable {
                                        if (!hasStartedDragging) {
                                            coroutineScope.launch {
                                                anchoredDraggableState.animateTo(
                                                    if (anchoredDraggableState.offset < COLLAPSE_THRESHOLD) {
                                                        Log.d("Expand", "Open on Click")
                                                        NotiExpandState.Opened
                                                    } else {
                                                        Log.d("Expand", "Close on Click")
                                                        NotiExpandState.Collapsed
                                                    }
                                                )
                                            }
                                        }
                                    }
                            )
                        } else {
                            Spacer(Modifier.size(25.dp))
                        }
                    }
                    Spacer(modifier = Modifier.padding(5.dp))
                }

                NotiActionBar(notiUnit, drawerViewModel)

                if (requiresExpansion) {

                    // Update maxContentHeightPx based on contentHeightPx
                    LaunchedEffect(contentHeightPx) {
                        val maxHeightPx = with(density) { maxHeightDp.toPx() }
                        maxContentHeightPx = minOf(contentHeightPx.toFloat(), maxHeightPx)
                        anchoredDraggableState.updateAnchors(
                            DraggableAnchors {
                                NotiExpandState.Collapsed at 0f
                                NotiExpandState.Opened at maxContentHeightPx
                            }
                        )
                    }

                    val currentHeightPx =
                        anchoredDraggableState.offset.coerceIn(0f, maxContentHeightPx)

                    Column(
                        modifier = Modifier
                            .height(with(density) { currentHeightPx.toDp() })
                            .clipToBounds()
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = Color.White
                        )

                        // Scrollable content
                        val scrollState = rememberScrollState()

                        val isGroup = (listOf(notiOverallTitle)
                                + notiBody.map { it.getDisplayedTitle(pkgName, isPeople) })
                            .filter { it.isNotBlank() }
                            .toSet().size > 1

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .onSizeChanged { size ->
                                    contentHeightPx = size.height
                                }
                        ) {
                            notiBody.forEachIndexed { idx, noti ->

                                val notiTitle = noti.getDisplayedTitle(pkgName, isPeople)
                                val prevTitle = if (idx == 0)
                                    notiOverallTitle
                                else
                                    notiBody[idx - 1].getDisplayedTitle(pkgName, isPeople)
                                val notiTime = noti.time
                                val notiContent = noti.content
                                val notiSeen = noti.notiSeen
                                val newTitle =
                                    (notiTitle != prevTitle && notiTitle.isNotBlank() && prevTitle.isNotBlank())
                                val showTitle = isGroup && newTitle

                                val infoTimeColor = when {
                                    !notiSeen -> MaterialTheme.colorScheme.error
                                    pinned -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }

                                if (showTitle)
                                    Spacer(modifier = Modifier.height(notiInfoGapDp))

                                if (idx == notiBody.size - 1) {
                                    Box(
                                        modifier = Modifier.onSizeChanged { size ->
                                            latestMessageHeightPx = size.height
                                        }
                                    ) {
                                        ExpandedNotiInfo(
                                            notiTitle,
                                            notiTime,
                                            notiContent,
                                            notiSeen,
                                            showTitle,
                                            infoTimeColor,
                                            viewedInfos
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(notiInfoGapDp))
                                } else {
                                    ExpandedNotiInfo(
                                        notiTitle,
                                        notiTime,
                                        notiContent,
                                        notiSeen,
                                        showTitle,
                                        infoTimeColor,
                                        viewedInfos
                                    )
                                }
                            }
                        }

                        // Scroll to the appropriate position based on requirements
                        LaunchedEffect(contentHeightPx, maxContentHeightPx, latestMessageHeightPx) {
                            val maxHeightPx = with(density) { maxHeightDp.toPx() }
                            val notiInfoGapPx = with(density) { notiInfoGapDp.toPx() }
                            if (contentHeightPx > maxHeightPx) {
                                val targetScroll = if (latestMessageHeightPx >= maxHeightPx) {
                                    contentHeightPx - latestMessageHeightPx - notiInfoGapPx
                                } else {
                                    contentHeightPx - maxHeightPx - notiInfoGapPx
                                }
                                scrollState.scrollTo(maxOf(targetScroll.toInt(), 0))
                            } else {
                                scrollState.scrollTo(0)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(notiTopViewed, notiBottomViewed) {
        if (notiTopViewed && notiBottomViewed && !notiUnit.wholeNotiRead && !requiresExpansion) {
            notiViewed.value = true
            for (noti in notiBody)
                noti.notiSeen = true
            viewedInfos.clear()
        }
    }
}

@Composable
fun ScoreDisplay(notiUnit: NotiUnit) {
    Row {
        Text(
            text = String.format("%.2f", notiUnit.sortScore),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Cyan
        )
        Spacer(Modifier.padding(5.dp))
    }
}

@Composable
fun isKeyboardVisible(): Boolean {
    val view = LocalView.current
    val rootView = (LocalContext.current as Activity).window.decorView
    var isKeyboardVisible by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.height
            val keypadHeight = screenHeight - rect.bottom
            isKeyboardVisible = keypadHeight > screenHeight * 0.15
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)

        onDispose {
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    return isKeyboardVisible
}