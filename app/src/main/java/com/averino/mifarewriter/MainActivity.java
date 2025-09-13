package com.averino.mifarewriter;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;

import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import android.os.Environment;

import androidx.appcompat.app.AlertDialog;
import java.util.Arrays;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {
    private NfcAdapter nfcAdapter;
    private TextView textView;
    private TextView tagCountTextView;

    private List<CodeEntry> codeEntries;
    private  boolean deepdebug;
    private  int attesa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView versionText = findViewById(R.id.versionText);
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            versionText.setText("Author: Averino v" + versionName);
        } catch (Exception e) {
            versionText.setText("v?.?");
        }
        tagCountTextView = findViewById(R.id.tagCountTextView);
        textView = findViewById(R.id.textView);
        tagCountTextView.setOnLongClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/json"); // <- SOLO file JSON
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 1001);
            return true;
        });
        textView.setText("Waiting for MIFARE Classic...");
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        loadConfig();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
            prefs.edit().putString("config_uri", uri.toString()).apply();

            Toast.makeText(this, "File config selezionato!", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadConfig() {
        boolean usingExternal=false;
        codeEntries = new ArrayList<>();
        InputStream is = null;
        try {
/*
            //File externalConfig = new File(getExternalFilesDir(null), "config.json");
            File externalConfig = getMediaConfigFile();

            InputStream is;
            if (externalConfig.exists()) {
                is = new FileInputStream(externalConfig);
                Toast.makeText(this, "Config Loaded", Toast.LENGTH_SHORT).show();

            } else {
                // fallback a config interno nell'APK
                is = getAssets().open("config.json");
                //Toast.makeText(this, "Caricato config interno", Toast.LENGTH_SHORT).show();

            }
*/
            SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
            String savedUriStr = prefs.getString("config_uri", null);





            if (savedUriStr != null) {
                Uri savedUri = Uri.parse(savedUriStr);
                is = getContentResolver().openInputStream(savedUri);
                usingExternal = true;

            } else {
                is = getAssets().open("config.json");
            }

            if (is == null) throw new FileNotFoundException();
            if (usingExternal){
                Toast.makeText(this, "Config Loaded", Toast.LENGTH_SHORT).show();
            }

        }catch (FileNotFoundException e) {
            // File esterno non più disponibile → fallback interno
            Toast.makeText(this, "File esterno non trovato. Uso config interno.", Toast.LENGTH_SHORT).show();
            getSharedPreferences("config", MODE_PRIVATE).edit().remove("config_uri").apply();
            try {
                is = getAssets().open("config.json");
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String jsonStr = new String(buffer, "UTF-8");
                JSONObject json = new JSONObject(jsonStr);
            } catch (Exception ex) {
                Toast.makeText(this, "Errore nel config interno", Toast.LENGTH_LONG).show();
            }

        }
        catch (Exception e) {
            textView.setText("Errore: "+e.toString());
            showFatalErrorDialog("Errore critico: " + e.getMessage());
            //Toast.makeText(this, "Errore nel file di configurazione"+e.toString(), Toast.LENGTH_LONG).show();
            //new android.os.Handler().postDelayed(() -> finish(), 2000); // 2 sec delay
        }
        final boolean uext = usingExternal;
        try {

            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            // InputStream is = getAssets().open("config.json");
            //byte[] buffer = new byte[is.available()];
            //is.read(buffer);
            //is.close();
            String jsonStr = new String(buffer, "UTF-8");
            JSONObject json = new JSONObject(jsonStr);
            JSONArray matchArray = json.getJSONArray("matchTable");
            attesa = json.optInt("delay",0);
            deepdebug = json.optString("debug","").equalsIgnoreCase("on") ;

            if (deepdebug) {
                Toast.makeText(this, "Deepdebug mode", Toast.LENGTH_LONG).show();
            }
            /* Verifica Firma come futura funzionalità
             String signature = json.getString("signature");
            // Ricalcola l'hash localmente
            String matchTableString = matchArray.toString();
            String calculatedSignature = calculateSHA256(matchTableString);

            if (!signature.equalsIgnoreCase(calculatedSignature)) {
                Toast.makeText(this, "Firma del file di configurazione non valida!", Toast.LENGTH_LONG).show();
                return;
            }*/


            for (int i = 0; i < matchArray.length(); i++) {
                JSONObject obj = matchArray.getJSONObject(i);
                String code = obj.getString("code");
                if (!code.matches("^[0-9a-fA-F]+$")) {
                    throw new IllegalArgumentException("Codice non valido: contiene caratteri non esadecimali");
                }
                if (code.length() > 32) {
                    throw new IllegalArgumentException("Codice non valido: lunghezza superiore a 32 caratteri");
                }


                String key = obj.getString("key");
                if (!key.matches("^[0-9a-fA-F]{12}$")) {
                    throw new IllegalArgumentException("Chiave lettura non valida per code " + code);
                }

                String keyType = obj.getString("keyType");
                if (!keyType.equalsIgnoreCase("A") && !keyType.equalsIgnoreCase("B")) {
                    throw new IllegalArgumentException("keyType deve essere 'A' o 'B' in code " + code);
                }
                String desc = obj.getString("description");

                List<WriteEntry> writes = new ArrayList<>();
                JSONArray writeArray = obj.getJSONArray("writes");
                for (int j = 0; j < writeArray.length(); j++) {
                    JSONObject w = writeArray.getJSONObject(j);

                    int block = w.getInt("block");
                    if (block < 0 || block > 63) {
                        throw new IllegalArgumentException("Blocco fuori range in writes[" + j + "]");
                    }

                    String writeKey = w.getString("key");
                    if (!writeKey.matches("^[0-9a-fA-F]{12}$")) {
                        throw new IllegalArgumentException("Chiave scrittura non valida in writes[" + j + "]");
                    }

                    String data = w.optString("data","");
                    if (w.has("data"))  {
                        if (!data.matches("^[0-9a-fA-F]{32}$")) {
                            throw new IllegalArgumentException("Data non valida in writes[" + j + "]");
                        }   }
                    String writekeyType = w.optString("keyType","");
                    if (!writekeyType.equals("")){

                        if (!writekeyType.equalsIgnoreCase("A") && !writekeyType.equalsIgnoreCase("B")) {
                            throw new IllegalArgumentException("keyType deve essere 'A' o 'B' in writes[" + j + "]");
                        }
                    }
                    String writecommand = w.optString("command","");
                    if (w.has("command"))  {
                        if (!writecommand.equalsIgnoreCase("RT") && !writecommand.equalsIgnoreCase("R") && !writecommand.equalsIgnoreCase("T")) {
                            throw new IllegalArgumentException("Command deve essere 'R' o 'T' in  writes[" + j + "]");
                        }     }


                    writes.add(new WriteEntry(
                            w.getInt("block"),
                            hexStringToByteArray(w.optString("data","")),
                            hexStringToByteArray(w.getString("key")),
                            w.getString("keyType"),
                            w.optString("command",""),
                            w.optString("commento","")
                    ));
                }

                codeEntries.add(new CodeEntry(code, hexStringToByteArray(key), keyType, desc,writes));
            }
            runOnUiThread(() -> {

                if (uext) {
                    tagCountTextView.setText("Tag gestiti: " + matchArray.length());
                } else {
                    tagCountTextView.setText("Select a Configuration File");
                }

            });
        }  catch (Exception e) {
            textView.setText("Errore: "+e.toString());
            showFatalErrorDialog("Errore critico: " + e.getMessage());
            //Toast.makeText(this, "Errore nel file di configurazione"+e.toString(), Toast.LENGTH_LONG).show();
            //new android.os.Handler().postDelayed(() -> finish(), 2000); // 2 sec delay
        }





    }

    @Override
    protected void onResume() {
        super.onResume();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter[] filters = new IntentFilter[]{tagDetected};
        String[][] techListsArray = new String[][]{new String[]{MifareClassic.class.getName()}};
        if (nfcAdapter != null)
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, techListsArray);
        loadConfig();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null)
            nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            processTag(tag);
        }
        //new android.os.Handler().postDelayed(() -> finish(), 2000); // 2 sec delay
    }

    private void processTag(Tag tag) {
        MifareClassic mfc = MifareClassic.get(tag);
        int currblock=-1;
        try {
            mfc.connect();
            for (CodeEntry entry : codeEntries) {
                int sector0 = mfc.blockToSector(0);
                boolean auth = entry.keyType.equalsIgnoreCase("A")
                        ? mfc.authenticateSectorWithKeyA(sector0, entry.key)
                        : mfc.authenticateSectorWithKeyB(sector0, entry.key);
                Thread.sleep(attesa);
                if (!auth) continue;

                byte[] data = mfc.readBlock(0);
                //mfc.restore(1);
                //mfc.authenticateSectorWithKeyA(1, entry.key);
                //mfc.transfer(4);
                String block0Hex = byteArrayToHexString(data).toUpperCase();
                Boolean confronto = block0Hex.startsWith(entry.code);
                if (!confronto) continue;
                String descrizione = entry.description ;

                for (WriteEntry write : entry.writes) {



                    int sector = mfc.blockToSector(write.block);
                    if(!write.keyType.equals("")){
                        boolean writeAuth = write.keyType.equalsIgnoreCase("A")
                                ? mfc.authenticateSectorWithKeyA(sector, write.key)
                                : mfc.authenticateSectorWithKeyB(sector, write.key);

                        if (!writeAuth) {

                            textView.append("\nAuth Failed for block " + write.block);
                            //textView.postDelayed(() -> finish(), 2000);
                            mfc.close();
                            return;
                        }
                    }
                    if (write.comando.equals("RT"))  {
                        mfc.restore(write.block);
                        Thread.sleep(50);
                        mfc.transfer(write.block);



                    } else if  (write.comando.equals("R"))  {
                        mfc.restore(write.block);

                    }
                    else if  (write.comando.equals("T"))  {
                        mfc.transfer(write.block);

                    }
                    else {
//currblock= write.block;
                        mfc.writeBlock(write.block, write.data);

                        if (deepdebug) {
                            byte[] verify = mfc.readBlock(write.block);

                            if (!Arrays.equals(verify, write.data)) {
                                currblock= write.block;

                            }
                        }

                    }

                    // ✅ MOSTRA IL COMMENTO SE PRESENTE
                    if (!write.commento.isEmpty() && deepdebug) {
                        runOnUiThread(() -> {
                            textView.append("\n" + write.commento);
                        });
                    }
                    Thread.sleep(attesa);
                }
                if (currblock!=-1){
                    showFatalErrorDialog("Unable to write Block:" + currblock + " The write process can't change the block data");
                }else
                {
                    textView.append("\nWriting process for " + descrizione +" Completed " );
                    mfc.close();

                    // Delay per mostrare il messaggio prima della chiusura
                    //textView.postDelayed(() -> finish(), 2000); // chiude dopo 2 secondi
                    return;
                }

            }

            //textView.setText("No code detected");


            String[] techList = tag.getTechList();
            StringBuilder sb = new StringBuilder("No code detected\n Tecnologie supportate:\n");

            for (String tech : techList) {
                sb.append(tech).append("\n");
            }

            //Log.d("TagTechList", sb.toString()); // Per logcat
            textView.setText(sb.toString());     // Per mostrarlo a schermo

            mfc.close();
            textView.postDelayed(() -> finish(), 2000); // chiude dopo 2 secondi
        } catch (Exception e) {
            textView.append("Error during writing process"+e.getMessage()+" "+currblock);

        }
    }
    private void showFatalErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Errore")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Chiudi app", (dialog, which) -> {
                    finishAffinity(); // chiude tutte le activity
                    System.exit(0);   // forza la chiusura del processo
                })
                .show();
    }
    private File getMediaConfigFile() {
        File mediaDir = new File(Environment.getExternalStorageDirectory(),
                "Android/media/com.averino.mifarewriter");

        if (!mediaDir.exists()) {
            mediaDir.mkdirs();
        }


        return new File(mediaDir, "config.json");
    }
    /*private String calculateSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getByte(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02X", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }*/
    private String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte)
                    ((Character.digit(s.charAt(i), 16) << 4)
                            + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    // Classi di supporto
    private static class CodeEntry {
        String code;
        String description;

        byte[] key;
        String keyType;
        List<WriteEntry> writes;

        CodeEntry(String code, byte[] key, String keyType, String desc , List<WriteEntry> writes) {
            this.code = code;
            this.key = key;
            this.keyType = keyType;
            this.writes = writes;
            this.description = desc;

        }
    }

    private static class WriteEntry {
        int block;
        byte[] data;
        byte[] key;
        String keyType;
        String comando;

        String commento;

        WriteEntry(int block, byte[] data, byte[] key, String keyType, String Comando,String commento) {
            this.block = block;
            this.data = data;
            this.key = key;
            this.keyType = keyType;
            this.comando = Comando;
        this.commento = commento;
        }
    }
}
