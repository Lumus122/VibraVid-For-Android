# Android 2.8.0 — domini automatici e Update

Gli indirizzi delle fonti vengono sincronizzati in background all’avvio/ritorno nell’app, al massimo una volta all’ora, dall’endpoint usato dal PC: `https://domains-tracker.server66.workers.dev/get`. Se una ricerca fallisce, viene tentato un aggiornamento e una sola nuova ricerca quando l’indirizzo è cambiato. I tentativi sono limitati ad almeno trenta secondi di distanza; senza rete o con servizio indisponibile rimane l’ultimo elenco salvato. Non si indovinano estensioni casuali. Solo fonti note e indirizzi HTTPS validi vengono accettati. Gli indirizzi modificati esplicitamente o importati dall’utente sono conservati come personalizzazioni. La fonte centrale deve conoscere il dominio nuovo; la disponibilità di ogni sito non è garantita.

**Impostazioni → Update** controlla le ultime venti release pubblicate di `Lumus122/VibraVid-For-Android`, ignora bozze/prerelease e seleziona il pacchetto Android stabile più recente. Se è più nuovo, avvia il download con il gestore Android; alla conclusione, o riaprendo l’app, verifica dimensione, hash GitHub quando presente, identificativo dell’app, versionCode crescente e identità della firma. L’installazione usa la schermata Android e richiede la conferma dell’utente. Se necessario Android offre il permesso per installare da questa app; rifiutandolo non viene aperta ripetutamente la richiesta. Tocca nuovamente Update per riprovare. Nessuna installazione silenziosa, nessun caricamento sulla repo dal telefono.

La repo osservata contieneva ancora `v2.5.1-android`: il comando considera correttamente 2.8.0 già aggiornata finché non viene pubblicato un APK più nuovo. Pubblicazione solo dopo il consenso del proprietario. La prova dell’installazione completa di una release successiva su telefono resta da effettuare: non esisteva una release più nuova da installare durante questa verifica.

---

# VibraVid Android 2.7.0 — galleria e lingue

Correzione del percorso **Salva → Salva video** nel lettore. Su Android 10+ il file viene creato nel volume concreto `external_primary` e pubblicato rimuovendo `IS_PENDING`; durata e dimensioni vengono lette dal servizio multimediale di Android. Su Android 8–9 resta il salvataggio nella cartella pubblica con il permesso di scrittura. Le copie incomplete vengono rimosse, quelle pubblicate conservate.

La creazione dell’MP4 usa Media3 InAppMuxer 1.5.1. Il servizio dichiara anche `mediaProcessing`, lo usa esplicitamente su API 35+, e mantiene `dataSync` sulle versioni precedenti. Errori di avvio, permessi, notifiche e spazio insufficiente vengono gestiti con uno stato leggibile; il video originale non viene rimosso. L’annullamento viene elaborato anche quando il servizio è occupato.

**Impostazioni → Lingua**: italiano, inglese, spagnolo, portoghese, francese, tedesco, olandese, polacco, rumeno, ucraino, russo, turco, arabo, hindi, bengalese, cinese semplificato, giapponese, coreano, indonesiano, vietnamita e thailandese. Italiano predefinito, selezione persistente, cataloghi inclusi e uso senza rete. La lingua interessa interfaccia, controlli del lettore, finestre native e messaggi; non cambia l’audio dei film. Arabo con direzione da destra a sinistra; la barra temporale conserva la direzione cronologica. Titoli, note, percorsi, URL e contenuti delle fonti restano originali. I testi estesi sono bozze tradotte automaticamente; le principali etichette e la terminologia galleria/aspetto sono state riviste, ma non si dichiara una revisione linguistica professionale completa.

# VibraVid Studio Android 2.6.0

App autonoma per Android 8.0 o successivo. Mantieni aggiornato Android System WebView per le pagine delle fonti. Non richiede il PC acceso. Il file installabile è `release/VibraVid_Studio_Android.apk`, con una copia sul Desktop. Installa come aggiornamento della versione precedente, senza disinstallarla, per conservare libreria e video. Firma locale identica alla 2.1; la chiave privata non è nei sorgenti distribuiti.

## Ricerca e download direttamente nell’app

La 2.6.0 introduce un lettore cinematografico nativo e il salvataggio dei video in galleria. Mantiene ricerca e download diretti, libreria, dieci layout e dieci palette. La revisione e le prove, con i rispettivi hash degli APK, sono documentate in QA.md e AUDIT.md.

La 2.5.0 offre dieci layout dell'intera UI e dieci palette in Impostazioni > Aspetto: cinque scure e cinque chiare/colorate, indipendenti e salvate sul dispositivo. Classico/Grafite resta la prima scelta. La modalità cinema è compatibile anche con le palette chiare. La 2.3.2 ripristina le locandine a colori e introduce sfumature carbone, grafite e perla, senza cambiamenti funzionali. La 2.3.1 è una revisione di affidabilità e grafica: palette nero/grigio/bianco, senza nuove funzioni. Corregge salvataggi e callback tardivi, riconoscimento Unicode dei titoli, preparazioni, gestione dei file e recupero del componente web. Vedi AUDIT.md e QA.md per copertura, risultati e limiti.

1. Apri **Cerca**, scrivi il titolo e premi **Cerca e scarica**. Non occorre aprire il sito, avviare un player o aspettare un rilevatore.
2. Se il titolo del film corrisponde esattamente a un solo nome/anno, il download parte automaticamente. Titoli omonimi di anni diversi vengono mostrati per la scelta, così non viene scaricato il film sbagliato.
3. Altrimenti scegli **Scarica** sul risultato. Per serie e anime, scegli stagione ed episodio dentro VibraVid.
4. **Fonti e qualità** permette di scegliere una fonte specifica, tipo di contenuto e qualità massima (480p/720p/1080p). La selezione iniziale interroga tutte le fonti automatiche compatibili con il tipo.
5. In **Download** trovi bytes realmente scaricati, avanzamento, pausa, ripresa, rimozione e **Guarda offline** quando il video è completo. Il lettore usa esclusivamente i dati locali.

Risultati equivalenti di più fonti vengono raggruppati. Se la prima fonte non riesce a preparare un film, viene tentata la successiva disponibile nello stesso gruppo. Per una stessa fonte, titolo/episodio e qualità, un nuovo URL firmato non crea un duplicato del download già presente. Ricerche e risoluzione dei collegamenti possono essere interrotte; il pulsante resta disabilitato durante la preparazione delle tracce.

**Verifica e ripara** controlla nuovamente il download e recupera dalla fonte le parti non presenti. Se il lettore rileva parti mancanti, il video viene segnalato come incompleto e offre la riparazione. Serve connessione; se il collegamento della fonte è scaduto, rileva nuovamente il video.

HLS e DASH vengono conservati come manifest e tracce nello spazio offline dell'app. Il lettore può ora crearne una copia MP4 nella galleria. Anche i video progressivi usano la cache offline. Disinstallando VibraVid elimini i download e la libreria privata; le copie già salvate in galleria e i video scelti dal dispositivo restano nella loro posizione originale.

## Lettore cinematografico e galleria

Apri **Download → Guarda offline**, oppure **Scegli video** per un file del telefono. Il lettore usa sfondo nero, sfumature discrete, titoli leggibili e controlli a scomparsa. Tocca il video per mostrare o nascondere i comandi. In pausa restano visibili; durante la riproduzione scompaiono dopo pochi secondi. Il movimento ridotto delle impostazioni viene rispettato.

- Doppio tocco a sinistra/destra: indietro/avanti di dieci secondi. Al centro: pausa o riproduzione.
- Scorrimento verticale nella metà sinistra: luminosità della finestra del lettore; nella metà destra: volume multimediale.
- Barra di avanzamento con tempo trascorso e rimanente; velocità da 0,5× a 2×; audio e sottotitoli effettivamente disponibili nel video; adatta/riempi schermo.
- Lucchetto: blocca i tocchi accidentali. Tocca il video per ritrovare il pulsante di sblocco. Mini player sui telefoni con supporto Picture-in-Picture.
- **Salva → Salva video** crea una copia in **Filmati/VibraVid** (`Movies/VibraVid`), accessibile alle app di galleria. Il download originale rimane disponibile.

L'esportazione di un download completo usa esclusivamente la cache locale, senza riscaricare il video. HLS/DASH sono assemblati in MP4 con video H.264 e audio AAC compatibili; se il formato richiede conversione, il telefono può impiegare più tempo e spazio temporaneo. La copia contiene una traccia video e una audio: eventuali tracce audio alternative e sottotitoli rimangono nel download dell'app. I video progressivi e i file del dispositivo vengono copiati nel loro contenitore originale quando riconosciuto.

Si può salvare un solo video alla volta. Avanzamento e annullamento sono disponibili nel lettore e nella notifica; il salvataggio prosegue fuori dal lettore. I file incompleti vengono eliminati, e il video è pubblicato in MediaStore solo dopo la validazione. Un collegamento online deve essere prima scaricato nell'app. Android 8–9 richiede il permesso di scrittura dei file al momento del salvataggio; Android 10+ usa MediaStore senza accesso generale all'archivio. Alcune gallerie aggiornano l'elenco con un breve ritardo. Un arresto forzato interrompe il salvataggio: riaprilo per riprovare.

## Adattatori Android e compatibilità

Sono portati nativamente dalla logica dei corrispondenti sorgenti PC: **StreamingCommunity**, **Streamingcommunity 2**, **AnimeUnity**, **AnimeWorld**, **Eurostreaming** e **RaiPlay**. I cataloghi vengono interrogati tramite HTTPS; le risposte HTML/JSON vengono analizzate localmente senza eseguire JavaScript dei siti. Il risolutore ricava il video dai protocolli dei player. Per StreamingCommunity gestisce sia risposte Inertia sia cataloghi JSON paginati, segue i cambi di dominio e conserva i parametri dei manifest firmati, anche quando l’indirizzo HLS non termina con .m3u8.

L’app conserva **27 indirizzi** della configurazione PC, ma non sono 27 adattatori automatici portati integralmente. Gli altri servizi, inclusi quelli che richiedono account, chiavi API o licenze DRM, restano negli strumenti aggiuntivi **Gestisci le fonti**. Gli indirizzi possono essere modificati o importati tramite domains.json del PC. Non vengono incorporati credenziali o chiavi private PC.

Un adattatore può non rispondere se il sito cambia protocollo, indirizzo, richiede verifiche/accesso o non offre il video nel paese del telefono. La ricerca restituisce i risultati delle fonti disponibili e riporta gli errori delle altre. In questa verifica l’endpoint WordPress configurato di Eurostreaming ha risposto 403: l’adattatore esiste, ma non viene dichiarato disponibile per quel dominio. I contenuti DRM e le dirette continuano a essere rifiutati dal motore offline; sono necessarie licenze offline ufficiali del servizio, non incluse.

La libreria e la ricerca dei metadati restano separate dal download effettivo. Una scheda salvata non viene presentata come video scaricato.

## Blocco pubblicità nel browser delle fonti

Attivo per impostazione iniziale. Il pulsante **AD** mostra le richieste e le nuove finestre bloccate nella pagina corrente; toccalo per attivare o disattivare la protezione soltanto sulla fonte aperta. La preferenza resta salvata anche riaprendo l’app. Cambiare la protezione ricarica la pagina.

Il filtro intercetta i domini pubblicitari e di tracciamento prima del rilevamento video, così un MP4 pubblicitario bloccato non viene proposto come film. Include un sottoinsieme locale delle regole di dominio AdGuard DNS, con eccezioni e priorità, e la rimozione dei contenitori pubblicitari comuni, anche aggiunti dinamicamente. Blocca le nuove finestre quando è attivo. Nessuna VPN, modifica DNS del telefono o estensione Chrome da installare.

Il filtro non interpreta l’intera sintassi ABP: le regole wildcard/regex non supportate vengono omesse. Annunci ospitati sullo stesso dominio del film, contenitori non riconosciuti, iframe di altri domini e richieste gestite da service worker possono non essere filtrati. Se una fonte necessita di richieste bloccate per avviare il player, disattiva AD su quella fonte. La lista è inclusa nell’APK: non viene scaricata o aggiornata durante la navigazione.

Sorgente originale non modificata: `tools/filter_snapshot/adguard-dns.txt`. `python tools/refresh_adblock.py --snapshot` rigenera lo stesso sottoinsieme senza rete; omettendo `--snapshot` scarica una nuova lista in fase di sviluppo. Provenienza, data, hash e conteggi sono in `app/src/main/assets/adblock/metadata.json`. Le regole di dominio e le eccezioni vengono cercate per hostname esatto e suffissi di dominio, senza scansione lineare di tutte le regole per ogni risorsa. Caricamento su un thread separato prima di aprire la fonte.

`python tools/build_smoke.py AdBlockSmoke` genera il test nativo separato. Installalo esclusivamente sul dispositivo QA e avvia `adb -s emulator-5582 shell am instrument -w it.vibravid.studio.tests/.AdBlockSmoke`. Verifica richieste bloccate, esclusione degli annunci dai candidati video, rilevamento HLS, banner dinamici, eccezioni per fonte e assenza del bridge nella pagina remota.

## Altre funzioni

Libreria privata fino a 2.000 titoli, raccolte, note, stelle, stati e durate; filtri e ordinamento; Serata con proposte entro il tempo disponibile; importazione ed esportazione JSON compatibile con Windows; video del dispositivo mediante selettore Android; ripresa della posizione, sottotitoli e Picture-in-Picture; modalità cinema e movimento ridotto. **Cerca informazioni** usa Wikidata/TVmaze per metadati e poster e rimane distinta dalle fonti dei video.

## Rete, spazio e notifiche

Due download simultanei, al massimo tre trasferimenti di segmenti. Pausa e stato sono persistenti. La preferenza solo Wi-Fi viene applicata alla coda (rete non a consumo). Il servizio di download mantiene una notifica e un blocco CPU soltanto mentre trasferisce. Android 15 limita a sei ore il servizio dataSync in background: se scade, la coda viene sospesa e puoi riprenderla riaprendo l'app. Android può interrompere un'app arrestata forzatamente o soggetta a risparmio energetico del produttore.

Permessi: Internet e stato rete, servizio in primo piano dataSync, notifiche su Android 13+, riattivazione programmata e blocco CPU durante il trasferimento. Solo Android 8–9 richiede la scrittura nell'archivio condiviso per la galleria; Android 10+ non richiede quel permesso. Nessun accesso a contatti, posizione, fotocamera o microfono; il browser nega le richieste dei siti per microfono/fotocamera. Nessuna telemetria. Libreria scritta atomicamente, download e indice persistenti. Per conservare la libreria prima di una disinstallazione, esportala.

## Verifica del nuovo flusso

`python tools/build_smoke.py ProviderSmoke` compila un test strumentale separato da installare soltanto sull’emulatore QA. Verifica il catalogo, il risolutore e gli episodi di StreamingCommunity attraverso richieste HTTPS reali. Poi usa fixture dei protocolli per avviare dalla UI il download reale dei campioni pubblici Google/Shaka HLS e DASH, verifica la riproduzione con cache priva di sorgente di rete e controlla che il browser delle fonti non si apra. Verifica anche titoli omonimi e riuso del download dopo il rinnovo del collegamento. I campioni non sono film dell’utente; le fixture e il runner non sono incorporati nell’APK. Avvio: `adb -s emulator-5582 shell am instrument -w it.vibravid.studio.tests/.ProviderSmoke`.

## Compilazione e verifiche

Richiede Python 3, JDK 17+, Gradle 8.13, SDK Android platform 35 e build-tools 35.0.0. La build usa JDK 21 e Android Gradle Plugin 8.13.2. Colloca Gradle in `toolchain/gradle-8.13` e SDK in `toolchain/sdk`, oppure adatta i percorsi nello script locale. Esegui `python tools/build_apk.py`: compila, allinea, firma, verifica la firma e genera hash e manifesto. Conserva `.signing` privatamente per aggiornamenti compatibili.

`node tools/verify_model.cjs` verifica dati e regole della libreria. `python tools/build_smoke.py StreamSmoke` genera il test strumentale separato, firmato con la stessa chiave. `python tools/run_stream_smoke.py` usa esclusivamente l'emulatore QA emulator-5582; il test scarica il campione pubblico Angel One e avvia il lettore con cache priva di sorgente di rete. `resume` verifica nuovamente l'indice dopo un riavvio del processo. La modalità `dash` verifica anche pausa e ripresa; `repair` riattiva il controllo di un HLS già indicizzato. L’avvio senza argomenti ripulisce soltanto i dati dell’emulatore QA prima del test. Test e strumenti Android non vengono incorporati nell'APK. Vedi QA.md per risultati e limiti effettivi.

## Licenze

VibraVid Studio: GNU GPL v3, sorgenti corrispondenti distribuiti. Sottoinsieme AdGuard DNS filter: GPL-3.0, con snapshot originale e compilatore inclusi; https://github.com/AdguardTeam/AdGuardSDNSFilter. jsoup: MIT. Core library desugaring e AndroidX Media3 e dipendenze AndroidX/Guava/Kotlin: Apache 2.0; JSR305: BSD. Nunito Sans e Bricolage Grotesque: SIL OFL. Wikidata: CC0; metadati TVmaze: CC BY-SA; immagini Commons con crediti e licenza nelle schede. La UI include testi delle licenze locali.

Documentazione del download: https://developer.android.com/media/media3/exoplayer/downloading-media
Esportazione e MediaStore: https://developer.android.com/media/media3/transformer e https://developer.android.com/training/data-storage/shared/media
Media3: https://github.com/androidx/media/tree/1.5.1
