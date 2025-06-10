package com.example.forensicsapp.yandexgpt

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.forensicsapp.BuildConfig
import com.example.forensicsapp.databinding.ActivityYandexgptBinding
import com.example.forensicsapp.report.ReportGenerator
import com.example.forensicsapp.yandexgpt.viewmodel.YandexGPTViewModel
import java.io.File

class YandexGPTActivity : AppCompatActivity() {
    private lateinit var binding: ActivityYandexgptBinding
    private lateinit var viewModel: YandexGPTViewModel

    private var formalTextResult: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYandexgptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imagePath = intent.getStringExtra("imagePath")
        val photoPath = intent.getStringExtra("photoPath")

        Log.d("YandexGPTActivity", "imagePath: $imagePath, photoPath: $photoPath")

        if (!imagePath.isNullOrEmpty()) {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            binding.ivPhoto.setImageBitmap(bitmap)
        }

        viewModel = ViewModelProvider(this).get(YandexGPTViewModel::class.java)

        binding.btnSubmit.setOnClickListener {
            val text = binding.etInput.text.toString()
            if (text.isNotEmpty()) {
                // Получаем ключи из BuildConfig
                val folderId = BuildConfig.YANDEX_FOLDER_ID
                val iamToken = BuildConfig.YANDEX_IAM_TOKEN

                // Проверяем, что ключи не пустые
                if (folderId.isEmpty() || iamToken.isEmpty()) {
                    Toast.makeText(this, "API ключи не настроены", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                viewModel.getFormalText(folderId, iamToken, text) { result ->
                    runOnUiThread {
                        binding.tvResult.text = result
                        formalTextResult = result
                    }
                }
            }
        }

        binding.btnGenerateReport.setOnClickListener {
            Log.d("YandexGPTActivity", "Attempting to generate report...")
            val formalText = formalTextResult
            if (!photoPath.isNullOrEmpty() && !formalText.isNullOrEmpty()) {
                val photoFile = File(photoPath)
                val reportFile = ReportGenerator.generateReport(this, photoFile, formalText)
                if (reportFile != null) {
                    Toast.makeText(this, "Отчет сохранен в: ${reportFile.absolutePath}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Ошибка при генерации отчета", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Недостаточно данных для генерации отчета", Toast.LENGTH_SHORT).show()
            }
        }
    }
}