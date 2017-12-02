

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.content.defaultResource
import io.ktor.content.resources
import io.ktor.content.static
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.routing.Routing
import io.ktor.websocket.*
import io.netty.util.internal.ConcurrentSet
import kotlinx.coroutines.experimental.channels.consumeEach
import java.time.Duration

var arr = ConcurrentSet<WebSocketSession>()

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(WebSockets) {
        pingPeriod = Duration.ofMinutes(1)
    }
    install(Routing) {
        webSocket("/ws{name}") {
            add(this)
            broadcast("Hi")
            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        broadcast(frame.readText())
                    }
                }
            } finally {
                remove(this)
                broadcast("left")
            }
        }
        static {
            defaultResource("index.html", "web")
            resources("web")
        }
    }
}

suspend fun add(socket : WebSocketSession) {
    arr.add(socket)
}

suspend fun broadcast(msg : String) {
    arr.forEach {
        it.send(Frame.Text(msg))
    }
}

suspend fun remove(socket : WebSocketSession) {
    arr.remove(socket)
}