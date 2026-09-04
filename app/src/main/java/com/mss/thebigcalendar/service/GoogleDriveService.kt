package com.mss.thebigcalendar.service

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import java.io.File

class GoogleDriveService(
    private val context: Context,
    private val account: GoogleSignInAccount
) {

    val drive: Drive by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = account.account
        }

        Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("The Big Calendar")
            .build()
    }

    companion object {
        const val BACKUP_FOLDER = "appDataFolder"
    }

    fun uploadBackupFile(
        file: File,
        appProperties: Map<String, String>
    ): com.google.api.services.drive.model.File? {
        val fileMetadata = com.google.api.services.drive.model.File().apply {
            name = file.name
            parents = listOf(BACKUP_FOLDER)
            this.appProperties = appProperties
        }
        val mediaContent = com.google.api.client.http.FileContent("application/json", file)

        return drive.files().create(fileMetadata, mediaContent).execute()
    }

    fun getBackupFiles(): List<com.google.api.services.drive.model.File> {
        val files = drive.files().list()
            .setSpaces(BACKUP_FOLDER)
            .setFields("files(id, name, createdTime, appProperties)")
            .execute()
            .files ?: emptyList()
        android.util.Log.d("GoogleDriveService", "📂 Arquivos listados no AppDataFolder: ${files.map { "${it.name} (ID: ${it.id})" }}")
        return files
    }
    fun downloadBackupFile(fileId: String, destination: File) {
        android.util.Log.d("GoogleDriveService", "📥 Iniciando download do backup. FileId: $fileId")
        try {
            // 1. Tenta obter os metadados para ver se o arquivo existe e está acessível
            val metadata = drive.files().get(fileId).execute()
            android.util.Log.d("GoogleDriveService", "✅ Metadados encontrados: nome=${metadata.name}, tamanho=${metadata.size}")

            // 2. Realiza o download
            drive.files().get(fileId).executeMediaAndDownloadTo(destination.outputStream())
            android.util.Log.d("GoogleDriveService", "✅ Arquivo baixado com sucesso em: ${destination.absolutePath}")
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            android.util.Log.e("GoogleDriveService", "❌ Erro da API do Google (Código ${e.statusCode}): ${e.message}")
            android.util.Log.e("GoogleDriveService", "❌ Detalhes do erro: ${e.content}")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveService", "❌ Erro geral ao baixar o arquivo: ${e.message}", e)
            throw e
        }
    }

    fun deleteBackupFile(fileId: String) {
        drive.files().delete(fileId).execute()
    }
}
