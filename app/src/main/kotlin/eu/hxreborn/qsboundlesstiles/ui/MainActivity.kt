package eu.hxreborn.qsboundlesstiles.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.res.use
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import eu.hxreborn.qsboundlesstiles.BuildConfig
import eu.hxreborn.qsboundlesstiles.QSBoundlessTilesApp
import eu.hxreborn.qsboundlesstiles.R
import eu.hxreborn.qsboundlesstiles.databinding.ActivityMainBinding
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import eu.hxreborn.qsboundlesstiles.scanner.TileScanner
import eu.hxreborn.qsboundlesstiles.util.RootUtils
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import com.google.android.material.R as M

class MainActivity :
    AppCompatActivity(),
    XposedServiceHelper.OnServiceListener {
    private lateinit var binding: ActivityMainBinding
    private var xposedService: XposedService? = null

    // Syncs with hooked SystemUI via libxposed RemotePreferences
    private var remotePrefs: SharedPreferences? = null

    // Requires root to read sysui_qs_tiles, 0 if unavailable
    private var activeQsCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupSlider()
        setupFeedback()
        QSBoundlessTilesApp.addServiceListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        QSBoundlessTilesApp.removeServiceListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        remotePrefs = service.getRemotePreferences(PrefsManager.PREFS_GROUP)
        runOnUiThread {
            updateStatusCard()
            loadPrefs()
        }
    }

    override fun onServiceDied(service: XposedService) {
        xposedService = null
        remotePrefs = null
    }

    override fun onResume() {
        super.onResume()
        updateStatusCard()
        loadPrefs()
        refreshActiveQsCount()
    }

    private fun getMaxBound(): Int =
        remotePrefs?.getInt("max_bound", PrefsManager.DEFAULT_MAX_BOUND)
            ?: PrefsManager.DEFAULT_MAX_BOUND

    private fun refreshActiveQsCount() {
        lifecycleScope.launch {
            activeQsCount = RootUtils.getActiveQsTileCount()
            loadPrefs()
        }
    }

    private fun setMaxBound(value: Int) {
        val clamped = value.coerceIn(PrefsManager.DEFAULT_MAX_BOUND, PrefsManager.MAX_BOUND)
        remotePrefs?.edit { putInt("max_bound", clamped) }
    }

    private fun updateStatusCard() {
        val isActive = xposedService != null

        val (titleRes, iconRes, bgColorAttr, contentColorAttr) =
            if (isActive) {
                StatusStyle(
                    R.string.module_active,
                    R.drawable.ic_check_circle_24,
                    android.R.attr.colorPrimary,
                    M.attr.colorOnPrimary,
                )
            } else {
                StatusStyle(
                    R.string.module_inactive,
                    R.drawable.ic_warning_24,
                    M.attr.colorTertiary,
                    M.attr.colorOnTertiary,
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
    }

    private fun loadPrefs() {
        val activeInQs = activeQsCount
        val availableApps = TileScanner.getThirdPartyTileCount(this)
        val recommended = activeInQs + DEFAULT_AUTO_BUFFER
        val maxBound = getMaxBound()

        binding.targetLimit.text = maxBound.toString()
        binding.systemuiStatus.text = maxBound.toString()

        val sliderMax = availableApps.coerceAtLeast(10)
        binding.maxBoundSlider.valueTo = sliderMax.toFloat()
        binding.sliderMaxLabel.text = getString(R.string.slider_max_label, sliderMax)
        val clampedValue = maxBound.coerceIn(PrefsManager.DEFAULT_MAX_BOUND, sliderMax)
        binding.maxBoundSlider.value = clampedValue.toFloat()

        binding.recommendedIndicator.isInvisible = activeInQs <= 0
        if (activeInQs > 0) positionRecommendedTick(recommended)
        updateStatusLine(maxBound, activeInQs)

        val hasActiveQs = activeInQs > 0
        binding.statusDivider.isGone = !hasActiveQs
        binding.statusLineContainer.isGone = !hasActiveQs

        binding.statActiveValue.text = if (activeInQs == 0) "—" else activeInQs.toString()
        binding.statProvidersValue.text = availableApps.toString()
        updateRootStatus()
    }

    // Aligns tick indicator with slider track position for recommended value
    private fun positionRecommendedTick(recommended: Int) {
        binding.maxBoundSlider.post {
            val slider = binding.maxBoundSlider
            val indicator = binding.recommendedIndicator

            val fraction =
                (recommended - slider.valueFrom) / (slider.valueTo - slider.valueFrom)

            // Account for slider padding and center the tick
            val trackStart =
                slider.left + slider.paddingStart +
                    (slider.width - slider.paddingStart - slider.paddingEnd - slider.trackWidth) / 2
            val tickX = trackStart + (slider.trackWidth * fraction) - (indicator.width / 2f)

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
                        M.attr.colorTertiary
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
                getThemeColor(M.attr.colorTertiary)
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

    private var dragStartValue: Int = 0
    private var currentSnackbar: Snackbar? = null

    private fun setupSlider() {
        binding.maxBoundSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val intValue = value.toInt()
                setMaxBound(intValue)
                binding.targetLimit.text = intValue.toString()
                updateStatusLine(intValue, activeQsCount)
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
                    setMaxBound(oldValue)
                    binding.targetLimit.text = oldValue.toString()
                    updateStatusLine(oldValue, activeQsCount)
                    updateStatusCard()
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
        val recommended =
            (activeQsCount + DEFAULT_AUTO_BUFFER).coerceIn(
                PrefsManager.DEFAULT_MAX_BOUND,
                PrefsManager.MAX_BOUND,
            )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.apply_recommended)
            .setMessage(getString(R.string.apply_recommended_confirm, recommended))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                setMaxBound(recommended)
                binding.maxBoundSlider.value = recommended.toFloat()
                binding.targetLimit.text = recommended.toString()
                updateStatusLine(recommended, activeQsCount)
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
                val stockValue = PrefsManager.DEFAULT_MAX_BOUND
                setMaxBound(stockValue)
                binding.maxBoundSlider.value = stockValue.toFloat()
                binding.targetLimit.text = stockValue.toString()
                updateStatusLine(stockValue, activeQsCount)
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
                val rebound = awaitSystemUiRebind()
                updateStatusCard()
                loadPrefs()
                if (!rebound) {
                    Toast
                        .makeText(
                            this@MainActivity,
                            R.string.systemui_still_restarting,
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }

    private suspend fun awaitSystemUiRebind(timeout: Duration = 5.seconds): Boolean =
        withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { cont ->
                val listener =
                    object : XposedServiceHelper.OnServiceListener {
                        override fun onServiceBind(service: XposedService) {
                            QSBoundlessTilesApp.removeServiceListener(this)
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onServiceDied(service: XposedService) = Unit
                    }
                QSBoundlessTilesApp.addServiceListener(listener)
                cont.invokeOnCancellation { QSBoundlessTilesApp.removeServiceListener(listener) }
            }
        } ?: false

    private fun getThemeColor(attrResId: Int): Int =
        obtainStyledAttributes(intArrayOf(attrResId)).use { it.getColor(0, 0) }

    private data class StatusStyle(
        val titleRes: Int,
        val iconRes: Int,
        val bgColorAttr: Int,
        val contentColorAttr: Int,
    )

    companion object {
        // Headroom added to active tile count for recommended limit
        private const val DEFAULT_AUTO_BUFFER = 2
        private const val GITHUB_ISSUES_URL =
            "https://github.com/hxreborn/qs-boundless-tiles/issues"
    }
}
