package com.openclaw.a11ykernel.server

import com.openclaw.a11ykernel.BuildConfig
import com.openclaw.a11ykernel.model.ActRequest
import com.openclaw.a11ykernel.model.CapabilitiesResponse
import com.openclaw.a11ykernel.model.HealthResponse
import com.openclaw.a11ykernel.service.MyAccessibilityService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

class LocalApiServer(private val service: MyAccessibilityService) {
    private val port = 7333
    private val token = BuildConfig.LOCAL_API_TOKEN

    private val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
                encodeDefaults = true
            })
        }

        routing {
            get("/health") {
                call.respond(
                    HealthResponse(
                        ok = true,
                        service = "android-a11y-kernel",
                        apiPort = port,
                        serviceEnabled = true,
                        ts = System.currentTimeMillis()
                    )
                )
            }

            get("/capabilities") {
                if (!authorized(call.request.headers["Authorization"])) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
                    return@get
                }
                val rootAvailable = service.isRootAvailable()
                val supportedActions = mutableListOf(
                    "tap",
                    "type",
                    "scroll",
                    "back",
                    "home",
                    "wait",
                    "done"
                )
                if (BuildConfig.ENABLE_ROOT_FALLBACK) {
                    supportedActions.addAll(listOf("launch_app", "keyevent", "swipe"))
                }
                call.respond(
                    CapabilitiesResponse(
                        ok = true,
                        rootAvailable = rootAvailable,
                        onDeviceMode = true,
                        actions = supportedActions,
                        ts = System.currentTimeMillis()
                    )
                )
            }

            get("/screen") {
                if (!authorized(call.request.headers["Authorization"])) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
                    return@get
                }
                call.respond(service.buildScreenResponse())
            }

            post("/act") {
                if (!authorized(call.request.headers["Authorization"])) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
                    return@post
                }
                val req = call.receive<ActRequest>()
                call.respond(service.executeAction(req))
            }
        }
    }

    fun start() {
        server.start(wait = false)
    }

    fun stop() {
        server.stop(gracePeriodMillis = 300, timeoutMillis = 1000)
    }

    private fun authorized(header: String?): Boolean {
        return header == "Bearer $token"
    }
}
