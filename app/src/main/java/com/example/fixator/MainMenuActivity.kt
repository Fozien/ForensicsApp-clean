package com.example.fixator

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.cardview.widget.CardView
import com.example.fixator.cvcamera.MainActivity
import com.example.fixator.yandexgpt.YandexGPTActivity

class MainMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        val btnCamera = findViewById<CardView>(R.id.btn_camera)
        val btnGptAssistant = findViewById<CardView>(R.id.btn_gpt_assistant)

        btnCamera.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        btnGptAssistant.setOnClickListener {
            startActivity(Intent(this, YandexGPTActivity::class.java))
        }
    }
}