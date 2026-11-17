package com.rabbeat.app.desktop.sys

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/**
 * Minimal JNA bindings for native window behaviour that Compose's undecorated
 * window doesn't get for free: OS-animated minimize/maximize/restore via
 * ShowWindow, and Windows 11 rounded corners via DwmSetWindowAttribute.
 */
object WinNative {

    private interface WindowProc : StdCallLibrary.StdCallCallback {
        fun callback(hwnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long
    }

    private interface User32 : StdCallLibrary {
        fun ShowWindow(hwnd: Pointer, cmd: Int): Boolean
        fun SetForegroundWindow(hwnd: Pointer): Boolean
        fun IsZoomed(hwnd: Pointer): Boolean
        fun IsIconic(hwnd: Pointer): Boolean
        fun GetSystemMetrics(index: Int): Int
        fun SendMessageW(hwnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long
        fun GetWindowLongPtrW(hwnd: Pointer, index: Int): Long
        fun SetWindowLongPtrW(hwnd: Pointer, index: Int, value: Long): Long
        fun SetWindowLongPtrW(hwnd: Pointer, index: Int, proc: WindowProc): Long
        fun CallWindowProcW(proc: Long, hwnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long
        fun SetWindowPos(
            hwnd: Pointer,
            insertAfter: Pointer?,
            x: Int,
            y: Int,
            cx: Int,
            cy: Int,
            flags: Int,
        ): Boolean
    }

    private interface DwmApi : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: Pointer, attr: Int, value: IntByReference, size: Int): Int
    }

    private const val SW_SHOW = 5
    private const val SW_MINIMIZE = 6
    private const val SW_RESTORE = 9
    private const val WM_SYSCOMMAND = 0x0112
    private const val WM_NCCALCSIZE = 0x0083
    private const val SC_MAXIMIZE = 0xF030
    private const val SC_RESTORE = 0xF120
    private const val GWL_STYLE = -16
    private const val GWLP_WNDPROC = -4
    private const val WS_THICKFRAME = 0x00040000
    private const val WS_CAPTION = 0x00C00000
    private const val WS_SYSMENU = 0x00080000
    private const val WS_MINIMIZEBOX = 0x00020000
    private const val WS_MAXIMIZEBOX = 0x00010000
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SM_CXFRAME = 32
    private const val SM_CYFRAME = 33
    private const val SM_CXPADDEDBORDER = 92
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWCP_ROUND = 2

    private interface Kernel32 : StdCallLibrary {
        fun GetCurrentProcess(): Pointer
        fun SetProcessWorkingSetSize(process: Pointer, min: Long, max: Long): Boolean
    }

    private val user32 by lazy { runCatching { Native.load("user32", User32::class.java) }.getOrNull() }
    private val kernel32 by lazy { runCatching { Native.load("kernel32", Kernel32::class.java) }.getOrNull() }
    private val dwm by lazy { runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull() }
    /** Kept alive so the native callbacks are not collected. */
    private val borderlessProcs = mutableListOf<WindowProc>()
    private val borderlessProcAddrs = mutableSetOf<Long>()

    private fun hwnd(w: Window): Pointer? =
        runCatching { Pointer(Native.getComponentID(w)) }.getOrNull()

    /**
     * Gives the undecorated window a real caption/thick frame so Windows plays
     * the maximize animation, then hides that frame by handling WM_NCCALCSIZE.
     */
    fun installBorderlessFrame(w: Window) {
        val hwnd = hwnd(w) ?: return
        val u = user32 ?: return
        runCatching {
            val style = u.GetWindowLongPtrW(hwnd, GWL_STYLE)
            val framed = style or
                WS_CAPTION.toLong() or WS_THICKFRAME.toLong() or
                WS_SYSMENU.toLong() or WS_MINIMIZEBOX.toLong() or WS_MAXIMIZEBOX.toLong()
            u.SetWindowLongPtrW(hwnd, GWL_STYLE, framed)
            val currentProc = u.GetWindowLongPtrW(hwnd, GWLP_WNDPROC)
            if (currentProc !in borderlessProcAddrs) {
                val proc = object : WindowProc {
                    override fun callback(hwnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long {
                        if (msg == WM_NCCALCSIZE && wParam != 0L && lParam != 0L) {
                            if (u.IsZoomed(hwnd)) insetMaximizedClient(u, Pointer(lParam))
                            return 0L
                        }
                        return u.CallWindowProcW(currentProc, hwnd, msg, wParam, lParam)
                    }
                }
                borderlessProcs += proc
                u.SetWindowLongPtrW(hwnd, GWLP_WNDPROC, proc)
                borderlessProcAddrs += u.GetWindowLongPtrW(hwnd, GWLP_WNDPROC)
            }
            u.SetWindowPos(
                hwnd, null, 0, 0, 0, 0,
                SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
            )
        }
    }

    private fun insetMaximizedClient(u: User32, params: Pointer) {
        val padX = u.GetSystemMetrics(SM_CXFRAME) + u.GetSystemMetrics(SM_CXPADDEDBORDER)
        val padY = u.GetSystemMetrics(SM_CYFRAME) + u.GetSystemMetrics(SM_CXPADDEDBORDER)
        params.setInt(0, params.getInt(0) + padX)
        params.setInt(4, params.getInt(4) + padY)
        params.setInt(8, params.getInt(8) - padX)
        params.setInt(12, params.getInt(12) - padY)
    }

    /** Native maximize with the OS expand animation. Returns false when unavailable. */
    fun maximize(w: Window): Boolean = sysCommand(w, SC_MAXIMIZE)

    /** Native restore from maximized, with the OS animation. */
    fun restore(w: Window): Boolean = sysCommand(w, SC_RESTORE)

    private fun sysCommand(w: Window, command: Int): Boolean {
        val hwnd = hwnd(w) ?: return false
        val u = user32 ?: return false
        return runCatching {
            u.SendMessageW(hwnd, WM_SYSCOMMAND, command.toLong(), 0L)
            true
        }.getOrDefault(false)
    }

    fun minimize(w: Window): Boolean =
        hwnd(w)?.let { user32?.ShowWindow(it, SW_MINIMIZE) } == true

    /** Same trim Microsoft PC Manager uses: drop pages that are not being touched. */
    fun trimWorkingSet() {
        val k = kernel32 ?: return
        runCatching { k.SetProcessWorkingSetSize(k.GetCurrentProcess(), -1L, -1L) }
    }

    /** Bring a hidden or minimized window back without dropping a maximized frame. */
    fun toForeground(w: Window) {
        hwnd(w)?.let { h ->
            val u = user32 ?: return
            val cmd = if (u.IsIconic(h)) SW_RESTORE else SW_SHOW
            runCatching { u.ShowWindow(h, cmd) }
            runCatching { u.SetForegroundWindow(h) }
        }
    }

    /** Windows 11 rounded corners; returns false on older Windows / non-Windows. */
    fun applyRoundCorners(w: Window): Boolean =
        hwnd(w)?.let { h ->
            dwm?.DwmSetWindowAttribute(
                h, DWMWA_WINDOW_CORNER_PREFERENCE, IntByReference(DWMWCP_ROUND), 4,
            ) == 0
        } == true
}
