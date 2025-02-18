package code.name.monkey.appthemehelper.common.prefs.supportv7

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import androidx.preference.ListPreference
import code.name.monkey.appthemehelper.R
import code.name.monkey.appthemehelper.ThemeStore

/**
 * @author Aidan Follestad (afollestad)
 */
class ATEListPreference : ListPreference {

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    private fun init() {
        icon?.setColorFilter(ThemeStore.textColorSecondary(context), PorterDuff.Mode.SRC_IN)
        layoutResource = R.layout.ate_preference_custom_support
        if (summary == null || summary.toString().trim { it <= ' ' }.isEmpty())
            summary = "%s"
    }
}
