package app.okusurikiroku

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.okusurikiroku.ui.OkusuriAppUi
import app.okusurikiroku.ui.OkusuriTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // 設定で選んだ言語で表示する
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OkusuriTheme {
                OkusuriAppUi()
            }
        }
    }
}
