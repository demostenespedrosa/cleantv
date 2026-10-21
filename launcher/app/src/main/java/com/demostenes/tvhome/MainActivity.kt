package com.demostenes.tvhome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Estado observavel da tela inicial. */
class HomeState(private val repo: AppRepository) {
    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var favorites by mutableStateOf<List<String>>(emptyList())
        private set
    var hidden by mutableStateOf<Set<String>>(emptySet())
        private set
    var loaded by mutableStateOf(false)
        private set

    /** Incrementa toda vez que o botao Home e apertado com a tela inicial ja aberta. */
    var homeTick by mutableIntStateOf(0)

    suspend fun reload() {
        val list = repo.loadApps()
        apps = list
        hidden = repo.hidden()
        favorites = repo.favorites(list).filter { it !in hidden }
        loaded = true
    }

    fun toggleFavorite(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        repo.saveFavorites(favorites)
    }

    fun moveFavorite(pkg: String, delta: Int) {
        val i = favorites.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in favorites.indices) return
        favorites = favorites.toMutableList().apply { add(j, removeAt(i)) }
        repo.saveFavorites(favorites)
    }

    fun toggleHidden(pkg: String) {
        hidden = if (pkg in hidden) hidden - pkg else hidden + pkg
        repo.saveHidden(hidden)
        if (pkg in hidden && pkg in favorites) {
            favorites = favorites - pkg
            repo.saveFavorites(favorites)
        }
    }
}

data class InputItem(val id: String?, val label: String)

interface HomeActions {
    fun launch(pkg: String)
    fun openSettings()
    fun appInfo(pkg: String)
    fun uninstall(pkg: String)
    fun inputs(): List<InputItem>
    fun openInput(input: InputItem)
}

class MainActivity : ComponentActivity(), HomeActions {

    private lateinit var repo: AppRepository
    private lateinit var state: HomeState

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lifecycleScope.launch { state.reload() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(applicationContext)
        state = HomeState(repo)
        lifecycleScope.launch { state.reload() }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }

        setContent { HomeScreen(state = state, actions = this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) state.homeTick++
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    // ---------------- HomeActions ----------------

    private fun start(intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun launch(pkg: String) {
        if (!start(repo.launchIntent(pkg))) {
            Toast.makeText(this, "Não foi possível abrir o app", Toast.LENGTH_SHORT).show()
        }
    }

    override fun openSettings() {
        start(Intent(Settings.ACTION_SETTINGS))
    }

    override fun appInfo(pkg: String) {
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
    }

    override fun uninstall(pkg: String) {
        start(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }

    override fun inputs(): List<InputItem> {
        val list = mutableListOf<InputItem>()
        val tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        tim?.tvInputList
            ?.filter { it.isPassthroughInput && !it.isHidden(this) }
            ?.sortedBy { it.type != TvInputInfo.TYPE_HDMI }
            ?.forEach { info ->
                val label = info.loadCustomLabel(this)?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.loadLabel(this).toString()
                list += InputItem(info.id, label)
            }
        list += InputItem(null, "TV (antena)")
        return list
    }

    override fun openInput(input: InputItem) {
        val ok = if (input.id != null) {
            start(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)))
        } else {
            start(Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)) || false
        }
        if (!ok) launch("com.tcl.tv")
    }
}
