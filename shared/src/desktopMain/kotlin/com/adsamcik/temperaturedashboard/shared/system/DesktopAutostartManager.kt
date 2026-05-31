package com.adsamcik.temperaturedashboard.shared.system

import io.github.aakira.napier.Napier
import java.io.File
import java.util.Locale

/**
 * Desktop autostart with per-OS implementations:
 *
 * - **Linux**: writes / removes `~/.config/autostart/temperature-dashboard.desktop`
 *   (the freedesktop.org XDG autostart spec, honoured by GNOME, KDE, XFCE, …)
 * - **Windows**: writes / deletes the HKCU\Software\Microsoft\Windows\
 *   CurrentVersion\Run\TemperatureDashboard registry value via `reg.exe`
 *   (no extra dep; works on any Windows 10+ install)
 * - **macOS**: writes / removes `~/Library/LaunchAgents/com.adsamcik.
 *   temperaturedashboard.plist`. The plist contains a `RunAtLoad` flag
 *   and a `ProgramArguments` pointing at the app's launcher script.
 *
 * Auto-detects which path is appropriate from [System.getProperty("os.name")].
 * The launcher command is provided by the caller — typically the absolute
 * path to the installed app binary (e.g. `/usr/bin/temperature-dashboard`
 * after `dpkg -i`).
 */
class DesktopAutostartManager(
    private val launcherCommand: String,
) : AutostartManager {

    private val os: TargetOs = TargetOs.detect()
    override val supported: Boolean = os != TargetOs.Unknown

    override fun isEnabled(): Boolean = runCatching {
        when (os) {
            TargetOs.Linux -> linuxAutostartFile().exists()
            TargetOs.Windows -> windowsQuery()
            TargetOs.MacOs -> macLaunchAgentFile().exists()
            TargetOs.Unknown -> false
        }
    }.getOrElse {
        Napier.w("autostart isEnabled probe failed: ${it.message}", tag = LOG_TAG)
        false
    }

    override fun enable(): AutostartResult = runCatching {
        when (os) {
            TargetOs.Linux -> writeLinuxAutostart()
            TargetOs.Windows -> writeWindowsRunKey()
            TargetOs.MacOs -> writeMacLaunchAgent()
            TargetOs.Unknown -> return AutostartResult.NotSupported
        }
        AutostartResult.Ok
    }.getOrElse { AutostartResult.Failed(it.message ?: "autostart enable failed") }

    override fun disable(): AutostartResult = runCatching {
        when (os) {
            TargetOs.Linux -> linuxAutostartFile().delete()
            TargetOs.Windows -> deleteWindowsRunKey()
            TargetOs.MacOs -> macLaunchAgentFile().delete()
            TargetOs.Unknown -> return AutostartResult.NotSupported
        }
        AutostartResult.Ok
    }.getOrElse { AutostartResult.Failed(it.message ?: "autostart disable failed") }

    // ----------------------------------------------------- Linux (XDG autostart)

    private fun linuxAutostartFile(): File {
        val configHome = System.getenv("XDG_CONFIG_HOME")
            ?: (System.getProperty("user.home") + "/.config")
        return File("$configHome/autostart", "temperature-dashboard.desktop")
    }

    private fun writeLinuxAutostart() {
        val file = linuxAutostartFile()
        file.parentFile?.mkdirs()
        file.writeText(
            buildString {
                appendLine("[Desktop Entry]")
                appendLine("Type=Application")
                appendLine("Name=Temperature Dashboard")
                appendLine("Comment=Universal Bluetooth temperature & humidity sensor dashboard")
                appendLine("Exec=$launcherCommand")
                appendLine("Icon=temperature-dashboard")
                appendLine("X-GNOME-Autostart-enabled=true")
                appendLine("X-KDE-autostart-after=panel")
                appendLine("Terminal=false")
            },
        )
    }

    // ----------------------------------------------------- Windows (Run reg key)

    private val regPath = """HKCU\Software\Microsoft\Windows\CurrentVersion\Run"""
    private val regValueName = "TemperatureDashboard"

    private fun windowsQuery(): Boolean {
        val proc = ProcessBuilder("reg", "query", regPath, "/v", regValueName)
            .redirectErrorStream(true)
            .start()
        proc.waitFor()
        return proc.exitValue() == 0
    }

    private fun writeWindowsRunKey() {
        val proc = ProcessBuilder(
            "reg", "add", regPath,
            "/v", regValueName,
            "/t", "REG_SZ",
            "/d", launcherCommand,
            "/f",
        ).redirectErrorStream(true).start()
        proc.waitFor()
        check(proc.exitValue() == 0) { "reg add returned ${proc.exitValue()}" }
    }

    private fun deleteWindowsRunKey() {
        val proc = ProcessBuilder("reg", "delete", regPath, "/v", regValueName, "/f")
            .redirectErrorStream(true).start()
        proc.waitFor()
        // exit 1 = value not found, acceptable
    }

    // ----------------------------------------------------- macOS (LaunchAgent)

    private fun macLaunchAgentFile(): File =
        File(System.getProperty("user.home"), "Library/LaunchAgents/com.adsamcik.temperaturedashboard.plist")

    private fun writeMacLaunchAgent() {
        val file = macLaunchAgentFile()
        file.parentFile?.mkdirs()
        file.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>com.adsamcik.temperaturedashboard</string>
                <key>ProgramArguments</key>
                <array>
                    <string>$launcherCommand</string>
                </array>
                <key>RunAtLoad</key>
                <true/>
                <key>KeepAlive</key>
                <false/>
            </dict>
            </plist>
            """.trimIndent(),
        )
    }

    private enum class TargetOs {
        Linux, Windows, MacOs, Unknown;

        companion object {
            fun detect(): TargetOs {
                val name = System.getProperty("os.name", "").lowercase(Locale.ROOT)
                return when {
                    "win" in name -> Windows
                    "mac" in name || "darwin" in name -> MacOs
                    "nux" in name || "nix" in name -> Linux
                    else -> Unknown
                }
            }
        }
    }

    private companion object {
        const val LOG_TAG = "DesktopAutostart"
    }
}
