package com.rabbeat.app.desktop.sys

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal enum class HotkeyAction { Play, Prev, Next, Search, Dismiss }

/** A key plus modifiers. [vk] is a Windows virtual-key code. */
internal data class Chord(
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val meta: Boolean,
    val vk: Int,
) {
    fun encode(): String = listOfNotNull(
        "ctrl".takeIf { ctrl },
        "alt".takeIf { alt },
        "shift".takeIf { shift },
        "win".takeIf { meta },
        vk.toString(),
    ).joinToString("+")

    fun label(): String = listOfNotNull(
        "Ctrl".takeIf { ctrl },
        "Alt".takeIf { alt },
        "Shift".takeIf { shift },
        "Win".takeIf { meta },
        vkLabel(vk),
    ).joinToString(" + ")

    val hasModifier: Boolean get() = ctrl || alt || shift || meta

    companion object {
        fun decode(raw: String?): Chord? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.lowercase().split('+').filter { it.isNotBlank() }
            val vk = parts.lastOrNull()?.toIntOrNull() ?: return null
            if (vk <= 0) return null
            return Chord(
                ctrl = "ctrl" in parts,
                alt = "alt" in parts,
                shift = "shift" in parts,
                meta = "win" in parts,
                vk = vk,
            )
        }
    }
}

internal fun chordOf(event: KeyEvent): Chord? {
    val vk = event.key.keyCode.toInt()
    if (vk == 0 || vk in modifierVks) return null
    return Chord(
        ctrl = event.isCtrlPressed,
        alt = event.isAltPressed,
        shift = event.isShiftPressed,
        meta = event.isMetaPressed,
        vk = vk,
    )
}

internal val DefaultChords: Map<HotkeyAction, Chord> = mapOf(
    HotkeyAction.Play to Chord(ctrl = true, alt = true, shift = false, meta = false, vk = 0x20),
    HotkeyAction.Prev to Chord(ctrl = true, alt = true, shift = false, meta = false, vk = 0x25),
    HotkeyAction.Next to Chord(ctrl = true, alt = true, shift = false, meta = false, vk = 0x27),
    HotkeyAction.Search to Chord(ctrl = true, alt = true, shift = false, meta = false, vk = 0x46),
)

internal fun DesktopSettings.chord(action: HotkeyAction): Chord? {
    val raw = when (action) {
        HotkeyAction.Play -> hotkeyPlay
        HotkeyAction.Prev -> hotkeyPrev
        HotkeyAction.Next -> hotkeyNext
        HotkeyAction.Search -> hotkeySearch
        HotkeyAction.Dismiss -> hotkeyDismiss
    }
    if (raw.isBlank() || raw == "none") return null
    return Chord.decode(raw)
}

internal fun DesktopSettings.storeChord(action: HotkeyAction, chord: Chord?) {
    val raw = chord?.encode() ?: "none"
    when (action) {
        HotkeyAction.Play -> hotkeyPlay = raw
        HotkeyAction.Prev -> hotkeyPrev = raw
        HotkeyAction.Next -> hotkeyNext = raw
        HotkeyAction.Search -> hotkeySearch = raw
        HotkeyAction.Dismiss -> hotkeyDismiss = raw
    }
}

internal fun DesktopSettings.hotkeyMap(): Map<HotkeyAction, Chord> =
    HotkeyAction.entries.mapNotNull { action -> chord(action)?.let { action to it } }.toMap()

/**
 * Low-level keyboard hook. It keeps working after the main window is minimized
 * or closed to the tray, because it does not depend on that window.
 */
internal object GlobalHotkeys {
    private val bindings = ConcurrentHashMap<Chord, HotkeyAction>()
    private val down = HashSet<Int>()
    private val paused = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private var handler: ((HotkeyAction) -> Unit)? = null
    var onCapture: ((KeyEvent) -> Boolean)? = null
    private var hook: Pointer? = null
    private var proc: HookProc? = null
    private var thread: Thread? = null

    fun install(onAction: (HotkeyAction) -> Unit) {
        handler = onAction
        if (thread != null) return
        val user = user32 ?: return
        val callback = object : HookProc {
            override fun callback(nCode: Int, wParam: Long, lParam: Pointer?): Long {
                if (nCode < 0 || lParam == null) {
                    return user.CallNextHookEx(hook, nCode, wParam, lParam ?: Pointer.NULL)
                }
                return if (handle(wParam, lParam)) 1L else user.CallNextHookEx(hook, nCode, wParam, lParam)
            }
        }
        proc = callback
        thread = Thread({
            hook = user.SetWindowsHookExW(WH_KEYBOARD_LL, callback, null, 0)
            ready.set(hook != null)
            if (hook != null) {
                val msg = Msg()
                while (user.GetMessageW(msg, null, 0, 0) > 0) {
                    user.TranslateMessage(msg)
                    user.DispatchMessageW(msg)
                }
                user.UnhookWindowsHookEx(hook)
            }
        }, "rabbeat-hotkeys").apply { isDaemon = true; start() }
    }

    fun apply(chords: Map<HotkeyAction, Chord>) {
        bindings.clear()
        chords.forEach { (action, chord) -> bindings[chord] = action }
    }

    fun pause(value: Boolean) {
        paused.set(value)
    }

    fun isPaused(): Boolean = paused.get()

    fun isReady(): Boolean = ready.get()

    fun messageBytes(): Int = Msg().size()

    private fun handle(wParam: Long, lParam: Pointer): Boolean {
        val vk = lParam.getInt(0)
        val up = lParam.getInt(8) and 0x80 != 0
        if (up) {
            down.remove(vk)
            return false
        }
        if (wParam != WM_KEYDOWN && wParam != WM_SYSKEYDOWN) return false
        if (!down.add(vk)) {
            return chord(vk)?.let { bindings.containsKey(it) } == true && !paused.get()
        }
        if (paused.get()) return false
        val action = bindings[chord(vk) ?: return false] ?: return false
        handler?.invoke(action)
        return true
    }

    private fun chord(vk: Int): Chord? {
        if (vk in modifierVks) return null
        val u = user32 ?: return null
        fun down(code: Int) = u.GetAsyncKeyState(code).toInt() and 0x8000 != 0
        return Chord(
            ctrl = down(0x11),
            alt = down(0x12),
            shift = down(0x10),
            meta = down(0x5B) || down(0x5C),
            vk = vk,
        )
    }

    private interface HookProc : StdCallLibrary.StdCallCallback {
        fun callback(nCode: Int, wParam: Long, lParam: Pointer?): Long
    }

    private interface User32 : StdCallLibrary {
        fun SetWindowsHookExW(idHook: Int, proc: HookProc, module: Pointer?, threadId: Int): Pointer?
        fun UnhookWindowsHookEx(hook: Pointer?): Boolean
        fun CallNextHookEx(hook: Pointer?, nCode: Int, wParam: Long, lParam: Pointer): Long
        fun GetMessageW(msg: Msg, hwnd: Pointer?, min: Int, max: Int): Int
        fun TranslateMessage(msg: Msg): Boolean
        fun DispatchMessageW(msg: Msg): Long
        fun GetAsyncKeyState(vk: Int): Short
    }

    private val user32 by lazy { runCatching { Native.load("user32", User32::class.java) as User32 }.getOrNull() }

    @Structure.FieldOrder("hWnd", "message", "wParam", "lParam", "time", "ptX", "ptY")
    class Msg : Structure() {
        @JvmField var hWnd: Pointer? = null
        @JvmField var message: Int = 0
        @JvmField var wParam: Long = 0
        @JvmField var lParam: Long = 0
        @JvmField var time: Int = 0
        @JvmField var ptX: Int = 0
        @JvmField var ptY: Int = 0
    }

    private const val WH_KEYBOARD_LL = 13
    private const val WM_KEYDOWN = 0x0100L
    private const val WM_SYSKEYDOWN = 0x0104L
}

private val modifierVks = setOf(0x10, 0x11, 0x12, 0x5B, 0x5C, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 157, 524)

private fun vkLabel(vk: Int): String = when (vk) {
    0x20 -> "Space"
    0x1B -> "Esc"
    0x25 -> "←"
    0x26 -> "↑"
    0x27 -> "→"
    0x28 -> "↓"
    0x09 -> "Tab"
    0x0D -> "Enter"
    0x08 -> "Backspace"
    0x2E -> "Delete"
    in 0x70..0x7B -> "F${vk - 0x6F}"
    in 0x30..0x39 -> (vk - 0x30).toString()
    in 0x41..0x5A -> ('A'.code + (vk - 0x41)).toChar().toString()
    else -> "0x${vk.toString(16)}"
}
