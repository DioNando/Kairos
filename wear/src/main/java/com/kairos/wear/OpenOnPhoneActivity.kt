package com.kairos.wear

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.wear.remote.interactions.RemoteActivityHelper

/**
 * Activité transparente déclenchée par l'appui sur la tuile : demande l'ouverture de Kairós sur le
 * téléphone (deep link `kairos://open` via [RemoteActivityHelper]), affiche un bref retour, puis se
 * ferme. La montre elle-même n'a pas d'écran d'app (tuile seule).
 */
class OpenOnPhoneActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("kairos://open"))

        val future = RemoteActivityHelper(this).startRemoteActivity(intent)
        Toast.makeText(this, R.string.open_on_phone, Toast.LENGTH_SHORT).show()
        // Se ferme une fois la demande transmise (succès ou échec — sans conséquence bloquante).
        future.addListener({ finish() }, mainExecutor)
    }
}
