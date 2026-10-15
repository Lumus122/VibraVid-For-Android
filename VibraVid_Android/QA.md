# Verifiche 3.1.0

- 38 test Java Android locali con Robolectric, API 35: tutti passati.
- 304 controlli di regressione della UI e 73 controlli mirati 3.1 in Chromium: passati, nessun errore JavaScript non gestito.
- 195 controlli UI delle 21 lingue; 15,587 controlli dei cataloghi locali.
- 22 controlli del modello, 10 controlli delle nuove regole e 123 dell’aspetto.
- Lint Android: 0 errori, 29 avvisi. Non viene dichiarato codice privo di qualunque bug.
- Firma APK, integrità ZIP, corrispondenza sorgenti/asset incorporati e assenza dei test nell’APK: verificati.
- Template Generali invariato: SHA-256 d8b42b3f8755f9d30a0aac020009d79e163800d50c3a78f8e2175fa662376e48.

Le prove del browser usano risposte Android e trasferimenti controllati. Il video di prova è un MP4 codificato; i test nativi eseguono le classi reali in Robolectric. Nessun telefono o AVD era collegato. Non sono stati misurati FPS sul telefono né riprovati download provider, conversioni HLS/DASH complete o installazione su Android 17 beta. Trailer, cast e metadati dipendono dai servizi esterni; la scheda rimane utilizzabile quando mancano. Le nuove traduzioni sono bozze automatiche e non tutte revisionate da madrelingua.

Gli script sono in tools/. Il test Generali usa tools/general-settings.sha256. I test con trasporto controllato non equivalgono a una prova end-to-end delle fonti sul telefono.
