# Revisione Android 2.5.1 — consegna richiesta

APK: **4fe6f9de33241d25cbc1cb9e819780e268f3f7058c35600ce68eb2f13eed2e4b**, versionCode 25001, 6187657 bytes. Firma, certificato di aggiornamento, sorgenti e asset del pacchetto verificati. Test e fixture assenti dall'APK.

145 controlli sulle regole e sull'aspetto: 22 modello e 123 palette/persistenza/contrasto. Anteprima isolata: 300 casi su dieci layout e sei schermate a 280, 320, 390, 640 e 900 px; dieci dialoghi e due regressioni di risposte tardive. Poster a colori.

Le suite native vengono registrate in release/*-smoke-251.json con l'hash esatto di ciascuna build incrementale. Android 9, audit 32, affidabilità 28, import/export 14, trasporto 17, adattatori 25, adblock 23, provider 20 e coda 12 erano superati prima dell'ultima correzione del contatore della cache. Il test provider trasferisce HLS 6.724.521 bytes e DASH 3.951.608 bytes e riproduce entrambi senza upstream di rete. Coda verifica anche intenzione di pausa/ripresa, persistenza dei byte dopo riavvio del processo e rimozione. Sei protocolli degli adattatori verificati con fixture; catalogo/risolutore/episodi StreamingCommunity interrogati realmente.

Il primo tentativo del provider ha rilevato un contatore a zero quando i segmenti erano già salvati; il log del tentativo fallito è conservato in release/logs-251. Identificata la mancanza di callback nel percorso tutto-in-cache di Media3 1.5.1, applicata la correzione e aggiunte due regressioni dedicate HLS/DASH. Stato della prova sull'ultimo APK: **interrupted at user request; not claimed passed**. Il dettaglio aggiornato è in release/VALIDATION.json.

L'utente ha richiesto la consegna senza ulteriori test. La revisione non garantisce assenza assoluta di bug. Emulatore dedicato Android 9/API 28; nessun telefono fisico. Non certificati selettore SAF reale, notifiche Android 13+, restrizioni Android 15, PiP/decoder fisici e risparmio energetico OEM. Le fonti esterne possono cambiare. La revisione applicativa ha lasciato invariata la versione Windows. Le sezioni successive sono storico, con i rispettivi hash.

---

# Dieci layout e palette Android 2.5.0

APK SHA-256: **edb360e11ab6804173f913ae32f5a09283f41fe72da1ff7cd06ea42b1b74db02**, versionCode 25000, 6183561 bytes. Firma, certificato per aggiornamento, integrità del pacchetto e confronto degli asset verificati.

142 controlli automatici superati: 123 su aspetto, migrazione e persistenza delle 100 combinazioni, contrasto di tutte le palette nelle due modalità; 19 sulle regole della libreria. Contrasto minimo del testo 5,41:1 nelle superfici controllate, con verifica separata del testo sui pulsanti. Le vecchie scelte restano compatibili e Classico/Grafite rimane il default.

Verifica nel browser: 180 casi (10 layout x 6 schermate x 3 dimensioni: 320, 390 e 900 px), senza overflow orizzontale. Per ogni schermata e dimensione sono state rilevate dieci strutture distinte tramite misure di area contenuti, spaziature, testata e navigazione; la verifica esclude il colore, quindi non si limita alle locandine della libreria. Altri 20 casi verificano apertura dei dettagli, note preesistenti, larghezza, griglia dei campi e angoli dei dialoghi a 320/900 px. Provati anche modulo di download e opzioni di ricerca. Studio ha una barra laterale anche sul telefono; Focus porta la navigazione in alto.

Palette chiara salvata e ripristinata dopo ricaricamento; scegliere Carta durante cinema riporta alla modalità normale e aggiorna l'interruttore. La barra Android supporta icone scure sui fondi chiari. Le immagini sono sempre senza filtri monocromatici. Le schede sintetiche appartengono soltanto a una copia di anteprima isolata e non sono distribuite.

Compilazione della shell Android completata; nessuna prova su telefono fisico per questa revisione. La suite nativa funzionale completa della 2.3.1 non è stata ripetuta. I siti di terze parti, i selettori Android e il lettore nativo conservano la loro organizzazione; i dieci profili riguardano le schermate e i dialoghi dell'interfaccia VibraVid. Le prove precedenti sotto restano riferite ai rispettivi APK.

---

# Aspetto personalizzabile Android 2.4.0

APK SHA-256: **c661d7888aff64550b693d433bf4f2f8b1cc05bf8add3e1ddd5d17f154109295**, versionCode 24000, 6179401 bytes. Compilazione release, verifica della firma, certificato invariato e confronto degli asset superati.

57 controlli automatici superati: 38 su scelte e palette, 19 sulle regole della libreria. Verificati ripristino delle vecchie preferenze, valori non validi, persistenza e conservazione dei dati nelle 25 combinazioni, importazione senza modificare l'aspetto scelto e contrasto del testo almeno 6,34:1 per tutte le palette, anche in modalità cinema.

Nel browser sono state provate le cinque scelte attraverso i pulsanti delle impostazioni, la navigazione verso la libreria, 15 casi di dimensioni (320/390/768 px), il ripristino dopo ricaricamento, la scheda con nota e valutazione preesistenti, modalità cinema Malva e movimento ridotto. Tutte le opzioni mantengono bersagli di almeno 72 px di altezza; impostazioni e ricerca non producono overflow a 320 px. Opzioni di ricerca e gestione delle 27 fonti ancora accessibili. Le sei schede sintetiche appartengono solo a una copia di anteprima isolata, esclusa dalla distribuzione.

La sincronizzazione del fondo e delle barre Android è stata compilata; non è stata provata su un telefono fisico. La suite nativa completa della 2.3.1 non è stata ripetuta per la 2.4.0. Restano sotto le prove delle revisioni precedenti, con i rispettivi hash; non sono attribuite a questo nuovo APK.

---

# Aggiornamento grafico Android 2.3.2

APK SHA-256: **f530c90b6607e4088956bb5aa245b0ee4ed5e9d92b6c02991c8bff775b321d64**. VersionCode 23003. Correzione limitata ai colori: poster senza grayscale, sfumature carbone/grafite/perla, contrasto minimo verificato 6,94:1 nelle coppie controllate. Compilazione, firma, integrità del pacchetto e corrispondenza degli asset superate; certificato invariato. Anteprima Cerca verificata a 390 x 844 nel browser. Nessuna modifica funzionale; i test nativi completi non sono stati ripetuti per questa correzione estetica. Le prove della 2.3.1 sotto restano riferite al suo APK.

---

# Verifica Android 2.3.1 — affidabilità e restyling

APK: **b98fe77044f210bedc13ee9b82a3a2ae8b80ba28eff71b1d949bb9ec5139411c**. VersionCode 23002, versione 2.3.1-android, 6,175,305 bytes. Android 8/API 26 con WebView aggiornato; target/compile SDK 35. Firma verificata e certificato identico alla 2.3, quindi aggiornamento senza disinstallazione. Sorgenti e asset dell'APK confrontati per hash e per byte. Nessun runner di prova nel codice distribuito.

## Risultati sull'APK finale

**133 controlli automatici superati**: 114 nativi e 19 sulle regole della libreria. Dispositivo dedicato emulator-5582, Android 9/API 28 x86, WebView 66. Non è un telefono fisico.

| Suite | Controlli | Evidenza |
| --- | ---: | --- |
| audit | 32 | `release/audit-smoke-231.txt` |
| picker | 10 | `release/picker-smoke-231.txt` |
| transport | 12 | `release/transport-smoke-231.txt` |
| provider | 20 | `release/provider-smoke-231.txt` |
| adblock | 23 | `release/adblock-smoke-231.txt` |
| android | 9 | `release/android-smoke-231.txt` |
| queue | 8 | `release/queue-smoke-231.txt` |
| model | 19 | `release/model-smoke-231.txt` |

L'audit verifica fonti non valide, Unicode, backup AtomicFile, transazioni interrotte e condivise, annullamento, stagioni zero, risposte tardive, libreria/manuale/note/raccolte, Serata, preferenze, licenze, conservazione delle bozze, conferma di eliminazione e pannelli dopo Salva. Un download che termina mentre modifichi una scheda lascia intatte pagina e note non salvate. Il callback di perdita del renderer apre una spiegazione e il pulsante riavvia la UI leggendo i dati esistenti.

ProviderSmoke interroga realmente StreamingCommunity: 100 risultati nel limite, risoluzione del film a HLS e stagioni/episodi. Poi fixture dei cataloghi guidano il flusso UI completo e scaricano campioni pubblici reali: HLS 6.724.521 bytes, DASH 3.951.608 bytes. Entrambi avanzano con video e audio dal lettore privo di upstream di rete. Nessuna SourceActivity viene aperta nel flusso automatico; omonimi chiedono scelta e URL rinnovati riusano il download.

QueueSmoke scarica realmente un MP4 pubblico Big Buck Bunny (64,657,027 bytes), verifica pausa individuale, ripresa, riproduzione dalla cache, riparazione, rimozione dall'indice, rilascio degli span della sua URI unica e pulizia di errori/posizione. Il primo campione GTV ha risposto 403: il tentativo è conservato in build e non contato come superato. Il campione finale è quello utilizzato nei [test e issue di AndroidX Media](https://github.com/androidx/media/issues/289).

TransportSmoke esercita il trasporto reale con connessioni deterministicamente simulate: Range 206, server che ignora Range, 416 a fine file, intervallo errato, EOF troncato, 403, gzip, redirect con rimozione Authorization, downgrade e ciclo di redirect. Nessuna eccezione TLS o connessione insicura è stata abilitata.

PickerSmoke usa il codice effettivo del risultato del selettore e ContentResolver, con URI di file privati di prova: import/export UTF-8, JSON errato, file assente, cancellazione, permesso non persistente, risultato dopo ricreazione, importazione fonti atomica e rilascio token. Verifica anche il messaggio del lettore per file locale non accessibile e lo spegnimento del flag schermo.

AdBlockSmoke verifica domini, eccezioni, filtri prima del rilevamento, banner dinamici e cambi di attributo sotto CSP restrittiva, HLS consentito, popup negati, assenza del bridge remoto, preferenza per fonte e perdita del renderer gestita. La lista rigenerata dallo snapshot mantiene gli stessi dati: 183.307 blocchi, 180 eccezioni di dominio, 19 eccezioni esatte, 3 priorità; 685 regole non supportate omesse.

## Grafica e controllo statico

49 valori cromatici verificati: tutti neutri. Contrasto testo bianco/pannello **15,63:1**, testo secondario/superficie **7,85:1**, pulsante principale **17,45:1**. Due modalità scure, font locali, icona/splash, barre, browser, AD, dialoghi e lettore coerenti. Anteprime controllate a 320, 390×844 e 768×1024: Cerca con opzioni, Libreria vuota/popolata e modifica, Serata, Download e Impostazioni. Nessun overflow orizzontale o errore JS nell'anteprima. PNG in release sono catture del browser, non del telefono.

**Lint: 0 errori, 32 warning.** Gli errori di compatibilità, opt-in Media3 e percorso SDK sono stati corretti. I warning residui riguardano versioni/target deliberatamente mantenuti, JavaScript necessario (bridge solo su documenti locali), testi italiani, singleton con application context, raccomandazione PiP/data extraction e una cartella di risorse v26 vuota. Non è stato applicato un silenziamento globale. Dettagli in `release/lint-231.txt`.

## Limiti della verifica

Il tentativo di usare il selettore Android reale nell'emulatore ha bloccato System UI/DocumentsUI. Non viene dichiarato superato: è stato verificato separatamente il codice di restituzione/IO, ma la selezione e i permessi concessi da un provider reale richiedono ancora un telefono. La perdita del renderer è simulata attraverso il callback reale; non è un test di esaurimento fisico della RAM.

Non sono stati scaricati film commerciali completi da ogni fonte o usato un telefono fisico. Restano sei adattatori e 27 indirizzi; nessun nuovo porting. Le verifiche reali finali del provider riguardano StreamingCommunity. Eurostreaming aveva risposto 403 nell'audit precedente; servizi, indirizzi, accesso e disponibilità possono cambiare. Il manifest effettivo era stato verificato nella 2.3 e quel log è mantenuto come evidenza precedente, non attribuito automaticamente al nuovo APK.

Decoder/PiP fisici, notifiche Android 13+, restrizioni Android 15 e risparmio energetico del produttore non sono certificati da questo emulatore. La ripresa della coda dopo una pausa globale è provata attraverso il lifecycle effettivo; non è una prova di sei ore su Android 15. DRM/dirette rimangono esclusi e HLS/DASH rimangono nello spazio privato dell'app.

La revisione dettagliata delle funzioni e delle correzioni è in AUDIT.md. La versione Windows non è stata modificata; hash verificato: b81f8d922751a1f6a4f2cedf7884f0d0fd7b5910d10aa8ada1c083215b52e372.
