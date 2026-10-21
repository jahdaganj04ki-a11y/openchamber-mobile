package com.openchamber.android.projects

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.openchamber.android.core.model.Project
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The projects that live on the phone.
 *
 * A project is a directory in the app's private storage. The user either
 * copies a folder they pick from the device into it, or clones a repository
 * into it. The server is told about the directory afterwards, because the
 * server is the one that runs the engine in it.
 *
 * Android gives an app no writable path outside its own storage, so a
 * project always lives there. A folder the user picks is copied, not linked:
 * the engine runs in the copy.
 */
class ProjectStore(context: Context, private val root: File) {

    private val resolver = context.applicationContext.contentResolver

    /** The projects in the app's storage, by name. */
    fun list(): List<Project> =
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?.map { directory ->
                Project(
                    id = directory.absolutePath,
                    worktree = directory.absolutePath,
                    name = directory.name,
                )
            }
            .orEmpty()

    /**
     * The name of a folder the user picked.
     *
     * The picker hands out a tree, and the tree is named after the folder it
     * points at. A tree without a name gets one from its id.
     */
    fun displayName(tree: Uri): String {
        resolver.query(
            tree,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
        return "project"
    }

    /**
     * Copies a folder the user picked into the app's storage.
     *
     * The name is the name the folder had on the device. When a project with
     * that name exists already, the copy gets a number behind it, so nothing
     * the user owns is overwritten.
     */
    suspend fun importTree(tree: Uri, name: String): File = withContext(Dispatchers.IO) {
        val target = uniqueDirectory(name)
        copyDocument(tree, target)
        target
    }

    /**
     * Clones a repository into the app's storage.
     *
     * The clone is shallow, because a phone has no use for the history of a
     * repository it is about to work in.
     */
    suspend fun clone(url: String, name: String, git: File): File = withContext(Dispatchers.IO) {
        if (!git.exists()) {
            throw IllegalStateException("The phone has no git yet")
        }
        val target = uniqueDirectory(name)
        val process = ProcessBuilder(
            git.absolutePath,
            "clone",
            "--depth",
            "1",
            url,
            target.absolutePath,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        if (code != 0) {
            target.deleteRecursively()
            throw IllegalStateException(output.trim().ifEmpty { "The clone failed with code $code" })
        }
        target
    }

    /** Removes a project and everything in it. */
    suspend fun remove(project: Project): Boolean = withContext(Dispatchers.IO) {
        val directory = File(project.worktree)
        directory.deleteRecursively()
    }

    /**
     * The directory a project with this name gets.
     *
     * A name that cannot be a path becomes the repository name, and a name
     * that is already taken gets a number behind it.
     */
    private fun uniqueDirectory(name: String): File {
        val cleaned = cleanName(name)
        var candidate = File(root, cleaned)
        var counter = 2
        while (candidate.exists()) {
            candidate = File(root, "$cleaned-$counter")
            counter += 1
        }
        candidate.mkdirs()
        return candidate
    }

    private fun cleanName(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "-")
            .trim('-', '.', ' ')
        return cleaned.ifEmpty { "project" }.take(60)
    }

    /**
     * Copies a document and everything under it.
     *
     * The picker hands out a tree, and every folder in it is asked for by
     * document id, because the uri of a child changes with the folder it
     * lives in.
     */
    private fun copyDocument(source: Uri, target: File) {
        val documentId = DocumentsContract.getTreeDocumentId(source)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(source, documentId)
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex) ?: continue
                val childName = cursor.getString(nameIndex) ?: continue
                val mime = cursor.getString(mimeIndex) ?: continue
                val childUri = DocumentsContract.buildDocumentUriUsingTree(source, childId)
                val child = File(target, childName)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    child.mkdirs()
                    copyDocument(childUri, child)
                } else {
                    child.parentFile?.mkdirs()
                    resolver.openInputStream(childUri)?.use { input ->
                        child.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}
