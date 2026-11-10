package app.okusurikiroku

import android.app.Application
import app.okusurikiroku.data.AppDatabase
import app.okusurikiroku.data.ImageStore
import app.okusurikiroku.data.MedRepository
import app.okusurikiroku.data.SettingsStore

class OkusuriApp : Application() {
    @Volatile
    private var repositoryInstance: MedRepository? = null

    /** テストではインメモリ DB のリポジトリに差し替える。 */
    var repository: MedRepository
        get() = repositoryInstance ?: synchronized(this) {
            repositoryInstance ?: MedRepository(AppDatabase.create(this)).also { repositoryInstance = it }
        }
        set(value) {
            repositoryInstance = value
        }

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val images: ImageStore by lazy { ImageStore(this) }
}
