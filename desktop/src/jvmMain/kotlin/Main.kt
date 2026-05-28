import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.mss.thebigcalendar.ui.screens.CommonCalendarScreen

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "The Big Calendar") {
        val scope = rememberCoroutineScope()
        val viewModel = remember { DesktopCalendarViewModel(scope) }
        
        MaterialTheme(
            colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        ) {
            CommonCalendarScreen(viewModel = viewModel)
        }
    }
}
