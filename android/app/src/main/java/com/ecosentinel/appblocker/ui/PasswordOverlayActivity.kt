package com.ecosentinel.appblocker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.OverlayPasswordBinding
import com.ecosentinel.appblocker.security.AppPasswordStore
import com.ecosentinel.appblocker.security.PasswordSessionManager
import com.ecosentinel.appblocker.util.InstalledAppsHelper

class PasswordOverlayActivity : AppCompatActivity() {

    private lateinit var binding: OverlayPasswordBinding
    private lateinit var passwordStore: AppPasswordStore
    private var packageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OverlayPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        passwordStore = AppPasswordStore(this)

        binding.protectedAppName.text = InstalledAppsHelper.getAppLabel(this, packageName)
        binding.pinInput.requestFocus()

        binding.btnUnlock.setOnClickListener { tryUnlock() }
        binding.pinInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryUnlock()
                true
            } else {
                false
            }
        }
        binding.btnGoHome.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    private fun tryUnlock() {
        val pin = binding.pinInput.text?.toString().orEmpty()
        if (passwordStore.verifyPin(pin)) {
            PasswordSessionManager.unlock(packageName)
            finish()
        } else {
            binding.errorText.visibility = android.view.View.VISIBLE
            binding.errorText.text = getString(R.string.app_password_wrong_pin)
            binding.pinInput.text?.clear()
            Toast.makeText(this, R.string.app_password_wrong_pin, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"

        fun launch(context: Context, packageName: String) {
            context.startActivity(
                Intent(context, PasswordOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                }
            )
        }
    }
}
