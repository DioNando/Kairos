package com.kairos.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.kairos.app.data.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Stockage des pièces jointes **légères** : copie le document choisi dans le dossier privé de l'app
 * (taille plafonnée à [MAX_BYTES]) et l'expose en lecture via le FileProvider de l'app.
 */
class AttachmentStore(private val context: Context) {

    /** Fichier trop volumineux pour une pièce jointe « légère ». */
    class TooLargeException : IllegalArgumentException()

    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    /**
     * Copie [uri] dans le stockage privé et renvoie la pièce jointe correspondante (id 0, à insérer
     * avec la tâche). Échoue avec [TooLargeException] au-delà de [MAX_BYTES].
     */
    suspend fun import(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "fichier"
        var declaredSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) declaredSize = c.getLong(1)
                }
            }
        if (declaredSize > MAX_BYTES) throw TooLargeException()
        val extension = name.substringAfterLast('.', "")
        val fileName = UUID.randomUUID().toString() + if (extension.isEmpty()) "" else ".$extension"
        val target = File(dir, fileName)
        var copied = 0L
        try {
            val input = resolver.openInputStream(uri) ?: error("Impossible d'ouvrir le fichier")
            input.use {
                target.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = it.read(buffer)
                        if (read < 0) break
                        copied += read
                        // Taille déclarée absente ou fausse : on coupe dès que le plafond est dépassé.
                        if (copied > MAX_BYTES) throw TooLargeException()
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        Attachment(
            taskId = 0,
            displayName = name,
            fileName = fileName,
            mimeType = resolver.getType(uri) ?: "application/octet-stream",
            sizeBytes = copied,
        )
    }

    /** Uri partageable (lecture seule) pour ouvrir la pièce jointe dans une autre app. */
    fun uriFor(attachment: Attachment): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.attachments", File(dir, attachment.fileName))

    suspend fun delete(fileName: String) {
        withContext(Dispatchers.IO) { File(dir, fileName).delete() }
    }

    /** Supprime tout fichier du dossier absent de [referenced]. */
    suspend fun sweep(referenced: Set<String>) {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.filter { it.name !in referenced }?.forEach { it.delete() }
        }
    }

    companion object {
        private const val DIR = "attachments"
        const val MAX_BYTES = 10L * 1024 * 1024
    }
}
