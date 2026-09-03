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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int PICK_XLSX = 1001;

    private TextView resultText;

    private List<String[]> currentPlayers =
            new ArrayList<>();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    /*
     * Moduli consentiti.
     */
    private static final String[] MODULES = {
            "3-4-3",
            "4-4-2",
            "3-5-2",
            "4-5-1",
            "5-4-1"
    };

    /*
     * Dati online raccolti per ciascun giocatore.
     */
    private static class PlayerInfo {

        String name;

        String role = "";

        double starterProbability = 0;

        double fantasyScore = 0;

        boolean injured = false;

        boolean suspended = false;

        boolean doubtful = false;

        boolean foundOnline = false;

        PlayerInfo(String name) {
            this.name = name;
        }
    }

    /*
     * Una formazione candidata.
     */
    private static class Formation {

        String module;

        List<PlayerInfo> goalkeepers =
                new ArrayList<>();

        List<PlayerInfo> defenders =
                new ArrayList<>();

        List<PlayerInfo> midfielders =
                new ArrayList<>();

        List<PlayerInfo> attackers =
                new ArrayList<>();

        double score;
    }

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
                30,
                30,
                30,
                30
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
                "\nCarica il tuo Excel.\n\n" +
                "L'app legge i 25 giocatori di One Pisa " +
                "e cerca online ruolo, titolarità, " +
                "rendimento e indisponibilità.\n\n" +
                "Moduli analizzati:\n" +
                "3-4-3 • 4-4-2 • 3-5-2 • 4-5-1 • 5-4-1\n"
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

        layout.addView(automateButton);

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
                0,
                30,
                0,
                20
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

    /*
     * =========================================================
     * AVVIO ANALISI
     * =========================================================
     */

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
                "ANALISI IN CORSO...\n\n" +
                "Sto cercando i tuoi giocatori online.\n" +
                "Recupero ruoli, titolarità e rendimento.\n\n" +
                "Attendi..."
        );

        executor.execute(() -> {

            try {

                List<PlayerInfo> players =
                        createPlayerList(
                                currentPlayers
                        );

                /*
                 * 1. Dati ufficiali Fantacalcio.
                 */
                String statsPage =
                        downloadPage(
                                "https://www.fantacalcio.it/statistiche-serie-a"
                        );

                /*
                 * 2. Probabili formazioni Fantacalcio.
                 */
                String probablePage =
                        downloadPage(
                                "https://www.fantacalcio.it/probabili-formazioni-serie-a"
                        );

                /*
                 * 3. Quotazioni/FVM Fantacalcio.
                 */
                String quotationPage =
                        downloadPage(
                                "https://www.fantacalcio.it/quotazioni-fantacalcio"
                        );

                analyzeFantacalcioData(
                        players,
                        statsPage,
                        probablePage,
                        quotationPage
                );

                /*
                 * 4. Analisi delle cinque formazioni.
                 */
                Formation best =
                        findBestFormation(
                                players
                        );

                String result =
                        buildFinalResult(
                                players,
                                best
                        );

                runOnUiThread(() ->
                        resultText.setText(
                                result
                        )
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        resultText.setText(
                                "ERRORE DURANTE L'ANALISI\n\n" +
                                e.getMessage() +
                                "\n\n" +
                                "Controlla la connessione internet."
                        )
                );
            }
        });
    }

    /*
     * =========================================================
     * CREAZIONE GIOCATORI
     * =========================================================
     */

    private List<PlayerInfo> createPlayerList(
            List<String[]> excelPlayers) {

        List<PlayerInfo> result =
                new ArrayList<>();

        for (String[] row :
                excelPlayers) {

            if (row == null ||
                    row.length < 2) {
                continue;
            }

            String name =
                    row[1];

            if (name == null ||
                    name.trim().isEmpty()) {
                continue;
            }

            result.add(
                    new PlayerInfo(
                            name.trim()
                    )
            );
        }

        return result;
    }

    /*
     * =========================================================
     * DOWNLOAD PAGINA
     * =========================================================
     */

    private String downloadPage(
            String address) throws Exception {

        URL url =
                new URL(address);

        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        connection.setRequestMethod(
                "GET"
        );

        connection.setConnectTimeout(
                20000
        );

        connection.setReadTimeout(
                20000
        );

        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) " +
                "AppleWebKit/537.36 " +
                "Chrome/130.0 Mobile Safari/537.36"
        );

        connection.setRequestProperty(
                "Accept-Language",
                "it-IT,it;q=0.9"
        );

        int responseCode =
                connection.getResponseCode();

        if (responseCode < 200 ||
                responseCode >= 300) {

            throw new Exception(
                    "Pagina non raggiungibile: " +
                    responseCode +
                    "\n" +
                    address
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

        StringBuilder result =
                new StringBuilder();

        String line;

        while ((line =
                reader.readLine()) != null) {

            result.append(
                    line
            ).append(
                    "\n"
            );
        }

        reader.close();

        connection.disconnect();

        return result.toString();
    }

    /*
     * =========================================================
     * ANALISI DATI
     * =========================================================
     */

    private void analyzeFantacalcioData(
            List<PlayerInfo> players,
            String statsPage,
            String probablePage,
            String quotationPage) {

        String stats =
                stripHtml(
                        statsPage
                );

        String probable =
                stripHtml(
                        probablePage
                );

        String quotation =
                stripHtml(
                        quotationPage
                );

        for (PlayerInfo player :
                players) {

            String normalized =
                    normalize(
                            player.name
                    );

            if (normalized.isEmpty()) {
                continue;
            }

            /*
             * Cerca il giocatore nelle statistiche.
             */
            int statsPosition =
                    findPlayerPosition(
                            stats,
                            normalized
                    );

            if (statsPosition >= 0) {

                player.foundOnline = true;

                String context =
                        getContext(
                                stats,
                                statsPosition,
                                500
                        );

                player.role =
                        detectRole(
                                context
                        );

                player.fantasyScore =
                        detectFantasyScore(
                                context
                        );
            }

            /*
             * Cerca nelle probabili formazioni.
             */
            int probablePosition =
                    findPlayerPosition(
                            probable,
                            normalized
                    );

            if (probablePosition >= 0) {

                player.foundOnline = true;

                String context =
                        getContext(
                                probable,
                                probablePosition,
                                800
                        );

                double probability =
                        detectProbability(
                                context
                        );

                if (probability > 0) {

                    player.starterProbability =
                            Math.max(
                                    player.starterProbability,
                                    probability
                            );
                }

                String lower =
                        context.toLowerCase(
                                Locale.ITALIAN
                        );

                if (lower.contains(
                        "infortun"
                )) {

                    player.injured = true;
                }

                if (lower.contains(
                        "squalificat"
                )) {

                    player.suspended = true;
                }

                if (lower.contains(
                        "in dubbio"
                )) {

                    player.doubtful = true;
                }
            }

            /*
             * Cerca anche nelle quotazioni.
             */
            int quotationPosition =
                    findPlayerPosition(
                            quotation,
                            normalized
                    );

            if (quotationPosition >= 0) {

                player.foundOnline = true;

                String context =
                        getContext(
                                quotation,
                                quotationPosition,
                                500
                        );

                if (player.role.isEmpty()) {

                    player.role =
                            detectRole(
                                    context
                            );
                }

                double score =
                        detectFvm(
                                context
                        );

                if (score > 0 &&
                        player.fantasyScore == 0) {

                    player.fantasyScore =
                            score;
                }
            }

            /*
             * Se il giocatore non ha probabilità,
             * ma compare nelle probabili formazioni,
             * gli assegniamo una probabilità minima.
             */
            if (probablePosition >= 0 &&
                    player.starterProbability == 0) {

                player.starterProbability =
                        50;
            }
        }
    }

    /*
     * =========================================================
     * RICERCA NOME
     * =========================================================
     */

    private int findPlayerPosition(
            String text,
            String normalizedName) {

        if (text == null ||
                normalizedName == null ||
                normalizedName.isEmpty()) {

            return -1;
        }

        /*
         * Prima prova la ricerca normale.
         */
        String normalizedText =
                normalize(text);

        return normalizedText.indexOf(
                normalizedName
        );
    }

    /*
     * =========================================================
     * CONTESTO
     * =========================================================
     */

    private String getContext(
            String text,
            int position,
            int length) {

        if (position < 0) {
            return "";
        }

        int start =
                Math.max(
                        0,
                        position - 250
                );

        int end =
                Math.min(
                        text.length(),
                        position + length
                );

        return text.substring(
                start,
                end
        );
    }

    /*
     * =========================================================
     * RUOLO CLASSIC
     * =========================================================
     */

    private String detectRole(
            String context) {

        String lower =
                context.toLowerCase(
                        Locale.ITALIAN
                );

        /*
         * Prima controlliamo le abbreviazioni.
         */
        if (containsAny(
                lower,
                "por",
                "portiere",
                "portieri"
        )) {

            return "POR";
        }

        if (containsAny(
                lower,
                "dif",
                "difensore",
                "difensori"
        )) {

            return "DIF";
        }

        if (containsAny(
                lower,
                "cen",
                "centrocampista",
                "centrocampisti"
        )) {

            return "CEN";
        }

        if (containsAny(
                lower,
                "att",
                "attaccante",
                "attaccanti"
        )) {

            return "ATT";
        }

        /*
         * Alcune pagine possono usare i ruoli
         * per esteso.
         */
        if (lower.contains("portiere")) {
            return "POR";
        }

        if (lower.contains("difensore")) {
            return "DIF";
        }

        if (lower.contains("centrocampista")) {
            return "CEN";
        }

        if (lower.contains("attaccante")) {
            return "ATT";
        }

        return "";
    }

    /*
     * =========================================================
     * PROBABILITA' TITOLARITA'
     * =========================================================
     */

    private double detectProbability(
            String context) {

        Pattern pattern =
                Pattern.compile(
                        "(\\d{1,3})\\s*%"
                );

        Matcher matcher =
                pattern.matcher(
                        context
                );

        double best = 0;

        while (matcher.find()) {

            try {

                double value =
                        Double.parseDouble(
                                matcher.group(1)
                        );

                if (value >= 1 &&
                        value <= 100) {

                    best =
                            Math.max(
                                    best,
                                    value
                            );
                }

            } catch (Exception ignored) {
            }
        }

        return best;
    }

    /*
     * =========================================================
     * FANTAVOTO
     * =========================================================
     */

    private double detectFantasyScore(
            String context) {

        /*
         * Cerca valori tipo:
         *
         * FM 8,50
         * FantaVoto 8,50
         */
        Pattern pattern =
                Pattern.compile(
                        "(?:FM|FantaVoto|FantaVoto\\s*)\\s*" +
                        "([0-9]+[\\.,][0-9]+)"
                );

        Matcher matcher =
                pattern.matcher(
                        context
                );

        double best = 0;

        while (matcher.find()) {

            try {

                double value =
                        Double.parseDouble(
                                matcher.group(1)
                                        .replace(
                                                ",",
                                                "."
                                        )
                        );

                if (value > best) {
                    best = value;
                }

            } catch (Exception ignored) {
            }
        }

        return best;
    }

    /*
     * =========================================================
     * FVM
     * =========================================================
     */

    private double detectFvm(
            String context) {

        Pattern pattern =
                Pattern.compile(
                        "(?:FVM\\s*/\\s*1000|FVM)" +
                        "\\s*([0-9]+)"
                );

        Matcher matcher =
                pattern.matcher(
                        context
                );

        double best = 0;

        while (matcher.find()) {

            try {

                double value =
                        Double.parseDouble(
                                matcher.group(1)
                        );

                best =
                        Math.max(
                                best,
                                value
                        );

            } catch (Exception ignored) {
            }
        }

        return best;
    }

    /*
     * =========================================================
     * NORMALIZZAZIONE
     * =========================================================
     */

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
                        "à",
                        "a"
                )
                .replace(
                        "è",
                        "e"
                )
                .replace(
                        "é",
                        "e"
                )
                .replace(
                        "ì",
                        "i"
                )
                .replace(
                        "ò",
                        "o"
                )
                .replace(
                        "ù",
                        "u"
                )
                .replaceAll(
                        "[^a-z0-9]",
                        ""
                );
    }

    /*
     * =========================================================
     * RIMOZIONE HTML
     * =========================================================
     */

    private String stripHtml(
            String html) {

        if (html == null) {
            return "";
        }

        String text =
                html.replaceAll(
                        "(?s)<script.*?</script>",
                        " "
                );

        text =
                text.replaceAll(
                        "(?s)<style.*?</style>",
                        " "
                );

        text =
                text.replaceAll(
                        "<[^>]*>",
                        " "
                );

        text =
                text.replace(
                        "&nbsp;",
                        " "
                );

        text =
                text.replace(
                        "&amp;",
                        "&"
                );

        text =
                text.replace(
                        "&quot;",
                        "\""
                );

        text =
                text.replace(
                        "&#39;",
                        "'"
                );

        return text;
    }

    /*
     * =========================================================
     * CONTROLLO PAROLE
     * =========================================================
     */

    private boolean containsAny(
            String text,
            String... values) {

        for (String value :
                values) {

            if (text.contains(value)) {
                return true;
            }
        }

        return false;
    }

    /*
     * =========================================================
     * TROVA FORMAZIONE MIGLIORE
     * =========================================================
     */

    private Formation findBestFormation(
            List<PlayerInfo> players) {

        Formation best = null;

        for (String module :
                MODULES) {

            Formation formation =
                    buildBestFormationForModule(
                            players,
                            module
                    );

            if (formation == null) {
                continue;
            }

            if (best == null ||
                    formation.score > best.score) {

                best = formation;
            }
        }

        return best;
    }

    /*
     * =========================================================
     * FORMAZIONE PER MODULO
     * =========================================================
     */

    private Formation buildBestFormationForModule(
            List<PlayerInfo> players,
            String module) {

        String[] parts =
                module.split("-");

        int defenders =
                Integer.parseInt(
                        parts[0]
                );

        int midfielders =
                Integer.parseInt(
                        parts[1]
                );

        int attackers =
                Integer.parseInt(
                        parts[2]
                );

        List<PlayerInfo> goalkeepers =
                getByRole(
                        players,
                        "POR"
                );

        List<PlayerInfo> defenderList =
                getByRole(
                        players,
                        "DIF"
                );

        List<PlayerInfo> midfielderList =
                getByRole(
                        players,
                        "CEN"
                );

        List<PlayerInfo> attackerList =
                getByRole(
                        players,
                        "ATT"
                );

        if (goalkeepers.size() < 1 ||
                defenderList.size() < defenders ||
                midfielderList.size() < midfielders ||
                attackerList.size() < attackers) {

            return null;
        }

        sortPlayers(
                goalkeepers
        );

        sortPlayers(
                defenderList
        );

        sortPlayers(
                midfielderList
        );

        sortPlayers(
                attackerList
        );

        Formation result =
                new Formation();

        result.module =
                module;

        result.goalkeepers.add(
                goalkeepers.get(0)
        );

        for (int i = 0;
             i < defenders;
             i++) {

            result.defenders.add(
                    defenderList.get(i)
            );
        }

        for (int i = 0;
             i < midfielders;
             i++) {

            result.midfielders.add(
                    midfielderList.get(i)
            );
        }

        for (int i = 0;
             i < attackers;
             i++) {

            result.attackers.add(
                    attackerList.get(i)
            );
        }

        result.score =
                calculateFormationScore(
                        result
                );

        return result;
    }

    /*
     * =========================================================
     * FILTRA PER RUOLO
     * =========================================================
     */

    private List<PlayerInfo> getByRole(
            List<PlayerInfo> players,
            String role) {

        List<PlayerInfo> result =
                new ArrayList<>();

        for (PlayerInfo player :
                players) {

            if (role.equals(
                    player.role
            )) {

                result.add(
                        player
                );
            }
        }

        return result;
    }

    /*
     * =========================================================
     * ORDINAMENTO
     * =========================================================
     */

    private void sortPlayers(
            List<PlayerInfo> players) {

        players.sort(
                (a, b) ->
                        Double.compare(
                                playerValue(b),
                                playerValue(a)
                        )
        );
    }

    /*
     * =========================================================
     * PUNTEGGIO GIOCATORE
     * =========================================================
     */

    private double playerValue(
            PlayerInfo player) {

        double value = 0;

        /*
         * Titolarità:
         * massimo 60 punti.
         */
        value +=
                player.starterProbability
                        * 0.60;

        /*
         * Rendimento:
         * piccolo contributo.
         */
        value +=
                player.fantasyScore
                        * 5.0;

        /*
         * Un giocatore non trovato online
         * viene penalizzato.
         */
        if (!player.foundOnline) {

            value -= 15;
        }

        /*
         * Indisponibilità:
         * forte penalizzazione.
         */
        if (player.injured) {

            value -= 100;
        }

        if (player.suspended) {

            value -= 100;
        }

        /*
         * Dubbio:
         * penalità moderata.
         */
        if (player.doubtful) {

            value -= 20;
        }

        return value;
    }

    /*
     * =========================================================
     * PUNTEGGIO FORMAZIONE
     * =========================================================
     */

    private double calculateFormationScore(
            Formation formation) {

        double score = 0;

        for (PlayerInfo player :
                formation.goalkeepers) {

            score +=
                    playerValue(
                            player
                    );
        }

        for (PlayerInfo player :
                formation.defenders) {

            score +=
                    playerValue(
                            player
                    );
        }

        for (PlayerInfo player :
                formation.midfielders) {

            score +=
                    playerValue(
                            player
                    );
        }

        for (PlayerInfo player :
                formation.attackers) {

            score +=
                    playerValue(
                            player
                    );
        }

        return score;
    }

    /*
     * =========================================================
     * RISULTATO FINALE
     * =========================================================
     */

    private String buildFinalResult(
            List<PlayerInfo> players,
            Formation best) {

        StringBuilder result =
                new StringBuilder();

        result.append(
                "FORMAZIONE OTTIMALE\n"
        );

        result.append(
                "========================\n\n"
        );

        if (best == null) {

            result.append(
                    "Non sono riuscito a costruire " +
                    "una formazione completa.\n\n"
            );

            result.append(
                    "RUOLI RICONOSCIUTI\n"
            );

            result.append(
                    "------------------------\n"
            );

            for (PlayerInfo player :
                    players) {

                result.append(
                        player.name
                );

                result.append(
                        " → "
                );

                result.append(
                        player.role.isEmpty()
                                ? "RUOLO NON TROVATO"
                                : player.role
                );

                result.append(
                        "\n"
                );
            }

            return result.toString();
        }

        result.append(
                "Modulo scelto: "
        );

        result.append(
                best.module
        );

        result.append(
                "\nPunteggio: "
        );

        result.append(
                String.format(
                        Locale.ITALIAN,
                        "%.1f",
                        best.score
                )
        );

        result.append(
                "\n\n"
        );

        result.append(
                "PORTIERE\n"
        );

        result.append(
                "------------------------\n"
        );

        appendPlayers(
                result,
                best.goalkeepers
        );

        result.append(
                "\nDIFENSORI\n"
        );

        result.append(
                "------------------------\n"
        );

        appendPlayers(
                result,
                best.defenders
        );

        result.append(
                "\nCENTROCAMPISTI\n"
        );

        result.append(
                "------------------------\n"
        );

        appendPlayers(
                result,
                best.midfielders
        );

        result.append(
                "\nATTACCANTI\n"
        );

        result.append(
                "------------------------\n"
        );

        appendPlayers(
                result,
                best.attackers
        );

        result.append(
                "\n\nANALISI ROSA\n"
        );

        result.append(
                "========================\n"
        );

        for (PlayerInfo player :
                players) {

            result.append(
                    player.name
            );

            result.append(
                    " | "
            );

            result.append(
                    player.role.isEmpty()
                            ? "?"
                            : player.role
            );

            result.append(
                    " | tit. "
            );

            result.append(
                    String.format(
                            Locale.ITALIAN,
                            "%.0f%%",
                            player.starterProbability
                    )
            );

            if (player.injured) {

                result.append(
                        " | INFORTUNATO"
                );
            }

            if (player.suspended) {

                result.append(
                        " | SQUALIFICATO"
                );
            }

            if (player.doubtful) {

                result.append(
                        " | IN DUBBIO"
                );
            }

            result.append(
                    "\n"
            );
        }

        result.append(
                "\n\nNOTA\n"
        );

        result.append(
                "La formazione è una proposta " +
                "automatica basata sui dati " +
                "online disponibili."
        );

        return result.toString();
    }

    /*
     * =========================================================
     * STAMPA GIOCATORI
     * =========================================================
     */

    private void appendPlayers(
            StringBuilder result,
            List<PlayerInfo> players) {

        for (PlayerInfo player :
                players) {

            result.append(
                    "✓ "
            );

            result.append(
                    player.name
            );

            result.append(
                    " ("
            );

            result.append(
                    String.format(
                            Locale.ITALIAN,
                            "%.0f%%",
                            player.starterProbability
                    )
            );

            result.append(
                    ")\n"
            );
        }
    }

    /*
     * =========================================================
     * EXCEL
     * =========================================================
     */

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
