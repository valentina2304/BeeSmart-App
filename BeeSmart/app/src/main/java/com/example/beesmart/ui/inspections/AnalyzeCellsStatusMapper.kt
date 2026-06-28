package com.example.beesmart.ui.inspections

internal fun analyzeCellsErrorMessage(status: String, message: String?): String {
    val serviceMessage = message?.trim().orEmpty()
    if (serviceMessage.isNotEmpty()) {
        return serviceMessage
    }

    return when (status.trim().lowercase()) {
        "low_quality" ->
            "Fotografia nu este suficient de clara pentru analiza. Refotografiaza rama cu lumina buna si focus pe fagure."
        "not_comb_image" ->
            "Nu am detectat o rama analizabila. Incarca o fotografie in care fagurele ocupa cea mai mare parte a imaginii."
        "uncertain_analysis" ->
            "Analiza nu este suficient de sigura. Incearca o fotografie mai clara, facuta frontal pe rama."
        "error" ->
            "Analiza a esuat. Incearca alta fotografie."
        else ->
            "Analiza a esuat. Incearca din nou cu o fotografie mai clara."
    }
}
