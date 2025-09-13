# 📱 MyMifareWriter

Un’app Android sviluppata in **Android Studio** per la gestione di schede **MIFARE Classic**.  
L’app permette di scrivere, ripristinare e trasferire i dati su tessere contactless, utilizzando file **JSON** come input e le **key** opportune per l’accesso ai blocchi.

---

## ✨ Funzionalità

- 📂 **Import JSON**: legge file JSON che descrivono i dati da scrivere sulle tessere  
- ✍️ **Scrittura su blocchi**: scrive i dati desiderati nei blocchi della tessera utilizzando le chiavi specificate  
- 🔄 **Restore**: ripristina i contenuti di una tessera da un backup  
- 📤 **Trasfer**: trasferisce i dati tra due tessere compatibili  

---

## 🛠️ Requisiti

- Android **5.0+** (Lollipop o successivi)  
- Dispositivo con **NFC abilitato**  
- Autorizzazioni per l’uso dell’NFC  

---

## 📂 Formato del file JSON

Il file JSON contiene le informazioni necessarie per scrivere i dati sui blocchi della tessera.  

### Struttura generale

- `delay` (opzionale): millisecondi di attesa tra le operazioni  
- `debug` (opzionale): "on"/"off" per abilitare messaggi di debug  
- `matchTable`: lista di configurazioni con le operazioni da eseguire  

Ogni elemento in `matchTable` include:  
- `code`: identificativo tessera in esadecimale (max 32 char)  
- `key`: chiave del settore in esadecimale (12 char)  
- `keyType`: "A" o "B"  
- `description`: descrizione libera  
- `writes`: lista di operazioni di scrittura o comandi speciali  

### Regole

- Ogni `writes` deve avere **o `data` o `command`**, non entrambi  
- `block`: numero di blocco (0–63)  
- `key` e `keyType`: obbligatori per autenticarsi  
- `data`: 16 byte = 32 caratteri esadecimali  
- `command`: solo `R`, `T` o `RT`  

---

## 📌 Esempio JSON

```json
{
  "delay": 100,
  "debug": "on",
  "matchTable": [
    {
      "code": "11223344AABB",
      "key": "A0A1A2A3A4A5",
      "keyType": "A",
      "description": "Tessera demo",
      "writes": [
        {
          "block": 4,
          "key": "FFFFFFFFFFFF",
          "keyType": "B",
          "command": "R",
          "commento": "Ripristino del blocco 4"
        },
        {
          "block": 5,
          "key": "FFFFFFFFFFFF",
          "keyType": "A",
          "data": "00112233445566778899AABBCCDDEEFF",
          "commento": "Scrittura dati demo nel blocco 5"
        }
      ]
    }
  ]
}
```

---

## 🚀 Installazione (compilazione manuale)

1. Clona il repository:  
   ```bash
   git clone https://github.com/averinoing/MyMifareWriter.git
   ```
2. Apri il progetto in **Android Studio**  
3. Connetti un dispositivo Android con NFC oppure usa un emulatore compatibile  
4. Compila e avvia l’app  

---

## ⚠️ Note importanti

- Non condividere i file delle **key** in repository pubblici  
- Questa app è destinata a scopi didattici e sperimentali. L’uso improprio di tessere e chiavi potrebbe violare norme o diritti di terzi  

---

## 📜 Licenza

Scegli una licenza (MIT, Apache 2.0, GPL, ecc.) e aggiungi un file LICENSE al progetto.
