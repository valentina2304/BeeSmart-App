package com.example.beesmart.integration

import okhttp3.mockwebserver.MockResponse

/**
 * Convenience builders for MockWebServer JSON responses keyed off the real
 * server payloads (ApiaryResponse, HiveResponse, TaskResponse, HiveTreatment,
 * HiveExtraction, InspectionResponse).
 */
object MockResponses {

    fun apiary(id: String, name: String = "Apiary $id"): MockResponse =
        json(
            200,
            """
            {
              "id": "$id",
              "userId": "user-1",
              "name": "$name",
              "description": null,
              "location": null,
              "hiveCount": 0,
              "createdAt": "2025-01-01T00:00:00Z",
              "updatedAt": "2025-01-01T00:00:00Z"
            }
            """.trimIndent()
        )

    fun hive(id: String, apiaryId: String, name: String = "Hive $id"): MockResponse =
        json(
            200,
            """
            {
              "id": "$id",
              "apiaryId": "$apiaryId",
              "apiaryName": "Apiary $apiaryId",
              "name": "$name",
              "type": "Langstroth",
              "status": "Active",
              "notes": null,
              "createdAt": "2025-01-01T00:00:00Z",
              "updatedAt": "2025-01-01T00:00:00Z"
            }
            """.trimIndent()
        )

    fun task(id: String, hiveId: String? = null, apiaryId: String? = null, status: String = "Pending"): MockResponse =
        json(
            200,
            """
            {
              "id": "$id",
              "userId": "user-1",
              "apiaryId": ${apiaryId?.let { "\"$it\"" } ?: "null"},
              "apiaryName": null,
              "hiveId": ${hiveId?.let { "\"$it\"" } ?: "null"},
              "hiveName": null,
              "title": "Task $id",
              "description": null,
              "priority": "Normal",
              "status": "$status",
              "dueDate": null,
              "completedAt": null,
              "createdAt": "2025-01-01T00:00:00Z",
              "updatedAt": "2025-01-01T00:00:00Z"
            }
            """.trimIndent()
        )

    fun treatment(id: String, hiveId: String, apiaryId: String = "apiary-server"): MockResponse =
        json(
            200,
            """
            {
              "id": "$id",
              "hiveId": "$hiveId",
              "apiaryId": "$apiaryId",
              "treatmentDate": "2025-04-01",
              "type": "Varroa",
              "productName": "Apivar",
              "substance": "Amitraz",
              "dosage": null,
              "notes": null,
              "nextTreatmentDate": null,
              "createdAt": "2025-04-01T00:00:00Z",
              "updatedAt": "2025-04-01T00:00:00Z"
            }
            """.trimIndent()
        )

    fun extraction(id: String, hiveId: String, apiaryId: String = "apiary-server"): MockResponse =
        json(
            200,
            """
            {
              "id": "$id",
              "hiveId": "$hiveId",
              "apiaryId": "$apiaryId",
              "extractionDate": "2025-04-01",
              "type": "Honey",
              "quantity": 12.5,
              "unit": "kg",
              "notes": null,
              "createdAt": "2025-04-01T00:00:00Z",
              "updatedAt": "2025-04-01T00:00:00Z"
            }
            """.trimIndent()
        )

    fun inspection(
        id: String,
        hiveId: String,
        apiaryId: String = "apiary-server",
        hiveName: String = "Hive $hiveId",
        apiaryName: String = "Apiary $apiaryId"
    ): MockResponse =
        json(
            200,
            """
            {
              "id": "$id",
              "hiveId": "$hiveId",
              "hiveName": "$hiveName",
              "apiaryId": "$apiaryId",
              "apiaryName": "$apiaryName",
              "inspectionDate": "2025-05-20T08:00:00Z",
              "temperature": 21.5,
              "framesCount": 10,
              "broodFrames": 5,
              "honeyFrames": 3,
              "pollenFrames": 1,
              "queenSeen": true,
              "eggsSeen": true,
              "larvaeSeen": true,
              "photosCount": 0,
              "createdAt": "2025-05-20T08:00:00Z",
              "updatedAt": "2025-05-20T08:00:00Z",
              "queenCellsSeen": true,
              "queenCellsWithEggs": true,
              "beardingAtEntrance": true,
              "spaceNeeded": true,
              "broodPattern": "compact",
              "honeyCappingPercent": 75,
              "feedingGiven": true,
              "waterAvailable": true,
              "moistureOrMold": false,
              "deadBeesAtEntrance": true,
              "unusualBehavior": true,
              "temperament": "agresiv",
              "oldCombsToReplace": 2
            }
            """.trimIndent()
        )

    fun emptyOk(): MockResponse = MockResponse().setResponseCode(200)
    fun serverError(): MockResponse = MockResponse().setResponseCode(500)
    fun notFound(): MockResponse = MockResponse().setResponseCode(404)

    /** Forces the OkHttp client to throw IOException (socket disconnect). */
    fun networkFailure(): MockResponse = MockResponse()
        .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)

    private fun json(code: Int, body: String): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
