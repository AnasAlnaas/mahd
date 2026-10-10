package ly.mahd.app.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Prefs
import java.util.Locale

/**
 * The app is Arabic-first: it always shows Arabic, right-to-left, even on a phone set to
 * English or French. (A later version can add other languages.)
 *
 * It also speaks to whoever uses it: when the father does, every string that has a father version
 * (masculine forms, "your wife") is swapped here, so screens and notifications need no changes.
 * Android's own grammatical-gender resources need Android 14, and most phones here are older.
 */
object AppLocale {
    val LOCALE: Locale = Locale.forLanguageTag("ar-LY")

    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(LOCALE)
        config.setLayoutDirection(LOCALE)
        val localized = base.createConfigurationContext(config)
        return if (Prefs.storedParent(base) == Parent.FATHER) FatherContext(localized) else localized
    }
}

private class FatherContext(base: Context) : ContextWrapper(base) {
    private val father = FatherResources(base.resources)
    override fun getResources(): Resources = father
}

/** The app's resources, with each string that has a father version replaced by it. */
@Suppress("DEPRECATION") // The only way to hand out adjusted strings to every caller; assets and config are shared.
private class FatherResources(base: Resources) : Resources(base.assets, base.displayMetrics, base.configuration) {
    private fun swap(id: Int) = FATHER_STRINGS[id] ?: id

    override fun getText(id: Int): CharSequence = super.getText(swap(id))
    override fun getText(id: Int, def: CharSequence?): CharSequence? = super.getText(swap(id), def)
    override fun getString(id: Int): String = super.getString(swap(id))
    override fun getString(id: Int, vararg formatArgs: Any?): String = super.getString(swap(id), *formatArgs)
    override fun getQuantityText(id: Int, quantity: Int): CharSequence = super.getQuantityText(swap(id), quantity)
    override fun getQuantityString(id: Int, quantity: Int): String = super.getQuantityString(swap(id), quantity)
    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String =
        super.getQuantityString(swap(id), quantity, *formatArgs)
}
