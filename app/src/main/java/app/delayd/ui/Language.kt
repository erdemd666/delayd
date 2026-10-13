package app.delayd.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/** In-app language: the platform stores it (API 33+), the system's per-app language setting stays in sync. Older Android follows the phone. */
object AppLanguage {
    val supported get() = Build.VERSION.SDK_INT >= 33

    /** "de", "en", or null = follow the system. */
    fun current(c: Context): String? =
        if (!supported) null else c.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language

    fun set(c: Context, tag: String?) {
        if (supported) c.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }
}
