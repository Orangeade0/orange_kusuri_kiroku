package app.okusurikiroku

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.okusurikiroku.data.AppDatabase
import app.okusurikiroku.data.MedRepository

/** アプリのリポジトリをインメモリ DB に差し替える。 */
fun installInMemoryRepository(): Pair<AppDatabase, MedRepository> {
    val app = ApplicationProvider.getApplicationContext<OkusuriApp>()
    val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    val repository = MedRepository(db)
    app.repository = repository
    return db to repository
}

fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCompat(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().let { nodes ->
        List(nodes.size) { index -> onAllNodes(androidx.compose.ui.test.hasText(text))[index] }
    }
