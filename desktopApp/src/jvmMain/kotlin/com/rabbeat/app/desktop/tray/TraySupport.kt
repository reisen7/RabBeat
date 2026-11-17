package com.rabbeat.app.desktop.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.awt.Image
import java.awt.MouseInfo
import java.awt.Point
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * Raw AWT tray icon. Compose's `Tray` composable can only render the stock
 * AWT [java.awt.PopupMenu] and gives no right-click callback, so the custom
 * Compose popup ([TrayMenuWindow]) is driven from these AWT listeners instead.
 *
 * Left single click → [onShow]; right click (or platform popup trigger) →
 * [onMenu] with the current pointer position in screen coordinates.
 */
@Composable
fun AppTrayIcon(
    iconImage: Image,
    onShow: () -> Unit,
    onMenu: (Point) -> Unit,
) {
    if (!SystemTray.isSupported()) return

    val currentOnShow by rememberUpdatedState(onShow)
    val currentOnMenu by rememberUpdatedState(onMenu)

    DisposableEffect(iconImage) {
        val icon = TrayIcon(iconImage, "RabBeat").apply {
            // The image is already the tray's pixel size. Letting AWT scale
            // it again softens the bunny.
            isImageAutoSize = false
        }
        val mouseListener = object : MouseAdapter() {
            private fun maybePopup(e: MouseEvent) {
                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                    // Pointer position is used because TrayIcon carries no
                    // on-screen geometry of its own.
                    currentOnMenu(MouseInfo.getPointerInfo().location)
                }
            }

            override fun mousePressed(e: MouseEvent) = maybePopup(e)
            override fun mouseReleased(e: MouseEvent) = maybePopup(e)

            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) {
                    currentOnShow()
                }
            }
        }
        val actionListener = ActionListener { currentOnShow() }
        icon.addMouseListener(mouseListener)
        icon.addActionListener(actionListener)
        val tray = SystemTray.getSystemTray()
        runCatching { tray.add(icon) }
        onDispose {
            runCatching { tray.remove(icon) }
        }
    }
}
