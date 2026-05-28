package com.mss.thebigcalendar.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File

class DesktopDataStoreProvider : DataStoreProvider {
    override fun create(name: String): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = {
                val home = System.getProperty("user.home")
                val dir = File(home, ".thebigcalendar")
                if (!dir.exists()) dir.mkdirs()
                File(dir, "$name.preferences_pb")
            }
        )
    }
}

actual fun getDataStoreProvider(context: Any?): DataStoreProvider {
    return DesktopDataStoreProvider()
}
