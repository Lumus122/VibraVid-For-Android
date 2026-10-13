# Contribuire alla versione Android

Il progetto Android è in `VibraVid_Android/`. Per i problemi desktop originali consulta il progetto AstraeLabs/VibraVid; usa le Issues di questo fork per l’app Android.

Prima di proporre una modifica descrivi il problema, i passaggi per riprodurlo e l’effetto atteso. Mantieni compatibilità con la libreria esistente, permessi circoscritti, licenze e crediti delle risorse. Non aggiungere credenziali, chiavi di firma, cache, video o dati personali.

Per una pull request indica cosa cambia, perché, quali controlli hai eseguito e su quale versione Android. Esegui almeno i controlli del modello/aspetto per le modifiche pertinenti; per codice nativo compila e usa lint. Per download/lettore indica le prove effettivamente eseguite, senza attribuire test di un’altra build al nuovo APK. Non aggiungere nuove funzioni quando la modifica riguarda soltanto un bug.

Consulta `docs/BUILD_ANDROID.md` per l’ambiente di sviluppo e `VibraVid_Android/QA.md` per i limiti della verifica disponibile.
