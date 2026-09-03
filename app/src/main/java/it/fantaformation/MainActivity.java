package it.fantaformation;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private LinearLayout root;
    private TextView resultText;

    private ArrayList<String[]> formazione;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private static final String FANTACALCIO_QUOTE =
            "https://www.fantacalcio.it/quotazioni-fantacalcio/2026-27";

    private static final String FANTACALCIO_PROBABILI =
            "https://www.fantacalcio.it/probabili-formazioni-serie-a";

    private static final String FANTACALCIO_STATS =
            "https://www.fantacalcio.it/statistiche-serie-a/2026-27/fantacalcio/riepilogo";

    private static final String GAZZETTA_PROBABILI =
            "https://www.gazzetta.it/Calcio/prob_form/";

    private static final String SKY_PROBABILI =
            "https://sport.sky.it/calcio/serie-a/probabili-formazioni";

    private static final Set<String> ALLOWED_FORMATIONS =
            new HashSet<>(Arrays.asList(
                    "3-4-3",
                    "4-4-2",
                    "3-5-2",
                    "4-5-1",
                    "5-4-1"
            ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildInterface();
    }

    private void buildInterface() {

        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(35, 35, 35, 35);

        TextView title = new TextView(this);
        title.setText("FantaFormation");
        title.setTextSize(28);
        title.setPadding(0, 0, 0, 20);

        root.addView(title);

        TextView description = new TextView(this);
        description.setText(
                "Carica la rosa One Pisa dal file Excel. " +
                "L'app recupera i ruoli Classic ufficiali e analizza " +
                "le probabili formazioni."
        );
        description.setTextSize(16);
        description.setPadding(0, 0, 0, 25);

        root.addView(description);

        Button loadButton = new Button(this);
        loadButton.setText("CARICA FORMAZIONE EXCEL");
        root.addView(loadButton);

        Button automateButton = new Button(this);
        automateButton.setText("ANALIZZA E OTTIMIZZA FORMAZIONE");
        root.addView(automateButton);

        Button openButton = new Button(this);
        openButton.setText("APRI FANTACALCIO");
        root.addView(openButton);

        resultText = new TextView(this);
        resultText.setTextSize(15);
        resultText.setPadding(0, 30, 0, 30);

        root.addView(resultText);

        scroll.addView(root);

        setContentView(scroll);

        loadButton.setOnClickListener(v -> chooseExcel());

        automateButton.setOnClickListener(v -> {

            if (formazione == null || formazione.isEmpty()) {

                Toast.makeText(
                        MainActivity.this,
                        "Prima carica la formazione Excel",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            analyzeFormation();
        });

        openButton.setOnClickListener(v -> {

            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://leghe.fantacalcio.it/")
            );

            startActivity(intent);
        });
    }

    private void chooseExcel() {

        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != 100 ||
                resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {

            return;
        }

        Uri uri = data.getData();

        executor.execute(() -> {

            try {

                /*
                 * CORRETTO:
                 *
                 * XlsxReader attuale richiede:
                 *
                 * read(Context, Uri)
                 *
                 * Non InputStream.
                 */
                ArrayList<String[]> result =
                        XlsxReader.read(
                                MainActivity.this,
                                uri
                        );

                runOnUiThread(() -> {

                    formazione = result;

                    StringBuilder sb =
                            new StringBuilder();

                    sb.append("FORMAZIONE ONE PISA\n");
                    sb.append("====================\n\n");

                    for (String[] row : result) {

                        String player =
                                row.length > 1
                                        ? row[1]
                                        : "";

                        String cost =
                                row.length > 2
                                        ? row[2]
                                        : "";

                        sb.append(player);

                        if (cost != null &&
                                !cost.trim().isEmpty()) {

                            sb.append("  -  ")
                                    .append(cost);
                        }

                        sb.append("\n");
                    }

                    sb.append("\nTotale giocatori: ")
                            .append(result.size());

                    resultText.setText(
                            sb.toString()
                    );

                    Toast.makeText(
                            MainActivity.this,
                            "Formazione caricata correttamente",
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        resultText.setText(
                                "Errore lettura Excel:\n\n" +
                                e.getClass().getSimpleName() +
                                "\n" +
                                e.getMessage()
                        )
                );
            }
        });
    }

    private void analyzeFormation() {

        resultText.setText(
                "Analisi in corso...\n\n" +
                "1. Recupero ruoli Classic ufficiali\n" +
                "2. Recupero quotazioni/FVM\n" +
                "3. Recupero probabili Fantacalcio\n" +
                "4. Confronto Gazzetta\n" +
                "5. Confronto Sky\n" +
                "6. Calcolo delle 5 formazioni possibili\n"
        );

        executor.execute(() -> {

            try {

                String quotesHtml =
                        download(
                                FANTACALCIO_QUOTE
                        );

                String probabiliHtml =
                        download(
                                FANTACALCIO_PROBABILI
                        );

                String gazzettaHtml =
                        download(
                                GAZZETTA_PROBABILI
                        );

                String skyHtml =
                        download(
                                SKY_PROBABILI
                        );

                Map<String, OfficialPlayer>
                        officialPlayers =
                        parseOfficialPlayers(
                                quotesHtml
                        );

                Map<String, ProbabilityInfo>
                        probabilities =
                        parseFantacalcioProbabili(
                                probabiliHtml
                        );

                Map<String, Integer>
                        gazzetta =
                        parseExternalSource(
                                gazzettaHtml
                        );

                Map<String, Integer>
                        sky =
                        parseExternalSource(
                                skyHtml
                        );

                ArrayList<Player> players =
                        buildPlayers(
                                officialPlayers,
                                probabilities,
                                gazzetta,
                                sky
                        );

                FormationResult best =
                        calculateBestFormation(
                                players
                        );

                runOnUiThread(() ->
                        displayResult(
                                players,
                                best
                        )
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        resultText.setText(
                                "Errore durante l'analisi:\n\n" +
                                e.getClass().getSimpleName() +
                                "\n" +
                                e.getMessage()
                        )
                );
            }
        });
    }

    private String download(
            String address
    ) throws Exception {

        URL url =
                new URL(address);

        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        connection.setRequestMethod("GET");

        connection.setConnectTimeout(
                15000
        );

        connection.setReadTimeout(
                20000
        );

        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android) AppleWebKit/537.36"
        );

        int code =
                connection.getResponseCode();

        if (code < 200 || code >= 400) {

            throw new Exception(
                    "HTTP " +
                    code +
                    " - " +
                    address
            );
        }

        InputStream inputStream =
                connection.getInputStream();

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                inputStream,
                                "UTF-8"
                        )
                );

        StringBuilder result =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {

            result.append(line)
                    .append('\n');
        }

        reader.close();

        connection.disconnect();

        return result.toString();
    }

    private static class OfficialPlayer {

        String name = "";
        String team = "";
        String role = "";

        int classicQuote = 0;
        int fvm = 0;

        String profileUrl = "";
    }

    private static class ProbabilityInfo {

        int percentage = 0;

        boolean starter = false;
        boolean bench = false;

        String team = "";
    }

    private static class Player {

        String excelName;
        String officialName;

        String team;
        String role;

        int quote;
        int fvm;

        int probable;
        int externalAgreement;

        boolean starter;
        boolean bench;

        double score;

        Player(
                String excelName,
                String officialName,
                String team,
                String role,
                int quote,
                int fvm,
                int probable,
                int externalAgreement,
                boolean starter,
                boolean bench
        ) {

            this.excelName =
                    excelName;

            this.officialName =
                    officialName;

            this.team =
                    team;

            this.role =
                    role;

            this.quote =
                    quote;

            this.fvm =
                    fvm;

            this.probable =
                    probable;

            this.externalAgreement =
                    externalAgreement;

            this.starter =
                    starter;

            this.bench =
                    bench;

            calculateScore();
        }

        private void calculateScore() {

            score = 0;

            score +=
                    probable * 1.50;

            score +=
                    externalAgreement * 12.0;

            score +=
                    Math.min(
                            fvm,
                            300
                    ) * 0.12;

            score +=
                    quote * 0.15;

            if (starter) {
                score += 20;
            }

            if (bench && !starter) {
                score -= 12;
            }

            if (probable == 0) {
                score -= 25;
            }
        }
    }

    private Map<String, OfficialPlayer>
    parseOfficialPlayers(
            String html
    ) {

        Map<String, OfficialPlayer>
                result =
                new LinkedHashMap<>();

        Document document =
                Jsoup.parse(html);

        Elements links =
                document.select(
                        "a[href*='/serie-a/squadre/']"
                );

        for (Element link : links) {

            String name =
                    cleanName(
                            link.text()
                    );

            if (name.isEmpty()) {
                continue;
            }

            String href =
                    link.absUrl("href");

            if (href.isEmpty()) {

                href =
                        link.attr("href");
            }

            Element row =
                    link.closest("tr");

            if (row == null) {
                row = link.parent();
            }

            String rowText =
                    row != null
                            ? row.text()
                            : "";

            OfficialPlayer player =
                    new OfficialPlayer();

            player.name =
                    name;

            player.profileUrl =
                    href;

            player.team =
                    extractTeam(
                            rowText
                    );

            player.classicQuote =
                    extractClassicQuote(
                            row
                    );

            player.fvm =
                    extractFvm(
                            row
                    );

            player.role =
                    extractRoleFromPlayerElement(
                            link,
                            row
                    );

            if (player.role.isEmpty()) {

                player.role =
                        extractRoleFromAttributes(
                                row
                        );
            }

            String key =
                    normalize(
                            player.name
                    );

            if (!result.containsKey(key)) {

                result.put(
                        key,
                        player
                );
            }
        }

        return result;
    }

    private String extractRoleFromPlayerElement(
            Element link,
            Element row
    ) {

        String[] attributes = {
                "data-role",
                "data-ruolo",
                "role",
                "title",
                "aria-label",
                "class"
        };

        for (String attribute :
                attributes) {

            String value =
                    link.attr(
                            attribute
                    );

            String role =
                    roleFromText(
                            value
                    );

            if (!role.isEmpty()) {
                return role;
            }
        }

        if (row != null) {

            Elements elements =
                    row.select(
                            "[data-role], " +
                            "[data-ruolo], " +
                            "[title], " +
                            "[aria-label]"
                    );

            for (Element element :
                    elements) {

                for (String attribute :
                        attributes) {

                    String value =
                            element.attr(
                                    attribute
                            );

                    String role =
                            roleFromText(
                                    value
                            );

                    if (!role.isEmpty()) {
                        return role;
                    }
                }
            }
        }

        return "";
    }

    private String extractRoleFromAttributes(
            Element row
    ) {

        if (row == null) {
            return "";
        }

        String html =
                row.outerHtml();

        return roleFromText(
                html
        );
    }

    private String roleFromText(
            String text
    ) {

        if (text == null) {
            return "";
        }

        String normalized =
                normalize(text);

        if (normalized.contains(
                "portiere"
        )) {
            return "P";
        }

        if (normalized.contains(
                "difensore"
        )) {
            return "D";
        }

        if (normalized.contains(
                "centrocampista"
        )) {
            return "C";
        }

        if (normalized.contains(
                "attaccante"
        )) {
            return "A";
        }

        /*
         * Abbreviazioni consentite soltanto se
         * presenti come attributo strutturato.
         */
        if (normalized.matches(
                ".*\\bp\\b.*"
        )) {
            return "P";
        }

        if (normalized.matches(
                ".*\\bd\\b.*"
        )) {
            return "D";
        }

        if (normalized.matches(
                ".*\\bc\\b.*"
        )) {
            return "C";
        }

        if (normalized.matches(
                ".*\\ba\\b.*"
        )) {
            return "A";
        }

        return "";
    }

    private String extractTeam(
            String text
    ) {

        if (text == null) {
            return "";
        }

        String upper =
                text.toUpperCase(
                        Locale.ROOT
                );

        String[] teams = {
                "INT",
                "COM",
                "MIL",
                "ROM",
                "NAP",
                "JUV",
                "ATA",
                "BOL",
                "LAZ",
                "FIO",
                "PAR",
                "GEN",
                "MON",
                "TOR",
                "UDI",
                "SAS",
                "LEC",
                "CAG",
                "VEN",
                "FRO"
        };

        for (String team :
                teams) {

            if (upper.contains(team)) {
                return team;
            }
        }

        return "";
    }

    private int extractClassicQuote(
            Element row
    ) {

        if (row == null) {
            return 0;
        }

        Elements cells =
                row.select("td");

        ArrayList<Integer> numbers =
                new ArrayList<>();

        for (Element cell :
                cells) {

            String text =
                    cell.text().trim();

            if (text.matches("\\d+")) {

                try {

                    numbers.add(
                            Integer.parseInt(
                                    text
                            )
                    );

                } catch (Exception ignored) {
                }
            }
        }

        if (!numbers.isEmpty()) {

            return numbers.get(0);
        }

        return 0;
    }

    private int extractFvm(
            Element row
    ) {

        if (row == null) {
            return 0;
        }

        Elements cells =
                row.select("td");

        ArrayList<Integer> numbers =
                new ArrayList<>();

        for (Element cell :
                cells) {

            String text =
                    cell.text().trim();

            if (text.matches("\\d+")) {

                try {

                    numbers.add(
                            Integer.parseInt(
                                    text
                            )
                    );

                } catch (Exception ignored) {
                }
            }
        }

        if (numbers.size() >= 3) {

            return numbers.get(2);
        }

        return 0;
    }

    private Map<String, ProbabilityInfo>
    parseFantacalcioProbabili(
            String html
    ) {

        Map<String, ProbabilityInfo>
                result =
                new HashMap<>();

        Document document =
                Jsoup.parse(html);

        Elements headings =
                document.select(
                        "h2, h3"
                );

        String currentTeam = "";

        for (Element heading :
                headings) {

            String headingText =
                    heading.text().trim();

            if (isSerieATeam(
                    headingText
            )) {

                currentTeam =
                        headingText;
            }

            if (!isSerieATeam(
                    headingText
            )) {

                continue;
            }

            Element container =
                    findFormationContainer(
                            heading
                    );

            if (container == null) {
                continue;
            }

            Elements playerLinks =
                    container.select(
                            "a[href*='/serie-a/squadre/']"
                    );

            for (Element playerLink :
                    playerLinks) {

                String name =
                        cleanName(
                                playerLink.text()
                        );

                if (name.isEmpty()) {
                    continue;
                }

                int probability =
                        findProbabilityAfter(
                                playerLink
                        );

                boolean bench =
                        isInsideBench(
                                playerLink
                        );

                boolean starter =
                        !bench;

                String key =
                        normalize(name);

                ProbabilityInfo info =
                        result.get(key);

                if (info == null) {

                    info =
                            new ProbabilityInfo();

                    result.put(
                            key,
                            info
                    );
                }

                if (probability >
                        info.percentage) {

                    info.percentage =
                            probability;

                    info.starter =
                            starter;

                    info.bench =
                            bench;

                    info.team =
                            currentTeam;
                }
            }
        }

        if (result.isEmpty()) {

            Elements playerLinks =
                    document.select(
                            "a[href*='/serie-a/squadre/']"
                    );

            for (Element link :
                    playerLinks) {

                String name =
                        cleanName(
                                link.text()
                        );

                if (name.isEmpty()) {
                    continue;
                }

                int probability =
                        findProbabilityAfter(
                                link
                        );

                if (probability <= 0) {
                    continue;
                }

                ProbabilityInfo info =
                        new ProbabilityInfo();

                info.percentage =
                        probability;

                info.starter = true;

                result.put(
                        normalize(name),
                        info
                );
            }
        }

        return result;
    }

    private Element findFormationContainer(
            Element heading
    ) {

        Element current =
                heading.nextElementSibling();

        int counter = 0;

        while (current != null &&
                counter < 20) {

            if (current.select(
                    "a[href*='/serie-a/squadre/']"
            ).size() > 0) {

                return current;
            }

            current =
                    current.nextElementSibling();

            counter++;
        }

        return heading.parent();
    }

    private int findProbabilityAfter(
            Element playerLink
    ) {

        Element current =
                playerLink.nextElementSibling();

        int counter = 0;

        while (current != null &&
                counter < 4) {

            String text =
                    current.text().trim();

            int probability =
                    parsePercentage(text);

            if (probability >= 0) {
                return probability;
            }

            current =
                    current.nextElementSibling();

            counter++;
        }

        Element parent =
                playerLink.parent();

        if (parent != null) {

            String text =
                    parent.text();

            int probability =
                    parsePercentage(
                            text
                    );

            if (probability >= 0) {
                return probability;
            }
        }

        return 0;
    }

    private int parsePercentage(
            String text
    ) {

        if (text == null) {
            return -1;
        }

        text =
                text.trim();

        if (text.matches(
                "\\d{1,3}%"
        )) {

            try {

                return Integer.parseInt(
                        text.replace(
                                "%",
                                ""
                        )
                );

            } catch (Exception ignored) {
            }
        }

        return -1;
    }

    private boolean isInsideBench(
            Element element
    ) {

        Element current =
                element;

        for (int i = 0;
             i < 8 && current != null;
             i++) {

            String text =
                    current.text()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (text.startsWith(
                    "panchina"
            ) ||
                    text.contains(
                            "panchina"
                    )) {

                return true;
            }

            current =
                    current.parent();
        }

        return false;
    }

    private Map<String, Integer>
    parseExternalSource(
            String html
    ) {

        Map<String, Integer>
                result =
                new HashMap<>();

        if (html == null ||
                html.isEmpty()) {

            return result;
        }

        Document document =
                Jsoup.parse(html);

        String text =
                document.text();

        for (String[] row :
                formazione) {

            if (row.length < 2) {
                continue;
            }

            String excelName =
                    row[1];

            String normalized =
                    normalize(excelName);

            if (normalized.isEmpty()) {
                continue;
            }

            if (textContainsPlayer(
                    text,
                    excelName
            )) {

                result.put(
                        normalized,
                        1
                );
            }
        }

        return result;
    }

    private boolean textContainsPlayer(
            String text,
            String player
    ) {

        String a =
                normalize(text);

        String b =
                normalize(player);

        if (b.length() < 3) {
            return false;
        }

        return a.contains(b);
    }

    private ArrayList<Player> buildPlayers(
            Map<String, OfficialPlayer> official,
            Map<String, ProbabilityInfo> probabilities,
            Map<String, Integer> gazzetta,
            Map<String, Integer> sky
    ) {

        ArrayList<Player> result =
                new ArrayList<>();

        for (String[] row :
                formazione) {

            if (row.length < 2) {
                continue;
            }

            String excelName =
                    row[1];

            if (excelName == null ||
                    excelName.trim().isEmpty()) {

                continue;
            }

            OfficialPlayer officialPlayer =
                    findOfficialPlayer(
                            excelName,
                            official
                    );

            if (officialPlayer == null) {

                Player player =
                        new Player(
                                excelName,
                                excelName,
                                "",
                                "",
                                0,
                                0,
                                0,
                                0,
                                false,
                                false
                        );

                result.add(player);

                continue;
            }

            ProbabilityInfo probability =
                    probabilities.get(
                            normalize(
                                    officialPlayer.name
                            )
                    );

            int probable =
                    probability != null
                            ? probability.percentage
                            : 0;

            boolean starter =
                    probability != null &&
                            probability.starter;

            boolean bench =
                    probability != null &&
                            probability.bench;

            int externalAgreement = 0;

            String normalized =
                    normalize(
                            officialPlayer.name
                    );

            if (gazzetta.containsKey(
                    normalized
            )) {

                externalAgreement++;
            }

            if (sky.containsKey(
                    normalized
            )) {

                externalAgreement++;
            }

            Player player =
                    new Player(
                            excelName,
                            officialPlayer.name,
                            officialPlayer.team,
                            officialPlayer.role,
                            officialPlayer.classicQuote,
                            officialPlayer.fvm,
                            probable,
                            externalAgreement,
                            starter,
                            bench
                    );

            result.add(player);
        }

        return result;
    }

    private OfficialPlayer findOfficialPlayer(
            String excelName,
            Map<String, OfficialPlayer> official
    ) {

        String normalized =
                normalize(excelName);

        OfficialPlayer exact =
                official.get(normalized);

        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String,
                OfficialPlayer> entry :
                official.entrySet()) {

            if (similarNames(
                    normalized,
                    entry.getKey()
            )) {

                return entry.getValue();
            }
        }

        return null;
    }

    private boolean similarNames(
            String a,
            String b
    ) {

        if (a.equals(b)) {
            return true;
        }

        if (a.contains(b) ||
                b.contains(a)) {

            return true;
        }

        String[] aa =
                a.split(" ");

        String[] bb =
                b.split(" ");

        if (aa.length >= 2 &&
                bb.length >= 2) {

            String firstA =
                    aa[0];

            String lastA =
                    aa[aa.length - 1];

            String firstB =
                    bb[0];

            String lastB =
                    bb[bb.length - 1];

            if (firstA.equals(firstB) &&
                    lastA.equals(lastB)) {

                return true;
            }

            if (firstA.equals(lastB) &&
                    lastA.equals(firstB)) {

                return true;
            }
        }

        return false;
    }

    private static class FormationResult {

        String module;

        ArrayList<Player> goalkeeper =
                new ArrayList<>();

        ArrayList<Player> defenders =
                new ArrayList<>();

        ArrayList<Player> midfielders =
                new ArrayList<>();

        ArrayList<Player> attackers =
                new ArrayList<>();

        double score;

        boolean valid;
    }

    private FormationResult calculateBestFormation(
            ArrayList<Player> players
    ) {

        FormationResult best =
                null;

        for (String module :
                ALLOWED_FORMATIONS) {

            FormationResult result =
                    calculateFormation(
                            module,
                            players
                    );

            if (!result.valid) {
                continue;
            }

            if (best == null ||
                    result.score >
                            best.score) {

                best = result;
            }
        }

        if (best == null) {

            FormationResult empty =
                    new FormationResult();

            empty.module =
                    "NESSUNA";

            empty.valid =
                    false;

            return empty;
        }

        return best;
    }

    private FormationResult calculateFormation(
            String module,
            ArrayList<Player> players
    ) {

        FormationResult result =
                new FormationResult();

        result.module =
                module;

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

        List<Player> goalkeepers =
                playersForRole(
                        players,
                        "P"
                );

        List<Player> defenderList =
                playersForRole(
                        players,
                        "D"
                );

        List<Player> midfielderList =
                playersForRole(
                        players,
                        "C"
                );

        List<Player> attackerList =
                playersForRole(
                        players,
                        "A"
                );

        if (goalkeepers.isEmpty() ||
                defenderList.size() <
                        defenders ||
                midfielderList.size() <
                        midfielders ||
                attackerList.size() <
                        attackers) {

            result.valid =
                    false;

            return result;
        }

        sortByScore(
                goalkeepers
        );

        sortByScore(
                defenderList
        );

        sortByScore(
                midfielderList
        );

        sortByScore(
                attackerList
        );

        result.goalkeeper.add(
                goalkeepers.get(0)
        );

        result.defenders.addAll(
                defenderList.subList(
                        0,
                        defenders
                )
        );

        result.midfielders.addAll(
                midfielderList.subList(
                        0,
                        midfielders
                )
        );

        result.attackers.addAll(
                attackerList.subList(
                        0,
                        attackers
                )
        );

        result.score = 0;

        for (Player p :
                result.goalkeeper) {

            result.score +=
                    p.score;
        }

        for (Player p :
                result.defenders) {

            result.score +=
                    p.score;
        }

        for (Player p :
                result.midfielders) {

            result.score +=
                    p.score;
        }

        for (Player p :
                result.attackers) {

            result.score +=
                    p.score;
        }

        result.valid =
                true;

        return result;
    }

    private List<Player> playersForRole(
            ArrayList<Player> players,
            String role
    ) {

        ArrayList<Player> result =
                new ArrayList<>();

        for (Player player :
                players) {

            if (role.equals(
                    player.role
            )) {

                result.add(player);
            }
        }

        return result;
    }

    private void sortByScore(
            List<Player> players
    ) {

        Collections.sort(
                players,
                (a, b) ->
                        Double.compare(
                                b.score,
                                a.score
                        )
        );
    }

    private void displayResult(
            ArrayList<Player> players,
            FormationResult best
    ) {

        StringBuilder sb =
                new StringBuilder();

        sb.append(
                "ANALISI COMPLETATA\n"
        );

        sb.append(
                "==================\n\n"
        );

        sb.append(
                "RUOLI CLASSIC RILEVATI\n\n"
        );

        for (Player player :
                players) {

            sb.append(
                    player.excelName
            ).append(
                    " → "
            );

            if (player.role.isEmpty()) {

                sb.append(
                        "RUOLO NON DETERMINATO"
                );

            } else {

                sb.append(
                        roleName(
                                player.role
                        )
                );
            }

            sb.append("\n");

            if (!player.officialName
                    .equals(
                            player.excelName
                    )) {

                sb.append(
                        "   Nome ufficiale: "
                ).append(
                        player.officialName
                ).append("\n");
            }

            if (!player.team.isEmpty()) {

                sb.append(
                        "   Squadra: "
                ).append(
                        player.team
                ).append("\n");
            }

            sb.append(
                    "   Probabile: "
            ).append(
                    player.probable
            ).append(
                    "%\n"
            );

            sb.append(
                    "   FVM: "
            ).append(
                    player.fvm
            ).append("\n");

            sb.append(
                    "   Conferme Gazzetta/Sky: "
            ).append(
                    player.externalAgreement
            ).append(
                    "/2\n\n"
            );
        }

        sb.append("\n");

        if (!best.valid) {

            sb.append(
                    "NON È STATO POSSIBILE CREARE " +
                    "UNA FORMAZIONE VALIDA.\n\n"
            );

            sb.append(
                    "Uno o più ruoli Classic " +
                    "non sono stati riconosciuti."
            );

            resultText.setText(
                    sb.toString()
            );

            return;
        }

        sb.append(
                "================================\n"
        );

        sb.append(
                "FORMAZIONE OTTIMALE\n"
        );

        sb.append(
                "================================\n\n"
        );

        sb.append(
                "MODULO: "
        ).append(
                best.module
        ).append("\n");

        sb.append(
                "PUNTEGGIO: "
        ).append(
                String.format(
                        Locale.US,
                        "%.1f",
                        best.score
                )
        ).append("\n\n");

        sb.append(
                "PORTIERE\n"
        );

        for (Player p :
                best.goalkeeper) {

            appendSelectedPlayer(
                    sb,
                    p
            );
        }

        sb.append(
                "\nDIFENSORI\n"
        );

        for (Player p :
                best.defenders) {

            appendSelectedPlayer(
                    sb,
                    p
            );
        }

        sb.append(
                "\nCENTROCAMPISTI\n"
        );

        for (Player p :
                best.midfielders) {

            appendSelectedPlayer(
                    sb,
                    p
            );
        }

        sb.append(
                "\nATTACCANTI\n"
        );

        for (Player p :
                best.attackers) {

            appendSelectedPlayer(
                    sb,
                    p
            );
        }

        sb.append(
                "\n\n================================\n"
        );

        sb.append(
                "MODULI ANALIZZATI\n"
        );

        sb.append(
                "================================\n"
        );

        sb.append(
                "3-4-3\n" +
                "4-4-2\n" +
                "3-5-2\n" +
                "4-5-1\n" +
                "5-4-1\n"
        );

        resultText.setText(
                sb.toString()
        );
    }

    private void appendSelectedPlayer(
            StringBuilder sb,
            Player p
    ) {

        sb.append("• ")
                .append(
                        p.excelName
                )
                .append("  ")
                .append(
                        p.probable
                )
                .append("%");

        if (p.externalAgreement > 0) {

            sb.append(
                    "  ["
            )
                    .append(
                            p.externalAgreement
                    )
                    .append(
                            "/2 conferme"
                    )
                    .append(
                            "]"
                    );
        }

        sb.append("\n");
    }

    private String roleName(
            String role
    ) {

        switch (role) {

            case "P":
                return "PORTIERE";

            case "D":
                return "DIFENSORE";

            case "C":
                return "CENTROCAMPISTA";

            case "A":
                return "ATTACCANTE";

            default:
                return "SCONOSCIUTO";
        }
    }

    private String cleanName(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text
                .replace(
                        "\u00a0",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private String normalize(
            String text
    ) {

        if (text == null) {
            return "";
        }

        String normalized =
                Normalizer.normalize(
                        text,
                        Normalizer.Form.NFD
                );

        normalized =
                normalized.replaceAll(
                        "\\p{InCombiningDiacriticalMarks}+",
                        ""
                );

        normalized =
                normalized.toLowerCase(
                        Locale.ROOT
                );

        normalized =
                normalized.replaceAll(
                        "[^a-z0-9 ]",
                        " "
                );

        normalized =
                normalized.replaceAll(
                        "\\s+",
                        " "
                )
                .trim();

        return normalized;
    }

    private boolean isSerieATeam(
            String text
    ) {

        String normalized =
                normalize(text);

        String[] teams = {
                "inter",
                "napoli",
                "milan",
                "roma",
                "juventus",
                "atalanta",
                "bologna",
                "lazio",
                "fiorentina",
                "torino",
                "genoa",
                "como",
                "parma",
                "monza",
                "udinese",
                "sassuolo",
                "lecce",
                "cagliari",
                "venezia",
                "frosinone"
        };

        for (String team :
                teams) {

            if (normalized.equals(
                    team
            )) {

                return true;
            }
        }

        return false;
    }
}
