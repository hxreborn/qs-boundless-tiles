package eu.hxreborn.qsboundlesstiles.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.use
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import eu.hxreborn.qsboundlesstiles.BuildConfig
import eu.hxreborn.qsboundlesstiles.R
import eu.hxreborn.qsboundlesstiles.databinding.ActivityMainBinding
import eu.hxreborn.qsboundlesstiles.prefs.AppPrefsHelper
import eu.hxreborn.qsboundlesstiles.scanner.TileScanner
import eu.hxreborn.qsboundlesstiles.util.RootUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupSlider()
        setupFeedback()
    }

    override fun onResume() {
        super.onResume()
        updateStatusCard()
        loadPrefs()
    }

    private fun updateStatusCard() {
        val maxBound = AppPrefsHelper.getMaxBound(this)
        val injectedLimit = AppPrefsHelper.getHookBoundLimit(this)
        val isSynced = maxBound == injectedLimit

        val (titleRes, iconRes, bgColorAttr, contentColorAttr) =
            if (isSynced) {
                StatusStyle(
                    R.string.systemui_up_to_date,
                    R.drawable.ic_check_circle_24,
                    android.R.attr.colorPrimary,
                    com.google.android.material.R.attr.colorOnPrimary,
                )
            } else {
                StatusStyle(
                    R.string.systemui_restart_required,
                    R.drawable.ic_refresh_24,
                    com.google.android.material.R.attr.colorTertiary,
                    com.google.android.material.R.attr.colorOnTertiary,
                )
            }

        val bgColor = getThemeColor(bgColorAttr)
        val contentColor = getThemeColor(contentColorAttr)
        val card = binding.statusCard.statusCardRoot

        card.setCardBackgroundColor(bgColor)
        card.outlineAmbientShadowColor = bgColor
        card.outlineSpotShadowColor = bgColor

        binding.statusCard.statusIcon.setImageResource(iconRes)
        binding.statusCard.statusIcon.imageTintList = ColorStateList.valueOf(contentColor)
        binding.statusCard.statusTitle.setText(titleRes)
        binding.statusCard.statusTitle.setTextColor(contentColor)
        binding.statusCard.statusSubtitle.text =
            getString(
                R.string.status_active_subtitle,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            )
        binding.statusCard.statusSubtitle.setTextColor(contentColor)

        if (isSynced) {
            card.isClickable = false
            card.setOnClickListener(null)
        } else {
            card.isClickable = true
            card.setOnClickListener { showRestartDialog() }
        }
    }

    private fun loadPrefs() {
        val autoBuffer = AppPrefsHelper.getAutoBuffer(this)
        val tileInfo = TileScanner.getTileInfo(this)
        val recommended = tileInfo.activeInQs + autoBuffer
        val injectedLimit = AppPrefsHelper.getHookBoundLimit(this)

        if (!AppPrefsHelper.isMaxBoundSet(this)) {
            AppPrefsHelper.setMaxBound(this, injectedLimit)
        }
        val maxBound = AppPrefsHelper.getMaxBound(this)

        binding.targetLimit.text = maxBound.toString()
        binding.systemuiStatus.text = injectedLimit.toString()

        val sliderMax = (tileInfo.availableApps + autoBuffer).coerceAtLeast(10)
        binding.maxBoundSlider.valueTo = sliderMax.toFloat()
        binding.sliderMaxLabel.text = getString(R.string.slider_max_label, sliderMax)
        val clampedValue = maxBound.coerceIn(3, sliderMax)
        binding.maxBoundSlider.value = clampedValue.toFloat()

        positionRecommendedTick(recommended)
        updateStatusLine(maxBound, tileInfo.activeInQs)

        binding.statActiveValue.text = tileInfo.activeInQs.toString()
        binding.statProvidersValue.text = tileInfo.availableApps.toString()
        updateRootStatus()
    }

    private fun positionRecommendedTick(recommended: Int) {
        binding.maxBoundSlider.post {
            val slider = binding.maxBoundSlider
            val indicator = binding.recommendedIndicator

            val minValue = slider.valueFrom
            val maxValue = slider.valueTo
            val fraction = (recommended - minValue) / (maxValue - minValue)

            val trackWidth = slider.trackWidth
            val sliderLeft = slider.left
            val trackStart =
                sliderLeft + slider.paddingStart +
                    (slider.width - slider.paddingStart - slider.paddingEnd - trackWidth) / 2
            val tickX = trackStart + (trackWidth * fraction) - (indicator.width / 2f)

            indicator.translationX = tickX
        }
    }

    private fun updateRootStatus() {
        lifecycleScope.launch {
            val hasRoot = RootUtils.isRootAvailable()
            binding.rootStatus.text =
                if (hasRoot) {
                    getString(R.string.root_available)
                } else {
                    getString(R.string.root_unavailable)
                }
            binding.rootStatus.setTextColor(
                getThemeColor(
                    if (hasRoot) {
                        android.R.attr.colorPrimary
                    } else {
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    },
                ),
            )
        }
    }

    private fun updateStatusLine(
        maxBound: Int,
        activeInQs: Int,
    ) {
        val headroom = maxBound - activeInQs
        val isOverflow = headroom < 0

        val statusColor =
            if (isOverflow) {
                getThemeColor(com.google.android.material.R.attr.colorTertiary)
            } else {
                getThemeColor(android.R.attr.colorPrimary)
            }

        binding.statusLine.text =
            if (isOverflow) {
                getString(R.string.status_may_unbind, -headroom)
            } else {
                getString(R.string.status_all_bound)
            }
        binding.statusLine.setTextColor(statusColor)

        val iconRes = if (isOverflow) R.drawable.ic_warning_24 else R.drawable.ic_check_circle_24
        binding.tileStatusIcon.setImageResource(iconRes)
        binding.tileStatusIcon.imageTintList = ColorStateList.valueOf(statusColor)
    }

    private var previousMaxBound: Int = 0
    private var dragStartValue: Int = 0
    private var currentSnackbar: Snackbar? = null

    private fun setupSlider() {
        previousMaxBound = AppPrefsHelper.getMaxBound(this)

        binding.maxBoundSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val intValue = value.toInt()
                AppPrefsHelper.setMaxBound(this, intValue)
                binding.targetLimit.text = intValue.toString()
                val tileInfo = TileScanner.getTileInfo(this)
                updateStatusLine(intValue, tileInfo.activeInQs)
                updateStatusCard()
            }
        }

        binding.maxBoundSlider.addOnSliderTouchListener(
            object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(
                    slider: com.google.android.material.slider.Slider,
                ) {
                    dragStartValue = slider.value.toInt()
                }

                override fun onStopTrackingTouch(
                    slider: com.google.android.material.slider.Slider,
                ) {
                    val newValue = slider.value.toInt()
                    if (dragStartValue != newValue) {
                        showUndoSnackbar(dragStartValue, newValue)
                        previousMaxBound = newValue
                    }
                }
            },
        )
    }

    private fun showUndoSnackbar(
        oldValue: Int,
        newValue: Int,
    ) {
        currentSnackbar?.dismiss()
        currentSnackbar =
            Snackbar
                .make(
                    binding.root,
                    getString(R.string.limit_changed, oldValue, newValue),
                    Snackbar.LENGTH_LONG,
                ).setAction(R.string.undo) {
                    binding.maxBoundSlider.value = oldValue.toFloat()
                    AppPrefsHelper.setMaxBound(this, oldValue)
                    binding.targetLimit.text = oldValue.toString()
                    val tileInfo = TileScanner.getTileInfo(this)
                    updateStatusLine(oldValue, tileInfo.activeInQs)
                    updateStatusCard()
                    previousMaxBound = oldValue
                }
        currentSnackbar?.show()
    }

    private fun setupFeedback() {
        binding.githubIcon.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_ISSUES_URL)))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean = super.onPrepareOptionsMenu(menu)

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_apply_recommended -> {
                showApplyRecommendedDialog()
                true
            }

            R.id.action_reset_stock -> {
                showResetStockDialog()
                true
            }

            R.id.action_restart_systemui -> {
                showRestartDialog()
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }

    private fun showApplyRecommendedDialog() {
        val tileInfo = TileScanner.getTileInfo(this)
        val recommended = (tileInfo.activeInQs + AppPrefsHelper.getAutoBuffer(this)).coerceIn(3, 30)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.apply_recommended)
            .setMessage(getString(R.string.apply_recommended_confirm, recommended))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                AppPrefsHelper.setMaxBound(this, recommended)
                loadPrefs()
                updateStatusCard()
                Toast.makeText(this, R.string.apply_recommended_done, Toast.LENGTH_SHORT).show()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showResetStockDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_stock)
            .setMessage(R.string.reset_stock_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                AppPrefsHelper.setMaxBound(this, 3)
                loadPrefs()
                updateStatusCard()
                Toast.makeText(this, R.string.reset_stock_done, Toast.LENGTH_SHORT).show()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRestartDialog() {
        lifecycleScope.launch {
            if (!RootUtils.isRootAvailable()) {
                Toast
                    .makeText(
                        this@MainActivity,
                        R.string.restart_systemui_no_root,
                        Toast.LENGTH_LONG,
                    ).show()
                return@launch
            }

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.restart_systemui)
                .setMessage(R.string.restart_warning)
                .setPositiveButton(android.R.string.ok) { _, _ -> performRestart() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun performRestart() {
        lifecycleScope.launch {
            val success = RootUtils.restartSystemUI()
            val msg =
                if (success) R.string.restart_systemui_success else R.string.restart_systemui_failed
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()

            if (success) {
                kotlinx.coroutines.delay(2000)
                updateStatusCard()
                loadPrefs()
            }
        }
    }

    private fun getThemeColor(attrResId: Int): Int =
        obtainStyledAttributes(intArrayOf(attrResId)).use { it.getColor(0, 0) }

    private data class StatusStyle(
        val titleRes: Int,
        val iconRes: Int,
        val bgColorAttr: Int,
        val contentColorAttr: Int,
    )

    companion object {
        private const val GITHUB_ISSUES_URL =
            "https://github.com/hxreborn/qs-boundless-tiles/issues"
    }
}
