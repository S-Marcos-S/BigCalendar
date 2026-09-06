package com.mss.thebigcalendar.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mss.thebigcalendar.data.model.CalendarAiTemplateSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class CalendarAiTemplateRepository(private val context: Context) {

    companion object {
        private const val TAG = "AiTemplateRepository"
        private const val TEMPLATES_DIR_NAME = "ai_calendar_templates"
    }

    private val gson = Gson()
    private val templatesDir: File by lazy {
        File(context.filesDir, TEMPLATES_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private val _templatesFlow = MutableStateFlow<List<CalendarAiTemplateSpec>>(emptyList())
    val templatesFlow: Flow<List<CalendarAiTemplateSpec>> = _templatesFlow.asStateFlow()

    init {
        loadTemplatesSync()
    }

    private fun loadTemplatesSync() {
        try {
            val userTemplates = mutableListOf<CalendarAiTemplateSpec>()
            if (templatesDir.exists() && templatesDir.isDirectory) {
                templatesDir.listFiles { file -> file.extension.equals("json", ignoreCase = true) }?.forEach { file ->
                    try {
                        val json = file.readText()
                        val spec = CalendarAiTemplateSpec.fromJson(json)
                        if (spec != null) {
                            userTemplates.add(spec.copy(isUserCreated = true))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao ler template do arquivo ${file.name}", e)
                    }
                }
            }

            val allTemplates = CalendarAiTemplateSpec.getDefaultPresets() + userTemplates
            _templatesFlow.value = allTemplates
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar templates", e)
            _templatesFlow.value = CalendarAiTemplateSpec.getDefaultPresets()
        }
    }

    suspend fun saveTemplate(template: CalendarAiTemplateSpec): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(templatesDir, "${template.id}.json")
            file.writeText(template.copy(isUserCreated = true).toJson())
            loadTemplatesSync()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar template ${template.name}", e)
            false
        }
    }

    suspend fun deleteTemplate(templateId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(templatesDir, "$templateId.json")
            val deleted = if (file.exists()) file.delete() else false
            loadTemplatesSync()
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deletar template $templateId", e)
            false
        }
    }

    fun getAllTemplates(): List<CalendarAiTemplateSpec> {
        return _templatesFlow.value
    }

    fun getTemplateById(id: String): CalendarAiTemplateSpec? {
        return _templatesFlow.value.firstOrNull { it.id == id }
    }
}
