package org.wikipedia.suggestededits

import android.app.Activity
import android.view.View
import com.google.android.material.snackbar.Snackbar
import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.auth.AccountUtil
import org.wikipedia.descriptions.DescriptionEditActivity.Action
import org.wikipedia.util.FeedbackUtil

object SuggestedEditsSnackbars {

    fun interface OpenPageListener {
        fun open(actionView: View)
    }

    fun show(activity: Activity, action: Action?, sequentialSnackbar: Boolean = true, targetLanguageCode: String? = null,
             enableViewAction: Boolean = false, listener: OpenPageListener? = null) {
        val app = WikipediaApp.instance
        if (sequentialSnackbar) {
            val snackbar = FeedbackUtil.makeSnackbar(activity,
                    if ((action == Action.TRANSLATE_DESCRIPTION || action == Action.TRANSLATE_CAPTION) &&
                            app.languageState.appLanguageCodes.size > 1) {
                        activity.getString(
                                if (action == Action.TRANSLATE_DESCRIPTION) {
                                    R.string.description_edit_success_saved_in_lang_snackbar
                                } else {
                                    R.string.description_edit_success_saved_image_caption_in_lang_snackbar
                                }, app.languageState.getAppLanguageLocalizedName(targetLanguageCode))
                    } else {
                        activity.getString(
                                when (action) {
                                    Action.ADD_DESCRIPTION -> R.string.description_edit_success_saved_snackbar
                                    Action.ADD_IMAGE_TAGS -> R.string.description_edit_success_saved_image_tags_snackbar
                                    else -> R.string.description_edit_success_saved_image_caption_snackbar
                                })
                    })
            if (enableViewAction && listener != null) {
                snackbar.setAction(R.string.suggested_edits_article_cta_snackbar_action) { listener.open(it) }
            }

            snackbar.addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar, @DismissEvent event: Int) {
                            if (activity.isDestroyed) {
                                return
                            }
                            AccountUtil.maybeShowTempAccountWelcome(activity)
                        }
                    })

            snackbar.show()
        } else {
            AccountUtil.maybeShowTempAccountWelcome(activity)
        }
    }
}
