package ai.challenge.week2day1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import ai.challenge.week2day1.ui.ChatScreen
import ai.challenge.week2day1.ui.theme.Week2Day1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Week2Day1Theme {
                ChatScreen(modifier = Modifier)
            }
        }
    }
}
