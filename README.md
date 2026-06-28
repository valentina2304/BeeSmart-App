<div align="center">

# 🐝 BeeSmart

### Platforma full-stack pentru management apicol asistat de inteligenta artificiala

*Carnet digital inteligent de stupina: colectare de date in teren, analiza AI a ramelor si suport decizional explicabil pentru apicultor.*

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![Backend](https://img.shields.io/badge/backend-ASP.NET%20Core%208-512BD4)
![Language](https://img.shields.io/badge/Kotlin%20%7C%20C%23%20%7C%20Python-blue)
![AI](https://img.shields.io/badge/AI-DeepBee%20%7C%20TensorFlow-FF6F00)
![Cloud](https://img.shields.io/badge/cloud-Azure%20Container%20Apps-0078D4)

</div>

---

## Rezumat

BeeSmart este o platforma full-stack dedicata managementului apicol. Sistemul reuneste o **aplicatie mobila Android offline-first**, un **API ASP.NET Core 8** structurat pe principii de Clean Architecture si un **microserviciu Python de inteligenta artificiala** care ruleaza modelele DeepBee.

Aplicatia este gandita pentru utilizarea efectiva in teren: apicultorul poate introduce date chiar si in lipsa conexiunii la internet, modificarile fiind salvate local si sincronizate automat la revenirea conectivitatii. Pentru analiza ramelor, platforma clasifica individual celulele din fagure (capacite, oua, miere, larve, nectar, polen) si calculeaza metrici spatiale folosite ulterior pentru statistici longitudinale si recomandari.

Principiul de proiectare urmarit este unul de **suport decizional explicabil**: inteligenta artificiala asista apicultorul prin observatii si corelatii, fara a inlocui controlul fizic al stupului sau avizul medicului veterinar.

## Cuprins

- [Functionalitati](#functionalitati)
- [Stack tehnologic](#stack-tehnologic)
- [Arhitectura](#arhitectura)
- [Structura repository](#structura-repository)
- [Cerinte preliminare](#cerinte-preliminare)
- [Rulare rapida](#rulare-rapida)
- [Configurare Android](#configurare-android)
- [Configurare backend](#configurare-backend)
- [Sincronizare offline-first](#sincronizare-offline-first)
- [Baza de date locala (Room)](#baza-de-date-locala-room)
- [Inteligenta artificiala: DeepBee si analiza spatiala](#inteligenta-artificiala-deepbee-si-analiza-spatiala)
- [Statistici si suport decizional](#statistici-si-suport-decizional)
- [API REST](#api-rest)
- [Cod QR si deep links](#cod-qr-si-deep-links)
- [Meteo](#meteo)
- [Comenzi vocale](#comenzi-vocale)
- [Notificari locale](#notificari-locale)
- [Testare](#testare)
- [Deployment](#deployment)
- [Securitate](#securitate)
- [Limitari si lucrari viitoare](#limitari-si-lucrari-viitoare)
- [Licenta si atribuiri](#licenta-si-atribuiri)

## Functionalitati

BeeSmart functioneaza ca un **carnet digital inteligent de stupina**: colecteaza date in teren (inclusiv offline, prin formulare, voce, cod QR si fotografii), interpreteaza explicabil rama prin DeepBee, anticipeaza lucrari sezoniere si oportunitati de productie si extinde decizia de la nivel de stup la nivel de vatra, prin meteo si calitatea aerului. Inteligenta artificiala sustine apicultorul; nu inlocuieste controlul fizic sau medicul veterinar.

### Autentificare si cont

- Inregistrare cu validare si indicator de putere a parolei (`PasswordStrength`).
- Autentificare pe baza de JWT cu refresh token reimprospatat automat in fundal.
- Confirmare email si resetare parola prin link (deep link).
- Profil utilizator: vizualizare si editare a datelor de cont.

### Stupine (apiaries)

- Creare, editare, stergere si listare a stupinelor.
- Locatie text (utilizata pentru meteo si geocodare), descriere si sumar.
- Acces rapid in teren prin cod QR.

### Stupi (hives)

- Creare, editare, stergere si listare a stupilor per stupina.
- Atribute: tip stup, status, prezenta si varsta matcii, rame cu albine, rame cu puiet, rame cu miere, notite.
- Calcul automat al ultimei inspectii; fisa de detaliu per stup.
- Ecran dedicat de statistici per stup (`HiveStatsScreen`).
- Cod QR per stup pentru deschiderea directa a fisei.

### Inspectii — Inspectie inteligenta V2

Pe langa nucleul clasic (data, temperatura, rame, puiet, miere, polen, prezenta matcii, oua, larve, observatii, fotografii), inspectia colecteaza semnale structurale explicabile, completabile inclusiv vocal:

- **Regina si puiet:** `queenSeen`, `eggsSeen`, `larvaeSeen`, `broodPattern` (uniformitatea puietului), `broodFrames`.
- **Roire:** `queenCellsSeen` (botci), `queenCellsWithEggs` (botci cu oua), `beardingAtEntrance` (barba la urdinis), `spaceNeeded` (spatiu insuficient).
- **Rezerve si hranire:** `honeyFrames`, `pollenFrames`, `honeyCappingPercent` (gradul de capacire), `feedingGiven`, `waterAvailable`.
- **Igiena si sanatate:** `moistureOrMold` (umezeala/mucegai), `deadBeesAtEntrance` (mortalitate la urdinis), `unusualBehavior`, `oldCombsToReplace` (faguri de inlocuit).
- **Comportament:** `temperament` (blandete/agresivitate).

Inspectiile pot fi filtrate per stup sau per stupina, iar campurile sunt salvate offline, sincronizate si afisate sumar in lista. **Limita asumata:** aplicatia inregistreaza observatii; nu stabileste diagnostic sanitar.

### Fotografii si analiza AI DeepBee

- Adaugarea fotografiilor de inspectie din camera sau galerie, cu cache local si upload sincronizat.
- Analiza DeepBee a celulelor de fagure: clasificare per celula (capacite, oua, miere, larve, nectar, polen, altele), cu metadata de calitate (blur, luminozitate, contrast, acoperirea fagurelui).
- Salvarea rezultatelor in istoricul per inspectie si utilizarea coordonatelor celulelor pentru **analiza spatiala** (compactitatea puietului, gap-uri, rezerve la margine, polen langa puiet).
- Statusuri controlate: `success`, `low_quality`, `not_comb_image`, `uncertain_analysis`.

### Tratamente

- Inregistrarea produsului, dozei, datei si a observatiilor.
- Termen urmator (`nextTreatmentDate`) cu **reminder programat** si canal de notificare dedicat.
- Anularea notificarii la stergere si reprogramarea dupa repornirea telefonului.
- Consilierul DeepBee semnaleaza depasirea termenului si recomanda verificarea schemei/prospectului. Aplicatia nu prescrie produs, doza sau tratament.

### Extractii

- Inregistrarea cantitatii, datei, stupului asociat si a observatiilor.
- Utilizate pentru trasabilitate si pentru analiza productiei de miere.

### Activitati (tasks)

- Activitati per stup sau per stupina, cu marcare/demarcare ca finalizate.
- Filtre `pending` si `overdue`.
- Reminder programat cu notificare locala.

### Statistici si suport decizional

- **Index compozit de sanatate** per stup si **radar** STABLE / WATCH / CRITICAL (`ApiaryIntelligenceCalculator`).
- **Productie de miere** cu analytics si forecast, grafice lunare si comparatii intre ani (`DashboardAnalyticsCalculator`).
- **Consilierul DeepBee** (`DeepBeeContextAdvisor`): recomandari prioritizate URGENT / IMPORTANT / WATCH / OPPORTUNITY, insotite de evidence, coreland analiza ramei cu istoricul, activitatile, tratamentele si productia.
- **Calendar sezonier asistat:** propune lucrari de primavara, vara, toamna si iarna sub forma de activitati confirmate de apicultor (control sumar/general, rezerve, regina, apa, spatiu de cules, recolta, pregatirea iernarii, ventilatie).
- **Scor de risc pentru roire:** combina botci cu oua, barba la urdinis, spatiu insuficient, sezon, puterea coloniei si varsta matcii; recomandarea ramane verificarea fizica.
- **Fereastra de recolta:** foloseste `honeyCappingPercent`; sub aproximativ 33% capacire nu sugereaza recolta.
- **Verdict de zbor** pe baza meteo (`BeeFlightAdvisor`) si analiza puiet/rezerve (`BroodAnalyzer`).

### Meteo

- Integrare OpenWeatherMap pentru geocodare, vreme curenta, prognoza pe 5 zile / 3 ore si calitatea aerului, pentru locatia text a stupinei.
- Card meteo afisat pe `HiveListScreen`; cache de geocodare permanent, date meteo cache 30 de minute.

### Notificari locale

- Alarme exacte (`AlarmManager`) pentru activitati si tratamente.
- Istoric persistent cu stare citit/necitit si marcare ca citit.
- Reprogramare automata dupa repornirea telefonului.

### Comenzi vocale

- Completarea vocala a formularelor (ro-RO) prin `VoiceCommandManager` / `SpeechRecognizer`.
- Utila pentru folosirea cu manusi, in teren.

### Cod QR si deep links

- Cod QR per stup care deschide direct fisa in aplicatie.
- Suport pentru schema custom `beesmart://` si HTTPS `app.beesmart.ro`.
- Fluxuri prin deep link: confirmare email, resetare parola, navigare la stup.

### Functionare offline-first

- Toate datele pot fi introduse fara conexiune la internet si sincronizate automat la revenire, in ordine de dependenta (vezi [Sincronizare offline-first](#sincronizare-offline-first)).

## Stack tehnologic

| Componenta | Tehnologii |
|------------|-----------|
| **Client mobil** | Kotlin, Jetpack Compose, Fragments + Navigation, Hilt, Retrofit, OkHttp, Moshi, Room, WorkManager, CameraX, ML Kit, ZXing, SpeechRecognizer |
| **API backend** | ASP.NET Core 8.0, C#, Entity Framework Core, SQL Server / Azure SQL, JWT Bearer, Swagger/OpenAPI |
| **Serviciu AI** | Python, Flask, TensorFlow 1.14 + Keras 2.2, OpenCV, modelele DeepBee (segmentare + clasificare) |
| **Infrastructura** | Docker Compose (local), Azure Container Apps (productie) |

## Arhitectura

Sistemul este alcatuit din trei componente care comunica printr-un API central:

```text
   Android app  <───>  ASP.NET Core API  <───>  SQL Server / Azure SQL
                              │
                              ▼
                   DeepBee AI service (Flask + TensorFlow)
```

Clientul Android comunica **exclusiv** cu API-ul, niciodata direct cu serviciul AI. API-ul mediaza apelurile catre DeepBee, valideaza si persista rezultatele.

### Client Android — MVVM + Repository + Hilt

```text
   Compose / Fragments ──> ViewModel ──> Repository ──> Retrofit / Room
                                              │
                                              ▼
                                       SyncQueueEntity
```

- **Single Activity** (`MainActivity`): verifica starea de autentificare, gestioneaza rutarea deep link-urilor si lanseaza `ComposeAuthActivity` pentru fluxul de autentificare.
- **Navigatie:** abordare hibrida — un nav graph pe baza de Fragments gazduieste ecrane Compose.
- **Injectie de dependente:** module Hilt in `di/`; trei clienti OkHttp distinsi prin qualifiers `@UnauthenticatedClient` (login/register), `@AuthenticatedClient` (endpoint-uri protejate) si `@OpenWeatherClient` (meteo).
- **Networking:** `AuthInterceptor` reimprospateaza JWT-ul sincron (Mutex, pentru a preveni race conditions), cu backoff de 30 s dupa un refresh esuat; foloseste un client de refresh dedicat pentru a evita injectarea circulara.
- **Strat de date:** 11 repository-uri (Auth, Apiary, Hive, Task, Treatment, Extraction, Inspection, Home, UserProfile, Weather, NotificationHistory) peste API-uri Retrofit; toate raspunsurile folosesc un sealed class `Result<T>` (Success / Error / Loading).

### Backend — Clean Architecture

```text
   Api ──> Application ──> Domain ──> Infrastructure
```

- **`Api`:** 7 controllere HTTP; ID-ul utilizatorului este extras din claim-urile JWT.
- **`Application`:** DTO-uri, interfete de servicii/repository, exceptii custom, optiuni JWT.
- **`Domain`:** entitati de business (`User` detine Apiaries, RefreshTokens, tokens de confirmare/reset; `Hive` detine Inspections, Treatments, Extractions).
- **`Infrastructure`:** `AppDbContext` (EF Core, 13 DbSets, relatii si cascade configurate aici), 8 repository-uri cu autorizare pe baza de ownership, servicii, email, JWT si integrarea AI.

`Program.cs` centralizeaza configurarea: JWT Bearer (secret validat la pornire, minimum 32 de caractere), rate limiting fixed-window (global 120 req/min per IP; `login` 5/min), `UseForwardedHeaders` pentru extragerea IP-ului in spatele unui proxy/Azure, Swagger (doar in mediul de dezvoltare) si migrari aplicate la pornire cu retry (`ApplyMigrationsWithRetry`, 10 incercari).

## Structura repository

```text
BeeSmart/
├── BeeSmart/                          # Aplicatia Android
│   ├── app/src/main/java/com/example/beesmart/
│   │   ├── data/local/                # Room, DAO-uri, entitati, conversii
│   │   ├── data/repository/           # Repository-uri offline-first + analytics/advisory
│   │   ├── di/                        # Module Hilt (App, Database, Network, Repository, ...)
│   │   ├── network/                   # Retrofit APIs, interceptori, modele
│   │   ├── notifications/             # Programare alarme + istoric notificari
│   │   ├── sync/                      # SyncManager, SyncScheduler, WorkManager
│   │   ├── ui/                        # Ecrane Compose (apiaries, hives, inspections,
│   │   │                              #   analytics, tasks, treatment, extraction, qrcode, ...)
│   │   └── utils/                     # NetworkConfig, helpers sesiune/fisiere/poze
│   └── gradle/libs.versions.toml      # Versiuni dependinte centralizate
│
├── ApiaryServer/
│   ├── ApiaryServer/
│   │   ├── Api/Controllers/           # 7 controllere HTTP
│   │   ├── Application/               # DTO-uri, interfete, exceptii, optiuni
│   │   ├── Domain/Entities/           # 11 entitati EF Core
│   │   ├── Infrastructure/            # DbContext, repository-uri, servicii (incl. AiAnalysisService)
│   │   ├── AIService/                 # Serviciu Python Flask pentru DeepBee
│   │   ├── Migrations/                # EF Core migrations
│   │   └── docker-compose.yml         # SQL Server + AI service + API
│   └── ApiaryServer.Tests/            # Teste backend (xUnit)
│
└── Documentatie/                      # Audit functionalitati, screenshots, template
```

## Cerinte preliminare

| Componenta | Cerinte |
|------------|---------|
| Android | JDK 17, Android SDK, Android Studio (recomandat); cheie API OpenWeatherMap (optionala) |
| Backend | .NET SDK 8.0; SQL Server local sau via Docker |
| Serviciu AI | Python 3.x + dependintele din `requirements.txt` (sau rulare via Docker) |
| Full stack | Docker si Docker Compose |

## Rulare rapida

### Android

```powershell
cd BeeSmart
.\gradlew assembleDebug          # build debug
.\gradlew assembleRelease        # build release
.\gradlew testDebugUnitTest      # teste unit + integrare (JVM/Robolectric)
.\gradlew connectedAndroidTest   # teste instrumentate (necesita device/emulator)
```

### Backend fara Docker

```powershell
cd ApiaryServer\ApiaryServer
dotnet restore
dotnet build
dotnet run        # ruleaza pe http://localhost:5033
dotnet watch      # live reload
```

### Backend cu Docker (full stack)

Fisierul `docker-compose.yml` porneste cele trei servicii impreuna.

```powershell
cd ApiaryServer\ApiaryServer
docker compose up --build -d
docker compose down
```

Servicii expuse:

| Serviciu | Adresa |
|----------|--------|
| SQL Server 2022 | `localhost:1433` (date in volumul `sqlserver-data`) |
| DeepBee AI service | `localhost:5000` |
| API | `http://localhost:8080` |

## Configurare Android

### Cheia OpenWeatherMap

Cheia se adauga in `BeeSmart/local.properties` (fisier ignorat de Git):

```properties
openweather_api_key=your_key_here
```

In lipsa cheii, `WeatherRepository` returneaza o eroare, iar cardul meteo de pe `HiveListScreen` afiseaza starea indisponibila.

### Deep links

Variabilele de build sunt definite in `BeeSmart/gradle.properties`:

```properties
BEE_DEEP_LINK_HOST=app.beesmart.ro
BEE_DEEP_LINK_SCHEME=https
BEE_CUSTOM_SCHEME=beesmart
```

### URL-ul backend

`utils/NetworkConfig.kt` directioneaza Retrofit catre endpoint-ul public (Azure Container Apps), niciodata direct catre serviciul AI.

## Configurare backend

Backend-ul citeste configurarea din `appsettings.json`, `appsettings.Development.json` si din variabile de mediu. Pentru rulare locala trebuie configurate cel putin:

- `ConnectionStrings__DefaultConnection`
- `Jwt__Secret` (minimum 32 de caractere aleatorii; backend-ul refuza valorile placeholder)
- setarile SMTP (Mailtrap), pentru testarea confirmarii email / resetarii parolei
- `AiService__BaseUrl`, daca serviciul AI ruleaza pe alta adresa (implicit in Docker: `http://ai-service:5000`)

Pentru Docker Compose se creeaza un fisier `.env` in `ApiaryServer\ApiaryServer`:

```dotenv
DB_PASSWORD=YourStrongPasswordHere
Jwt__Issuer=ApiaryServer
Jwt__Audience=ApiaryClient
Jwt__Secret=replace_with_at_least_32_random_characters
```

### Migrari EF Core

```powershell
cd ApiaryServer\ApiaryServer
dotnet ef migrations add <MigrationName>
dotnet ef database update
```

## Sincronizare offline-first

Fiecare repository verifica `ConnectivityObserver.isCurrentlyOnline()` si `BackendReachability.isLikelyUnreachable()` inainte de orice apel de retea:

- **Offline:** scrie entitatea in Room cu `SyncStatus.PENDING_CREATE/PENDING_UPDATE/PENDING_DELETE` si adauga o intrare in `SyncQueueEntity`.
- **Online:** apeleaza direct Retrofit; la esec, revine la cache-ul Room.

`SyncQueueEntity` retine: `operationType` (CREATE / UPDATE / DELETE / COMPLETE / UNCOMPLETE), `entityType` (APIARY / HIVE / TASK / TREATMENT / EXTRACTION / INSPECTION / INSPECTION_PHOTO / USER_PROFILE), `entityLocalId`, `entityServerId`, `payload` (JSON), `retryCount`, `createdAt`. Maximum 3 reincercari inainte de `SYNC_FAILED`.

`SyncManager` proceseaza coada in **ordine de dependenta**, pentru a garanta integritatea cheilor straine:

```text
APIARY → HIVE → TASK → TREATMENT → EXTRACTION → INSPECTION → INSPECTION_PHOTO
```

Elementele copil sunt amanate pana cand parintele primeste un `serverId`. Exceptiile `IOException` tranzitorii nu incrementeaza `retryCount`; erorile HTTP da. `SyncScheduler` + WorkManager declanseaza procesarea in fundal si la schimbarile de conectivitate.

## Baza de date locala (Room)

`AppDatabase` (versiunea **8**) contine 10 entitati: `ApiaryEntity`, `HiveEntity`, `TaskEntity`, `TreatmentEntity`, `ExtractionEntity`, `InspectionEntity`, `InspectionPhotoEntity`, `SyncQueueEntity`, `InspectionAiAnalysisEntity`, `NotificationHistoryEntity`. Type converters in `Converters.kt` gestioneaza enum-uri, date si liste. Versiunea 8 a adaugat coloana `cellDetectionsJson` (implicit `"[]"`) in `InspectionAiAnalysisEntity`, pentru stocarea detectiilor AI serializate.

## Inteligenta artificiala: DeepBee si analiza spatiala

Fotografiile de inspectie pot fi salvate local si sincronizate ulterior. Pentru analiza AI, aplicatia trimite imaginea catre backend, iar backend-ul apeleaza serviciul DeepBee:

```text
Android (foto) ──> POST /inspections/analyze-cells ──> DeepBee POST /analyze
```

### Serviciul DeepBee (`AIService/`)

Microserviciu Flask care clasifica celulele de fagure dintr-o imagine de rama.

- **Endpoints:** `POST /analyze` (accepta `{ "image_base64": "..." }`), `GET /health`.
- **Pipeline:** decodare base64 → verificari de calitate (dimensiune, blur, luminozitate, contrast) → segmentare semantica (`segmentation.h5`, patch-based) pentru a izola fagurele → extragerea canalului rosu + CLAHE + filtru bilateral → HoughCircles adaptiv (scale-invariant) → crop 224×224 per celula → predictie batch cu `classification.h5`.
- **Clase** (ordinea conteaza, preluata din proiectul DeepBee): `Capped`, `Eggs`, `Honey`, `Larves`, `Nectar`, `Other`, `Pollen`.
- **Statusuri:** `success`, `low_quality`, `not_comb_image`, `uncertain_analysis`; raspunsul include metadata de calitate si un array `cellDetections` (coordonate + clasa + confidence) per celula.
- **Parametri de tuning:** variabilele de mediu `DEEPBEE_SEGMENTATION_MAX_SIDE` (implicit 2200 px), `DEEPBEE_SEGMENTATION_BATCH_SIZE` (16), `DEEPBEE_CLASSIFICATION_BATCH_SIZE` (64).

`AiAnalysisService` (C#) apeleaza acest serviciu prin `HttpClient` (timeout 180 s), pastreaza statusurile non-success (nu le forteaza la `success`), serializeaza `cellDetections` in `CellDetectionsJson` si mapeaza esecurile serviciului AI la erori HTTP controlate (502/504).

### Analiza spatiala (Android)

Detectiile per celula circula end-to-end ca `CellDetection` (coordonate pixel + normalizate, `className`, `confidence`). `InspectionRepository` le (de)serializeaza cu un adapter Moshi dedicat. `BroodAnalyzer` calculeaza `SpatialMetrics` pe grid din coordonatele normalizate (compactitatea puietului, brood gap ratio, stores-edge ratio, polen langa puiet, centrii puiet/rezerve; `SPATIAL_GRID_SIZE = 8`, prag minim de 12 celule de puiet / 10 celule de rezerve).

## Statistici si suport decizional

Clasele de analiza din `data/repository/`:

- **`ApiaryIntelligenceCalculator`** — index compozit de sanatate per stup + radar STABLE / WATCH / CRITICAL.
- **`DashboardAnalyticsCalculator`** — analytics privind productia de miere + forecast.
- **`DeepBeeContextAdvisor`** — recomandari prioritizate (URGENT / IMPORTANT / WATCH / OPPORTUNITY) cu evidence, calendar sezonier si risc de roire; combina trendurile longitudinale cu `SpatialMetrics`.
- **`BeeFlightAdvisor`** si **`BroodAnalyzer`** — verdict de zbor pe baza meteo, respectiv metrici de puiet/rezerve.

## API REST

Toate endpoint-urile, cu exceptia celor de autentificare, necesita un token JWT Bearer.

| Resursa | Prefix | Endpoint-uri |
|---------|--------|--------------|
| **Auth** | `/auth` | `POST register`, `POST login`, `POST refresh`, `POST logout`, `GET/PUT profile`, `POST resend-confirmation`, `POST confirm-email`, `GET confirm-email-link`, `POST forgot-password`, `POST reset-password`, `GET/POST reset-password-link` |
| **Apiaries** | `/api/apiaries` | `GET`, `GET {id}`, `POST`, `PUT {id}`, `DELETE {id}` |
| **Hives** | `/api/hives` | `GET`, `GET apiary/{apiaryId}`, `GET {id}`, `POST apiary/{apiaryId}`, `PUT {id}`, `DELETE {id}` |
| **Inspections** | `/inspections` | `GET`, `GET apiary/{apiaryId}`, `GET hive/{hiveId}`, `GET {id}`, `POST`, `PUT {id}`, `DELETE {id}`, `POST {inspectionId}/photos`, `PUT photos/{photoId}`, `DELETE photos/{photoId}`, `POST analyze-cells`, `POST {inspectionId}/ai-analyses`, `GET hive/{hiveId}/ai-analyses` |
| **Treatments** | `/api/treatments` | `GET`, `GET apiary/{apiaryId}`, `GET hive/{hiveId}`, `GET {id}`, `POST`, `PUT {id}`, `DELETE {id}` |
| **Extractions** | `/api/extractions` | `GET`, `GET apiary/{apiaryId}`, `GET hive/{hiveId}`, `GET {id}`, `POST`, `PUT {id}`, `DELETE {id}` |
| **Tasks** | `/api/tasks` | `GET`, `GET pending`, `GET overdue`, `GET apiary/{apiaryId}`, `GET hive/{hiveId}`, `GET {id}`, `POST`, `PUT {id}`, `POST {id}/complete`, `POST {id}/uncomplete`, `DELETE {id}` |

`InspectionController.SaveAiAnalysis` valideaza strict detectiile primite (`ValidateCellDetections`: maximum 10.000 de intrari; coordonate pixel finite si non-negative; coordonate normalizate in intervalul 0–1; confidence valid) inainte de persistare.

## Cod QR si deep links

Fiecare stup poate avea un cod QR care deschide direct fisa sa in aplicatie. Sunt suportate atat schema custom, cat si HTTPS:

```text
https://app.beesmart.ro/hive/{hiveId}
beesmart://hive/{hiveId}
```

Intent filters sunt declarate in `AndroidManifest.xml`; decodarea parametrilor de query este realizata manual in `MainActivity`. Fluxuri acoperite: confirmare email, resetare parola, navigare la stup. Scanarea foloseste CameraX + ML Kit Barcode Scanning; generarea codurilor QR foloseste ZXing (`QrCodeUtils`).

## Meteo

`WeatherApi` + `WeatherRepository` interogheaza OpenWeatherMap (geocodare, vreme curenta, prognoza pe 5 zile / 3 ore, calitatea aerului) pentru locatia text a unei stupine, printr-o instanta Retrofit dedicata (`@OpenWeatherClient`). Rezultatele de geocodare sunt cache-uite permanent, iar datele meteo sunt cache-uite 30 de minute per pereche de coordonate. Cardul este randat pe `HiveListScreen`.

## Comenzi vocale

`ui/components/VoiceTextField` si `VoiceFormFillerButton` ofera completarea vocala a formularelor prin `VoiceCommandManager` (Android `SpeechRecognizer`, ro-RO), care directioneaza datele catre ViewModel-ul relevant.

## Notificari locale

`TaskNotificationScheduler` si `TreatmentNotificationScheduler` programeaza alarme `AlarmManager` (exacte + allow-while-idle, cand este permis). `BootCompleteReceiver` reprogrameaza alarmele dupa repornire. Istoricul este persistat in `NotificationHistoryEntity`, cu stare citit/necitit.

## Testare

### Android

Testele ruleaza pe JVM prin **Robolectric** (SQLite in-memory) + **MockWebServer** — fara emulator.

```powershell
cd BeeSmart
.\gradlew testDebugUnitTest

# o clasa sau o metoda specifica
.\gradlew :app:testDebugUnitTest --tests "com.example.beesmart.integration.OfflineToOnlineSyncTest"
.\gradlew :app:testDebugUnitTest --tests "com.example.beesmart.integration.SyncEdgeCasesTest.retryOnNetworkError"
.\gradlew :app:testDebugUnitTest --tests "com.example.beesmart.repository.ApiaryRepositoryTest"
```

`SyncTestHarness` (in `test/integration/`) leaga o baza Room reala, un client Retrofit real si MockWebServer. `MockResponses` ofera buildere JSON pentru toate entitatile. Suitele de teste acopera: fluxuri offline → online (`integration/`), repository-uri (`repository/`), `SyncManager` (`sync/`), analytics/advisory (`data/repository/`), interceptori (`network/`) si ViewModel-uri/UI (`ui/`).

### Backend

```powershell
cd ApiaryServer
dotnet test
```

Teste xUnit pentru `AuthController`, serviciile Auth/Apiary/Hive/Inspection/Treatment/Extraction/Task (happy path + ownership), passthrough-ul de status in `AiAnalysisService` si verificari de precizie decimala in `AppDbContext`.

## Deployment

### Docker (local)

Cele trei servicii sunt definite in `ApiaryServer/ApiaryServer/docker-compose.yml`: SQL Server 2022 (port 1433), serviciul AI (port 5000) si API-ul (port 8080). API-ul asteapta SQL Server si serviciul AI si citeste secretele din `.env`.

### Azure (productie)

Deployment-ul public ruleaza pe **Azure Container Apps**: API-ul este expus prin HTTPS ingress, iar serviciul AI este intern (minimum 1 replica, pentru a evita cold start-ul modelelor; 2 CPU / 4 GiB, scaleaza pana la 3 replici). Persistenta foloseste **Azure SQL Database**, cu aceleasi migrari EF Core ca SQL Server local.

## Securitate

- Nu se comit `local.properties`, `.env`, chei API, parole sau secrete JWT.
- Secretul JWT trebuie sa aiba minimum 32 de caractere aleatorii; backend-ul refuza valorile placeholder la pornire.
- Clientii Android `UnauthenticatedClient` si `AuthenticatedClient` accepta toate certificatele in dezvoltare (`TrustAllCerts`) — **comportament ce trebuie inlocuit inainte de productie**.
- Endpoint-urile protejate folosesc JWT + verificari de ownership (`IsOwnedByUserAsync`) in servicii/repository-uri.
- Backend-ul aplica rate limiting global (120 req/min per IP) si dedicat pe login (5/min).
- Detectiile AI sunt validate strict inainte de persistare.

## Limitari si lucrari viitoare

In conformitate cu principiul de suport decizional explicabil, sistemul isi asuma urmatoarele limite:

- AI-ul **nu stabileste diagnostice** sanitare si nu prescrie tratamente; recomandarile cer intotdeauna verificare fizica.
- Analiza DeepBee estimeaza densitatea puietului, dar confirmarea unui model dispersat ramane in sarcina apicultorului.
- Prognoza de productie este explicabila, insa aproximativa, si depinde de gradul de capacire introdus manual.

Directii de extindere identificate: harta melifera si scorul amplasamentului, planificator pastoral, registru de incidente (intoxicatii), jurnal de greutate (stup de control), profil de selectie si ameliorare si integrarea functiilor cu impact legal pe baza legislatiei in vigoare.

## Licenta si atribuiri

Proiect realizat in cadrul lucrarii de licenta. _[Specificati tipul de licenta, dupa caz.]_

Modelele de inteligenta artificiala se bazeaza pe proiectul **DeepBee** (segmentare si clasificare a celulelor de fagure). Atribuirile pentru bibliotecile terte sunt documentate in fisierul `THIRD_PARTY_NOTICES.md`.
