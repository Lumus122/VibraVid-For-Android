# Changelog Android

## 3.1.0 — 2026-10-15

- Home verticale per generi Film/Serie TV e ricerca estesa alle fonti anche dalle categorie.
- Gradiente animato, tre destinazioni in basso, nuove pagine Download e Libreria con progressi reali.
- Trailer con ripiego sulla copertina, metadati/cast/simili e stagioni/episodi.
- Aspetto compatto con tutte le opzioni; Generali invariato.
- Player immersivo, controlli compatti in orizzontale, safe-area e auto-hide anche in pausa.
- Corretti polling, focus, risposte tardive, salvataggi asincroni e conservazione dei metadati.
- Verifiche locali: 38 test Android su host, 377 controlli UI, 195 controlli UI lingue; lint senza errori. Test sul telefono fisico ancora da fare.
- Include gli aggiornamenti locali 2.9 e 3.0. Release APK 3.1.0 pubblicata; tutti e cinque gli allegati riscaricati e verificati tramite SHA-256.

## 2.5.1 — 2026-10-13

- Corretti callback tardivi, salvataggi/esportazione e richieste dopo ricaricamento della UI.
- Migliorati importazione JSON, permessi dei file, URL HTTPS e gestione degli errori.
- Corretti ripresa del lettore, preferenza Wi-Fi, liste di episodi grandi e controlli del trasporto HTTP.
- Corretti navigazione su schermi piccoli e campioni delle palette sotto CSP.
- Corretta la proposta Serata per librerie grandi e il contatore quando tutti i segmenti sono già nella cache.
- Nessuna nuova funzione di prodotto; dieci layout/palette e immagini a colori conservati.

Nota di verifica: firma e sorgenti confrontati; suite native provate su build incrementali. L’ultima regressione della cache è stata interrotta e resta non verificata. Nessuna prova su telefono fisico.

## 2.5.0

Dieci layout per l’intera UI e dieci palette indipendenti, incluse palette chiare; compatibilità delle scelte precedenti e locandine a colori.

Lo storico dettagliato delle revisioni precedenti è in `VibraVid_Android/QA.md` e `VibraVid_Android/AUDIT.md`.

