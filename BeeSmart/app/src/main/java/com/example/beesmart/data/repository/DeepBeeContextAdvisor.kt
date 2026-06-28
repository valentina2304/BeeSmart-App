package com.example.beesmart.data.repository

import com.example.beesmart.network.models.ExtractionType
import com.example.beesmart.network.models.HiveExtraction
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveTreatment
import com.example.beesmart.network.models.InspectionResponse
import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.network.models.TaskResponse
import com.example.beesmart.network.models.TaskStatus
import com.example.beesmart.network.models.TreatmentType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class AdvicePriority {
    URGENT,
    IMPORTANT,
    WATCH,
    OPPORTUNITY
}

enum class AdviceCategory {
    QUEEN,
    BROOD,
    SWARM,
    NUTRITION,
    TREATMENT,
    PRODUCTION,
    MONITORING,
    DATA_QUALITY
}

data class BeekeeperAdvice(
    val hiveId: String,
    val hiveName: String,
    val apiaryName: String,
    val priority: AdvicePriority,
    val category: AdviceCategory,
    val title: String,
    val explanation: String,
    val action: String,
    val evidence: List<String>,
    val veterinaryReviewRecommended: Boolean = false
)

data class ApiaryAdviceDigest(
    val urgentCount: Int = 0,
    val importantCount: Int = 0,
    val watchCount: Int = 0,
    val opportunityCount: Int = 0,
    val advice: List<BeekeeperAdvice> = emptyList()
)

data class DeepBeeHiveContext(
    val hive: HiveResponse,
    val inspections: List<InspectionResponse>,
    val tasks: List<TaskResponse>,
    val treatments: List<HiveTreatment>,
    val extractions: List<HiveExtraction>,
    val aiSnapshots: List<AiHiveSnapshot>
)

/**
 * Cross-checks DeepBee frame analysis with the beekeeper's journal. The rules
 * intentionally recommend verification steps, not automatic diagnoses or
 * treatment prescriptions.
 */
object DeepBeeContextAdvisor {
    private const val MIN_MEANINGFUL_CELL_COUNT = 30
    private const val LOW_STORES_RATIO = 0.05
    private const val HIGH_STORES_RATIO = 0.25
    private const val HIGH_EMPTY_RATIO = 0.45
    private const val LOW_LARVAE_TO_CAPPED_RATIO = 0.20
    private const val LOW_EMPTY_RATIO_FOR_SPACE_PRESSURE = 0.15
    private const val MEANINGFUL_BROOD_TREND_DELTA = 0.18
    private const val MEANINGFUL_STORES_TREND_DELTA = 0.12
    private const val MEANINGFUL_SPATIAL_TREND_DELTA = 0.18
    private const val LOW_POLLEN_NEAR_BROOD_RATIO = 0.35
    private const val HIGH_BEE_FRAMES_FOR_SWARM = 8
    private const val HIGH_BROOD_FRAMES_FOR_SWARM = 5
    private const val MIN_CAPPING_PERCENT_FOR_HARVEST = 33

    fun digest(
        hives: List<HiveResponse>,
        tasks: List<TaskResponse>,
        inspections: List<InspectionResponse>,
        treatments: List<HiveTreatment>,
        extractions: List<HiveExtraction>,
        aiSnapshots: List<AiHiveSnapshot>,
        today: LocalDate = LocalDate.now()
    ): ApiaryAdviceDigest {
        val advice = hives
            .filter { it.status != HiveStatus.Inactive }
            .flatMap { hive ->
                advise(
                    context = DeepBeeHiveContext(
                        hive = hive,
                        inspections = inspections.filter { it.hiveId == hive.id },
                        tasks = tasks.filter { it.hiveId == hive.id },
                        treatments = treatments.filter { it.hiveId == hive.id },
                        extractions = extractions.filter { it.hiveId == hive.id },
                        aiSnapshots = aiSnapshots.filter { it.hiveId == hive.id }
                    ),
                    today = today
                )
            }
            .sortedWith(
                compareBy<BeekeeperAdvice> { it.priority.sortOrder }
                    .thenBy { it.hiveName }
                    .thenBy { it.category.ordinal }
            )

        return ApiaryAdviceDigest(
            urgentCount = advice.count { it.priority == AdvicePriority.URGENT },
            importantCount = advice.count { it.priority == AdvicePriority.IMPORTANT },
            watchCount = advice.count { it.priority == AdvicePriority.WATCH },
            opportunityCount = advice.count { it.priority == AdvicePriority.OPPORTUNITY },
            advice = advice
        )
    }

    fun advise(
        context: DeepBeeHiveContext,
        today: LocalDate = LocalDate.now()
    ): List<BeekeeperAdvice> {
        val hive = context.hive
        val advice = mutableListOf<BeekeeperAdvice>()
        val latestInspection = context.inspections.latestByDate { it.inspectionDate }
        val latestAi = context.aiSnapshots.latestByDate { it.inspectionDate }
        val openTasks = context.tasks.filter {
            it.status == TaskStatus.Pending || it.status == TaskStatus.InProgress
        }
        val overdueTreatments = context.treatments.filter {
            it.nextTreatmentDate.toLocalDateOrNull()?.isBefore(today) == true
        }

        val noBroodDetected = latestAi != null &&
            latestAi.totalCells >= MIN_MEANINGFUL_CELL_COUNT &&
            latestAi.cappedBroodCells + latestAi.larvaeCells + latestAi.eggsCells == 0
        val queenConfirmed = hive.reginaPrezenta ||
            latestInspection?.queenSeen == true ||
            latestInspection?.eggsSeen == true ||
            latestInspection?.larvaeSeen == true ||
            (latestAi?.eggsCells ?: 0) > 0 ||
            (latestAi?.larvaeCells ?: 0) > 0
        val queenNeedsVerification = hive.status == HiveStatus.Queenless || !queenConfirmed
        val aiEmptyRatio = latestAi?.ratio(latestAi.emptyCells).finiteOrNull()
        val aiStoresRatio = latestAi?.storesRatio.finiteOrNull()

        if (noBroodDetected && queenNeedsVerification) {
            advice += hive.advice(
                priority = if (hive.status == HiveStatus.Queenless) AdvicePriority.URGENT else AdvicePriority.IMPORTANT,
                category = AdviceCategory.QUEEN,
                title = "Verifica regina si continuitatea pontei",
                explanation = "DeepBee nu a identificat puiet pe rama analizata, iar jurnalul nu confirma regina. Rama poate sa nu fie din cuib, de aceea concluzia trebuie validata fizic.",
                action = "Verifica zona de cuib, ouale, larvele tinere si comportamentul coloniei. Daca nu exista semne clare de orfanizare, repeta controlul in aproximativ 14 zile.",
                evidence = listOf(
                    "DeepBee: 0 celule de puiet din ${latestAi!!.totalCells} analizate",
                    "Prezenta reginei nu este confirmata in jurnal"
                )
            )
        } else if (queenNeedsVerification) {
            advice += hive.advice(
                priority = if (hive.status == HiveStatus.Queenless) AdvicePriority.URGENT else AdvicePriority.IMPORTANT,
                category = AdviceCategory.QUEEN,
                title = "Confirma prezenta reginei",
                explanation = "Jurnalul nu confirma direct sau indirect regina. Analiza unei singure rame nu poate stabili singura starea coloniei.",
                action = "Verifica regina, ouale si larvele tinere la urmatoarea inspectie si actualizeaza jurnalul.",
                evidence = listOf("Starea reginei necesita confirmare")
            )
        }

        val beeFramesSignal = maxOf(hive.rameAlbine, latestInspection?.framesCount ?: 0)
        val broodFramesSignal = maxOf(hive.ramePuiet, latestInspection?.broodFrames ?: 0)
        val occupiedFrames = listOfNotNull(
            latestInspection?.broodFrames,
            latestInspection?.honeyFrames,
            latestInspection?.pollenFrames
        ).sum()
        val inspectionSpacePressure = latestInspection?.spaceNeeded == true ||
            (latestInspection?.framesCount
                ?.takeIf { it > 0 }
                ?.let { occupiedFrames >= (it * 0.85) }
                ?: false)
        val directSwarmSignal = latestInspection?.queenCellsWithEggs == true ||
            latestInspection?.beardingAtEntrance == true
        val aiSpacePressure = aiEmptyRatio?.let { it < LOW_EMPTY_RATIO_FOR_SPACE_PRESSURE } == true ||
            aiStoresRatio?.let { it >= HIGH_STORES_RATIO } == true
        val seasonSupportsSwarming = today.monthValue in 4..7
        val strongColony = beeFramesSignal >= HIGH_BEE_FRAMES_FOR_SWARM ||
            broodFramesSignal >= HIGH_BROOD_FRAMES_FOR_SWARM
        val broodGrowthSignal = broodFramesSignal >= HIGH_BROOD_FRAMES_FOR_SWARM ||
            latestAi?.let { it.cappedBroodCells + it.larvaeCells + it.eggsCells >= 50 } == true
        val spacePressure = inspectionSpacePressure || aiSpacePressure || hive.rameMiere >= 5

        if (
            hive.status == HiveStatus.Active &&
            queenConfirmed &&
            seasonSupportsSwarming &&
            ((strongColony && broodGrowthSignal && spacePressure) || directSwarmSignal)
        ) {
            advice += hive.advice(
                priority = if (directSwarmSignal || inspectionSpacePressure || hive.varstaRegina > 2) {
                    AdvicePriority.IMPORTANT
                } else {
                    AdvicePriority.WATCH
                },
                category = AdviceCategory.SWARM,
                title = "Verifica riscul de roire",
                explanation = "Sezonul, semnele de roire notate in inspectie si presiunea de spatiu pot preceda roirea. Regula ramane un semnal de verificare, nu o confirmare automata.",
                action = "La urmatoarea inspectie verifica botcile, aglomerarea la urdinis si spatiul disponibil; extinde spatiul doar dupa confirmarea fizica a situatiei.",
                evidence = buildList {
                    add("Sezon activ de roire: ${today.month}")
                    add("$beeFramesSignal rame ocupate de albine / reper minim $HIGH_BEE_FRAMES_FOR_SWARM")
                    add("$broodFramesSignal rame cu puiet / reper minim $HIGH_BROOD_FRAMES_FOR_SWARM")
                    if (latestInspection?.queenCellsSeen == true) add("Inspectie: botci observate")
                    if (latestInspection?.queenCellsWithEggs == true) add("Inspectie: botci cu oua")
                    if (latestInspection?.beardingAtEntrance == true) add("Inspectie: barba sau aglomerare la urdinis")
                    if (latestInspection?.spaceNeeded == true) add("Inspectie: spatiu insuficient")
                    if (inspectionSpacePressure && latestInspection?.spaceNeeded != true) {
                        add("Inspectie: ramele ocupate se apropie de capacitatea notata")
                    }
                    aiEmptyRatio?.let { add("DeepBee: celule goale ${it.asPercent()} pe rama analizata") }
                    if (hive.varstaRegina > 2) add("Regina peste 2 ani: ${hive.varstaRegina} ani")
                }
            )
        }

        if (latestAi != null) {
            val emptyRatio = latestAi.ratio(latestAi.emptyCells)
            val broodPatternConcern =
                latestAi.broodGapRatio?.let { it > 0.55 } == true ||
                    latestAi.broodCompactness?.let { it < 0.45 } == true
            when {
                latestAi.cappedBroodCells >= 20 &&
                    latestAi.larvaeToCappedRatio.finiteOrNull()?.let { it < LOW_LARVAE_TO_CAPPED_RATIO } == true -> {
                    advice += hive.advice(
                        priority = AdvicePriority.IMPORTANT,
                        category = AdviceCategory.BROOD,
                        title = "Urmareste ritmul pontei",
                        explanation = "Proportia larvelor fata de puietul capacit este redusa. Poate fi o variatie temporara, dar merita comparata cu evolutia coloniei.",
                        action = "Repeta inspectia in 5-7 zile si compara puietul deschis, ouale si aspectul ramei.",
                        evidence = listOf(
                            "DeepBee: raport larve/puiet capacit ${latestAi.larvaeToCappedRatio.asRatio()}",
                            "Puiet capacit detectat: ${latestAi.cappedBroodCells} celule"
                        )
                    )
                }
                broodPatternConcern -> {
                    advice += hive.advice(
                        priority = AdvicePriority.IMPORTANT,
                        category = AdviceCategory.BROOD,
                        title = "Verifica distributia puietului",
                        explanation = "Coordonatele DeepBee sugereaza goluri sau puiet dispersat pe rama analizata. Acesta este un semnal spatial, nu un diagnostic.",
                        action = "Inspecteaza vizual zona cu puiet, compara cu ramele vecine si repeta analiza la urmatoarea inspectie daca modelul ramane consecvent.",
                        evidence = buildList {
                            latestAi.broodGapRatio?.let { add("DeepBee: goluri/non-puiet in zona cuibului ${it.asPercent()}") }
                            latestAi.broodCompactness?.let { add("DeepBee: compactitate puiet ${it.asPercent()}") }
                        }
                    )
                }
                emptyRatio != null && emptyRatio > HIGH_EMPTY_RATIO &&
                    latestAi.cappedBroodCells + latestAi.larvaeCells + latestAi.eggsCells > 0 -> {
                    val hasSpatialPattern = latestAi.broodGapRatio != null || latestAi.broodCompactness != null
                    advice += hive.advice(
                        priority = AdvicePriority.IMPORTANT,
                        category = AdviceCategory.BROOD,
                        title = "Verifica fizic uniformitatea puietului",
                        explanation = if (hasSpatialPattern) {
                            "DeepBee observa multe celule goale, iar coordonatele pot orienta verificarea zonei cu goluri."
                        } else {
                            "DeepBee observa multe celule goale pe rama analizata. Fara coordonatele celulelor, modelul nu poate confirma un pattern dispersat."
                        },
                        action = "Inspecteaza vizual rama si compara cu urmatoarea analiza. Daca aspectul persista sau apar simptome, solicita evaluare de specialitate.",
                        evidence = buildList {
                            add("DeepBee: ${emptyRatio.asPercent()} celule goale")
                            latestAi.broodGapRatio?.let { add("DeepBee: goluri/non-puiet in zona cuibului ${it.asPercent()}") }
                        }
                    )
                }
            }

            latestAi.storesRatio.finiteOrNull()?.let { storesRatio ->
                if (latestAi.totalCells >= MIN_MEANINGFUL_CELL_COUNT && storesRatio < LOW_STORES_RATIO) {
                    advice += hive.advice(
                        priority = AdvicePriority.IMPORTANT,
                        category = AdviceCategory.NUTRITION,
                        title = "Evalueaza rezervele de hrana",
                        explanation = "Rama analizata are putine celule cu miere sau polen. Este un semnal local, nu o masurare completa a rezervelor coloniei.",
                        action = "Verifica ramele alaturate si conditiile meteo inainte de a decide daca este necesara hranirea.",
                        evidence = listOf("DeepBee: rezerve pe rama ${storesRatio.asPercent()}")
                    )
                } else if (
                    latestAi.totalCells >= MIN_MEANINGFUL_CELL_COUNT &&
                    storesRatio >= LOW_STORES_RATIO &&
                    latestAi.storesEdgeRatio?.let { it < 0.25 } == true
                ) {
                    advice += hive.advice(
                        priority = AdvicePriority.WATCH,
                        category = AdviceCategory.NUTRITION,
                        title = "Verifica pozitia rezervelor",
                        explanation = "Coordonatele DeepBee sugereaza ca mierea sau polenul sunt mai centrale decat pe marginea ramei.",
                        action = "Compara cu ramele vecine si verifica daca zona de cuib are suficient spatiu pentru ponta.",
                        evidence = listOf(
                            "DeepBee: rezerve pe rama ${storesRatio.asPercent()}",
                            "DeepBee: rezerve pe margini ${latestAi.storesEdgeRatio.asPercent()}"
                        )
                    )
                }
            }
        }

        advice += longitudinalAiAdvice(hive, context.aiSnapshots)

        if (overdueTreatments.isNotEmpty()) {
            val nearest = overdueTreatments.minByOrNull { it.nextTreatmentDate.toLocalDateOrNull() ?: LocalDate.MAX }!!
            advice += hive.advice(
                priority = AdvicePriority.IMPORTANT,
                category = AdviceCategory.TREATMENT,
                title = "Revizuieste tratamentul planificat",
                explanation = "Jurnalul contine un termen de reevaluare sau aplicare care a trecut.",
                action = "Verifica schema produsului, perioada de asteptare si regulile locale inainte de orice aplicare. Actualizeaza apoi jurnalul.",
                evidence = listOf(
                    "Termen in jurnal: ${nearest.nextTreatmentDate?.take(10)}",
                    "Tratament inregistrat: ${nearest.productName}"
                )
            )
        } else if (context.treatments.none { it.type == TreatmentType.Varroa }) {
            advice += hive.advice(
                priority = AdvicePriority.WATCH,
                category = AdviceCategory.TREATMENT,
                title = "Revizuieste evidenta Varroa",
                explanation = "Nu exista in jurnal un tratament Varroa inregistrat. Aceasta nu inseamna automat ca este necesara aplicarea unui produs.",
                action = "Verifica rezultatul monitorizarii periodice si protocolul stupinei; inregistreaza interventia doar daca a fost stabilita in mod justificat.",
                evidence = listOf("Jurnal tratamente: fara interventie Varroa inregistrata")
            )
        }

        val overdueTasks = openTasks.filter { it.dueDate.toLocalDateOrNull()?.isBefore(today) == true }
        if (overdueTasks.isNotEmpty() || openTasks.any { it.priority == TaskPriority.Critical }) {
            advice += hive.advice(
                priority = if (openTasks.any { it.priority == TaskPriority.Critical }) AdvicePriority.URGENT else AdvicePriority.IMPORTANT,
                category = AdviceCategory.MONITORING,
                title = "Rezolva interventiile ramase in urma",
                explanation = "Planul de lucru contine activitati care pot intarzia deciziile pentru acest stup.",
                action = "Revizuieste task-urile deschise si inchide mai intai interventiile critice sau intarziate.",
                evidence = listOf(
                    "${openTasks.size} task-uri deschise",
                    "${overdueTasks.size} task-uri intarziate"
                )
            )
        }

        seasonalAdvice(hive, today, openTasks)?.let { advice += it }

        val latestInspectionDate = latestInspection?.inspectionDate.toLocalDateOrNull()
            ?: hive.ultimaInspectie.toLocalDateOrNull()
        when {
            latestInspectionDate == null -> advice += hive.advice(
                priority = AdvicePriority.WATCH,
                category = AdviceCategory.MONITORING,
                title = "Inregistreaza inspectia de baza",
                explanation = "Consilierul are nevoie de observatii din teren pentru recomandari mai precise.",
                action = "Adauga o inspectie cu regina, puiet, rame cu hrana si fotografii reprezentative.",
                evidence = listOf("Jurnal: nicio inspectie disponibila")
            )
            latestInspectionDate.isBefore(today.minusDays(21)) -> advice += hive.advice(
                priority = AdvicePriority.WATCH,
                category = AdviceCategory.MONITORING,
                title = "Actualizeaza inspectia stupului",
                explanation = "Datele din teren sunt vechi, iar situatia coloniei se poate modifica rapid.",
                action = "Programeaza o inspectie si actualizeaza indicatorii stupului.",
                evidence = listOf("Ultima inspectie: ${latestInspectionDate.daysAgo(today)} zile in urma")
            )
        }

        if (latestAi == null) {
            advice += hive.advice(
                priority = AdvicePriority.WATCH,
                category = AdviceCategory.DATA_QUALITY,
                title = "Adauga o analiza DeepBee reprezentativa",
                explanation = "Consilierul nu are inca o analiza vizuala a ramei pentru acest stup.",
                action = "Fotografiaza o rama relevanta din cuib la urmatoarea inspectie pentru a crea un reper comparabil.",
                evidence = listOf("DeepBee: fara analiza salvata")
            )
        } else if (latestAi.inspectionDate.toLocalDateOrNull()?.isBefore(today.minusDays(21)) == true) {
            advice += hive.advice(
                priority = AdvicePriority.WATCH,
                category = AdviceCategory.DATA_QUALITY,
                title = "Reimprospateaza analiza DeepBee",
                explanation = "Ultima analiza vizuala nu mai descrie suficient de bine situatia curenta.",
                action = "Analizeaza o noua rama reprezentativa si compara rezultatele cu reperul anterior.",
                evidence = listOf("DeepBee: analiza mai veche de 21 zile")
            )
        }

        val latestHoneyFrames = latestInspection?.honeyFrames ?: hive.rameMiere
        val recentHoneyExtractions = context.extractions.filter {
            it.type == ExtractionType.Honey &&
                it.extractionDate.toLocalDateOrNull()?.isAfter(today.minusDays(90)) == true
        }
        if (
            hive.status == HiveStatus.Active &&
            latestHoneyFrames >= 5 &&
            (latestAi?.level == BroodAnalyzer.Level.HEALTHY || aiStoresRatio?.let { it >= HIGH_STORES_RATIO } == true)
        ) {
            advice += hive.advice(
                priority = AdvicePriority.OPPORTUNITY,
                category = AdviceCategory.PRODUCTION,
                title = "Evalueaza fereastra pentru recolta",
                explanation = "Colonia are rame cu miere si un semnal DeepBee favorabil (rezervele si capacirea sunt estimate din fotografie). Suportul recomanda recoltarea doar dupa verificarea maturarii mierii.",
                action = "Verifica daca cel putin o treime din fagure este capacita si daca mierea necapacita nu curge la apasare; separa doar ramele potrivite pentru extractie.",
                evidence = buildList {
                    add("$latestHoneyFrames rame cu miere in jurnal")
                    aiStoresRatio?.let { add("DeepBee: rezerve pe rama ${it.asPercent()}") }
                    if (recentHoneyExtractions.isNotEmpty()) {
                        add("${recentHoneyExtractions.size} extractii de miere in ultimele 90 zile")
                    } else {
                        add("Nicio extractie de miere in ultimele 90 zile")
                    }
                }
            )
        }

        return advice.distinctBy { it.category to it.title }
    }

    private fun longitudinalAiAdvice(
        hive: HiveResponse,
        snapshots: List<AiHiveSnapshot>
    ): List<BeekeeperAdvice> {
        val history = snapshots
            .mapNotNull { snapshot ->
                snapshot.inspectionDate.toLocalDateOrNull()?.let { date -> date to snapshot }
            }
            .filter { (_, snapshot) -> snapshot.totalCells >= MIN_MEANINGFUL_CELL_COUNT }
            .sortedBy { (date, _) -> date }
        if (history.size < 2) return emptyList()

        val previous = history.dropLast(1).last().second
        val latest = history.last().second
        val advice = mutableListOf<BeekeeperAdvice>()

        val broodDensityDelta = latest.broodDensity.deltaFrom(previous.broodDensity)
        val compactnessDelta = latest.broodCompactness.deltaFrom(previous.broodCompactness)
        val gapDelta = latest.broodGapRatio.deltaFrom(previous.broodGapRatio)
        val broodTrendConcern =
            broodDensityDelta?.let { it <= -MEANINGFUL_BROOD_TREND_DELTA } == true ||
                compactnessDelta?.let { it <= -MEANINGFUL_SPATIAL_TREND_DELTA } == true ||
                gapDelta?.let { it >= MEANINGFUL_SPATIAL_TREND_DELTA } == true

        if (latest.broodCellsTotal > 0 && broodTrendConcern) {
            advice += hive.advice(
                priority = if (
                    latest.broodGapRatio?.let { it >= 0.45 } == true ||
                    latest.broodCompactness?.let { it <= 0.55 } == true ||
                    broodDensityDelta?.let { it <= -0.25 } == true
                ) {
                    AdvicePriority.IMPORTANT
                } else {
                    AdvicePriority.WATCH
                },
                category = AdviceCategory.BROOD,
                title = "Urmareste evolutia compactitatii puietului",
                explanation = "Istoricul DeepBee arata o schimbare in densitatea sau uniformitatea puietului. Suportul apicol trateaza puietul compact si extins ca reper pentru o ponta buna, dar fotografia ramane un semnal de verificare.",
                action = "La urmatoarea inspectie fotografiaza o rama reprezentativa din cuib, compara cu ramele vecine si noteaza ouale, larvele si prezenta reginei.",
                evidence = buildList {
                    add("Analize comparate: ${previous.inspectionDate.take(10)} -> ${latest.inspectionDate.take(10)}")
                    broodDensityDelta?.let {
                        add("Densitate puiet: ${previous.broodDensity.percentOrDash()} -> ${latest.broodDensity.percentOrDash()} (${it.asSignedPoints()})")
                    }
                    compactnessDelta?.let {
                        add("Compactitate: ${previous.broodCompactness.percentOrDash()} -> ${latest.broodCompactness.percentOrDash()} (${it.asSignedPoints()})")
                    }
                    gapDelta?.let {
                        add("Goluri in zona cuibului: ${previous.broodGapRatio.percentOrDash()} -> ${latest.broodGapRatio.percentOrDash()} (${it.asSignedPoints()})")
                    }
                }
            )
        }

        val storesDelta = latest.storesRatio.deltaFrom(previous.storesRatio)
        val storesEdgeDelta = latest.storesEdgeRatio.deltaFrom(previous.storesEdgeRatio)
        val pollenNearDelta = latest.pollenNearBroodRatio.deltaFrom(previous.pollenNearBroodRatio)
        val nutritionTrendConcern =
            storesDelta?.let {
                it <= -MEANINGFUL_STORES_TREND_DELTA &&
                    latest.storesRatio?.let { ratio -> ratio >= LOW_STORES_RATIO } == true
            } == true ||
                storesEdgeDelta?.let { it <= -MEANINGFUL_SPATIAL_TREND_DELTA } == true ||
                pollenNearDelta?.let { it <= -MEANINGFUL_SPATIAL_TREND_DELTA } == true ||
                (latest.pollenCells >= 5 &&
                    latest.pollenNearBroodRatio?.let { it < LOW_POLLEN_NEAR_BROOD_RATIO } == true)

        if (nutritionTrendConcern) {
            advice += hive.advice(
                priority = if (
                    latest.storesRatio?.let { it < 0.08 } == true ||
                    latest.pollenNearBroodRatio?.let { it < 0.25 } == true
                ) {
                    AdvicePriority.IMPORTANT
                } else {
                    AdvicePriority.WATCH
                },
                category = AdviceCategory.NUTRITION,
                title = "Compara rezervele si polenul in timp",
                explanation = "Istoricul DeepBee sugereaza o schimbare in rezerve sau in pozitia polenului fata de puiet. Pentru cresterea puietului conteaza atat cantitatea de hrana, cat si apropierea polenului de zona de crestere.",
                action = "Verifica ramele laterale si coroana de miere, apoi compara polenul din jurul puietului cu observatiile din jurnal inainte de orice decizie de hranire.",
                evidence = buildList {
                    add("Analize comparate: ${previous.inspectionDate.take(10)} -> ${latest.inspectionDate.take(10)}")
                    storesDelta?.let {
                        add("Rezerve: ${previous.storesRatio.percentOrDash()} -> ${latest.storesRatio.percentOrDash()} (${it.asSignedPoints()})")
                    }
                    storesEdgeDelta?.let {
                        add("Rezerve pe margini: ${previous.storesEdgeRatio.percentOrDash()} -> ${latest.storesEdgeRatio.percentOrDash()} (${it.asSignedPoints()})")
                    }
                    pollenNearDelta?.let {
                        add("Polen langa puiet: ${previous.pollenNearBroodRatio.percentOrDash()} -> ${latest.pollenNearBroodRatio.percentOrDash()} (${it.asSignedPoints()})")
                    }
                }
            )
        }

        return advice
    }

    private fun HiveResponse.advice(
        priority: AdvicePriority,
        category: AdviceCategory,
        title: String,
        explanation: String,
        action: String,
        evidence: List<String>,
        veterinaryReviewRecommended: Boolean = false
    ) = BeekeeperAdvice(
        hiveId = id,
        hiveName = name,
        apiaryName = apiaryName,
        priority = priority,
        category = category,
        title = title,
        explanation = explanation,
        action = action,
        evidence = evidence,
        veterinaryReviewRecommended = veterinaryReviewRecommended
    )

    private fun seasonalAdvice(
        hive: HiveResponse,
        today: LocalDate,
        openTasks: List<TaskResponse>
    ): BeekeeperAdvice? {
        val season = when (today.monthValue) {
            in 3..5 -> SeasonalChecklist(
                name = "primavara",
                title = "Calendar sezonier: control de primavara",
                action = "Propune un task pentru control sumar/general: regina, rezerve, puiet, apa si reducerea sau largirea cuibului dupa temperatura.",
                evidence = listOf(
                    "Primavara: control sumar la 13-14 C si control general la 15-16 C",
                    "Verificari-cheie: matca, 8-12 kg miere, pastura si adapator"
                )
            )
            in 6..8 -> SeasonalChecklist(
                name = "vara",
                title = "Calendar sezonier: spatiu, roire si cules",
                action = "Propune un task pentru spatiu de cules, verificarea botcilor, aglomerarii la urdinis si a gradului de capacire inainte de extractie.",
                evidence = listOf(
                    "Vara: culesurile principale cer spatiu pentru nectar si ventilatie",
                    "Semnale de roire: botci cu oua, barba la urdinis, spatiu insuficient"
                )
            )
            in 9..11 -> SeasonalChecklist(
                name = "toamna",
                title = "Calendar sezonier: pregatire de iernare",
                action = "Propune un task pentru rezerve, calitatea matcii, restrangerea cuibului, faguri buni si eliminarea surselor de umezeala.",
                evidence = listOf(
                    "Toamna: familiile se pregatesc cu rezerve si matca verificata",
                    "Iernarea cere cuib potrivit, hrana suficienta si ventilatie buna"
                )
            )
            else -> SeasonalChecklist(
                name = "iarna",
                title = "Calendar sezonier: supraveghere de iarna",
                action = "Propune un task de supraveghere fara deranj inutil: zumzet, protectie, ventilatie, umezeala si consumul rezervelor.",
                evidence = listOf(
                    "Iarna: interventiile se limiteaza la supraveghere si protectie",
                    "Semnale utile: zumzet, umezeala, mortalitate si ventilatie"
                )
            )
        }

        val alreadyPlanned = openTasks.any { task ->
            task.title.contains(season.name, ignoreCase = true) ||
                task.title.contains("calendar sezonier", ignoreCase = true)
        }
        if (alreadyPlanned) return null

        return hive.advice(
            priority = AdvicePriority.WATCH,
            category = AdviceCategory.MONITORING,
            title = season.title,
            explanation = "Suportul de curs structureaza lucrarile pe anotimpuri; BeeSmart le transforma in propuneri de task, confirmate de apicultor.",
            action = season.action,
            evidence = season.evidence
        )
    }

    private data class SeasonalChecklist(
        val name: String,
        val title: String,
        val action: String,
        val evidence: List<String>
    )

    private fun <T> List<T>.latestByDate(date: (T) -> String?): T? =
        maxByOrNull { date(it).toLocalDateOrNull() ?: LocalDate.MIN }

    private fun AiHiveSnapshot.ratio(value: Int): Double? =
        if (totalCells <= 0) null else value.toDouble() / totalCells

    private fun String?.toLocalDateOrNull(): LocalDate? {
        if (this == null || length < 10) return null
        return runCatching { LocalDate.parse(take(10)) }.getOrNull()
    }

    private fun Double?.finiteOrNull(): Double? =
        this?.takeIf { it.isFinite() }

    private fun Double?.asRatio(): String =
        finiteOrNull()?.let { "%.2f".format(java.util.Locale.US, it) } ?: "n/a"

    private fun Double.asPercent(): String = "${(this * 100).toInt()}%"

    private fun Double?.percentOrDash(): String =
        finiteOrNull()?.asPercent() ?: "-"

    private fun Double?.deltaFrom(previous: Double?): Double? {
        val latest = finiteOrNull()
        val earlier = previous.finiteOrNull()
        return if (latest != null && earlier != null) latest - earlier else null
    }

    private fun Double.asSignedPoints(): String {
        val points = (this * 100).toInt()
        val sign = if (points > 0) "+" else ""
        return "$sign$points pp"
    }

    private val AiHiveSnapshot.broodCellsTotal: Int
        get() = cappedBroodCells + larvaeCells + eggsCells

    private fun LocalDate.daysAgo(today: LocalDate): Long = ChronoUnit.DAYS.between(this, today)

    private val AdvicePriority.sortOrder: Int
        get() = when (this) {
            AdvicePriority.URGENT -> 0
            AdvicePriority.IMPORTANT -> 1
            AdvicePriority.WATCH -> 2
            AdvicePriority.OPPORTUNITY -> 3
        }
}
