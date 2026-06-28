# Audit functionalitati apicole BeeSmart

## Scop

Audit realizat prin compararea aplicatiei BeeSmart cu suportul de curs
`SUPORT-APICULTOR-1.pdf` (158 pagini). Documentul propune functionalitati
digitale utile in teren si evita transformarea automata a suportului intr-un
manual de diagnostic sau tratament.

Suportul contine informatii legislative si exemple de substante care necesita
validare actuala. Pentru functiile cu impact sanitar sau legal, aplicatia trebuie
sa ofere suport decizional, trasabilitate si trimitere catre specialist, nu
prescriptii automate.

## Ce este deja relevant

| Functionalitate BeeSmart | Relevanta apicola |
| --- | --- |
| Stupine, stupi, localizare si QR | Baza carnetului digital de stupina si acces rapid in teren. |
| Inspectii cu rame, regina, oua, larve, fotografii | Corespund controlului sumar si general descris la paginile 82-84. |
| Analiza DeepBee a celulelor si evolutie longitudinala | Ajuta la urmarirea puietului, rezervelor si ritmului pontei. |
| Radar inteligent si Consilier DeepBee | Coreleaza analiza ramei cu istoricul, task-urile, tratamentele si productia. |
| Tratamente si extractii | Sunt necesare pentru trasabilitate, sanatate si analiza productiei. |
| Prognoza meteo, aer si verdict pentru zbor | Relevante pentru inspectii, cules si amplasarea stupinei. |
| Task-uri, notificari si istoric | Pot deveni baza calendarului sezonier asistat. |
| Voce si sincronizare offline-first | Potrivite pentru folosirea cu manusi si in vetre cu semnal slab. |

## Functionalitati existente care trebuie rafinate

### 1. Termenul urmator al tratamentului

Modelul Android si API-ul includ `nextTreatmentDate`, iar Consilierul DeepBee
poate semnala depasirea termenului. Formularul de tratament trimite insa mereu
valoarea `null`. Trebuie adaugate selectorul de data si notificarea aferenta.

### 2. Prognoza de miere

Prognoza actuala este explicabila, dar aproximativa: productie deja extrasa plus
rame cu miere inmultite cu o constanta. Suportul arata ca estimarea depinde de
tipul ramei si de gradul de capacire (pagina 84). Interfata trebuie sa afiseze
explicit ipoteza si sa permita completarea gradului de capacire.

### 3. Profilul stupinei

Stupina are momentan o locatie text. Pentru recomandari mai bune sunt necesare:
coordonate, sursa de apa, expunere, protectie fata de vant, umiditate observata,
surse melifere si riscuri industriale sau fitosanitare. Aceste criterii apar la
paginile 130 si 155.

### 4. Inspectia structurata

Inspectia actuala acopera nucleul corect, dar ii lipsesc observatii utile pentru
reguli explicabile:

- botci cu oua, aglomerare la urdinis si lipsa spatiului;
- uniformitatea puietului si gradul de capacire al mierii;
- rezerve estimate, hraniri si apa;
- umezeala, mucegai, mortalitate la urdinis si comportament neobisnuit;
- blandete sau agresivitate;
- vechimea fagurilor si necesarul de inlocuire.

### 5. Limitele DeepBee

Analiza actuala numara tipurile de celule, dar nu primeste coordonatele acestora.
Poate estima densitatea puietului, insa nu poate confirma singura un model
dispersat. Recomandarea corecta ramane verificarea fizica si compararea in timp.

### 6. Produsele apicole

Aplicatia inregistreaza miere, polen, propolis, laptisor de matca si ceara.
Suportul mentioneaza si pastura, veninul si apilarnilul (paginile 4-5 si 96).
Pastura merita adaugata prima; celelalte pot ramane optionale.

## Roadmap recomandat

## Stadiu implementare

- 2026-06-03: P0.1 a fost rafinat end-to-end pe Android. Selectorul pentru
  `nextTreatmentDate` exista deja in formular; au fost adaugate reminderul de
  tratament, canalul de notificare, istoricul notificarii, anularea la stergere
  si reprogramarea dupa repornirea telefonului.
- 2026-06-03: P0.2 a fost extins in Android si API cu Inspectie inteligenta V2:
  botci, botci cu oua, barba la urdinis, spatiu insuficient, uniformitatea
  puietului, gradul de capacire, hranire, apa, igiena, mortalitate,
  comportament, temperament si faguri vechi. Campurile sunt salvate offline,
  sincronizate, completabile vocal si afisate sumar in lista de inspectii.
- 2026-06-03: P0.3 a intrat in Consilierul DeepBee ca recomandare sezoniera
  asistata. Calendarul propune actiuni de primavara, vara, toamna si iarna,
  explicate ca task-uri confirmate de apicultor.
- 2026-06-03: P0.4 a fost legat de noile observatii din Inspectie inteligenta
  V2. Riscul de roire ramane prudent, ca verificare fizica, dar foloseste acum
  botci cu oua, barba la urdinis, spatiu insuficient, sezon, puterea coloniei
  si varsta matcii.

## Verificare fundament P0

| Functionalitate P0 | Fundament in suport | Decizie de implementare | Limita asumata |
| --- | --- | --- | --- |
| Inspectie inteligenta V2 | Controlul sumar si general de primavara urmaresc matca, oua/larve, rezerve, umiditate, mucegai, puterea familiei si uniformitatea puietului (pag. 82-84). | Formularul colecteaza semnale structurale: botci, puiet, rezerve, capacire, apa, igiena, mortalitate, temperament si faguri vechi. | Aplicatia inregistreaza observatii; nu stabileste diagnostic sanitar. |
| Calendar sezonier asistat | Lucrarile sunt descrise pe anotimpuri: toamna/iarna/primavara (pag. 73-85), cules si spatiu de recolta (pag. 66). | Consilierul propune task-uri sezoniere, fara a le crea automat. | Apicultorul confirma task-ul si adapteaza la vreme/stupina. |
| Risc de roire | Ouale in botci sunt semnal al intrarii in frigurile roitului; barba si lipsa spatiului sunt semne/factori importanti; matcile tinere reduc tendinta de roire (pag. 61). | Regula foloseste botci cu oua si barba ca semnale tari; spatiul insuficient este factor corelat cu sezonul si puterea familiei. | Recomandarea cere verificare fizica, nu declara roirea confirmata. |
| Recolta / capacire | Mierea se recolteaza cand o treime pana la doua treimi din faguri sunt capaciti si mierea necapacita nu curge la apasare (pag. 101). | Consilierul foloseste `honeyCappingPercent` cand exista; sub 33% nu sugereaza fereastra de recolta. | Testul de apasare si maturitatea reala raman verificari de teren. |
| Reminder tratament | Tratamentele trebuie facute cu discernamant, prospect si, unde e cazul, sfat veterinar; medicamentele pot produce intoxicatii daca sunt folosite gresit (pag. 134, 146, 152). | Reminderul semnaleaza termenul de revizuire/aplicare si Consilierul cere verificarea schemei/prospectului. | Aplicatia nu prescrie produs, doza sau tratament preventiv. |

### P0 - Impact rapid pentru prezentare

1. **Tratament complet cu reminder**
   - Selector pentru urmatoarea verificare sau aplicare.
   - Notificari si afisare in Consilierul DeepBee.
   - Camp optional pentru perioada de asteptare si observatii.

2. **Inspectie inteligenta V2**
   - Sectiuni rapide: regina, puiet, rezerve, roire, igiena, comportament.
   - Sabloane sezoniere si completare vocala.
   - Fotografii DeepBee legate de observatiile din teren.

3. **Calendar sezonier asistat**
   - Primavara: control sumar si general, rezerve, regina si apa (paginile 82-85).
   - Vara: spatiu pentru cules, roire si recolta (paginile 61 si 66).
   - Toamna: rezerve, matca si pregatirea pentru iernare (paginile 73-78).
   - Iarna: ventilatie, protectie si supraveghere (paginile 78-80).
   - Task-urile sunt propuneri explicabile, confirmate de apicultor.

4. **Scor de risc pentru roire**
   - Semnale: botci cu oua, aglomerare de tip "barba", spatiu insuficient,
     matca varstnica, crestere accelerata si sezon.
   - Recomandari prudente pentru verificare si extinderea spatiului.
   - Fundament: paginile 61-67.

### P1 - Diferentiatori pentru competitie

5. **Harta melifera si scorul amplasamentului**
   - Harta cu raza economica de 3 km si raza de necesitate de 6 km.
   - Calendar local de inflorire, apa, vant, umiditate, poluare si risc pesticide.
   - Capacitate estimata a vetrei pentru evitarea supraaglomerarii.
   - Fundament: paginile 109, 121-122 si 130.

6. **Planificator pastoral**
   - Ferestre de cules, lista de verificare pentru mutare, vatra temporara,
     istoric si compararea productiei pe amplasamente.
   - Fundament: paginile 67-70.

7. **Alerta si dosar pentru intoxicatii**
   - Inregistrare incident: data, locatie, cultura, simptome, fotografii,
     mortalitate estimata si probe colectate.
   - Alerte locale introduse manual sau importate din surse oficiale disponibile.
   - Export al unui raport pentru specialist sau autoritati.
   - Fundament: paginile 151-157.
   - Cerintele juridice trebuie validate din legislatia in vigoare.

8. **Stup de control si jurnal de greutate**
   - Inregistrari manuale sau import optional de la cantar.
   - Trend zilnic pentru cules si avertizari pentru pierderi neobisnuite.
   - Fundament: pagina 122.

9. **Jurnal de iernare cu audio optional**
   - Observatii ghidate pentru zumzet, hrana, umezeala si ventilatie.
   - Inregistrarea audio poate deveni ulterior tema unui model experimental.
   - Nu se prezinta ca diagnostic automat.
   - Fundament: pagina 80.

### P2 - Directii academice pe termen mediu

10. **Profil de selectie si ameliorare**
    - Productie, rezistenta la iernare, blandete, compactitatea puietului,
      tendinta de roire, varsta si istoricul matcii.
    - Clasament explicabil al familiilor valoroase.
    - Fundament: paginile 88-90.

11. **Modul de polenizare**
    - Contract sau misiune, cultura, suprafata, perioada, amplasare si numar de
      familii mobilizate.
    - Raport de impact pentru componenta ecologica a proiectului.
    - Fundament: paginile 4 si 127.

12. **Inventar apicol**
    - Stupi, corpuri, rame, magazii, unelte, hranitoare si materiale sanitare.
    - Util administrativ, dar cu valoare demonstrativa mai mica decat P0 si P1.
    - Fundament: paginile 44-57.

13. **Registru de sanatate si profilaxie**
    - Simptome structurate, izolare, igienizare, faguri de inlocuit si trimitere
      catre medicul veterinar.
    - Aplicatia nu stabileste diagnostice si nu prescrie tratamente.
    - Fundament: paginile 133-149.

## Propunere de narativ academic

BeeSmart poate fi prezentata drept un **carnet digital inteligent de stupina**:

1. Colecteaza date usor in teren, inclusiv offline, prin formulare, voce, QR si
   fotografii.
2. Interpreteaza explicabil rama prin DeepBee si o coreleaza cu jurnalul.
3. Anticipeaza lucrari sezoniere, risc de roire, lipsa rezervelor si oportunitati
   de productie.
4. Extinde decizia de la stup la vatra prin meteo, aer, flora melifera si
   pastoral.
5. Pastreaza limita corecta: AI-ul sustine apicultorul, nu inlocuieste controlul
   fizic sau medicul veterinar.

## Nota privind legislatia

Pentru dezvoltarea functiilor juridice trebuie folosita legislatia curenta, nu
doar suportul de curs. Portalul oficial indica Legea apiculturii nr. 383/2013,
cu modificari si completari ulterioare, inclusiv Legea nr. 79/2020:

`https://legislatie.just.ro/Public/DetaliiDocument/226401`
