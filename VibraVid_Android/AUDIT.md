# Revisione completa Android 2.5.1

Revisione di tutte le undici classi Java applicative e di ogni funzione di app.js, model.js e cosmetic.js; lettura integrale di HTML, CSS, manifest, risorse, configurazione, fonti e strumenti di compilazione/verifica. Dipendenze esterne e font binari verificati per presenza, licenze e integrità: non viene dichiarata una revisione dell'intero codice Android o Media3.

Nessuna nuova funzione di prodotto. Conservati ricerca/download diretti, browser con AD, libreria, Serata, import/export, video locali, lettore, dieci layout e dieci palette; poster sempre a colori. La versione Windows non è stata modificata. La pubblicazione del fork è successiva alla revisione applicativa.

| Problema | Correzione |
| --- | --- |
| Combinazione Serata valida oltre i primi 200 titoli | Valutazione fino al limite di 2.000 titoli, senza conservare tutte le coppie in memoria; mantenute le dodici migliori proposte. |
| Salvataggio o eliminazione completati dopo apertura di un altro dialogo | Revisione e identità del pannello verificate prima di aggiornare o chiudere; note non salvate preservate. |
| Esportazione immediata dopo una modifica | Attesa dei salvataggi pendenti prima dello snapshot. |
| Callback dopo ricaricamento della UI | Identificativi delle richieste unici per sessione; revisioni di tema e requisiti Wi-Fi ignorano aggiornamenti obsoleti. |
| Campioni delle palette bloccati dalla CSP | Applicazione dei colori con CSSOM, preservando la policy dei documenti locali. |
| Navigazione a 280 px o in Studio orizzontale | Etichette leggibili e menu scorrevole; collegamento di accessibilità porta al contenuto senza cambiare pagina. |
| Due titoli collegati allo stesso video | Posizione di riproduzione conservata fino alla rimozione dell'ultimo collegamento. |
| JSON con migliaia di livelli | Rifiuto esplicito oltre 64 livelli prima del parser; parentesi nelle stringhe correttamente ignorate. |
| HTTP 206 incoerente o senza Content-Length | Validazione del range e della dimensione totale; EOF prematuro diventa errore recuperabile. |
| URL HTTPS con schema maiuscolo | Normalizzazione prima dell'apertura. |
| Lista di 1.000 episodi | Cache dei riferimenti sufficiente a conservare titolo e tutti gli episodi della risposta. |
| Pausa del lettore per il lifecycle | Intento dell'utente distinto dalla pausa temporanea; video in pausa resta in pausa dopo ripresa o salvataggio dello stato. |
| Barre di sistema sul lettore | Insets applicati ai controlli. |
| Componente web, servizio o database non inizializzati | Spiegazione leggibile, controlli prima delle operazioni e rilascio delle risorse già create. |
| Causa di errore ciclica | Visita limitata per identità; nessun ciclo infinito nella spiegazione dell'errore. |
| Preparazione con sola traccia audio selezionata | Richiesta di almeno una traccia video selezionata e compatibile. |
| Tutti i segmenti già nella cache | Completamento del contatore con byte realmente presenti nelle risorse richieste, senza duplicare intervalli sovrapposti. |

L'ultimo caso è stato individuato durante ProviderSmoke. In Media3 1.5.1 il download di segmenti già completi può terminare senza notifiche di progresso: il contatore dell'indice rimane a zero anche se i dati sono disponibili. Il wrapper conserva la factory e le chiavi standard, misura gli intervalli effettivi della cache soltanto in assenza di callback e delega cancellazione/rimozione al downloader originale. Riferimento: [SegmentDownloader 1.5.1](https://raw.githubusercontent.com/androidx/media/1.5.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/offline/SegmentDownloader.java). Le nuove regressioni verificano due download distinti con segmenti HLS e DASH già salvati.

Copertura, risultati, hash e limiti sono in apertura di QA.md e in release/VALIDATION.json. Le sezioni successive sono storico delle versioni precedenti.

---

## Aggiornamento 2.5.0 — layout dell'intera UI e palette chiare

Dieci profili applicati alla shell, alle sei schermate e ai dialoghi, con scelte precedenti compatibili. Semantica separata per testo dei pulsanti nelle palette chiare, badge delle locandine sempre leggibili, contrasto verificato, modalità cinema coerente, icone delle barre Android scure sui fondi chiari e stato ripristinato. Nessuna modifica al motore di ricerca o download. Risultati correnti all'inizio di QA.md.

## Aggiornamento 2.4.0 — aspetto

Aggiunte cinque scelte di layout e cinque palette indipendenti, persistenti. Le preferenze precedenti migrano a Classico/Grafite senza cambiare libreria o download. Colori Android sincronizzati con il fondo selezionato e revisioni per ignorare risposte tardive. La copertura corrente è documentata all'inizio di QA.md; l'audit funzionale sotto si riferisce alla 2.3.1.

# Revisione VibraVid Android 2.3.1

La revisione conserva le funzioni della 2.3: ricerca/download diretti, sei adattatori, 27 indirizzi, browser con AD, libreria, Serata, video del dispositivo, import/export, lettore e impostazioni. Non sono stati aggiunti adattatori o nuove funzioni di prodotto. Il restyling usa esclusivamente nero, grigio e bianco.

## Copertura della lettura

Sono state lette tutte le classi e le funzioni dei sorgenti applicativi: MainActivity, ProviderEngine, ProviderHttp, SourceCatalog, SourceActivity, SourceHttpDataSource, MediaDetector, MediaEngine, MediaDownloadService, VideoActivity e AdBlocker; app.js, model.js e cosmetic.js. Sono stati controllati integralmente CSS, HTML, manifest, risorse, configurazione Gradle, elenco dei 27 indirizzi e strumenti di build, confezionamento e verifica. Anche i runner precedenti sono stati rivisti e gli assert riferiti alla vecchia UI sono stati aggiornati.

Font, licenze e snapshot upstream sono dati: controllo della presenza, provenienza, formati e hash; la lista AD viene rigenerata dal suo snapshot e confrontata. Non viene dichiarata una revisione riga per riga delle dipendenze Android, dei font binari o dell'intero sistema operativo.

## Correzioni

| Caso | Correzione e regressione |
| --- | --- |
| Fonte assente o indirizzo non valido | Errore leggibile; lifecycle del browser sicuro anche senza WebView inizializzata. |
| Preparazione in corso quando chiudi un'attività | Rilascio del helper, annullamento del timer e dei riferimenti; i download già accodati continuano. |
| Risposte tardive di episodi e pannelli | Revisione della richiesta e identità del modulo; un risultato vecchio non sostituisce un altro pannello. |
| Pannello che resta aperto dopo Salva | Attesa delle animazioni limitata, animazione precedente annullata e chiusura legata al modulo corrente. |
| Download che termina mentre scrivi altrove | Preserva pagina e modifiche non salvate; il video resta disponibile in Download. |
| Impostazioni e video completati dopo una navigazione | Aggiorna i dati senza sostituire la schermata scelta successivamente. |
| Bozza di ricerca cancellata da un aggiornamento | Conservazione del testo digitato prima del submit, anche cambiando Film/Serie. |
| Conferma di eliminazione persa ogni 4 secondi | Conferma mantenuta durante il polling e rimossa quando il download scompare. |
| Titoli non latini confusi nel confronto | Mantiene lettere e numeri Unicode; fallback sicuro per WebView meno recenti. |
| Caratteri U+2028/U+2029 nel bridge | Escaping compatibile con WebView precedenti a ES2019. |
| Stagione zero non selezionabile | Distinzione fra scelta iniziale automatica e stagione 0; stagione assente spiegata. |
| Libreria ricreata durante un salvataggio | Lock condiviso fra attività e verifica di interruzione prima della scrittura. |
| Solo backup AtomicFile presente | Recupero anche se il file principale manca. |
| File collegati nulli/duplicati e tipi errati | Validazione senza eccezioni grezze; rifiuto chiaro delle raccolte non valide. |
| Limite numerico degli identificativi | Riutilizzo di un ID libero senza produrre valori fuori formato. |
| Poster importato senza crediti | Fonte e autore conservati quando viene unita l'immagine. |
| Proposta Serata obsoleta dopo modifiche | Invalidazione dopo modifica/importazione dei titoli. |
| Intestazioni con CRLF | Controllo esplicito di entrambi i caratteri prima del trasporto. |
| Segmento scambiato per film o MIME incoerente | Segmenti esclusi prima degli hint; estensione HLS/DASH prioritaria. |
| Server ignora Range/comprime/risponde male | Skip corretto, controllo Content-Range, gzip, EOF prematuro e chiusura anche in errore. |
| Cookie aggiornati nei redirect del video | Cookie acquisiti e inviati soltanto per la destinazione corretta; Authorization non attraversa l'origine. |
| Errore di rete, spazio o permessi | Messaggi operativi per assenza rete, timeout, spazio pieno, file mancante e permesso non persistente. |
| Risultato del selettore dopo ricreazione | Recupero di importazione, esportazione o collegamento video nella nuova UI. |
| Collegamenti dimenticati che trattengono permessi | Token rimossi e permessi persistenti rilasciati quando non più usati; non vengono rimossi token nuovi ancora in attesa. |
| Ripresa della coda dopo timeout del servizio | Ripresa in foreground rispettando pause individuali e requisito Wi-Fi. |
| CPU/schermo mantenuti attivi inutilmente | Wakelock con scadenza rinnovata durante il trasferimento, rilascio a fine attività; flag schermo rimosso in pausa, fine o errore. |
| Lettore locale con file non accessibile | Messaggio che invita a selezionare di nuovo il video; dialogo rilasciato alla chiusura. |
| PiP non consentito dal dispositivo | Eccezione gestita senza chiudere l'app. |
| Renderer web terminato da Android | Risposta gestita, spiegazione e riapertura; libreria e download non vengono cancellati. |
| Banner cambia classe dopo l'inserimento | Il filtro cosmetico osserva anche gli attributi specifici, senza osservare le sue modifiche di stile. |
| Esportazione grande sul thread grafico | Serializzazione eseguita nel worker; controlli della dimensione prima del selettore. |

Sono stati eliminati i colori alternati e le decorazioni CSS inutilizzate, la proprietà metadata non usata e le registrazioni duplicate dell'errore immagine. Le due modalità del tema sono conservate con due profondità di nero. CSS e risorse Android condividono la palette; icona, splash, barre, dialoghi, AD e lettore sono coerenti. I contenuti remoti delle fonti conservano la loro grafica.

## Verifica e limiti

Risultati misurati, APK/hash e log sono riportati in QA.md e release/VALIDATION.json. I test nativi usano esclusivamente emulator-5582, con runner separati non inclusi nell'app. I video completi di prova sono i campioni pubblici Google/Shaka, non film dell'utente. Le fixture sostituiscono soltanto il trasporto dei cataloghi per controllare i percorsi deterministici; trasferimento, indice e lettore sono quelli reali.

Il selettore Android reale è stato tentato, ma System UI/DocumentsUI del dispositivo software si è bloccato. Le letture/scritture dei risultati del selettore sono quindi controllate separatamente con URI di file privati di prova; questo non viene descritto come prova completa del selettore su telefono. Il recupero del renderer viene simulato chiamando il callback reale, non provocando un esaurimento della memoria del sistema.

Non è possibile certificare l'assenza assoluta di bug, ogni dispositivo o ogni cambiamento delle fonti esterne. Rimangono da verificare fisicamente decoder, PiP, permessi Android 13+, restrizioni/risparmio energetico e selettori del produttore. I warning residui di lint sono documentati: dipendenze/target mantenuti, JavaScript necessario e isolato, testi italiani, singleton con application context e raccomandazioni del sistema; non sono stati nascosti globalmente.
