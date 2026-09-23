package com.ecosentinel.appblocker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ActivityBlockOverlayBinding

class BlockOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockOverlayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val reason = intent.getStringExtra(EXTRA_BLOCK_REASON).orEmpty()
        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            packageName
        }

        binding.blockedAppName.text = label

        binding.blockMessage.text = BlockOverlayTexts.messageForReason(this, reason)
        binding.resetTimeText.text = BlockOverlayTexts.footerForReason(this, packageName, reason)

        binding.btnGoHome.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_BLOCK_REASON = "extra_block_reason"
        const val REASON_PERMANENT = "permanent"
        const val REASON_TIME_LIMIT = "time_limit"
        const val REASON_TIME_OF_DAY = "time_of_day"
        const val REASON_FOCUS = "focus"
        const val REASON_COOLDOWN = "cooldown"
        const val REASON_ADULT_URL = "adult_url"
        const val REASON_WEBSITE = "website"
        const val REASON_YOUTUBE_SHORTS = "youtube_shorts"
        const val REASON_INSTAGRAM_REELS = "instagram_reels"
        const val REASON_BROWSER_INCOGNITO = "browser_incognito"

        fun launch(context: Context, packageName: String, reason: String) {
            val intent = Intent(context, BlockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_BLOCK_REASON, reason)
            }
            context.startActivity(intent)
        }
    }
}
