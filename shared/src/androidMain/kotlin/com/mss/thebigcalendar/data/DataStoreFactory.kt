package com.mss.thebigcalendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

class AndroidDataStoreProvider(private val context: Context) : DataStoreProvider {
    override fun create(name: String): DataStore<Preferences> {
        return androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(name) }
        )
    }
}

actual fun getDataStoreProvider(context: Any?): DataStoreProvider {
    return AndroidDataStoreProvider(context as Context)
}
