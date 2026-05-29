package com.rbitton.calendae

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rbitton.calendae.ui.calendar.CalendarScreen
import com.rbitton.calendae.ui.theme.CalendaeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendaeTheme {
                CalendarScreen()
            }
        }
    }
}
