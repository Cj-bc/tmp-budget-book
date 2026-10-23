package jp.cjbc.kakeibo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initStorage(this)
        if (savedInstanceState == null) handleRedirect(intent)
        setContent { App() }
    }

    // GitHub redirects back here after signing in; the activity is single-task.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirect(intent)
    }

    override fun onStop() {
        super.onStop()
        gitHubSync.retryPush()
    }

    private fun handleRedirect(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        gitHubSync.handleRedirect(uri)
    }
}
