package com.openchamber.android.core.runtime

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * The extraction of the bundled binaries into app storage.
 *
 * An APK entry cannot be executed in place, so the server binary, the engine
 * binary and the helper binaries are copied out on first launch and marked
 * executable. The extraction is atomic: the file is written under a temporary
 * name and renamed, so a half-written binary never replaces a working one.
 */
class BinaryInstaller(context: Context, private val paths: DevicePaths) {

    /**
     * The binaries the app ships, and where each one lands.
     *
     * The names in `assets/` are the names in the app's private storage. A
     * binary that is missing from the assets is reported, not skipped, so the
     * app can tell the user which file to repair.
     */
    private val bundled = listOf(
        "openchamber-server",
        "opencode",
        "git",
        "rg",
        "bash",
    )

    class MissingBinaryException(val name: String) :
        IOException("The bundled binary '$name' is not in the assets")

    /** Whether every bundled binary is already extracted and executable. */
    fun isInstalled(): Boolean = bundled.all { name ->
        val target = File(paths.binDir, name)
        target.isFile && target.canExecute()
    }

    /**
     * Extracts the binaries that are not in place yet.
     *
     * The ones that are already there are left alone, so a restart does not
     * rewrite 90 MB.
     */
    fun install(): List<String> {
        val installed = mutableListOf<String>()
        for (name in bundled) {
            val target = File(paths.binDir, name)
            if (target.isFile && target.canExecute()) {
                installed += name
                continue
            }
            extract(name, target)
            installed += name
        }
        return installed
    }

    private fun extract(name: String, target: File) {
        val assetName = "binaries/$name"
        val temporary = File(target.parentFile, "$name.tmp")
        try {
            context.assets.open(assetName).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporary.setExecutable(true, true)) {
                temporary.setExecutable(true, false)
            }
            if (target.exists() && !target.delete()) {
                throw IOException("The old binary '$name' could not be replaced")
            }
            if (!temporary.renameTo(target)) {
                throw IOException("The binary '$name' could not be moved into place")
            }
        } catch (error: IOException) {
            temporary.delete()
            throw error
        }
    }

    private val context: Context = context.applicationContext
}
