package com.openchamber.android.core.runtime

import android.content.Context
import java.io.File

/**
 * Where the app keeps its files.
 *
 * Everything the phone writes lives under the app's private directory, because
 * Android gives an app no writable path outside it. The paths are derived
 * from the context once and handed to the server process as its environment.
 */
class DevicePaths(context: Context) {

    private val files: File = context.filesDir

    /** The server's data directory: settings, sessions, logs. */
    val dataDir: File = File(files, "openchamber").apply { mkdirs() }

    /** The extracted binaries: the server, the engine, git, ripgrep, bash. */
    val binDir: File = File(files, "bin").apply { mkdirs() }

    /** The home directory the engine and the shell see. */
    val homeDir: File = File(files, "home").apply { mkdirs() }

    /** The XDG directories, all under the app's own storage. */
    val configHome: File = File(files, "xdg/config").apply { mkdirs() }
    val dataHome: File = File(files, "xdg/data").apply { mkdirs() }
    val stateHome: File = File(files, "xdg/state").apply { mkdirs() }
    val cacheHome: File = File(files, "xdg/cache").apply { mkdirs() }

    /** The projects the user imported or cloned. */
    val projectsDir: File = File(files, "projects").apply { mkdirs() }

    val serverBinary: File = File(binDir, "openchamber-server")
    val engineBinary: File = File(binDir, "opencode")

    /**
     * The environment the server and the engine run in.
     *
     * `OPENCHAMBER_BUNDLED_OPENCODE_CLI_DIR` is what makes the engine count
     * as bundled, which closes the in-place upgrade route. `OPENCODE_BINARY`
     * is deliberately absent: it would win over the bundled directory and
     * mark the engine as environment-provided, which reopens that route.
     */
    fun serverEnvironment(port: Int): Map<String, String> = buildMap {
        put("OPENCHAMBER_RUNTIME", "android")
        put("OPENCHAMBER_HOST", "127.0.0.1")
        put("OPENCHAMBER_API_ONLY", "1")
        put("OPENCHAMBER_PORT", port.toString())
        put("OPENCHAMBER_DATA_DIR", dataDir.absolutePath)
        put("OPENCHAMBER_BUNDLED_OPENCODE_CLI_DIR", binDir.absolutePath)
        put("HOME", homeDir.absolutePath)
        put("XDG_CONFIG_HOME", configHome.absolutePath)
        put("XDG_DATA_HOME", dataHome.absolutePath)
        put("XDG_STATE_HOME", stateHome.absolutePath)
        put("XDG_CACHE_HOME", cacheHome.absolutePath)
        put("PATH", "${binDir.absolutePath}:/system/bin:/system/xbin")
        put("NO_PROXY", "localhost,127.0.0.1")
        put("no_proxy", "localhost,127.0.0.1")
        put("TMPDIR", cacheHome.absolutePath)
    }
}
