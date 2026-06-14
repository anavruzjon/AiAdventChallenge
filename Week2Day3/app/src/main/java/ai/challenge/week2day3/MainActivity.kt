package ai.challenge.week2day3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import ai.challenge.week2day3.ui.ChatScreen
import ai.challenge.week2day3.ui.theme.Week2Day3Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Week2Day3Theme {
                ChatScreen(modifier = Modifier)
            }
        }
    }
}
