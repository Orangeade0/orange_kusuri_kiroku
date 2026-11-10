package app.okusurikiroku

import android.content.Context
import android.content.res.Configuration
import app.okusurikiroku.data.SettingsStore
import java.util.Locale

/** アプリの表示言語。 */
object AppLocale {
    /**
     * 表示言語に合わせた文字列を返す Context にして返す。
     *
     * 設定で言語を選んでいればその言語。「端末の設定に合わせる」のときも、アプリが対応していない言語の端末では
     * 日本語にそろえる（そうしないと、日付選択などシステム部品だけが別の言語になる）。
     */
    fun wrap(context: Context): Context {
        val app = context.applicationContext as? OkusuriApp ?: return context
        val systemLocale = context.resources.configuration.locales[0]
        val tag = app.settings.language.value ?: fallbackFor(systemLocale) ?: return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }

    /** 端末の言語のままでは表示がそろわないときに使う言語。そのままでよければ null。 */
    internal fun fallbackFor(systemLocale: Locale): String? = when (systemLocale.language) {
        "ja" -> null
        // 用意している中国語は簡体字。繁体字の端末でも簡体字で表示する
        "zh" -> {
            val traditional = systemLocale.script == "Hant" || systemLocale.country in setOf("TW", "HK", "MO")
            if (traditional) SettingsStore.LANGUAGE_CHINESE else null
        }

        else -> SettingsStore.LANGUAGE_JAPANESE
    }
}
