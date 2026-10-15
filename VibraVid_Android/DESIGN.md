# VibraVid 3.1

Brief: un catalogo cinematografico scuro, vicino allo schizzo del proprietario. Locandine e titoli hanno priorità. Restano tutte le funzioni, compresi Serata, fonti, importazione e palette.

Token: fondo #080808, carbone #141414, superficie #232323, testo #ffffff, testo secondario #b3b3b3, azione #e50914. Inter per interfaccia e informazioni, Bebas Neue solo per il marchio esistente. Titoli sezione 32/40, generi 20/26, testo 14/21. Allineamento a sinistra, margini mobili 20px.

Home:
    marchio                 profilo
    [ ricerca                 > ]
    Film                    Tutti
      Thriller [poster poster ...]
      Azione   [poster poster ...]
      ...
    Serie TV                Tutti
      Thriller [poster poster ...]
      ...
    Home      Download      Libreria

Download: locandina a sinistra, nome/stato/progresso e azioni a destra. Libreria: ricerca, filtro tipo a chip, altri filtri raccolti; griglia a due colonne mobile. Scheda: trailer 16:9, azioni sotto il video, trama, informazioni, cast e stagioni, simili. Aspetto: due selettori, una piccola anteprima.

Critica prima del codice: evitare statistiche enormi come apertura di ogni pagina e venti campioni colore in verticale. Nessun numero di velocità o avanzamento simulato. Il gradiente è richiesto esplicitamente: movimento molto lento e neutro, disattivato con Riduci movimento. Il player usa il bordo del display per il video, safe-area solo sui controlli e pannelli; la pausa non deve mantenere l'overlay visibile per sempre. Le palette esistenti restano disponibili.

Verifica: ogni punto della richiesta ha un controllo funzionale; confronto testuale Generali, ricerche fuori catalogo iniziale, risposte fuori ordine, errore trailer, chiusura e cambio scheda, progressi reali, focus durante aggiornamento download, tutti gli schermi a 360/390/768/1440 e orizzontale.
