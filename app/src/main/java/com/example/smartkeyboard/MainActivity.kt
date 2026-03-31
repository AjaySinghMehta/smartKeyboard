package com.example.smartkeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView


class MainActivity : android.app.Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val title = findViewById<TextView>(R.id.titleText)
        val description = findViewById<TextView>(R.id.descriptionText)

        val enableButton = findViewById<Button>(R.id.enableKeyboardBtn)
        val selectButton = findViewById<Button>(R.id.selectKeyboardBtn)

        title.text = "Smart Keyboard"
        description.text =
                "A customizable Android keyboard designed to bring desktop-level control to mobile typing.\\n\n" +
                        "Provides better control over text editing directly from the keyboard, reducing the need to switch between keyboard and screen.\\n\n" +
                        "\uD83E\uDDE0 Smart Suggestions – Get word suggestions while typing and improve spelling.\\n\n" +
                        "⌨\uFE0F Advanced Text Control – Move cursor using arrow keys and edit text precisely.\\n\n" +
                        "✂\uFE0F Editing Shortcuts – Select, Select All, Copy, Cut, and Paste directly from the keyboard.\\n\n" +
                        "\uD83C\uDFAF Selection System – Use shift with arrow keys for desktop-like text selection.\n"

        enableButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        selectButton.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }
}