package org.wikipedia.page.linkpreview

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.activity.FragmentUtil.getCallback
import org.wikipedia.analytics.eventplatform.ArticleLinkPreviewInteractionEvent
import org.wikipedia.bridge.JavaScriptActionHandler
import org.wikipedia.databinding.DialogLinkPreviewBinding
import org.wikipedia.dataclient.page.PageSummary
import org.wikipedia.edit.EditHandler
import org.wikipedia.edit.EditSectionActivity
import org.wikipedia.extensions.getString
import org.wikipedia.extensions.getStrings
import org.wikipedia.extensions.setLayoutDirectionByLang
import org.wikipedia.gallery.GalleryActivity
import org.wikipedia.gallery.GalleryThumbnailScrollView.GalleryViewListener
import org.wikipedia.history.HistoryEntry
import org.wikipedia.page.ExtendedBottomSheetDialogFragment
import org.wikipedia.page.Namespace
import org.wikipedia.page.PageActivity
import org.wikipedia.page.PageTitle
import org.wikipedia.readinglist.ReadingListBehaviorsUtil
import org.wikipedia.util.ClipboardUtil
import org.wikipedia.util.FeedbackUtil
import org.wikipedia.util.ShareUtil
import org.wikipedia.util.StringUtil
import org.wikipedia.util.log.L
import org.wikipedia.views.ViewUtil

class LinkPreviewDialog : ExtendedBottomSheetDialogFragment(), LinkPreviewErrorView.Callback, DialogInterface.OnDismissListener {
    interface LoadPageCallback {
        fun onLinkPreviewLoadPage(title: PageTitle, entry: HistoryEntry, inNewTab: Boolean)
    }

    interface DismissCallback {
        fun onLinkPreviewDismiss()
    }

    private var _binding: DialogLinkPreviewBinding? = null
    private val binding get() = _binding!!

    private val loadPageCallback get() = getCallback(this, LoadPageCallback::class.java)
    private val dismissCallback get() = getCallback(this, DismissCallback::class.java)

    private var articleLinkPreviewInteractionEvent: ArticleLinkPreviewInteractionEvent? = null
    private var overlayView: LinkPreviewOverlayView? = null
    private var navigateSuccess = false
    private val viewModel: LinkPreviewViewModel by viewModels()

    private val menuListener = PopupMenu.OnMenuItemClickListener { item ->
        return@OnMenuItemClickListener when (item.itemId) {
            R.id.menu_link_preview_add_to_list -> {
                doAddToList()
                true
            }
            R.id.menu_link_preview_share_page -> {
                ShareUtil.shareText(requireContext(), viewModel.pageTitle)
                true
            }
            R.id.menu_link_preview_copy_link -> {
                ClipboardUtil.setPlainText(requireActivity(), text = viewModel.pageTitle.uri)
                FeedbackUtil.showMessage(requireActivity(), R.string.address_copied)
                dismiss()
                true
            }
            else -> false
        }
    }

    private val galleryViewListener = GalleryViewListener { view, thumbUrl, imageName ->
        var options: ActivityOptionsCompat? = null
        view.drawable?.let {
            val hitInfo = JavaScriptActionHandler.ImageHitInfo(0f, 0f, it.intrinsicWidth.toFloat(), it.intrinsicHeight.toFloat(), thumbUrl)
            GalleryActivity.setTransitionInfo(hitInfo)
            view.transitionName = requireActivity().getString(R.string.transition_page_gallery)
            options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), view, requireActivity().getString(R.string.transition_page_gallery))
        }
        requestGalleryLauncher.launch(GalleryActivity.newIntent(requireContext(), viewModel.pageTitle,
            imageName, viewModel.pageTitle.wikiSite), options)
    }

    private val requestGalleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == GalleryActivity.ACTIVITY_RESULT_PAGE_SELECTED && it.data != null) {
            startActivity(it.data!!)
        }
    }

    private val requestStubArticleEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == EditHandler.RESULT_REFRESH_PAGE) {
            overlayView?.let { overlay ->
                FeedbackUtil.makeSnackbar(overlay.rootView, getString(R.string.stub_article_edit_saved_successfully))
                    .setAnchorView(overlay.secondaryButtonView).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogLinkPreviewBinding.inflate(inflater, container, false)
        binding.linkPreviewToolbar.setOnClickListener { goToLinkedPage(false) }
        binding.linkPreviewOverflowButton.setOnClickListener {
            setupOverflowMenu()
        }
        binding.linkPreviewEditButton.setOnClickListener {
            viewModel.pageTitle.run {
                requestStubArticleEditLauncher.launch(EditSectionActivity.newIntent(requireContext(), -1, null, this, Constants.InvokeSource.LINK_PREVIEW_MENU, null))
            }
        }
        binding.root.setLayoutDirectionByLang(viewModel.pageTitle.wikiSite.languageCode)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.uiState.collect {
                    when (it) {
                        is LinkPreviewViewState.Loading -> {
                            binding.linkPreviewProgress.visibility = View.VISIBLE
                        }
                        is LinkPreviewViewState.Error -> {
                            renderErrorState(it.throwable)
                        }
                        is LinkPreviewViewState.Content -> {
                            renderContentState(it.data)
                        }
                        is LinkPreviewViewState.Gallery -> {
                            renderGalleryState(it)
                        }
                        is LinkPreviewViewState.Completed -> {
                            binding.linkPreviewProgress.visibility = View.GONE
                        }
                    }
                }
            }
        }
        return binding.root
    }

    private fun setupOverflowMenu() {
        val popupMenu = PopupMenu(requireActivity(), binding.linkPreviewOverflowButton)
        popupMenu.inflate(R.menu.menu_link_preview)
        popupMenu.setOnMenuItemClickListener(menuListener)
        popupMenu.show()
    }

    private fun renderGalleryState(it: LinkPreviewViewState.Gallery) {
        binding.linkPreviewThumbnailGallery.setGalleryList(it.data)
        binding.linkPreviewThumbnailGallery.listener = galleryViewListener
        binding.linkPreviewProgress.visibility = View.GONE
    }

    private fun renderContentState(summary: PageSummary) {
        articleLinkPreviewInteractionEvent = ArticleLinkPreviewInteractionEvent(
                viewModel.pageTitle.wikiSite.dbName(),
                summary.pageId,
                viewModel.historyEntry.source
        )
        articleLinkPreviewInteractionEvent?.logLinkClick()

        binding.linkPreviewTitle.text = StringUtil.fromHtml(summary.displayTitle)
        showPreview(LinkPreviewContents(summary, viewModel.pageTitle.wikiSite))
    }

    private fun renderErrorState(throwable: Throwable) {
        L.e(throwable)
        binding.linkPreviewTitle.text = StringUtil.fromHtml(viewModel.pageTitle.displayText)
        showError(throwable)
    }

    override fun onResume() {
        super.onResume()

        val containerView = requireDialog().findViewById<ViewGroup>(android.R.id.content)
        if (overlayView == null && containerView != null) {
            LinkPreviewOverlayView(requireContext()).let {
                overlayView = it
                val strings = requireContext().getStrings(viewModel.pageTitle, intArrayOf(R.string.button_continue_to_talk_page, R.string.button_continue_to_article, R.string.menu_long_press_open_in_new_tab))
                it.callback = OverlayViewCallback()
                it.setPrimaryButtonText(strings[if (viewModel.pageTitle.namespace() === Namespace.TALK || viewModel.pageTitle.namespace() === Namespace.USER_TALK) R.string.button_continue_to_talk_page else R.string.button_continue_to_article])
                it.setSecondaryButtonText(strings[R.string.menu_long_press_open_in_new_tab])
                it.showTertiaryButton(false)
                containerView.addView(it)

                ViewCompat.setOnApplyWindowInsetsListener(it) { view, insets ->
                    val systemWindowInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    view.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = systemWindowInsets.bottom }
                    insets
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.linkPreviewThumbnailGallery.listener = null
        binding.linkPreviewToolbar.setOnClickListener(null)
        binding.linkPreviewOverflowButton.setOnClickListener(null)
        overlayView?.let {
            it.callback = null
            overlayView = null
        }
        _binding = null
        super.onDestroyView()
    }

    override fun onDismiss(dialogInterface: DialogInterface) {
        super.onDismiss(dialogInterface)
        dismissCallback?.onLinkPreviewDismiss()
        if (!navigateSuccess) {
            articleLinkPreviewInteractionEvent?.logCancel()
        }
    }

    override fun onAddToList() {
        doAddToList()
    }

    override fun onDismiss() {
        dismiss()
    }

    private fun doAddToList() {
        ReadingListBehaviorsUtil.addToDefaultList(requireActivity(), viewModel.pageTitle, true, Constants.InvokeSource.LINK_PREVIEW_MENU)
        dialog?.dismiss()
    }

    private fun showPreview(contents: LinkPreviewContents) {
        viewModel.loadGallery()
        setPreviewContents(contents)
    }

    private fun showError(caught: Throwable?) {
        binding.dialogLinkPreviewContainer.layoutTransition = null
        binding.dialogLinkPreviewContainer.minimumHeight = 0
        binding.linkPreviewProgress.visibility = View.GONE
        binding.dialogLinkPreviewContentContainer.visibility = View.GONE
        binding.dialogLinkPreviewErrorContainer.visibility = View.VISIBLE
        binding.dialogLinkPreviewErrorContainer.callback = this
        binding.dialogLinkPreviewErrorContainer.setError(caught, viewModel.pageTitle)
        LinkPreviewErrorType[caught, viewModel.pageTitle].run {
            overlayView?.let {
                it.showSecondaryButton(false)
                it.showTertiaryButton(false)
                it.setPrimaryButtonText(resources.getString(buttonText))
                it.callback = buttonAction(binding.dialogLinkPreviewErrorContainer)
                if (this !== LinkPreviewErrorType.OFFLINE) {
                    binding.linkPreviewToolbar.setOnClickListener(null)
                    binding.linkPreviewOverflowButton.visibility = View.GONE
                }
            }
        }
    }

    private fun setPreviewContents(contents: LinkPreviewContents) {
        val editVisibility = contents.extract.isNullOrBlank() && contents.ns?.id == Namespace.MAIN.code()
        binding.linkPreviewEditButton.isVisible = editVisibility
        binding.linkPreviewThumbnailGallery.isVisible = !editVisibility

        val extract = if (editVisibility) "<i>" + getString(R.string.link_preview_stub_placeholder_text) + "</i>" else contents.extract
        binding.linkPreviewExtract.text = StringUtil.fromHtml(extract)

        contents.title.thumbUrl?.let {
            binding.linkPreviewThumbnail.visibility = View.VISIBLE
            ViewUtil.loadImage(binding.linkPreviewThumbnail, it)
        }
        overlayView?.run {
            setPrimaryButtonText(
                requireContext().getString(
                    viewModel.pageTitle,
                    if (contents.isDisambiguation) R.string.button_continue_to_disambiguation
                    else if (viewModel.pageTitle.namespace() === Namespace.TALK || viewModel.pageTitle.namespace() === Namespace.USER_TALK) R.string.button_continue_to_talk_page
                    else R.string.button_continue_to_article
                )
            )
        }
    }

    private fun goToLinkedPage(inNewTab: Boolean) {
        navigateSuccess = true
        dialog?.dismiss()
        loadPage(viewModel.pageTitle, viewModel.historyEntry, inNewTab)
    }

    private fun loadPage(title: PageTitle, entry: HistoryEntry, inNewTab: Boolean) {
        loadPageCallback.let {
            if (it != null) {
                it.onLinkPreviewLoadPage(title, entry, inNewTab)
            } else {
                requireActivity().startActivity(
                    if (inNewTab) PageActivity.newIntentForNewTab(requireContext(), entry, entry.title)
                    else PageActivity.newIntentForCurrentTab(requireContext(), entry, entry.title, false)
                )
            }
        }
    }

    private inner class OverlayViewCallback : LinkPreviewOverlayView.Callback {
        override fun onPrimaryClick() {
            goToLinkedPage(false)
        }

        override fun onSecondaryClick() {
            goToLinkedPage(true)
        }

        override fun onTertiaryClick() {
            // ignore
        }
    }

    companion object {
        const val ARG_ENTRY = "entry"

        fun newInstance(entry: HistoryEntry): LinkPreviewDialog {
            return LinkPreviewDialog().apply {
                arguments = bundleOf(
                    ARG_ENTRY to entry
                )
            }
        }
    }
}
