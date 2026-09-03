package it.fantaformation;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int PICK_XLSX = 1001;

    private TextView resultText;

    private List<String[]> currentPlayers =
            new ArrayList<>();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildInterface();
    }

    private void buildInterface() {

        LinearLayout layout =
                new LinearLayout(this);

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        layout.setPadding(
                30, 30, 30, 30
        );

        TextView title =
                new TextView(this);

        title.setText(
                "FantaFormation"
        );

        title.setTextSize(28);

        title.setTypeface(
                null,
                Typeface.BOLD
        );

        title.setGravity(
                Gravity.CENTER
        );

        layout.addView(title);

        TextView description =
                new TextView(this);

        description.setText(
                "\nCarica il file Excel (.xlsx).\n\n" +
                "L'app legge la sezione One Pisa " +
                "e cerca online le probabili formazioni.\n"
        );

        description.setTextSize(16);

        layout.addView(description);

        Button loadButton =
                new Button(this);

        loadButton.setText(
                "CARICA FORMAZIONE EXCEL"
        );

        layout.addView(loadButton);

        Button automateButton =
                new Button(this);

        automateButton.setText(
                "AUTOMATIZZA FORMAZIONE"
        );

        layout.addView(
                automateButton
        );

        Button fantacalcioButton =
                new Button(this);

        fantacalcioButton.setText(
                "APRI FANTACALCIO"
        );

        layout.addView(
                fantacalcioButton
        );

        resultText =
                new TextView(this);

        resultText.setTextSize(16);

        resultText.setPadding(
                0, 30, 0, 20
        );

        ScrollView scroll =
                new ScrollView(this);

        scroll.addView(
                resultText
        );

        layout.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(layout);

        loadButton.setOnClickListener(
                v -> openFilePicker()
        );

        automateButton.setOnClickListener(
                v -> startAutomation()
        );

        fantacalcioButton.setOnClickListener(
                v -> openFantacalcio()
        );
    }

    private void openFilePicker() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );

        startActivityForResult(
                intent,
                PICK_XLSX
        );
    }

    private void openFantacalcio() {

        Intent intent =
                new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                                "https://leghe.fantacalcio.it/"
                        )
                );

        startActivity(intent);
    }

    private void startAutomation() {

        if (currentPlayers.isEmpty()) {

            Toast.makeText(
                    this,
                    "Prima carica il file Excel",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        resultText.setText(
                "RICERCA FORMAZIONE...\n\n" +
                "Sto controllando le probabili " +
                "formazioni aggiornate di Fantacalcio.\n" +
                "Attendi..."
        );

        executor.execute(() -> {

            try {

                String html =
                        downloadProbabiliFormazioni();

                List<String> found =
                        findPlayersInWebPage(
                                html,
                                currentPlayers
                        );

                final String result =
                        buildRecommendation(
                                found
                        );

                runOnUiThread(() ->
                        resultText.setText(
                                result
                        )
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        resultText.setText(
                                "ERRORE RICERCA ONLINE\n\n" +
                                e.getMessage()
                        )
                );
            }
        });
    }

    private String downloadProbabiliFormazioni()
            throws Exception {

        URL url =
                new URL(
                        "https://www.fantacalcio.it/" +
                        "probabili-formazioni-serie-a"
                );

        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        connection.setRequestMethod(
                "GET"
        );

        connection.setConnectTimeout(
                15000
        );

        connection.setReadTimeout(
                15000
        );

        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0"
        );

        int responseCode =
                connection.getResponseCode();

        if (responseCode < 200 ||
                responseCode >= 300) {

            throw new Exception(
                    "Sito Fantacalcio non raggiungibile (" +
                    responseCode +
                    ")"
            );
        }

        InputStream input =
                connection.getInputStream();

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                input,
                                "UTF-8"
                        )
                );

        StringBuilder html =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {

            html.append(line)
                    .append("\n");
        }

        reader.close();

        connection.disconnect();

        return html.toString();
    }

    private List<String> findPlayersInWebPage(
            String html,
            List<String[]> players) {

        List<String> found =
                new ArrayList<>();

        String cleanHtml =
                normalize(html);

        for (String[] player :
                players) {

            String name =
                    player[1];

            if (name == null ||
                    name.trim().isEmpty()) {

                continue;
            }

            String normalizedName =
                    normalize(name);

            if (normalizedName.length() < 3) {
                continue;
            }

            if (cleanHtml.contains(
                    normalizedName
            )) {

                found.add(name);
            }
        }

        return found;
    }

    private String buildRecommendation(
            List<String> found) {

        StringBuilder result =
                new StringBuilder();

        result.append(
                "FORMAZIONE ANALIZZATA\n"
        );

        result.append(
                "====================\n\n"
        );

        result.append(
                "Giocatori trovati nelle " +
                "probabili formazioni: "
        );

        result.append(
                found.size()
        );

        result.append("\n\n");

        if (found.isEmpty()) {

            result.append(
                    "Nessun giocatore è stato " +
                    "riconosciuto automaticamente.\n\n"
            );

            result.append(
                    "Controlla la connessione internet " +
                    "e i nomi presenti nell'Excel."
            );

            return result.toString();
        }

        result.append(
                "GIOCATORI RICONOSCIUTI\n"
        );

        result.append(
                "----------------------\n"
        );

        for (String player : found) {

            result.append(
                    "✓ "
            );

            result.append(
                    player
            );

            result.append("\n");
        }

        result.append(
                "\n----------------------\n\n"
        );

        result.append(
                "PROSSIMO PASSO\n\n"
        );

        result.append(
                "Questi giocatori sono stati " +
                "trovati nelle probabili " +
                "formazioni online.\n\n"
        );

        result.append(
                "Per scegliere automaticamente " +
                "gli 11 dobbiamo associare ogni " +
                "giocatore al suo ruolo " +
                "(POR/DIF/CEN/ATT).\n\n"
        );

        result.append(
                "La fase di selezione automatica " +
                "e il salvataggio su Leghe " +
                "verranno eseguiti dopo questa fase."
        );

        return result.toString();
    }

    private String normalize(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .toLowerCase(
                        Locale.ITALIAN
                )
                .replace(
                        "à", "a"
                )
                .replace(
                        "è", "e"
                )
                .replace(
                        "é", "e"
                )
                .replace(
                        "ì", "i"
                )
                .replace(
                        "ò", "o"
                )
                .replace(
                        "ù", "u"
                )
                .replaceAll(
                        "[^a-z0-9]",
                        ""
                );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == PICK_XLSX &&
                resultCode == RESULT_OK &&
                data != null &&
                data.getData() != null) {

            Uri uri =
                    data.getData();

            try {

                currentPlayers =
                        XlsxReader.read(
                                this,
                                uri
                        );

                StringBuilder result =
                        new StringBuilder();

                result.append(
                        "FORMAZIONE LETTA\n"
                );

                result.append(
                        "====================\n\n"
                );

                result.append(
                        "Squadra: One Pisa\n"
                );

                result.append(
                        "Giocatori: "
                );

                result.append(
                        currentPlayers.size()
                );

                result.append(
                        "\n\n"
                );

                for (String[] player :
                        currentPlayers) {

                    result.append(
                            player[1]
                    );

                    if (player[2] != null &&
                            !player[2].isEmpty()) {

                        result.append(
                                " - "
                        );

                        result.append(
                                player[2]
                        );

                        result.append(
                                " crediti"
                        );
                    }

                    result.append(
                            "\n"
                    );
                }

                resultText.setText(
                        result.toString()
                );

                Toast.makeText(
                        this,
                        "Formazione caricata!",
                        Toast.LENGTH_LONG
                ).show();

            } catch (Exception e) {

                resultText.setText(
                        "Errore nella lettura del file:\n\n" +
                        e.getMessage()
                );

                Toast.makeText(
                        this,
                        "Errore nel file Excel",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    protected void onDestroy() {

        executor.shutdownNow();

        super.onDestroy();
    }
}
