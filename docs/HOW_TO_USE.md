# Come usare VibraVid su Android

## Cercare e scaricare

1. Apri **Cerca**, inserisci il titolo e premi **Cerca e scarica**.
2. Se il nome corrisponde esattamente a un solo film/anno, il download parte. Per omonimi o corrispondenze parziali scegli **Scarica** sul risultato desiderato.
3. Per serie e anime apri il risultato, seleziona stagione ed episodio, poi avvia il download.
4. Espandi **Fonti e qualità** per scegliere fonte, categoria e qualità massima. Il limite di qualità non garantisce che la fonte offra proprio quella risoluzione.
5. Controlla il trasferimento in **Download**. Una scheda nella Libreria, da sola, non indica che il video sia scaricato.

Se una fonte non risponde, l’app mostra l’errore e conserva i risultati delle altre. Puoi riprovare o scegliere un’altra fonte. Per risultati equivalenti raggruppati vengono tentate le alternative disponibili quando la preparazione fallisce.

## Download e video offline

In **Download** puoi mettere in pausa, riprendere, eliminare o usare **Verifica e ripara**. Il lettore **Guarda offline** usa i dati già presenti sul dispositivo. La riparazione richiede rete e un collegamento ancora valido; se il collegamento è scaduto, ripeti la ricerca del video.

HLS e DASH rimangono manifest e segmenti nella cache privata, non un singolo file MP4 esportabile. Anche i video progressivi vengono conservati nella cache offline. Rimuovere un download libera i relativi dati; disinstallare l’app elimina download e libreria privata.

Il lettore conserva la posizione e permette sottotitoli e Picture-in-Picture quando il dispositivo lo supporta. I video del dispositivo vengono scelti con il selettore Android: restano nel loro percorso originale. Se sposti un file o il suo permesso scade, selezionalo nuovamente.

## Libreria e Serata

**Cerca informazioni** cerca dati e poster, separatamente dalle fonti video. Salva un titolo, assegna stato e valutazione, aggiungi note/durata e organizzalo nelle raccolte. Filtri e ordinamento aiutano a ritrovarlo. **Serata** propone combinazioni in base al tempo disponibile e alle preferenze selezionate.

L’importazione/esportazione JSON permette il backup della libreria e la compatibilità dei dati con la versione Windows. Il JSON non contiene i byte dei video scaricati: non è un backup dei download.

## Aspetto, rete e fonti

In **Impostazioni → Aspetto** scegli fra dieci layout e dieci palette, indipendenti e persistenti. Le locandine mantengono i colori originali. Sono presenti modalità cinema e movimento ridotto.

**Solo Wi-Fi** applica il requisito di rete non a consumo. Se il download attende, controlla la connessione e questa preferenza. Android può limitare il servizio in background: riaprire l’app permette di riprendere la coda, mantenendo le pause individuali.

**Gestisci le fonti** permette di modificare gli indirizzi o importare `domains.json` del PC. Sei servizi hanno adattatori Android automatici; gli altri indirizzi rimangono negli strumenti aggiuntivi. L’app non include account, chiavi API o licenze offline dei servizi.

## Browser e blocco pubblicità

Il browser delle fonti è uno strumento aggiuntivo: la ricerca automatica non richiede di aprire il sito. **AD** è attivo per default; mostra blocchi e popup e può essere disattivato per la sola fonte corrente. La modifica ricarica la pagina e resta salvata.

Il filtro usa regole di dominio locali e regole cosmetiche: non è un’estensione Chrome completa e non blocca ogni tipo di annuncio. Se il player non parte, prova a disattivare AD per quella fonte.

## Problemi comuni

| Problema | Cosa controllare |
| --- | --- |
| Nessun risultato o errore della fonte | Connessione, indirizzo della fonte e disponibilità del servizio; prova un’altra fonte. |
| Download fermo in attesa | Preferenza Solo Wi-Fi, rete non a consumo, pausa individuale e spazio libero. |
| Errore 401/403 | Il servizio può aver rifiutato l’accesso o il collegamento può essere scaduto; ripeti la ricerca. |
| Video segnalato incompleto | Usa Verifica e ripara con rete disponibile. |
| File del dispositivo non accessibile | Selezionalo nuovamente dal selettore Android. |
| L’aggiornamento non si installa | APK con firma diversa o versione precedente: i sorgenti compilati con una nuova chiave non aggiornano l’APK ufficiale. |

Per problemi Android apri una Issue nel fork, riportando versione dell’app, Android/WebView, passaggi e testo dell’errore. Non includere password, cookie, token o URL firmati completi.
