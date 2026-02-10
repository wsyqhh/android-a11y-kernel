package com.openclaw.a11ykernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Selector(
    val by: String,
    val value: String
)

@Serializable
data class ExpectedAfter(
    @SerialName("text_exists") val textExists: String? = null
)

@Serializable
data class ActRequest(
    val action: String,
    val selector: Selector? = null,
    val packageName: String? = null,
    val keycode: Int? = null,
    val text: String? = null,
    val direction: String? = null,
    val coordinates: List<Int>? = null,
    val from: List<Int>? = null,
    val to: List<Int>? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("timeout_ms") val timeoutMs: Long? = null,
    @SerialName("fallback_coordinates") val fallbackCoordinates: List<Int>? = null,
    @SerialName("expected_after") val expectedAfter: ExpectedAfter? = null
)

@Serializable
data class UiElement(
    val text: String? = null,
    @SerialName("resource_id") val resourceId: String? = null,
    @SerialName("content_desc") val contentDesc: String? = null,
    @SerialName("class_name") val className: String? = null,
    val clickable: Boolean,
    val enabled: Boolean,
    val bounds: String,
    val center: List<Int>
)

@Serializable
data class ScreenResponse(
    @SerialName("package") val packageName: String? = null,
    val activity: String? = null,
    val ts: Long,
    val elements: List<UiElement>
)

@Serializable
data class ActionResult(
    val ok: Boolean,
    val error: String? = null,
    val executor: String? = null,
    @SerialName("elapsed_ms") val elapsedMs: Long,
    @SerialName("matched_element") val matchedElement: UiElement? = null
)

@Serializable
data class HealthResponse(
    val ok: Boolean,
    val service: String,
    @SerialName("api_port") val apiPort: Int,
    @SerialName("service_enabled") val serviceEnabled: Boolean,
    val ts: Long
)

@Serializable
data class CapabilitiesResponse(
    val ok: Boolean,
    @SerialName("root_available") val rootAvailable: Boolean,
    @SerialName("on_device_mode") val onDeviceMode: Boolean,
    val actions: List<String>,
    val ts: Long
)
