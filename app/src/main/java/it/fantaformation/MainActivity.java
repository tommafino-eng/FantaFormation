package it.fantaformation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private LinearLayout root;
    private TextView resultText;
    private Button sendToLegheButton;

    private List<String[]> formazione;
    private PlayerRoleCache roleCache;
    private ArrayList<Player> lastParsedPlayers;
    private FormationResult currentBestResult;

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

        roleCache = new PlayerRoleCache(this);
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

        Button editRolesButton = new Button(this);
        editRolesButton.setText("MODIFICA RUOLI SALVATI");
        root.addView(editRolesButton);

        sendToLegheButton = new Button(this);
        sendToLegheButton.setText("CARICA SU LEGHE FANTACALCIO");
        sendToLegheButton.setVisibility(View.GONE);
        root.addView(sendToLegheButton);

        Button openButton = new Button(this);
        openButton.setText("APRI FANTACALCIO (BROWSER)");
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

        editRolesButton.setOnClickListener(v -> openEditRolesDialog());

        sendToLegheButton.setOnClickListener(v -> openLegheWebView());

        openButton.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://leghe.fantacalcio.it/")
                );
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Impossibile aprire il browser", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openLegheWebView() {
        if (currentBestResult == null || !currentBestResult.valid) {
            Toast.makeText(this, "Nessuna formazione valida calcolata", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> playerList = new ArrayList<>();
        for (Player p : currentBestResult.goalkeeper) playerList.add(p.officialName.isEmpty() ? p.excelName : p.officialName);
        for (Player p : currentBestResult.defenders) playerList.add(p.officialName.isEmpty() ? p.excelName : p.officialName);
        for (Player p : currentBestResult.midfielders) playerList.add(p.officialName.isEmpty() ? p.excelName : p.officialName);
        for (Player p : currentBestResult.attackers) playerList.add(p.officialName.isEmpty() ? p.excelName : p.officialName);

        try {
            Intent intent = new Intent(this, LegheWebViewActivity.class);
            intent.putStringArrayListExtra("PLAYERS", playerList);
            intent.putExtra("MODULE", currentBestResult.module);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Aggiungi LegheWebViewActivity nel file AndroidManifest.xml!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Errore apertura WebView: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseExcel() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            Toast.makeText(this, "Impossibile aprire il selettore file", Toast.LENGTH_SHORT).show();
        }
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

                List<String[]> result =
                        XlsxReader.read(
                                MainActivity.this,
                                uri
                        );

                runOnUiThread(() -> {
                    if (isFinishing()) return;

                    if (result == null || result.isEmpty()) {
                        resultText.setText("File Excel vuoto o formato non valido.");
                        Toast.makeText(MainActivity.this, "Nessun dato letto dal file", Toast.LENGTH_LONG).show();
                        return;
                    }

                    formazione = result;

                    StringBuilder sb = new StringBuilder();
                    sb.append("FORMAZIONE ONE PISA\n");
                    sb.append("====================\n\n");

                    for (String[] row : result) {
                        String player = row.length > 1 ? row[1] : "";
                        String cost = row.length > 2 ? row[2] : "";

                        if (player.trim().isEmpty()) continue;

                        sb.append(player);

                        if (cost != null && !cost.trim().isEmpty()) {
                            sb.append("  -  ").append(cost);
                        }

                        sb.append("\n");
                    }

                    sb.append("\nTotale giocatori letti: ").append(result.size());
                    resultText.setText(sb.toString());

                    Toast.makeText(
                            MainActivity.this,
                            "Formazione caricata correttamente",
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } catch (Exception e) {

                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    resultText.setText(
                            "Errore lettura Excel:\n\n" +
                            e.getClass().getSimpleName() +
                            "\n" +
                            e.getMessage()
                    );
                });
            }
        });
    }

    private void analyzeFormation() {

        sendToLegheButton.setVisibility(View.GONE);
        resultText.setText(
                "Analisi in corso...\n\n" +
                "1. Recupero ruoli Classic ufficiali e cache locale\n" +
                "2. Recupero quotazioni/FVM\n" +
                "3. Recupero probabili Fantacalcio\n" +
                "4. Confronto Gazzetta\n" +
                "5. Confronto Sky\n" +
                "6. Calcolo delle formazioni ottimali\n"
        );

        executor.execute(() -> {

            try {

                String quotesHtml = downloadSafe(FANTACALCIO_QUOTE);
                String probabiliHtml = downloadSafe(FANTACALCIO_PROBABILI);
                String gazzettaHtml = downloadSafe(GAZZETTA_PROBABILI);
                String skyHtml = downloadSafe(SKY_PROBABILI);

                Map<String, OfficialPlayer> officialPlayers = parseOfficialPlayers(quotesHtml);
                Map<String, ProbabilityInfo> probabilities = parseFantacalcioProbabili(probabiliHtml);
                Map<String, Integer> gazzetta = parseExternalSource(gazzettaHtml);
                Map<String, Integer> sky = parseExternalSource(skyHtml);

                ArrayList<Player> players = buildPlayers(officialPlayers, probabilities, gazzetta, sky);
                lastParsedPlayers = players;

                FormationResult best = calculateBestFormation(players);
                currentBestResult = best;

                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    displayResult(players, best);
                });

            } catch (Exception e) {

                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    resultText.setText(
                            "Errore durante l'analisi:\n\n" +
                            e.getClass().getSimpleName() +
                            "\n" +
                            e.getMessage()
                    );
                });
            }
        });
    }

    private String downloadSafe(String address) {
        try {
            return download(address);
        } catch (Exception e) {
            return "";
        }
    }

    private String download(String address) throws Exception {

        URL url = new URL(address);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);

        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8");

        int code = connection.getResponseCode();

        if (code < 200 || code >= 400) {
            throw new Exception("HTTP " + code + " - " + address);
        }

        InputStream inputStream = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

        StringBuilder result = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line).append('\n');
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
            this.excelName = excelName;
            this.officialName = officialName;
            this.team = team;
            this.role = role != null ? role : "";
            this.quote = quote;
            this.fvm = fvm;
            this.probable = probable;
            this.externalAgreement = externalAgreement;
            this.starter = starter;
            this.bench = bench;
            calculateScore();
        }

        private void calculateScore() {
            score = 0;
            score += probable * 1.50;
            score += externalAgreement * 12.0;
            score += Math.min(fvm, 300) * 0.12;
            score += quote * 0.15;

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

    private Map<String, OfficialPlayer> parseOfficialPlayers(String html) {

        Map<String, OfficialPlayer> result = new LinkedHashMap<>();
        if (html == null || html.isEmpty()) return result;

        Document document = Jsoup.parse(html);
        Elements links = document.select("a[href*='/serie-a/squadre/']");

        for (Element link : links) {

            String name = cleanName(link.text());
            if (name.isEmpty()) continue;

            String href = link.absUrl("href");
            if (href.isEmpty()) href = link.attr("href");

            Element row = link.closest("tr");
            if (row == null) row = link.parent();

            String rowText = row != null ? row.text() : "";

            OfficialPlayer player = new OfficialPlayer();
            player.name = name;
            player.profileUrl = href;
            player.team = extractTeam(rowText);
            player.classicQuote = extractClassicQuote(row);
            player.fvm = extractFvm(row);
            player.role = extractRoleFromPlayerElement(link, row);

            if (player.role.isEmpty()) {
                player.role = extractRoleFromAttributes(row);
            }

            String key = normalize(player.name);
            if (!result.containsKey(key)) {
                result.put(key, player);
            }
        }

        return result;
    }

    private String extractRoleFromPlayerElement(Element link, Element row) {
        String[] attributes = {"data-role", "data-ruolo", "role", "title", "aria-label", "class"};

        for (String attribute : attributes) {
            String value = link.attr(attribute);
            String role = roleFromText(value);
            if (!role.isEmpty()) return role;
        }

        if (row != null) {
            Elements elements = row.select("[data-role], [data-ruolo], [title], [aria-label]");
            for (Element element : elements) {
                for (String attribute : attributes) {
                    String value = element.attr(attribute);
                    String role = roleFromText(value);
                    if (!role.isEmpty()) return role;
                }
            }
        }

        return "";
    }

    private String extractRoleFromAttributes(Element row) {
        if (row == null) return "";
        return roleFromText(row.outerHtml());
    }

    private String roleFromText(String text) {
        if (text == null) return "";
        String normalized = normalize(text);

        if (normalized.contains("portiere")) return "P";
        if (normalized.contains("difensore")) return "D";
        if (normalized.contains("centrocampista")) return "C";
        if (normalized.contains("attaccante")) return "A";

        if (normalized.matches(".*\\bp\\b.*")) return "P";
        if (normalized.matches(".*\\bd\\b.*")) return "D";
        if (normalized.matches(".*\\bc\\b.*")) return "C";
        if (normalized.matches(".*\\ba\\b.*")) return "A";

        return "";
    }

    private String extractTeam(String text) {
        if (text == null) return "";
        String upper = text.toUpperCase(Locale.ROOT);
        String[] teams = {"INT", "COM", "MIL", "ROM", "NAP", "JUV", "ATA", "BOL", "LAZ", "FIO", "PAR", "GEN", "MON", "TOR", "UDI", "SAS", "LEC", "CAG", "VEN", "FRO"};

        for (String team : teams) {
            if (upper.contains(team)) return team;
        }

        return "";
    }

    private int extractClassicQuote(Element row) {
        if (row == null) return 0;
        Elements cells = row.select("td");
        for (Element cell : cells) {
            String text = cell.text().trim();
            if (text.matches("\\d+")) {
                try {
                    return Integer.parseInt(text);
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private int extractFvm(Element row) {
        if (row == null) return 0;
        Elements cells = row.select("td");
        ArrayList<Integer> numbers = new ArrayList<>();
        for (Element cell : cells) {
            String text = cell.text().trim();
            if (text.matches("\\d+")) {
                try {
                    numbers.add(Integer.parseInt(text));
                } catch (Exception ignored) {}
            }
        }
        if (numbers.size() >= 3) return numbers.get(2);
        return 0;
    }

    private Map<String, ProbabilityInfo> parseFantacalcioProbabili(String html) {
        Map<String, ProbabilityInfo> result = new HashMap<>();
        if (html == null || html.isEmpty()) return result;

        Document document = Jsoup.parse(html);
        Elements playerLinks = document.select("a[href*='/serie-a/squadre/'], .player-name, [data-player]");

        for (Element link : playerLinks) {
            String name = cleanName(link.text());
            if (name.isEmpty()) continue;

            Element parent = link.parent();
            if (parent == null) continue;

            // Ricerca estesa della percentuale nel blocco del giocatore
            Element container = link.closest("li, tr, .player-item, .player-row, div");
            String blockText = container != null ? container.text() : parent.text();

            int percentage = extractPercentageFromText(blockText);
            boolean bench = isInsideBench(link) || blockText.toLowerCase(Locale.ROOT).contains("panchina");

            String key = normalize(name);
            ProbabilityInfo info = result.get(key);

            if (info == null) {
                info = new ProbabilityInfo();
                result.put(key, info);
            }

            if (percentage > info.percentage || (percentage > 0 && info.percentage == 0)) {
                info.percentage = percentage;
                info.starter = !bench;
                info.bench = bench;
            }
        }

        return result;
    }

    private int extractPercentageFromText(String text) {
        if (text == null) return 0;
        Pattern pattern = Pattern.compile("(\\d{1,3})\\s*%");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                int val = Integer.parseInt(matcher.group(1));
                if (val <= 100) return val;
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private boolean isInsideBench(Element element) {
        Element current = element;
        for (int i = 0; i < 8 && current != null; i++) {
            String text = current.text().toLowerCase(Locale.ROOT);
            if (text.startsWith("panchina") || text.contains("panchina")) return true;
            current = current.parent();
        }
        return false;
    }

    private Map<String, Integer> parseExternalSource(String html) {
        Map<String, Integer> result = new HashMap<>();
        if (html == null || html.isEmpty() || formazione == null) return result;

        Document document = Jsoup.parse(html);
        String text = document.text();

        for (String[] row : formazione) {
            if (row.length < 2) continue;
            String excelName = row[1];
            String normalized = normalize(excelName);
            if (normalized.isEmpty()) continue;

            if (textContainsPlayer(text, excelName)) {
                result.put(normalized, 1);
            }
        }

        return result;
    }

    private boolean textContainsPlayer(String text, String player) {
        String a = normalize(text);
        String b = normalize(player);
        if (b.length() < 3) return false;
        return a.contains(b);
    }

    private ArrayList<Player> buildPlayers(
            Map<String, OfficialPlayer> official,
            Map<String, ProbabilityInfo> probabilities,
            Map<String, Integer> gazzetta,
            Map<String, Integer> sky
    ) {

        ArrayList<Player> result = new ArrayList<>();
        if (formazione == null) return result;

        for (String[] row : formazione) {
            if (row.length < 2) continue;
            String excelName = row[1];
            if (excelName == null || excelName.trim().isEmpty()) continue;

            String normalizedExcel = normalize(excelName);
            String cachedRole = roleCache.getRole(normalizedExcel);

            OfficialPlayer officialPlayer = findOfficialPlayer(excelName, official);

            if (officialPlayer == null) {
                Player player = new Player(
                        excelName,
                        excelName,
                        "",
                        cachedRole,
                        0, 0, 0, 0,
                        false, false
                );
                result.add(player);
                continue;
            }

            String normalizedOfficialName = normalize(officialPlayer.name);
            if (cachedRole.isEmpty()) {
                cachedRole = roleCache.getRole(normalizedOfficialName);
            }

            String role = !cachedRole.isEmpty() ? cachedRole : officialPlayer.role;

            ProbabilityInfo probability = probabilities.get(normalizedOfficialName);
            if (probability == null) {
                probability = probabilities.get(normalizedExcel);
            }

            int probable = probability != null ? probability.percentage : 0;
            boolean starter = probability != null && probability.starter;
            boolean bench = probability != null && probability.bench;

            int externalAgreement = 0;
            if (gazzetta.containsKey(normalizedOfficialName) || gazzetta.containsKey(normalizedExcel)) externalAgreement++;
            if (sky.containsKey(normalizedOfficialName) || sky.containsKey(normalizedExcel)) externalAgreement++;

            Player player = new Player(
                    excelName,
                    officialPlayer.name,
                    officialPlayer.team,
                    role,
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

    private OfficialPlayer findOfficialPlayer(String excelName, Map<String, OfficialPlayer> official) {
        String normalized = normalize(excelName);
        OfficialPlayer exact = official.get(normalized);
        if (exact != null) return exact;

        for (Map.Entry<String, OfficialPlayer> entry : official.entrySet()) {
            if (similarNames(normalized, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean similarNames(String a, String b) {
        if (a.equals(b) || a.contains(b) || b.contains(a)) return true;

        String[] aa = a.split(" ");
        String[] bb = b.split(" ");

        if (aa.length >= 2 && bb.length >= 2) {
            String firstA = aa[0], lastA = aa[aa.length - 1];
            String firstB = bb[0], lastB = bb[bb.length - 1];

            if (firstA.equals(firstB) && lastA.equals(lastB)) return true;
            if (firstA.equals(lastB) && lastA.equals(firstB)) return true;
        }

        return false;
    }

    private static class FormationResult {
        String module = "NESSUNA";
        ArrayList<Player> goalkeeper = new ArrayList<>();
        ArrayList<Player> defenders = new ArrayList<>();
        ArrayList<Player> midfielders = new ArrayList<>();
        ArrayList<Player> attackers = new ArrayList<>();
        double score = 0;
        boolean valid = false;
    }

    private FormationResult calculateBestFormation(ArrayList<Player> players) {
        FormationResult best = null;

        for (String module : ALLOWED_FORMATIONS) {
            FormationResult result = calculateFormation(module, players);
            if (!result.valid) continue;

            if (best == null || result.score > best.score) {
                best = result;
            }
        }

        if (best == null) {
            FormationResult empty = new FormationResult();
            empty.module = "NESSUNA";
            empty.valid = false;
            return empty;
        }

        return best;
    }

    private FormationResult calculateFormation(String module, ArrayList<Player> players) {
        FormationResult result = new FormationResult();
        result.module = module;

        String[] parts = module.split("-");
        int defenders = Integer.parseInt(parts[0]);
        int midfielders = Integer.parseInt(parts[1]);
        int attackers = Integer.parseInt(parts[2]);

        List<Player> goalkeepers = playersForRole(players, "P");
        List<Player> defenderList = playersForRole(players, "D");
        List<Player> midfielderList = playersForRole(players, "C");
        List<Player> attackerList = playersForRole(players, "A");

        if (goalkeepers.isEmpty() || defenderList.size() < defenders ||
                midfielderList.size() < midfielders || attackerList.size() < attackers) {
            result.valid = false;
            return result;
        }

        sortByScore(goalkeepers);
        sortByScore(defenderList);
        sortByScore(midfielderList);
        sortByScore(attackerList);

        result.goalkeeper.add(goalkeepers.get(0));
        result.defenders.addAll(defenderList.subList(0, defenders));
        result.midfielders.addAll(midfielderList.subList(0, midfielders));
        result.attackers.addAll(attackerList.subList(0, attackers));

        result.score = 0;
        for (Player p : result.goalkeeper) result.score += p.score;
        for (Player p : result.defenders) result.score += p.score;
        for (Player p : result.midfielders) result.score += p.score;
        for (Player p : result.attackers) result.score += p.score;

        result.valid = true;
        return result;
    }

    private List<Player> playersForRole(ArrayList<Player> players, String role) {
        ArrayList<Player> result = new ArrayList<>();
        for (Player player : players) {
            if (role.equals(player.role)) {
                result.add(player);
            }
        }
        return result;
    }

    private void sortByScore(List<Player> players) {
        Collections.sort(players, (a, b) -> Double.compare(b.score, a.score));
    }

    private void displayResult(ArrayList<Player> players, FormationResult best) {

        StringBuilder sb = new StringBuilder();
        sb.append("ANALISI COMPLETATA\n==================\n\nRUOLI CLASSIC RILEVATI\n\n");

        ArrayList<Player> playersWithoutRole = new ArrayList<>();

        for (Player player : players) {
            sb.append(player.excelName).append(" → ");

            if (player.role == null || player.role.isEmpty()) {
                sb.append("RUOLO NON DETERMINATO");
                playersWithoutRole.add(player);
            } else {
                sb.append(roleName(player.role));
            }

            sb.append("\n");

            if (!player.officialName.equals(player.excelName)) {
                sb.append("   Nome ufficiale: ").append(player.officialName).append("\n");
            }
            if (!player.team.isEmpty()) {
                sb.append("   Squadra: ").append(player.team).append("\n");
            }

            sb.append("   Probabile: ").append(player.probable).append("%\n");
            sb.append("   FVM: ").append(player.fvm).append("\n");
            sb.append("   Conferme Gazzetta/Sky: ").append(player.externalAgreement).append("/2\n\n");
        }

        sb.append("\n");

        if (!playersWithoutRole.isEmpty()) {
            showRoleAssignmentDialog(playersWithoutRole, players);
            return;
        }

        currentBestResult = best;
        if (best != null && best.valid) {
            sendToLegheButton.setVisibility(View.VISIBLE);
        } else {
            sendToLegheButton.setVisibility(View.GONE);
        }

        displayFormationResult(sb, best);
    }

    private void showRoleAssignmentDialog(ArrayList<Player> playersWithoutRole, ArrayList<Player> allPlayers) {
        showRoleDialogForPlayer(playersWithoutRole, 0, allPlayers);
    }

    private void showRoleDialogForPlayer(ArrayList<Player> playersWithoutRole, int playerIndex, ArrayList<Player> allPlayers) {

        if (playerIndex >= playersWithoutRole.size()) {
            FormationResult recalculatedBest = calculateBestFormation(allPlayers);
            currentBestResult = recalculatedBest;
            displayResult(allPlayers, recalculatedBest);
            return;
        }

        if (isFinishing()) return;

        Player player = playersWithoutRole.get(playerIndex);
        String[] roleOptions = {"PORTIERE (P)", "DIFENSORE (D)", "CENTROCAMPISTA (C)", "ATTACCANTE (A)"};

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Assegna ruolo a: " + player.excelName);

        builder.setItems(roleOptions, (dialog, which) -> {
            String selectedRole = "";
            switch (which) {
                case 0: selectedRole = "P"; break;
                case 1: selectedRole = "D"; break;
                case 2: selectedRole = "C"; break;
                case 3: selectedRole = "A"; break;
            }

            String normalizedExcelName = normalize(player.excelName);
            String normalizedOfficialName = normalize(player.officialName.isEmpty() ? player.excelName : player.officialName);

            roleCache.setRole(normalizedExcelName, selectedRole);
            roleCache.setRole(normalizedOfficialName, selectedRole);

            player.role = selectedRole;

            showRoleDialogForPlayer(playersWithoutRole, playerIndex + 1, allPlayers);
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void openEditRolesDialog() {
        if (formazione == null || formazione.isEmpty()) {
            Toast.makeText(this, "Prima carica la formazione Excel per gestire i ruoli", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> playerNames = new ArrayList<>();
        List<String> playerKeys = new ArrayList<>();

        for (String[] row : formazione) {
            if (row.length > 1 && row[1] != null && !row[1].trim().isEmpty()) {
                String name = row[1].trim();
                String key = normalize(name);
                String currentRole = roleCache.getRole(key);

                String label = name + (currentRole.isEmpty() ? " [Ruolo da assegnare]" : " [" + roleName(currentRole) + "]");
                playerNames.add(label);
                playerKeys.add(name);
            }
        }

        if (playerNames.isEmpty()) {
            Toast.makeText(this, "Nessun giocatore trovato nella rosa", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Seleziona giocatore da modificare");
        builder.setItems(playerNames.toArray(new String[0]), (dialog, which) -> {
            String selectedPlayerName = playerKeys.get(which);
            promptChangeRoleForPlayer(selectedPlayerName);
        });

        builder.setNegativeButton("Annulla", null);
        builder.show();
    }

    private void promptChangeRoleForPlayer(String playerName) {
        String[] roleOptions = {"PORTIERE (P)", "DIFENSORE (D)", "CENTROCAMPISTA (C)", "ATTACCANTE (A)"};

        if (isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nuovo ruolo per: " + playerName);

        builder.setItems(roleOptions, (dialog, which) -> {
            String selectedRole = "";
            switch (which) {
                case 0: selectedRole = "P"; break;
                case 1: selectedRole = "D"; break;
                case 2: selectedRole = "C"; break;
                case 3: selectedRole = "A"; break;
            }

            String key = normalize(playerName);
            roleCache.setRole(key, selectedRole);

            Toast.makeText(this, "Ruolo aggiornato per " + playerName, Toast.LENGTH_SHORT).show();

            if (lastParsedPlayers != null) {
                for (Player p : lastParsedPlayers) {
                    if (normalize(p.excelName).equals(key) || normalize(p.officialName).equals(key)) {
                        p.role = selectedRole;
                    }
                }
                FormationResult newBest = calculateBestFormation(lastParsedPlayers);
                currentBestResult = newBest;
                displayResult(lastParsedPlayers, newBest);
            }
        });

        builder.setNegativeButton("Annulla", null);
        builder.show();
    }

    private void displayFormationResult(StringBuilder sb, FormationResult best) {

        if (best == null || !best.valid) {
            sb.append("NON È STATO POSSIBILE CREARE UNA FORMAZIONE VALIDA.\n\n");
            sb.append("Uno o più ruoli Classic non sono stati riconosciuti o mancano giocatori a sufficienza per completare un modulo.");
            resultText.setText(sb.toString());
            return;
        }

        sb.append("================================\nFORMAZIONE OTTIMALE\n================================\n\n");
        sb.append("MODULO: ").append(best.module).append("\n");
        sb.append("PUNTEGGIO: ").append(String.format(Locale.US, "%.1f", best.score)).append("\n\n");

        sb.append("PORTIERE\n");
        for (Player p : best.goalkeeper) appendSelectedPlayer(sb, p);

        sb.append("\nDIFENSORI\n");
        for (Player p : best.defenders) appendSelectedPlayer(sb, p);

        sb.append("\nCENTROCAMPISTI\n");
        for (Player p : best.midfielders) appendSelectedPlayer(sb, p);

        sb.append("\nATTACCANTI\n");
        for (Player p : best.attackers) appendSelectedPlayer(sb, p);

        sb.append("\n\n================================\nMODULI ANALIZZATI\n================================\n");
        sb.append("3-4-3\n4-4-2\n3-5-2\n4-5-1\n5-4-1\n");

        resultText.setText(sb.toString());
    }

    private void appendSelectedPlayer(StringBuilder sb, Player p) {
        sb.append("• ").append(p.excelName).append("  ").append(p.probable).append("%");
        if (p.externalAgreement > 0) {
            sb.append("  [").append(p.externalAgreement).append("/2 conferme]");
        }
        sb.append("\n");
    }

    private String roleName(String role) {
        if (role == null) return "SCONOSCIUTO";
        switch (role) {
            case "P": return "PORTIERE";
            case "D": return "DIFENSORE";
            case "C": return "CENTROCAMPISTA";
            case "A": return "ATTACCANTE";
            default: return "SCONOSCIUTO";
        }
    }

    private String cleanName(String text) {
        if (text == null) return "";
        return text.replace("\u00a0", " ").replaceAll("\\s+", " ").trim();
    }

    private String normalize(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9 ]", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private boolean isSerieATeam(String text) {
        String normalized = normalize(text);
        String[] teams = {"inter", "napoli", "milan", "roma", "juventus", "atalanta", "bologna", "lazio", "fiorentina", "torino", "genoa", "como", "parma", "monza", "udinese", "sassuolo", "lecce", "cagliari", "venezia", "frosinone"};

        for (String team : teams) {
            if (normalized.equals(team)) return true;
        }

        return false;
    }
}