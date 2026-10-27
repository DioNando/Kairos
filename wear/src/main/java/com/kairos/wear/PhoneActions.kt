package com.kairos.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit

/**
 * Actions envoyées au téléphone (MessageClient), seul détenteur de la base : il les applique puis
 * repousse l'état à jour. Envoi à tous les nœuds connectés ; bloquant → appeler hors du thread
 * principal. Renvoie false si aucun téléphone n'est joignable.
 */
object PhoneActions {
    private const val PATH_COMPLETE = "/kairos/action/complete"
    private const val PATH_POMODORO = "/kairos/action/pomodoro"

    fun complete(context: Context, taskId: Long): Boolean = send(context, PATH_COMPLETE, taskId.toString())

    fun togglePomodoro(context: Context): Boolean = send(context, PATH_POMODORO, "toggle")

    fun resetPomodoro(context: Context): Boolean = send(context, PATH_POMODORO, "reset")

    private fun send(context: Context, path: String, payload: String): Boolean = runCatching {
        val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 5, TimeUnit.SECONDS)
        val client = Wearable.getMessageClient(context)
        nodes.forEach { node ->
            Tasks.await(client.sendMessage(node.id, path, payload.toByteArray(Charsets.UTF_8)), 5, TimeUnit.SECONDS)
        }
        nodes.isNotEmpty()
    }.getOrDefault(false)
}
