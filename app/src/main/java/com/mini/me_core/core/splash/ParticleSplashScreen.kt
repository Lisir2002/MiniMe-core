package com.mini.me_core.core.splash

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mini.me_core.core.theme.LocalAnimationScale
import com.mini.me_core.core.theme.ScaledAnimation
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 纯函数：启动画面是否应当退场。
 *
 * 三条退场路径（满足任一即退）：
 * 1. 播放达到质量档总时长（硬上限，防初始化异常时无限等待）；
 * 2. 初始化就绪（[AppInitState.isReady]）且已展示最短时长；
 * 3. 用户点跳过（由调用方直接置 isDismissing，不经本函数）。
 */
internal fun shouldDismissSplash(
    elapsedMs: Long,
    minDisplayMs: Long,
    isReady: Boolean,
    totalDurationMs: Long,
): Boolean = elapsedMs >= totalDurationMs || (elapsedMs >= minDisplayMs && isReady)

/** 启动阶段 → 阶段文案（F1.7 由时间轴硬编码改为真实阶段映射）。 */
private fun stageTextRes(phase: StartupPhase): Int = when (phase) {
    StartupPhase.STARTING, StartupPhase.DATA_LAYER -> R.string.splash_stage_data
    StartupPhase.CORE_SERVICES -> R.string.splash_stage_ai
    StartupPhase.ASYNC_WARMUP -> R.string.splash_stage_ui
}

/**
 * Particle splash screen host.
 *
 * Handles:
 * - F1.1: Quality detection → particle count / duration
 * - F1.5: Upgraded skip button (pill shape, outline, pressed scale, last-2s error color)
 * - F1.6: Cross-fade transition during FADE_OUT stage
 * - F1.7: Bottom progress bar + stage text (real startup phases, not fake timeline)
 * - A1: Exit gate = init ready (AppInitState) AND min display; total duration as hard cap
 * - A3: Honors LocalAnimationScale (0 = skip animation entirely)
 *
 * @param onDismiss Called when animation completes (or skip is pressed).
 */
@Composable
fun ParticleSplashScreen(
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current.density
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val animScale = LocalAnimationScale.current

    // Detect device quality level
    val quality = remember { SplashQualityLevel.detect(context) }

    // Animation elapsed time in ms
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var isDismissing by remember { mutableStateOf(false) }
    val overlayAlpha = remember { Animatable(1f) }

    // Screen size
    var screenWidthPx by remember { mutableStateOf(0f) }
    var screenHeightPx by remember { mutableStateOf(0f) }

    // Particle system and glass shatter
    var particleSystem by remember { mutableStateOf<ParticleSystem?>(null) }
    var glassShatter by remember { mutableStateOf<GlassShatterEffect?>(null) }

    // Skip button press state
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val skipScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = ScaledAnimation.tween(100),
        label = "skip_scale"
    )
    var skipButtonVisible by remember { mutableStateOf(true) }

    // B5: 跳过按钮倒计时按秒派生——elapsedMs 每帧变，但剩余秒数每秒才变一次，
    // derivedStateOf 让按钮子树只在整秒边界重组（原每帧重组）。
    val remainingSec by remember { derivedStateOf { SplashStage.remainingSeconds(elapsedMs, quality) } }
    val isUrgent by remember { derivedStateOf { remainingSec <= 2 && remainingSec > 0 } }

    // A2: 真实启动阶段（MiniMeCore 三阶段上报）与就绪态。
    // startupReady 由主循环每帧从 AppInitState.isReady 采样（volatile 读，无重组成本），
    // 翻 true 时触发一次重组，让进度条平滑走到 1.0。
    val startupPhase by StartupProgress.phase.collectAsState()
    var startupReady by remember { mutableStateOf(AppInitState.isReady) }
    val animDisabled = animScale <= 0f

    // F1.7 + A2: 进度条目标值 = 真实阶段权重；animateFloatAsState 平滑过渡。
    val progress by animateFloatAsState(
        targetValue = StartupProgress.progressOf(startupPhase, startupReady),
        animationSpec = ScaledAnimation.tween(400, scale = animScale),
        label = "splash_progress"
    )
    val stageResId = stageTextRes(startupPhase)

    // B5: 百分比文本按 100ms 采样——progress 是每帧动画值，直接在组合里读
    // 会让 Text 每帧重组；协程采样（快照读，不订阅重组）则最多 10 次/秒。
    var displayedPercent by remember { mutableIntStateOf(100) }
    LaunchedEffect(Unit) {
        while (isActive) {
            displayedPercent = (progress * 100).roundToInt()
            delay(100)
        }
    }

    // Main animation loop
    LaunchedEffect(screenWidthPx, screenHeightPx, animDisabled) {
        if (screenWidthPx <= 0f || screenHeightPx <= 0f) return@LaunchedEffect
        if (animDisabled) {
            // A3：用户关闭动效——跳过粒子动画，就绪即退（500ms 淡出 tween 时长为 0，瞬间交接）。
            AppInitState.awaitReady()
            isDismissing = true
            return@LaunchedEffect
        }
        if (particleSystem == null) {
            // B4: 构造（文本位图采样 + O(n²) 半平面裁剪）移到 Default 线程，
            // 不再占冷启动关键路径的主线程；首帧前无 Canvas，底层先显示纯色背景。
            val (ps, gs) = withContext(Dispatchers.Default) {
                val ps = ParticleSystem(screenWidthPx, screenHeightPx, density, quality)
                val gs = if (quality.enable3D) {
                    GlassShatterEffect(screenWidthPx, screenHeightPx, density, quality.shardCount)
                } else {
                    null
                }
                ps to gs
            }
            particleSystem = ps
            glassShatter = gs
        }

        var startNanos = 0L
        var lastNanos = 0L
        while (isActive && !isDismissing) {
            val frameNanos = withFrameNanos { it }
            if (startNanos == 0L) {
                startNanos = frameNanos
                lastNanos = frameNanos
            }
            // A3: dt 乘动效缩放（scale>1 更慢；<=0 已在上方短路，不会到这里）。
            val dt = (frameNanos - lastNanos) / 1_000_000f * animScale
            lastNanos = frameNanos
            elapsedMs = (frameNanos - startNanos) / 1_000_000L
            if (AppInitState.isReady && !startupReady) startupReady = true
            particleSystem?.update(elapsedMs, dt)

            // A1: 就绪即退 + 最短展示 + 总时长硬上限。
            if (shouldDismissSplash(
                    elapsedMs,
                    MIN_DISPLAY_MS,
                    startupReady,
                    SplashStage.totalDurationMs(quality),
                )
            ) {
                isDismissing = true
            }
        }
    }

    // F1.6: Cross-fade — overlay fades out, main content underneath fades in
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            skipButtonVisible = false
            overlayAlpha.animateTo(0f, ScaledAnimation.tween(500, scale = animScale))
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .alpha(overlayAlpha.value)
            .onSizeChanged { size ->
                screenWidthPx = size.width.toFloat()
                screenHeightPx = size.height.toFloat()
            },
    ) {
        // Canvas layer
        val ps = particleSystem
        if (ps != null) {
            // Background refraction blur (API 31+): during the first 400ms of SHATTER,
            // blur the canvas layer to simulate glass fracture refraction.
            // Low versions fall back to the frost overlay drawn inside ParticleSplashCanvas.
            val shatterStartMs = SplashStage.holdEndMs(quality)
            val blurWindowMs = 400L
            val inBlurWindow = elapsedMs in shatterStartMs..(shatterStartMs + blurWindowMs)
            val blurT = if (inBlurWindow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (elapsedMs - shatterStartMs).toFloat() / blurWindowMs
            } else 1f

            ParticleSplashCanvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (inBlurWindow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            // Blur ramps from 8f → 0 over 400ms
                            val radius = 8f * (1f - blurT)
                            renderEffect = RenderEffect
                                .createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        } else {
                            renderEffect = null
                        }
                    },
                particleSystem = ps,
                glassShatter = glassShatter,
                elapsedMs = elapsedMs,
                backgroundColor = colors.background,
                primaryColor = colors.primary,
                onBackgroundColor = colors.onBackground,
                density = density,
                quality = quality,
            )
        }

        // F1.7: Bottom progress indicator
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(stageResId),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(200.dp)
                    .height(2.dp),
                color = colors.primary,
                trackColor = colors.onSurfaceVariant.copy(alpha = 0.2f),
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$displayedPercent%",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }

        // F1.5: Upgraded skip button (pill shape, top-right)
        if (skipButtonVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .scale(skipScale),
                color = colors.surface.copy(alpha = if (isUrgent) 0.8f else 0.5f),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 0.dp,
                onClick = {
                    skipButtonVisible = false
                    isDismissing = true
                },
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.splash_skip),
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "$remainingSec",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUrgent) colors.error else colors.primary,
                        )
                    }
                }
            }
        }
    }
}
