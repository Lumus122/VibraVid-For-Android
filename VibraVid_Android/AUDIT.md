# Audit 3.1.0

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

- 38 test Java Android locali con Robolectric, API 35: tutti passati.
- 304 controlli di regressione della UI e 73 controlli mirati 3.1 in Chromium: passati, nessun errore JavaScript non gestito.
- 195 controlli UI delle 21 lingue; 15,587 controlli dei cataloghi locali.
- 22 controlli del modello, 10 controlli delle nuove regole e 123 dell’aspetto.
- Lint Android: 0 errori, 29 avvisi. Non viene dichiarato codice privo di qualunque bug.
- Firma APK, integrità ZIP, corrispondenza sorgenti/asset incorporati e assenza dei test nell’APK: verificati.
- Template Generali invariato: SHA-256 d8b42b3f8755f9d30a0aac020009d79e163800d50c3a78f8e2175fa662376e48.

Le prove del browser usano risposte Android e trasferimenti controllati. Il video di prova è un MP4 codificato; i test nativi eseguono le classi reali in Robolectric. Nessun telefono o AVD era collegato. Non sono stati misurati FPS sul telefono né riprovati download provider, conversioni HLS/DASH complete o installazione su Android 17 beta. Trailer, cast e metadati dipendono dai servizi esterni; la scheda rimane utilizzabile quando mancano. Le nuove traduzioni sono bozze automatiche e non tutte revisionate da madrelingua.
