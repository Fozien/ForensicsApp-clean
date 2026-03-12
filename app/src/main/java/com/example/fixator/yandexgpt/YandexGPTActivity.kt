package com.example.fixator.yandexgpt

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.fixator.BuildConfig
import com.example.fixator.databinding.ActivityYandexgptBinding
import com.example.fixator.report.ReportGenerator
import com.example.fixator.yandexgpt.viewmodel.YandexGPTViewModel
import java.io.File

class YandexGPTActivity : AppCompatActivity() {

    private lateinit var binding: ActivityYandexgptBinding
    private lateinit var viewModel: YandexGPTViewModel

    // Храним в ViewModel через SavedStateHandle было бы идеально,
    // но для простоты — onSaveInstanceState достаточно
    private var formalTextResult: String? = null
    private var photoPath: String? = null

    companion object {
        private const val KEY_FORMAL_TEXT = "formal_text"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYandexgptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Восстанавливаем результат после поворота экрана
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
            val apiKey = BuildConfig.YANDEX_API_KEY  // Переименовано с YANDEX_IAM_TOKEN

            if (folderId.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(this, "API-ключи не настроены в BuildConfig", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            setLoadingState(true)

            viewModel.getFormalText(folderId, apiKey, text) { result, isError ->
                runOnUiThread {
                    setLoadingState(false)

                    if (isError) {
                        // Ошибки показываем отдельно, не затираем предыдущий успешный результат
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
                    Toast.makeText(
                        this,
                        "Сначала получите формализованный текст",
                        Toast.LENGTH_SHORT
                    ).show()
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
                        val reportFile = ReportGenerator.generateReport(this, photoFile, formalText)
                        Toast.makeText(
                            this,
                            "Отчёт сохранён:\n${reportFile.absolutePath}",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Ошибка генерации отчёта: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    /** Блокирует кнопку и показывает индикатор загрузки во время запроса */
    private fun setLoadingState(isLoading: Boolean) {
        binding.btnSubmit.isEnabled = !isLoading
        //binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        // Если в layout нет progressBar — удали эту строку
    }

    /** Сохраняем результат при повороте экрана */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        formalTextResult?.let { outState.putString(KEY_FORMAL_TEXT, it) }
    }
}