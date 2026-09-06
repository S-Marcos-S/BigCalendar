package com.mss.thebigcalendar.data.model

import com.google.gson.annotations.SerializedName

enum class GeminiAction {
    @SerializedName("CREATE")
    CREATE,
    @SerializedName("UPDATE")
    UPDATE,
    @SerializedName("DELETE")
    DELETE,
    @SerializedName("COMPLETE")
    COMPLETE,
    @SerializedName("QUERY")
    QUERY,
    @SerializedName("NONE")
    NONE
}

data class GeminiCommandResult(
    @SerializedName("action")
    val action: GeminiAction = GeminiAction.NONE,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("date")
    val date: String? = null, // Formato "yyyy-MM-dd"

    @SerializedName("startTime")
    val startTime: String? = null, // Formato "HH:mm"

    @SerializedName("endTime")
    val endTime: String? = null, // Formato "HH:mm"

    @SerializedName("isAllDay")
    val isAllDay: Boolean = true,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("activityType")
    val activityType: String? = "TASK", // "EVENT", "TASK", "NOTE", "BIRTHDAY"

    @SerializedName("priority")
    val priority: String? = null, // "1" (Baixa/Branco), "2" (Média/Azul), "3" (Alta/Amarelo), "4" (Urgente/Vermelho) ou hex

    @SerializedName("visibility")
    val visibility: String? = "LOW", // "LOW", "MEDIUM", "HIGH"

    @SerializedName("recurrenceRule")
    val recurrenceRule: String? = null, // "DAILY", "WEEKLY", "MONTHLY", "YEARLY", "FREQ=HOURLY;INTERVAL=N", "FREQ=DAILY;INTERVAL=N", "FREQ=YEARLY;INTERVAL=N", etc.

    @SerializedName("notificationEnabled")
    val notificationEnabled: Boolean = false,

    @SerializedName("notificationMinutesBefore")
    val notificationMinutesBefore: Int = 0,

    @SerializedName("targetActivityId")
    val targetActivityId: String? = null,

    @SerializedName("targetActivityTitle")
    val targetActivityTitle: String? = null,

    @SerializedName("replyMessage")
    val replyMessage: String = "",

    @SerializedName("queriedActivities")
    val queriedActivities: List<String>? = null
)
