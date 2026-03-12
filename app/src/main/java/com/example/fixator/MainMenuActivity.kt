package com.example.fixator

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.example.fixator.cvcamera.MainActivity
import com.example.fixator.yandexgpt.YandexGPTActivity

class MainMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        // Находим кнопки
        val btnCamera = findViewById<Button>(R.id.btn_camera)
        val btnGptAssistant = findViewById<Button>(R.id.btn_gpt_assistant)

        // Обработчики нажатий
        btnCamera.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        btnGptAssistant.setOnClickListener {
            startActivity(Intent(this, YandexGPTActivity::class.java))
        }
    }
}