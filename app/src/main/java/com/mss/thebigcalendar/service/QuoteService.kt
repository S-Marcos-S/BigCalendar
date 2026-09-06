package com.mss.thebigcalendar.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mss.thebigcalendar.data.model.Quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Serviço para gerenciar frases inspiracionais do arquivo frases.json
 */
class QuoteService(private val context: Context) {
    
    companion object {
        private const val TAG = "QuoteService"
        private const val PREFS_NAME = "quote_prefs"
        private const val KEY_LAST_QUOTE_DATE = "last_quote_date"
        private const val KEY_LAST_QUOTE_INDEX = "last_quote_index"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var quotes: List<Quote>? = null
    
    /**
     * Carrega as frases do arquivo frases.json
     */
    private suspend fun loadQuotes(): List<Quote> = withContext(Dispatchers.IO) {
        if (quotes != null) return@withContext quotes!!
        
        try {
            val jsonString = context.assets.open("frases.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val quotesList = mutableListOf<Quote>()
            
            for (i in 0 until jsonArray.length()) {
                val quoteJson = jsonArray.getJSONObject(i)
                val quote = Quote(
                    autor = quoteJson.getString("autor"),
                    frase = quoteJson.getString("frase")
                )
                quotesList.add(quote)
            }
            
            quotes = quotesList
            quotesList
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao carregar frases", e)
            emptyList()
        }
    }
    
    /**
     * Obtém a frase do dia baseada na data atual
     */
    suspend fun getQuoteOfTheDay(): Quote? {
        val quotes = loadQuotes()
        if (quotes.isEmpty()) return null
        
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastDate = prefs.getString(KEY_LAST_QUOTE_DATE, "")
        val lastIndex = prefs.getInt(KEY_LAST_QUOTE_INDEX, 0)
        
        return if (today != lastDate) {
            // Nova data, usar próxima frase
            val nextIndex = (lastIndex + 1) % quotes.size
            val quote = quotes[nextIndex]
            
            // Salvar nova data e índice
            prefs.edit()
                .putString(KEY_LAST_QUOTE_DATE, today)
                .putInt(KEY_LAST_QUOTE_INDEX, nextIndex)
                .apply()
            
            quote
        } else {
            // Mesmo dia, usar frase salva
            quotes[lastIndex]
        }
    }
    
    /**
     * Obtém a frase do dia de forma síncrona (para evitar piscadas na UI)
     */
    fun getQuoteOfTheDaySync(): Quote? {
        val quotes = loadQuotesSync()
        if (quotes.isEmpty()) return null
        
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastDate = prefs.getString(KEY_LAST_QUOTE_DATE, "")
        val lastIndex = prefs.getInt(KEY_LAST_QUOTE_INDEX, 0)
        
        return if (today != lastDate) {
            // Nova data, usar próxima frase
            val nextIndex = (lastIndex + 1) % quotes.size
            val quote = quotes[nextIndex]
            
            // Salvar nova data e índice
            prefs.edit()
                .putString(KEY_LAST_QUOTE_DATE, today)
                .putInt(KEY_LAST_QUOTE_INDEX, nextIndex)
                .apply()
            
            quote
        } else {
            // Mesmo dia, usar frase salva
            quotes[lastIndex]
        }
    }
    
    /**
     * Carrega as frases de forma síncrona
     */
    private fun loadQuotesSync(): List<Quote> {
        if (quotes != null) return quotes!!
        
        return try {
            val jsonString = context.assets.open("frases.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val quotesList = mutableListOf<Quote>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val autor = jsonObject.getString("autor")
                val frase = jsonObject.getString("frase")
                quotesList.add(Quote(autor = autor, frase = frase))
            }
            
            quotes = quotesList
            quotesList
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao carregar frases (sync)", e)
            emptyList()
        }
    }
    
    /**
     * Obtém uma frase aleatória
     */
    suspend fun getRandomQuote(): Quote? {
        val quotes = loadQuotes()
        if (quotes.isEmpty()) return null
        
        val randomIndex = (0 until quotes.size).random()
        return quotes[randomIndex]
    }
    
    /**
     * Obtém o total de frases disponíveis
     */
    suspend fun getTotalQuotes(): Int {
        val quotes = loadQuotes()
        return quotes.size
    }
    
    /**
     * Reseta o contador de frases (útil para testes)
     */
    fun resetQuoteCounter() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Limpa o cache de frases e força o recarregamento
     */
    fun clearCacheAndReload() {
        quotes = null // Limpa o cache em memória
        prefs.edit().clear().apply() // Limpa o cache persistente
    }
    
    /**
     * Obtém o índice da última frase exibida
     */
    fun getLastQuoteIndex(): Int {
        val lastDate = prefs.getString(KEY_LAST_QUOTE_DATE, "")
        if (lastDate.isNullOrEmpty()) {
            getQuoteOfTheDaySync()
        }
        return prefs.getInt(KEY_LAST_QUOTE_INDEX, 0)
    }

    /**
     * Obtém a data da última frase exibida
     */
    fun getLastQuoteDate(): String {
        val lastDate = prefs.getString(KEY_LAST_QUOTE_DATE, "")
        if (lastDate.isNullOrEmpty()) {
            getQuoteOfTheDaySync()
        }
        return prefs.getString(KEY_LAST_QUOTE_DATE, "") ?: ""
    }

    /**
     * Obtém a frase atualmente ativa/mostrada no sidebar
     */
    fun getCurrentQuote(): Quote? {
        val quotes = loadQuotesSync()
        if (quotes.isEmpty()) return null
        val lastDate = prefs.getString(KEY_LAST_QUOTE_DATE, "")
        if (lastDate.isNullOrEmpty()) {
            return getQuoteOfTheDaySync()
        }
        val lastIndex = prefs.getInt(KEY_LAST_QUOTE_INDEX, 0)
        return if (lastIndex in quotes.indices) quotes[lastIndex] else quotes[0]
    }

    /**
     * Restaura o estado da frase (índice, data e opcionalmente valida pelo texto da frase)
     */
    fun restoreQuoteState(index: Int, date: String = "", quoteText: String = "") {
        val quotes = loadQuotesSync()
        var targetIndex = index

        if (quoteText.isNotBlank() && quotes.isNotEmpty()) {
            val foundIndex = quotes.indexOfFirst { it.frase.trim().equals(quoteText.trim(), ignoreCase = true) }
            if (foundIndex != -1) {
                targetIndex = foundIndex
            }
        }

        if (quotes.isNotEmpty()) {
            targetIndex = if (targetIndex in quotes.indices) targetIndex else targetIndex.coerceIn(0, quotes.size - 1)
        }

        prefs.edit()
            .putInt(KEY_LAST_QUOTE_INDEX, targetIndex)
            .putString(KEY_LAST_QUOTE_DATE, date)
            .apply()

        Log.d(TAG, "Frase restaurada com sucesso: index=$targetIndex, date=$date")
    }

    /**
     * Força o recarregamento das frases (ignora cache)
     */
    suspend fun forceReloadQuotes(): List<Quote> = withContext(Dispatchers.IO) {
        quotes = null // Limpa cache em memória
        loadQuotes() // Recarrega do arquivo
    }
}

