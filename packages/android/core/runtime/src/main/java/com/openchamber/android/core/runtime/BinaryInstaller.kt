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
     * The binaries the app cannot run without.
     *
     * The server is the product and the engine is what the server drives.
     * Without one of them the app has nothing to show, so a missing one is an
     * error the user can repair.
     */
    private val required = listOf(
        "openchamber-server",
        "opencode",
    )

    /**
     * The binaries the server uses when it finds them.
     *
     * `git` drives the source control surface, `rg` the file search, `bash` the
     * shell of the terminal, and the library of the pseudoterminal the
     * terminal itself. The server answers with a clear error when one of them
     * is missing, so a build without them still runs.
     */
    private val optional = listOf(
        "git",
        "rg",
        "bash",
        "librust_pty.so",
    )

    class MissingBinaryException(val name: String) :
        IOException("The bundled binary '$name' is not in the assets")

    /** Whether every required binary is already extracted and executable. */
    fun isInstalled(): Boolean = required.all { name ->
        val target = File(paths.binDir, name)
        target.isFile && target.canExecute()
    }

    /**
     * Extracts the binaries that are not in place yet.
     *
     * The ones that are already there are left alone, so a restart does not
     * rewrite 90 MB. A binary that the build did not ship is skipped.
     */
    fun install(): List<String> {
        val shipped = shippedAssets()
        val installed = mutableListOf<String>()

        for (name in required) {
            if (name !in shipped) throw MissingBinaryException(name)
            val target = File(paths.binDir, name)
            if (target.isFile && target.canExecute()) {
                installed += name
                continue
            }
            extract(name, target)
            installed += name
        }

        for (name in optional) {
            if (name !in shipped) continue
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

    /** The names of the binaries this build of the app carries. */
    private fun shippedAssets(): Set<String> =
        runCatching { context.assets.list(ASSET_DIR)?.toSet() ?: emptySet() }
            .getOrDefault(emptySet())

    private fun extract(name: String, target: File) {
        val temporary = File(target.parentFile, "$name.tmp")
        try {
            context.assets.open("$ASSET_DIR/$name").use { input ->
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

    private companion object {
        const val ASSET_DIR = "binaries"
    }
}
