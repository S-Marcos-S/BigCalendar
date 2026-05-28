import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "The Big Calendar") {
        // O conteúdo virá do módulo :shared futuramente
    }
}
