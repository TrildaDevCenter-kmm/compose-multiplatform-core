/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.events.EventTargetListener
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.toComposeEvent
import androidx.compose.ui.input.pointer.BrowserCursor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.composeButton
import androidx.compose.ui.input.pointer.composeButtons
import androidx.compose.ui.native.ComposeLayer
import androidx.compose.ui.platform.DefaultInputModeManager
import androidx.compose.ui.platform.WebTextInputService
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfoImpl
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.coroutines.coroutineContext
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.skiko.SkiaLayer
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.DOMRect
import org.w3c.dom.DOMRectReadOnly
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLStyleElement
import org.w3c.dom.HTMLTitleElement
import org.w3c.dom.MediaQueryListEvent
import org.w3c.dom.TouchEvent
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent

private val actualDensity
    get() = window.devicePixelRatio

private abstract external class ExtendedTouchEvent : TouchEvent {
    val force: Double
}

internal interface ComposeWindowState {
    fun init() {}
    fun sizeFlow(): Flow<IntSize>

    val globalEvents: EventTargetListener

    fun dispose() {
        globalEvents.dispose()
    }

    companion object {
        fun createFromLambda(lambda: suspend () -> IntSize): ComposeWindowState {
            return object : ComposeWindowState {
                override val globalEvents = EventTargetListener(window)
                override fun sizeFlow(): Flow<IntSize> = flow {
                    while (coroutineContext.isActive) {
                        emit(lambda())
                    }
                }
            }
        }
    }
}

internal class DefaultWindowState(private val viewportContainer: Element) : ComposeWindowState {
    private val channel = Channel<IntSize>(CONFLATED)

    override val globalEvents = EventTargetListener(window)

    override fun init() {

        globalEvents.addDisposableEvent("resize") {
            channel.trySend(getParentContainerBox())
        }

        initMediaEventListener {
            channel.trySend(getParentContainerBox())
        }

        channel.trySend(getParentContainerBox())
    }

    private fun getParentContainerBox(): IntSize {
        return IntSize(viewportContainer.clientWidth, viewportContainer.clientHeight)
    }

    private fun initMediaEventListener(handler: (Double) -> Unit) {
        val contentScale = actualDensity
        window.matchMedia("(resolution: ${contentScale}dppx)")
            .addEventListener("change", { evt ->
                evt as MediaQueryListEvent
                if (!evt.matches) {
                    handler(contentScale)
                }
                initMediaEventListener(handler)
            }, AddEventListenerOptions(capture = true, once = true))
    }

    override fun sizeFlow() = channel.receiveAsFlow()
}

@OptIn(InternalComposeApi::class)
internal class ComposeWindow(
    private val canvas: HTMLCanvasElement,
    content: @Composable () -> Unit,
    private val state: ComposeWindowState
) : LifecycleOwner {
    private val density: Density = Density(
        density = actualDensity.toFloat(),
        fontScale = 1f
    )

    private val _windowInfo = WindowInfoImpl().apply {
        isWindowFocused = true
    }

    private val canvasEvents = EventTargetListener(canvas)

    private val platformContext: PlatformContext = object : PlatformContext {
        override val windowInfo get() = _windowInfo

        override val inputModeManager: InputModeManager = DefaultInputModeManager()

        override val textInputService = object : WebTextInputService() {
            override fun resolveInputMode() = inputModeManager.inputMode
            override fun getOffset(rect: Rect): Offset {
                val viewportRect = canvas.getBoundingClientRect()
                val offsetX = viewportRect.left.toFloat().coerceAtLeast(0f) + (rect.left / density.density)
                val offsetY = viewportRect.top.toFloat().coerceAtLeast(0f) + (rect.top / density.density)
                return Offset(offsetX, offsetY)
            }
        }

        override val viewConfiguration =
            object : ViewConfiguration by PlatformContext.Empty.viewConfiguration {
                override val touchSlop: Float get() = with(density) { 18.dp.toPx() }
            }

        override fun setPointerIcon(pointerIcon: PointerIcon) {
            if (pointerIcon is BrowserCursor) {
                canvas.style.cursor = pointerIcon.id
            }
        }

    }

    private val layer = ComposeLayer(
        layer = SkiaLayer(),
        platformContext = platformContext,
    )
    private val systemThemeObserver = getSystemThemeObserver()

    private val lifecycleOwner = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleOwner

    private fun <T : Event> addTypedEvent(
        type: String,
        handler: (event: T) -> Unit
    ) {
        canvasEvents.addDisposableEvent(type) { event -> handler(event as T) }
    }

    private fun initEvents(canvas: HTMLCanvasElement) {
        var offset = Offset.Zero

        addTypedEvent<TouchEvent>("touchstart") { event ->
            event.preventDefault()

            canvas.getBoundingClientRect().apply {
                offset = Offset(x = left.toFloat(), y = top.toFloat())
            }

            layer.onTouchEvent(event, offset)
        }

        addTypedEvent<TouchEvent>("touchmove") { event ->
            event.preventDefault()
            layer.onTouchEvent(event, offset)
        }

        addTypedEvent<TouchEvent>("touchend") { event ->
            event.preventDefault()
            layer.onTouchEvent(event, offset)
        }

        addTypedEvent<TouchEvent>("touchcancel") { event ->
            event.preventDefault()
            layer.onTouchEvent(event, offset)
        }

        addTypedEvent<MouseEvent>("mousedown") { event ->
            layer.onMouseEvent(event)
        }

        addTypedEvent<MouseEvent>("mouseup") { event ->
            layer.onMouseEvent(event)
        }

        addTypedEvent<MouseEvent>("mousemove") { event ->
            layer.onMouseEvent(event)
        }

        addTypedEvent<MouseEvent>("mouseenter") { event ->
            layer.onMouseEvent(event)
        }

        addTypedEvent<MouseEvent>("mouseleave") { event ->
            layer.onMouseEvent(event)
        }

        addTypedEvent<WheelEvent>("wheel") { event ->
            layer.onWheelEvent(event)
        }

        canvas.addEventListener("contextmenu", { event ->
            event.preventDefault()
        })

        addTypedEvent<KeyboardEvent>("keydown") { event ->
            val processed = layer.onKeyboardEvent(event.toComposeEvent())
            if (processed) event.preventDefault()
        }

        addTypedEvent<KeyboardEvent>("keyup") { event ->
            val processed = layer.onKeyboardEvent(event.toComposeEvent())
            if (processed) event.preventDefault()
        }

        state.globalEvents.addDisposableEvent("focus") {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        state.globalEvents.addDisposableEvent("blur") {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    init {
        initEvents(canvas)
        state.init()

        canvas.setAttribute("tabindex", "0")

        layer.setDensity(density)
        layer.setContent {
            CompositionLocalProvider(
                LocalSystemTheme provides systemThemeObserver.currentSystemTheme.value,
                LocalLifecycleOwner provides this,
                content = {
                    content()
                    rememberCoroutineScope().launch {
                        state.sizeFlow().collect { size ->
                            this@ComposeWindow.resize(size)
                        }
                    }
                }
            )
        }

        lifecycleOwner.handleLifecycleEvent(if (document.hasFocus()) Lifecycle.Event.ON_RESUME else Lifecycle.Event.ON_START)
    }

    fun resize(boxSize: IntSize) {
        // FIXME: density is not integer
        val scaledDensity = density.density.toInt()

        val width = boxSize.width * scaledDensity
        val height = boxSize.height * scaledDensity

        canvas.width = width
        canvas.height = height

        // Scale canvas to allow high DPI rendering as suggested in
        // https://www.khronos.org/webgl/wiki/HandlingHighDPI.
        canvas.style.width = "${boxSize.width}px"
        canvas.style.height = "${boxSize.height}px"

        _windowInfo.containerSize = IntSize(width, height)

        layer.layer.attachTo(canvas)
        layer.setSize(width, height)
        layer.layer.needRedraw()
    }

    // TODO: need to call .dispose() on window close.
    fun dispose() {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        layer.dispose()
        systemThemeObserver.dispose()
        state.dispose()
        // modern browsers supposed to garbage collect all events on the element disposed
        // but actually we never can be sure dom element was collected in first place
        canvasEvents.dispose()
    }

    private fun ComposeLayer.onTouchEvent(
        event: TouchEvent,
        offset: Offset,
    ) {
        val eventType = when (event.type) {
            "touchstart" -> PointerEventType.Press
            "touchmove" -> PointerEventType.Move
            "touchend", "touchcancel" -> PointerEventType.Release
            else -> PointerEventType.Unknown
        }
        val pointers = event.changedTouches.asList().map { touch ->
            ComposeScenePointer(
                id = PointerId(touch.identifier.toLong()),
                position = Offset(
                    x = touch.clientX - offset.x,
                    y = touch.clientY - offset.y
                ) * density.density,
                pressed = when (eventType) {
                    PointerEventType.Press, PointerEventType.Move -> true
                    else -> false
                },
                type = PointerType.Touch,
                pressure = touch.unsafeCast<ExtendedTouchEvent>().force.toFloat()
            )
        }
        onTouchEvent(
            eventType = eventType,
            pointers = pointers,
            nativeEvent = event,
        )
    }

    private fun ComposeLayer.onMouseEvent(
        event: MouseEvent,
    ) {
        val eventType = when (event.type) {
            "mousedown" -> PointerEventType.Press
            "mousemove" -> PointerEventType.Move
            "mouseup" -> PointerEventType.Release
            "mouseenter" -> PointerEventType.Enter
            "mouseleave" -> PointerEventType.Exit
            else -> PointerEventType.Unknown
        }
        onMouseEvent(
            eventType = eventType,
            position = event.offset,
            buttons = event.composeButtons,
            keyboardModifiers = PointerKeyboardModifiers(
                isCtrlPressed = event.ctrlKey,
                isMetaPressed = event.metaKey,
                isAltPressed = event.altKey,
                isShiftPressed = event.shiftKey,
            ),
            nativeEvent = event,
            button = event.composeButton,
        )
    }

    private fun ComposeLayer.onWheelEvent(
        event: WheelEvent,
    ) {
        onMouseEvent(
            eventType = PointerEventType.Scroll,
            position = event.offset,
            scrollDelta = Offset(
                x = event.deltaX.toFloat(),
                y = event.deltaY.toFloat()
            ),
            buttons = event.composeButtons,
            keyboardModifiers = PointerKeyboardModifiers(
                isCtrlPressed = event.ctrlKey,
                isMetaPressed = event.metaKey,
                isAltPressed = event.altKey,
                isShiftPressed = event.shiftKey,
            ),
            nativeEvent = event,
            button = event.composeButton,
        )
    }

    private val MouseEvent.offset get() = Offset(
        x = offsetX.toFloat(),
        y = offsetY.toFloat()
    ) * density.density
}

private const val defaultCanvasElementId = "ComposeTarget"

/**
 * EXPERIMENTAL! Might be deleted or changed in the future!
 *
 * Initializes the composition in HTML canvas identified by [canvasElementId].
 *
 * It can be resized by providing [requestResize].
 * By default, it will listen to the window resize events.
 *
 * By default, styles will be applied to use the entire inner window, disabling scrollbars.
 * This can be turned off by setting [applyDefaultStyles] to false.
 */
@ExperimentalComposeUiApi
fun CanvasBasedWindow(
    title: String? = null,
    canvasElementId: String = defaultCanvasElementId,
    requestResize: (suspend () -> IntSize)? = null,
    applyDefaultStyles: Boolean = true,
    content: @Composable () -> Unit = { }
) {
    if (title != null) {
        val htmlTitleElement = (
            document.head!!.getElementsByTagName("title").item(0)
                ?: document.createElement("title").also { document.head!!.appendChild(it) }
            ) as HTMLTitleElement
        htmlTitleElement.textContent = title
    }

    if (applyDefaultStyles) {
        document.head!!.appendChild(
            (document.createElement("style") as HTMLStyleElement).apply {
                type = "text/css"
                appendChild(
                    document.createTextNode(
                        "body { margin: 0; overflow: hidden; } #$canvasElementId { outline: none; }"
                    )
                )
            }
        )
    }

    val canvas = document.getElementById(canvasElementId) as HTMLCanvasElement

    ComposeWindow(
        canvas = canvas,
        content = content,
        state = if (requestResize == null) DefaultWindowState(document.documentElement!!) else ComposeWindowState.createFromLambda(requestResize)
    )
}

/**
 * EXPERIMENTAL! Might be deleted or changed in the future!
 *
 * Creates the composition in HTML canvas created in parent container identified by [viewportContainer] id.
 * This size of canvas is adjusted with the size of the container
 */
@ExperimentalComposeUiApi
fun ComposeViewport(
    viewportContainer: String,
    content: @Composable () -> Unit = { }
) {
    ComposeViewport(document.getElementById(viewportContainer)!!, content)
}

/**
 * EXPERIMENTAL! Might be deleted or changed in the future!
 *
 * Creates the composition in HTML canvas created in parent container identified by [viewportContainer] Element.
 * This size of canvas is adjusted with the size of the container
 */
@ExperimentalComposeUiApi
fun ComposeViewport(
    viewportContainer: Element,
    content: @Composable () -> Unit = { }
) {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.setAttribute("tabindex", "0")

    viewportContainer.appendChild(canvas)

    ComposeWindow(
        canvas = canvas,
        content = content,
        state = DefaultWindowState(viewportContainer)
    )
}