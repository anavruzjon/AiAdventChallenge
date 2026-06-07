package ai.challenge.week0day5

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import ai.challenge.week0day5.ui.ChatScreen
import ai.challenge.week0day5.ui.theme.Week0Day5Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Week0Day5Theme {
                ChatScreen(modifier = Modifier)
            }
        }
    }
}
