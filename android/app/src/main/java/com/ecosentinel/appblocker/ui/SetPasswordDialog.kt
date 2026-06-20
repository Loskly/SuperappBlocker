package com.ecosentinel.appblocker.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.DialogSetPasswordBinding
import com.ecosentinel.appblocker.security.AppPasswordStore

class SetPasswordDialog : DialogFragment() {

    interface Listener {
        fun onPasswordSaved()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogSetPasswordBinding.inflate(LayoutInflater.from(requireContext()))
        val passwordStore = AppPasswordStore(requireContext())

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val pin = binding.pinInput.text?.toString().orEmpty()
                        val confirm = binding.pinConfirmInput.text?.toString().orEmpty()
                        when {
                            pin.length < 4 -> {
                                Toast.makeText(requireContext(), R.string.app_password_pin_too_short, Toast.LENGTH_SHORT).show()
                            }
                            pin != confirm -> {
                                Toast.makeText(requireContext(), R.string.app_password_pin_mismatch, Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                passwordStore.setPin(pin)
                                (activity as? Listener ?: parentFragment as? Listener)?.onPasswordSaved()
                                dismiss()
                            }
                        }
                    }
                }
            }
    }

    companion object {
        fun newInstance(): SetPasswordDialog = SetPasswordDialog()
    }
}
