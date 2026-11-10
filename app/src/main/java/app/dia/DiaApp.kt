package app.dia

import android.app.Application
import app.dia.data.DiaRepository
import app.dia.data.db.DiaDatabase
import app.dia.data.libre.LibreLinkUpClient
import app.dia.data.settings.SettingsRepository
import app.dia.sync.SyncWorker

class DiaApp : Application() {
    lateinit var repository: DiaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DiaRepository(
            db = DiaDatabase.create(this),
            settings = SettingsRepository(this),
            libre = LibreLinkUpClient(),
        )
        SyncWorker.schedule(this)
    }
}
