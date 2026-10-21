package com.demostenes.tvhome

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class AppInfo(
    val packageName: String,
    val label: String,
    /** Banner 16:9 (se o app tiver) ou o icone quadrado. */
    val image: ImageBitmap,
    val hasBanner: Boolean,
    /** Versao minuscula da imagem: desenhada esticada vira um fundo "desfocado" barato. */
    val blur: ImageBitmap,
    val color: Color,
)

/**
 * Carrega os apps instalados e guarda favoritos / ocultos em SharedPreferences.
 */
class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val cache = HashMap<String, AppInfo>()

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, ResolveInfo>()
        // Apps de TV primeiro (tem banner), depois apps "de celular" que tambem abrem na TV.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || pkg in EXCLUDED) continue
                found.putIfAbsent(pkg, ri)
            }
        }
        val live = found.keys
        cache.keys.retainAll(live)
        found.map { (pkg, ri) -> cache.getOrPut(pkg) { build(ri) } }
            .sortedBy { it.label.lowercase() }
    }

    private fun build(ri: ResolveInfo): AppInfo {
        val ai = ri.activityInfo
        val banner: Drawable? = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
        val label = ri.loadLabel(pm).toString().ifBlank { ai.packageName }
        val bmp = if (banner != null) {
            banner.toBitmap(320, 180)
        } else {
            ai.loadIcon(pm).toBitmap(192, 192)
        }
        val avg = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
        val blur = Bitmap.createScaledBitmap(bmp, 24, if (banner != null) 14 else 24, true)
        return AppInfo(
            packageName = ai.packageName,
            label = label,
            image = bmp.asImageBitmap(),
            hasBanner = banner != null,
            blur = blur.asImageBitmap(),
            color = vivid(avg),
        )
    }

    // ---------- favoritos / ocultos ----------

    fun favorites(installed: List<AppInfo>): List<String> {
        val pkgs = installed.map { it.packageName }.toSet()
        val saved = prefs.getString(KEY_FAVORITES, null)
        val list = if (saved == null) {
            DEFAULT_FAVORITES.filter { it in pkgs }.also { saveFavorites(it) }
        } else {
            saved.split(',').filter { it.isNotBlank() }
        }
        return list.filter { it in pkgs }
    }

    fun saveFavorites(list: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, list.joinToString(",")).apply()
    }

    fun hidden(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet())!!.toSet()

    fun saveHidden(set: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN, set).apply()
    }

    // ---------- acoes ----------

    fun launchIntent(pkg: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"

        /** Ordem inicial do "dock" (so entram os que estiverem instalados). */
        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.globo.globotv",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.wbd.stream",
            "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android",
            "com.tcl.tv",
        )

        /** Itens de sistema que nao fazem sentido como "app". */
        val EXCLUDED = setOf(
            "com.android.tv.settings",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tungsten.setupwraith",
            "com.tcl.partnercustomizer",
            "com.tcl.suspension",
        )

        private fun vivid(argb: Int): Color {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, hsv)
            hsv[1] = (hsv[1] * 1.25f).coerceIn(0f, 0.85f)
            hsv[2] = hsv[2].coerceIn(0.30f, 0.75f)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }
    }
}

private fun Drawable.toBitmap(w: Int, h: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null && bitmap.width == w && bitmap.height == h) return bitmap
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(c)
    return bmp
}
