package com.example.fixator.yandexgpt

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.fixator.BuildConfig
import com.example.fixator.databinding.ActivityYandexgptBinding
import com.example.fixator.report.ReportGenerator
import com.example.fixator.yandexgpt.viewmodel.YandexGPTViewModel
import java.io.File

class YandexGPTActivity : AppCompatActivity() {

    private lateinit var binding: ActivityYandexgptBinding
    private lateinit var viewModel: YandexGPTViewModel

    private var formalTextResult: String? = null
    private var photoPath: String? = null

    companion object {
        private const val KEY_FORMAL_TEXT = "formal_text"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYandexgptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        savedInstanceState?.getString(KEY_FORMAL_TEXT)?.let { saved ->
            formalTextResult = saved
            binding.tvResult.text = saved
        }

        val imagePath = intent.getStringExtra("imagePath")
        photoPath = intent.getStringExtra("photoPath")

        if (!imagePath.isNullOrEmpty()) {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap != null) {
                binding.ivPhoto.setImageBitmap(bitmap)
            } else {
                binding.ivPhoto.visibility = View.GONE
            }
        }

        viewModel = ViewModelProvider(this).get(YandexGPTViewModel::class.java)

        binding.btnSubmit.setOnClickListener {
            val text = binding.etInput.text.toString().trim()

            if (text.isEmpty()) {
                Toast.makeText(this, "Введите описание происшествия", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val folderId = BuildConfig.YANDEX_FOLDER_ID
            val apiKey = BuildConfig.YANDEX_API_KEY

            if (folderId.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(this, "API-ключи не настроены в BuildConfig", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            setLoadingState(true)

            viewModel.getFormalText(folderId, apiKey, text) { result, isError ->
                runOnUiThread {
                    setLoadingState(false)
                    if (isError) {
                        Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                    } else {
                        binding.tvResult.text = result
                        formalTextResult = result
                    }
                }
            }
        }

        binding.btnGenerateReport.setOnClickListener {
            val formalText = formalTextResult

            when {
                formalText.isNullOrEmpty() -> {
                    Toast.makeText(this, "Сначала получите формализованный текст", Toast.LENGTH_SHORT).show()
                }
                photoPath.isNullOrEmpty() -> {
                    Toast.makeText(this, "Отсутствует путь к фотографии", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val photoFile = File(photoPath!!)
                    if (!photoFile.exists()) {
                        Toast.makeText(this, "Файл фотографии не найден", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    try {
                        val reportUri = ReportGenerator.generateReport(this, photoFile, formalText)
                        openPdf(reportUri)
                        Toast.makeText(this, "Отчёт сохранён в Загрузки/Fixator", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Ошибка генерации отчёта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun openPdf(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Установите приложение для просмотра PDF", Toast.LENGTH_LONG).show()
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnSubmit.isEnabled = !isLoading
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        formalTextResult?.let { outState.putString(KEY_FORMAL_TEXT, it) }
    }
}