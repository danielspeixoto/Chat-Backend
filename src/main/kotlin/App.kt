

import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.content.defaultResource
import io.ktor.content.resources
import io.ktor.content.static
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.routing.Routing
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.experimental.channels.consumeEach
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

var arr = ConcurrentHashMap<Int, WebSocketSession>()
var idCounter : Int = 1

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(WebSockets) {
        pingPeriod = Duration.ofMinutes(1)
    }
    install(Sessions) {
        cookie<ChatId>("chatId")
    }

    intercept(ApplicationCallPipeline.Infrastructure) {
        if (call.sessions.get<ChatId>() == null) {
            call.sessions.set(ChatId(idCounter++.toString()))
        }
    }

    install(Routing) {
        webSocket("/ws") {
            val chatId = call.sessions.get<ChatId>()

            if (chatId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No Id"))
                return@webSocket
            }

            val id = chatId.id.toInt()

            add(this, id)
            broadcast("$id: Hi")
            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        broadcast("$id: ${frame.readText()}")
                    }
                }
            } finally {
                remove(id)
                broadcast("$id: left")
            }
        }
        static {
            defaultResource("index.html", "web")
            resources("web")
        }
    }
}

suspend fun add(socket : WebSocketSession, id : Int) {
    arr.put(id, socket)
}

suspend fun broadcast(msg : String) {
    for((_, value) in arr) {
        value.send(Frame.Text(msg))
    }
}

suspend fun remove(id : Int) {
    arr.remove(id)
}

data class ChatId(val id : String)