# VibraVid 3.1.0 — interfaccia locale e player Android

Aggiornamento locale del 15 ottobre 2026. Installare l’APK sopra la versione esistente, senza disinstallare. Package it.vibravid.studio, versionCode 31000, firma originale; formato della libreria versione 1 conservato. Backup della 3.0 in release/precedente-3.0.0. La UI aggiornata è negli asset web dell’app Android; i sorgenti completi includono anche questi file. Windows non è stato modificato. I sorgenti di questa revisione sono pubblicati nel repository; la creazione di una nuova release APK è separata dal push.

## Verifica degli 11 punti

1. **Home per generi — applicato.** Pagina verticale unica con titolo Film, caroselli Thriller, Azione, Commedia, Horror, Drammatico, Fantascienza e altri generi disponibili; poi titolo Serie TV e i relativi generi. I selettori in alto scorrono alle due sezioni; Tutti apre la categoria. Le righe contengono titoli reali del catalogo, non segnaposto.
2. **Ricerca Serie TV — corretta.** La pagina di categoria filtrava soltanto i 24 titoli locali. Ora il suo modulo Cerca/Invio usa lo stesso motore completo della Home, con tipo serie e tutte le fonti. Tutti include le serie. La Home azzera i filtri rimasti da una ricerca precedente. Il testo digitato continua anche a filtrare i titoli visibili; Cerca/Invio interroga le fonti oltre il catalogo iniziale.
3. **Gradiente — applicato.** Sfondo nero/grigio con movimento lento su tutte le pagine web e superfici coerenti nelle schede. Riduci movimento e la preferenza del sistema fermano l’animazione. Le palette chiare esistenti conservano un gradiente leggibile coerente con la palette scelta.
4. **Navigazione — applicato.** Rimosso Cerca dalla barra inferiore; rimangono Home, Download e Libreria con la stessa larghezza. Ricerca e funzioni delle fonti restano raggiungibili dalla Home e dalle schede.
5. **Download — ridisegnato.** Card con locandina quando disponibile, ripiego grafico, stato, progresso animato, byte, velocità misurata e tempo residuo quando calcolabile. Filtri Tutti/In corso/Completati/Errori; pausa, ripresa, annullamento confermato, riparazione e riproduzione offline. Stato vuoto con accesso alla Home. I download Android precedenti mantengono le azioni supportate dal relativo motore: il vecchio DownloadManager non offre pausa. Nessuna velocità o durata viene inventata per una coda o un totale sconosciuto.
6. **Libreria — ridisegnata.** Griglia di locandine, ricerca interna, filtri Film/Serie TV, ordinamento recenti/A–Z/valutazione/durata e filtri avanzati comprimibili per stato/raccolta. Avanzamento ricavato dalla riproduzione effettiva dei video associati. Per le serie si riferisce all’ultimo episodio visto; i vecchi download privi di associazione non generano percentuali retroattive. Serata, inserimento manuale, raccolte, note e import/export conservati.
7. **Player orizzontale — corretto nel codice.** Finestra immersiva tramite le API Android compatibili, video esteso al display, cutout e safe-area applicati ai controlli. Controlli più compatti in orizzontale, scomparsa dopo 3,5 secondi anche in pausa, ricomparsa al tocco; blocco, audio/sottotitoli, velocità, riempimento, galleria e mini player conservati. Il trailer web gestisce fullscreen e ripristino della pagina. Verificati comportamento dei controlli e configurazione finestra su host; la verifica sul telefono fisico resta da fare.
8. **Impostazioni — applicato.** Generali è identico al template precedente, verificato anche con hash. Aspetto usa due menu con tutte le 10 disposizioni e 10 palette, più una sola anteprima piccola e applicazione immediata.
9. **Trailer — applicato.** Video nella parte superiore della scheda, avvio silenzioso con controlli, audio attivabile e fullscreen. Copertina se il trailer non esiste o non si carica. Chiusura, cambio scheda e cambio pagina fermano e scaricano il video dalla memoria. I trailer Apple disponibili vengono mantenuti; TVmaze non fornisce un trailer video, quindi le serie senza un URL di trailer usano la copertina. Non vengono usati episodi completi o video non verificati al posto del trailer.
10. **Scheda completa — applicato.** Trama, anno, durata, generi, voto pubblico quando disponibile, valutazione personale distinta, regia, cast, simili per genere; per le serie stagioni/episodi con caricamento dell’elenco all’apertura. Le disponibilità per il download rimangono verificate dal motore delle fonti. Apple/TVmaze/Wikidata sono fonti di metadati; campi assenti sono dichiarati, senza inventare cast o voti.
11. **Audit e bug — eseguito.** Controllo di percorsi, azioni, icone, persistenza/importazione, risposte asincrone, caricamento media, layout, lingue e codice Android. Elenco delle correzioni e risultati sotto.

## Bug e casi limite corretti

- La ricerca di categoria era limitata ai 24 titoli caricati: collegata al motore completo e verificata con un titolo esterno al catalogo iniziale.
- Filtri di tipo/fonte rimasti da una ricerca potevano escludere risultati nella Home: ripristinato l’ambito completo per la ricerca Home.
- Il player in pausa forzava la ricomparsa dell’overlay a ogni aggiornamento: eliminata la riapertura ciclica e aggiunta scomparsa temporizzata anche in pausa.
- Il layout dei controlli considerava compatte solo finestre sotto 330dp di altezza: ora riconosce direttamente l’orientamento orizzontale.
- Fullscreen e inset usavano soltanto flag Android precedenti: adottate API compatibili per barre transitorie, cutout e ripristino dell’area originale dopo il trailer.
- I parser azzeravano i trailer e la policy web non permetteva i media: mantenuti gli URL di anteprima, consentiti i video HTTPS e aggiunto il fullscreen WebView.
- Il modello eliminava i nuovi metadati durante salvataggio/importazione: aggiunta conservazione validata di trailer, cast, regia, ID e voto pubblico, lasciando separati note e voto personale.
- L’aggiornamento Download ricreava l’intera lista ogni quattro secondi, perdendo focus e continuità: aggiornamento per identificatore della card, azioni conservate e risposte di pagine precedenti ignorate.
- Timer di velocità e ETA gestiscono pausa, contatori azzerati, rete ferma, totali sconosciuti e ritorno dopo un intervallo lungo senza numeri falsi.
- Risposte dei dettagli arrivate tardi potevano cambiare il contenuto corrente: annullamento della richiesta e verifica della revisione della scheda.
- Un salvataggio completato dopo la chiusura poteva riaprire la vecchia scheda: aggiornamento dei soli pulsanti se la scheda è ancora la stessa. Il trailer non riparte dall’inizio quando salvi.
- Azioni native ripetute durante una richiesta: pulsante disabilitato fino all’esito e riabilitato in caso di errore.
- Casi limite dei nuovi metadati: cache distinta per ID, orari futuri della cache rifiutati, cast dei lungometraggi riconosciuto, immagini del cast non valide rimosse. Verificati con test dedicati o audit.
- Avanzamento e metadati pubblici vengono trattati separatamente dalle scelte manuali Visto/Da vedere e dalle valutazioni personali.

## Verifiche effettive

- 38 test Java Android locali con Robolectric, API 35: tutti passati.
- 304 controlli di regressione della UI e 73 controlli mirati 3.1 in Chromium: passati, nessun errore JavaScript non gestito.
- 195 controlli UI delle 21 lingue; 15,587 controlli dei cataloghi locali.
- 22 controlli del modello, 10 controlli delle nuove regole e 123 dell’aspetto.
- Lint Android: 0 errori, 29 avvisi. Non viene dichiarato codice privo di qualunque bug.
- Firma APK, integrità ZIP, corrispondenza sorgenti/asset incorporati e assenza dei test nell’APK: verificati.
- Template Generali invariato: SHA-256 d8b42b3f8755f9d30a0aac020009d79e163800d50c3a78f8e2175fa662376e48.

Le prove del browser usano risposte Android e trasferimenti controllati. Il video di prova è un MP4 codificato; i test nativi eseguono le classi reali in Robolectric. Nessun telefono o AVD era collegato. Non sono stati misurati FPS sul telefono né riprovati download provider, conversioni HLS/DASH complete o installazione su Android 17 beta. Trailer, cast e metadati dipendono dai servizi esterni; la scheda rimane utilizzabile quando mancano. Le nuove traduzioni sono bozze automatiche e non tutte revisionate da madrelingua.

## Identità della consegna

- APK: 8,450,073 byte.
- SHA-256: b0faaefb43355f2a98f249282ae60a2e074924d09dfebcfc6bb105f6769dc20a.
- Certificato SHA-256: 46eb37b17e112eb855fa340f22d96aca07c05c4fe530e370f3a532b9eba5402a.
- Font: Inter per UI/controlli e Bebas Neue per il marchio, entrambi inclusi con licenza OFL. La palette iniziale mantiene nero, grigi, bianco e rosso #E50914; non viene dichiarato l’uso di Netflix Sans.
- Metadati iniziali: 86 film Apple Movies e 100 serie TVmaze, generi reali. Cache distinta dal file della libreria.
- Rapporti leggibili: VALIDATION.json, RELEASE_CHECKS.json, VERIFICA-UI.json e VERIFICA-NUOVE-FUNZIONI.json.
