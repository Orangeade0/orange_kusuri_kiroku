package app.okusurikiroku

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** 設定で選んだ表示言語。 */
object AppLocale {
    /** 言語が選ばれていれば、その言語の文字列を返す Context にして返す。 */
    fun wrap(context: Context): Context {
        val app = context.applicationContext as? OkusuriApp ?: return context
        val tag = app.settings.language.value ?: return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }
}
