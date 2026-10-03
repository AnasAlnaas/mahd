package ly.mahd.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app is Arabic-first: it always shows Arabic, right-to-left, even on a phone set to
 * English or French. (A later version can add other languages.)
 */
object AppLocale {
    val LOCALE: Locale = Locale.forLanguageTag("ar-LY")

    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(LOCALE)
        config.setLayoutDirection(LOCALE)
        return base.createConfigurationContext(config)
    }
}
