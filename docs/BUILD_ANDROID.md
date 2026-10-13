# Compilare l’app Android

## Progetto e requisiti

Apri `VibraVid_Android/` in Android Studio oppure compila con Gradle. Il progetto usa Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17 o successivo, compile/target SDK 35 e min SDK 26 (Android 8). La build pubblicata è stata creata con JDK 21 e build-tools 35.0.0. Python 3 serve agli strumenti locali; Node.js serve ai controlli della UI e del modello.

Il codice Android è Java con UI HTML/CSS/JavaScript incorporata. Non richiede il runtime Python sul telefono. SDK, JDK e Gradle non sono inclusi nel repository.

## Android Studio o Gradle standard

1. Importa `VibraVid_Android/`, configura JDK e SDK e installa la piattaforma Android 35.
2. Usa Gradle 8.13 come distribuzione locale, se Android Studio richiede di specificarla.
3. Compila **Build APK(s)** per una build debug, oppure **Generate Signed Bundle / APK** per una release firmata con una tua chiave.

Da una shell con Gradle 8.13, JDK e Android SDK configurati:

```sh
cd VibraVid_Android
gradle --no-daemon assembleDebug
```

L’APK debug viene generato in `app/build/outputs/apk/debug/`. Per una release non ancora firmata usa `gradle --no-daemon assembleRelease`. La procedura Android Studio permette di firmarla.

## Script Windows usato per la release

Lo script `tools/build_apk.py` richiede questa struttura dentro `VibraVid_Android/`:

```text
toolchain/
  gradle-8.13/bin/gradle.bat
  sdk/
    platforms/android-35/android.jar
    build-tools/35.0.0/
```

Imposta `JAVA_HOME` sul tuo JDK; lo script locale usa come fallback un percorso JDK Windows specifico. Poi esegui:

```sh
python tools/build_apk.py
```

Lo script compila, allinea e firma l’APK, verifica la firma e produce `release/VibraVid_Studio_Android.apk`, il relativo SHA-256 e `BUILD_MANIFEST.json`. Se non esiste una chiave locale, ne genera una in `.signing/`. Conserva privatamente questa cartella per gli aggiornamenti successivi e non pubblicarla.

La chiave della release distribuita non è inclusa. Un APK compilato con una chiave nuova non può aggiornare quello già installato con la chiave della release. Esporta la libreria prima di un’eventuale disinstallazione; i download privati non sono inclusi nell’esportazione.

## Verifiche disponibili

```sh
node tools/verify_model.cjs
node tools/verify_appearance.cjs
gradle --no-daemon lintRelease
```

I runner Java in `tools/` sono test strumentali separati dall’app. Gli script nativi locali sono configurati esclusivamente per il dispositivo di prova `emulator-5582`: adattali intenzionalmente al tuo ambiente, evitando dispositivi con dati personali. `build_smoke.py` genera l’APK di test; `run_regressions.py` conserva log e hash del pacchetto effettivamente provato.

La documentazione delle prove già svolte è in `VibraVid_Android/QA.md`; non equivale a una certificazione su ogni versione Android o telefono. L’ultima prova della cache sulla 2.5.1 è stata interrotta.

## Dati e licenze

La lista originale AdGuard DNS è in `tools/filter_snapshot/`. `python tools/refresh_adblock.py --snapshot` rigenera il sottoinsieme senza rete. Font, licenze e notifiche delle dipendenze sono incorporati in `app/src/main/assets/`.
