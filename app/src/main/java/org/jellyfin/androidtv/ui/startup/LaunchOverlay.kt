package org.jellyfin.androidtv.ui.startup

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R

internal fun launchTrace(event: String) {
    if (BuildConfig.DEBUG) timber.log.Timber.tag("ArrStreamerLaunch").d("${android.os.SystemClock.uptimeMillis()} $event")
}
internal object LaunchTransfer {
    var sequence: LaunchSequence? = null
    fun take(): LaunchSequence? = sequence.also { sequence = null }
}

/** The whole bitmap fades on a native view; transfer happens only after fade-in completes. */
internal class LaunchSequence(motion: Boolean, cold: Boolean) {
    private val animated = motion && cold
    private var started = false
    @Volatile var complete = !animated
        private set
    private val finished = mutableListOf<() -> Unit>()
    fun start(image: ImageView) {
        if (started || !animated) return
        started = true
        // Upload the actual bitmap before its first visible animation frame.
        image.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        image.buildLayer()
        image.afterDraw {
        launchTrace("whole logo fade-in begins")
        image.animate().alpha(1f)
            .setDuration(LaunchTimeline.DURATION_MS.toLong())
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction {
                complete = true
                launchTrace("whole logo fade-in finished")
                finished.toList().also { finished.clear() }.forEach { it() }
            }.start()
        }
    }
    fun onComplete(action: () -> Unit) { if (complete) action() else finished += action }
    suspend fun awaitFadeIn() = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
        val done = { if (continuation.isActive) continuation.resume(Unit) { _, _, _ -> }; Unit }
        onComplete(done)
        continuation.invokeOnCancellation { finished.remove(done) }
    }
}

/** Runs after actual drawing, not merely after layout or a scheduled vsync. */
internal fun View.afterDraw(action: () -> Unit) {
	val observer = viewTreeObserver
	var drawn = false
	val hardwareCommit = Build.VERSION.SDK_INT >= 29 && isHardwareAccelerated
	// Register before traversal: registering inside onDraw applies to a later frame,
	// which a static/cached surface is not required to produce.
	if (hardwareCommit) observer.registerFrameCommitCallback { post(action) }
	val listener = object : ViewTreeObserver.OnDrawListener {
		override fun onDraw() {
			if (drawn || width == 0 || height == 0) return
			drawn = true
			if (!hardwareCommit) post(action)
			post { if (observer.isAlive) observer.removeOnDrawListener(this) }
		}
	}
	observer.addOnDrawListener(listener)
	invalidate()
}

/** A single native image avoids Compose/UI-thread animation stalls during Activity creation. */
internal class LaunchOverlay(private val activity: ComponentActivity, private val root: ViewGroup) {
    val view = FrameLayout(activity).apply {
        isFocusable = false
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setBackgroundColor(android.graphics.Color.rgb(8, 12, 16))
    }
    private val image = ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    lateinit var sequence: LaunchSequence
        private set
    private var ending = false
    private var ready = false
    init {
        val density = activity.resources.displayMetrics.density
        view.addView(image, FrameLayout.LayoutParams((400 * density).toInt(), (116.164f * density).toInt(), android.view.Gravity.CENTER))
        root.addView(view, ViewGroup.LayoutParams(-1, -1))
        launchTrace("cover attached ${activity.javaClass.simpleName}")
    }
    fun animate(host: StartupActivity) = hold(LaunchSequence(launchMotionEnabled(activity), host.animateColdStart), Build.VERSION.SDK_INT >= 31)
    fun hold(shared: LaunchSequence, deferStart: Boolean = false) {
        sequence = shared
        image.setImageResource(R.drawable.app_logo)
        image.alpha = if (shared.complete) 1f else 0f
        if (!deferStart) view.afterDraw { shared.start(image) }
    }
    fun nativeHandoff(remove: () -> Unit) {
        view.afterDraw { remove(); sequence.start(image) }
    }
    fun watchContent(manager: FragmentManager, accept: (Fragment) -> Boolean = { true }) {
        val callback = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: android.os.Bundle?) {
                if (!accept(f)) return
                manager.unregisterFragmentLifecycleCallbacks(this)
                revealAfterDraw(v)
            }
        }
        manager.registerFragmentLifecycleCallbacks(callback, true)
    }
    fun revealAfterDraw(content: View) = content.afterDraw {
        if (!ready) {
            ready = true
            // Finish the requested fade-in; no additional hold or readiness timer.
            sequence.onComplete(::revealContent)
        }
    }
    private fun revealContent() {
        if (ending || view.parent == null) return
        ending = true
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        view.buildLayer()
        view.afterDraw {
        launchTrace("destination frame committed; cover fade begins ${activity.javaClass.simpleName}")
        view.animate().alpha(0f).setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .setDuration(if (launchMotionEnabled(activity)) LaunchTimeline.TRANSITION_MS.toLong() else 0L)
            .withEndAction {
                launchTrace("cover removed ${activity.javaClass.simpleName}")
                root.removeView(view)
            }.start()
        }
    }
}
