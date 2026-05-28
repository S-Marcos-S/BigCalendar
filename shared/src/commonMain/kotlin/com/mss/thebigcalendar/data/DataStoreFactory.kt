package com.mss.thebigcalendar.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

interface DataStoreProvider {
    fun create(name: String): DataStore<Preferences>
}

expect fun getDataStoreProvider(context: Any? = null): DataStoreProvider
