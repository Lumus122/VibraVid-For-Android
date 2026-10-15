

<div align="center">
  <div style="max-width: 280px;">
    <video src="https://github.com/user-attachments/assets/995caa99-c0c7-4761-913e-fb237d67507a" controls style="width: 100%; border-radius: 10px;"></video>
  </div>
</div>


# VibraVid-For-Android

**Android 3.1.0**: Home per generi, ricerca coerente, Download e Libreria ridisegnati, trailer e schede complete, Aspetto compatto e player immersivo. [Modifiche e verifica degli 11 punti](VibraVid_Android/README.md).

La **release APK 3.1.0 è pubblicata** con la stessa firma delle versioni precedenti. Tutti e cinque gli allegati sono stati riscaricati da GitHub e verificati tramite SHA-256.







Versione Android autonoma di **VibraVid Studio**, basata sul progetto [AstraeLabs/VibraVid](https://github.com/AstraeLabs/VibraVid). Ricerca, download e libreria funzionano sul telefono: non serve un PC acceso.

[**Scarica l’APK 3.1.0**](https://github.com/Lumus122/VibraVid-For-Android/releases/download/v3.1.0-android/VibraVid_Studio_Android.apk) · [Releases](https://github.com/Lumus122/VibraVid-For-Android/releases) · [Guida d’uso](docs/HOW_TO_USE.md) · [Compilazione](docs/BUILD_ANDROID.md)

## Funzionalità

- Ricerca e download diretto dall’app; scelta del risultato quando esistono titoli omonimi.
- Film, stagioni ed episodi con sei adattatori automatici e 27 indirizzi configurabili.
- Download HLS, DASH e video progressivi; pausa, ripresa, riparazione e riproduzione offline.
- Libreria fino a 2.000 titoli, raccolte, note, valutazioni, filtri e proposte Serata.
- Browser delle fonti con blocco pubblicità, immagini a colori, dieci layout e dieci palette.
- Importazione/esportazione della libreria, video del dispositivo, ripresa della posizione e Picture-in-Picture.

## Installazione

1. Serve **Android 8.0 o successivo** e Android System WebView aggiornato.
2. Scarica `VibraVid_Studio_Android.apk` dalla [release](https://github.com/Lumus122/VibraVid-For-Android/releases/tag/v3.1.0-android).
3. Apri il file sul telefono e, se richiesto, consenti al browser o al gestore file di installare l’app.
4. Se hai già una versione firmata con la stessa chiave, installa l’aggiornamento senza disinstallare: libreria e download vengono conservati.

L’APK è unico per tutte le architetture supportate. Non occorrono Python, Termux, Docker o un server sul PC.

## Primo utilizzo

Apri **Cerca**, scrivi il titolo e premi **Cerca e scarica**. Una corrispondenza esatta e univoca avvia il download; negli altri casi scegli il risultato. Per serie e anime scegli stagione ed episodio. In **Download** trovi avanzamento e **Guarda offline** quando il video è completo.

**Cerca informazioni** recupera soltanto metadati e locandine: salvare una scheda nella Libreria non scarica il video. In **Impostazioni → Aspetto** puoi scegliere separatamente layout e palette; il default resta Classico/Grafite.

La [guida completa](docs/HOW_TO_USE.md) spiega fonti, Wi-Fi, adblock, backup e risoluzione dei problemi.

## Compatibilità e stato delle verifiche

Gli adattatori Android automatici sono StreamingCommunity, Streamingcommunity 2, AnimeUnity, AnimeWorld, Eurostreaming e RaiPlay. I 27 indirizzi conservati non equivalgono a 27 adattatori automatici. Disponibilità, protocolli e accesso delle fonti possono cambiare; DRM e dirette non sono supportati dal download offline.

HLS/DASH sono conservati come manifest e tracce nella cache privata dell’app, senza conversione in un MP4 esportabile. Disinstallare l’app elimina libreria e download privati; prima di farlo esporta la libreria.

La release 3.1.0 include le modifiche riportate sopra e conserva le funzionalità precedenti. Verifiche locali superate: 38 test Android su host, 377 controlli UI, 195 controlli UI delle lingue e 15.587 controlli dei cataloghi linguistici. Firma e integrità dei pacchetti pubblicati verificate. La prova su telefono fisico resta da completare. Risultati e limiti in [QA.md](VibraVid_Android/QA.md).

## Codice e compilazione

Il progetto Android completo è in [`VibraVid_Android/`](VibraVid_Android/): Java, UI HTML/CSS/JavaScript, risorse, filtri, configurazione Gradle e strumenti di prova. Per creare il tuo APK segui [BUILD_ANDROID.md](docs/BUILD_ANDROID.md). La chiave privata usata per l’APK pubblicato è esclusa dal repository.

Il codice desktop originale e la storia Git sono conservati dal fork; il [README originale](docs/UPSTREAM_README.md) descrive quella versione. La documentazione Android si riferisce esclusivamente al progetto in `VibraVid_Android/`.

## Contributi e segnalazioni

Consulta [CONTRIBUTING.md](CONTRIBUTING.md) e usa le [Issues](https://github.com/Lumus122/VibraVid-For-Android/issues) di questo fork per i problemi Android. Indica versione dell’app, versione Android/WebView, passaggi e messaggio d’errore. Le modifiche sono riassunte in [CHANGELOG.md](CHANGELOG.md).

## Crediti e licenze

Progetto originale: **AstraeLabs e collaboratori di VibraVid**. Questo fork ospita un adattamento Android indipendente e non è una release ufficiale di AstraeLabs. Licenza [GNU GPL v3](LICENSE); le dipendenze e gli asset conservano le proprie licenze, elencate nelle [notifiche di terze parti](VibraVid_Android/app/src/main/assets/THIRD-PARTY-NOTICES.txt). Nessun film o credenziale delle fonti è incluso nel repository.

