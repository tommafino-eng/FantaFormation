package it.fantaformation;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
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
    private ScrollView scrollView;
    private TextView resultText;
    private Button sendToLegheButton;

    private List<String[]> formazione;
    private PlayerRoleCache roleCache;
    private CredentialsManager credentialsManager;
    private ArrayList<Player> lastParsedPlayers;
    private FormationResult currentBestResult;
    private boolean pendingAutoFill = false;
    private boolean autoFlowEnabled = false;
    private boolean loginAttempted = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String FANTACALCIO_QUOTE = "https://www.fantacalcio.it/quotazioni-fantacalcio/2026-27";
    private static final String FANTACALCIO_PROBABILI = "https://www.fantacalcio.it/probabili-formazioni-serie-a";
    private static final String GAZZETTA_PROBABILI = "https://www.gazzetta.it/Calcio/prob_form/";
    private static final String SKY_PROBABILI = "https://sport.sky.it/calcio/serie-a/probabili-formazioni";
    private static final String LEGA_HOME_URL = "https://leghe.fantacalcio.it/yoooo/";
    private static final String LEGA_FORMATION_URL = "https://leghe.fantacalcio.it/yoooo/view/competition/208800/lineup/new";

    private static final Set<String> ALLOWED_FORMATIONS = new HashSet<>(Arrays.asList(
            "3-4-3", "4-4-2", "3-5-2", "4-5-1", "5-4-1"
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        roleCache = new PlayerRoleCache(this);
        credentialsManager = new CredentialsManager(this);
        buildInterface();
    }

    private void buildInterface() {
        scrollView = new ScrollView(this);

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

        Button setCredsButton = new Button(this);
        setCredsButton.setText("🔑 IMPOSTA CREDEZIALI LEGHE");
        root.addView(setCredsButton);

        sendToLegheButton = new Button(this);
        sendToLegheButton.setText("CARICA SU LEGHE FANTACALCIO");
        sendToLegheButton.setVisibility(View.GONE);
        root.addView(sendToLegheButton);

        Button openButton = new Button(this);
        openButton.setText("APRI FANTACALCIO (BROWSER ESTERNO)");
        root.addView(openButton);

        resultText = new TextView(this);
        resultText.setTextSize(15);
        resultText.setPadding(0, 30, 0, 30);
        root.addView(resultText);

        scrollView.addView(root);
        setContentView(scrollView);

        loadButton.setOnClickListener(v -> chooseExcel());
        automateButton.setOnClickListener(v -> {
            if (formazione == null || formazione.isEmpty()) {
                Toast.makeText(MainActivity.this, "Prima carica la formazione Excel", Toast.LENGTH_LONG).show();
                return;
            }
            analyzeFormation();
        });

        editRolesButton.setOnClickListener(v -> openEditRolesDialog());
        setCredsButton.setOnClickListener(v -> promptSaveCredentials());
        sendToLegheButton.setOnClickListener(v -> openLegheWebViewDialog());

        openButton.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://leghe.fantacalcio.it/"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Impossibile aprire il browser", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openLegheWebViewDialog() {
        if (currentBestResult == null || !currentBestResult.valid) {
            Toast.makeText(this, "Nessuna formazione valida calcolata", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(10, 10, 10, 10);

        Button autoLoginButton = new Button(this);
        autoLoginButton.setText("🔑 LOGIN");

        Button autoFillButton = new Button(this);
        autoFillButton.setText("⚡ INSERISCI");

        Button copyButton = new Button(this);
        copyButton.setText("📋 COPIA");

        Button closeButton = new Button(this);
        closeButton.setText("❌ CHIUDI");

        actionBar.addView(autoLoginButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));
        actionBar.addView(autoFillButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
        actionBar.addView(copyButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));
        actionBar.addView(closeButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));

        layout.addView(actionBar);

        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (url == null) return;

                // Flusso completamente automatico: login -> home della lega yoooo -> Schiera Formazione.
                if (autoFlowEnabled) {
                    if (url.contains("/login/")) {
                        if (!loginAttempted) {
                            loginAttempted = true;
                            view.postDelayed(() -> executeAutoLoginScript(view), 900);
                        }
                        return;
                    }

                    // Dopo il login andiamo DIRETTAMENTE alla pagina della formazione.
                    // Non passiamo più dalla home della lega e non cerchiamo link nel DOM.
                    if (url.contains("/view/competition/208800/lineup/new")) {
                        if (pendingAutoFill) {
                            pendingAutoFill = false;
                            view.postDelayed(() -> executeAutoFillScript(view), 1200);
                        }
                        return;
                    }

                    if (url.contains("/area-gioco/inserisci-formazione")) {
                        if (pendingAutoFill) {
                            pendingAutoFill = false;
                            view.postDelayed(() -> executeAutoFillScript(view), 1200);
                        }
                        return;
                    }

                    // Qualsiasi pagina della lega raggiunta dopo il login viene portata
                    // direttamente alla pagina di inserimento della formazione.
                    if (url.contains("leghe.fantacalcio.it") &&
                            !url.contains("/area-gioco/inserisci-formazione") &&
                            !url.contains("/login/") &&
                            !url.contains("/view/competition/208800/lineup/new")) {
                        view.postDelayed(() -> view.loadUrl(LEGA_FORMATION_URL), 300);
                    }
                }
            }
        });

        // Bridge used only for reporting asynchronous JavaScript results.
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void report(String message) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show()
                );
            }

            @JavascriptInterface
            public void openFormation(String url) {
                if (url == null || url.trim().isEmpty()) return;
                String safe = url.trim();
                if (!safe.startsWith("https://leghe.fantacalcio.it/")) return;
                runOnUiThread(() -> {
                    pendingAutoFill = true;
                    webView.loadUrl(safe);
                });
            }
        }, "AndroidBridge");

        layout.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        dialog.setContentView(layout);
        dialog.show();

        autoFlowEnabled = true;
        loginAttempted = false;
        pendingAutoFill = true;
        webView.loadUrl("https://leghe.fantacalcio.it/login/area-gioco/formazioni");

        autoLoginButton.setOnClickListener(v -> {
            autoFlowEnabled = true;
            loginAttempted = false;
            executeAutoLoginScript(webView);
        });
        autoFillButton.setOnClickListener(v -> executeAutoFillScript(webView));
        copyButton.setOnClickListener(v -> copyFormationToClipboard());
        closeButton.setOnClickListener(v -> dialog.dismiss());
    }

    private void openSchieraFormazioneFromDashboard(WebView webView) {
        // Dopo il login, entra direttamente nella pagina di inserimento della competizione.
        webView.loadUrl(LEGA_FORMATION_URL);
    }

    private void promptSaveCredentials() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10);

        final EditText userInput = new EditText(this);
        userInput.setHint("Email / Username Fantacalcio");
        userInput.setText(credentialsManager.getUsername());
        layout.addView(userInput);

        final EditText passInput = new EditText(this);
        passInput.setHint("Password");
        passInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passInput.setText(credentialsManager.getPassword());
        layout.addView(passInput);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Credenziali Leghe Fantacalcio");
        builder.setView(layout);

        builder.setPositiveButton("Salva", (dialog, which) -> {
            String user = userInput.getText().toString();
            String pass = passInput.getText().toString();
            credentialsManager.saveCredentials(user, pass);
            Toast.makeText(this, "Credenziali salvate!", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Annulla", null);
        builder.show();
    }

    private String cleanJsResult(String value, String fallback) {
        if (value == null || value.isEmpty()) return fallback;
        String msg = value;
        if (msg.length() >= 2 && msg.charAt(0) == '"' && msg.charAt(msg.length() - 1) == '"') {
            msg = msg.substring(1, msg.length() - 1);
        }
        msg = msg.replace("\\\"", "\"").replace("\\\\", "\\");
        return msg;
    }

    private void executeAutoLoginScript(WebView webView) {
        if (!credentialsManager.hasCredentials()) {
            Toast.makeText(this, "Imposta prima le credenziali col tasto 🔑", Toast.LENGTH_LONG).show();
            promptSaveCredentials();
            return;
        }

        String currentUrl = webView.getUrl();
        if (currentUrl == null || !currentUrl.contains("/login/")) {
            webView.loadUrl("https://leghe.fantacalcio.it/login/area-gioco/formazioni");
            Toast.makeText(this, "Apro la pagina di login...", Toast.LENGTH_SHORT).show();
            return;
        }

        String user = credentialsManager.getUsername()
                .replace("\\", "\\\\").replace("'", "\\'");
        String pass = credentialsManager.getPassword()
                .replace("\\", "\\\\").replace("'", "\\'");

        String js = "(async function() {" +
                "const USER='" + user + "';" +
                "const PASS='" + pass + "';" +
                "const sleep=ms=>new Promise(r=>setTimeout(r,ms));" +
                "function visible(e){return e && e.offsetParent!==null;}" +
                "function setNative(e,v){" +
                " if(!e)return false;" +
                " const proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;" +
                " const d=Object.getOwnPropertyDescriptor(proto,'value');" +
                " if(d&&d.set)d.set.call(e,v);else e.value=v;" +
                " ['input','change','blur','keyup'].forEach(n=>e.dispatchEvent(new Event(n,{bubbles:true}))); return true;" +
                "}" +
                "function find(sel){for(const e of document.querySelectorAll(sel))if(visible(e))return e;return null;}" +
                "let u=null,p=null;" +
                "for(let i=0;i<30&&(!u||!p);i++){" +
                " u=find('input[placeholder*=\"Username\" i],input[placeholder*=\"email\" i],input[type=email],input[name=email],input[name=username],input[id=email],input[id=username],input[autocomplete=username],input[autocomplete=email]');" +
                " p=find('input[placeholder*=\"Password\" i],input[type=password],input[name=password],input[id=password],input[autocomplete=current-password]');" +
                " if(!u||!p)await sleep(300);" +
                "}" +
                "if(!u||!p){AndroidBridge.report('LOGIN: campi Username/Password non trovati');return;}" +
                "setNative(u,USER);setNative(p,PASS);await sleep(400);" +
                "let b=find('button[type=submit],input[type=submit],button');" +
                "if(b){b.click();AndroidBridge.report('LOGIN: credenziali inserite, accesso in corso...');return;}" +
                "let f=u.closest('form')||p.closest('form');" +
                "if(f){if(f.requestSubmit)f.requestSubmit();else f.submit();AndroidBridge.report('LOGIN: form inviato');return;}" +
                "AndroidBridge.report('LOGIN: campi compilati, premi LOGIN');" +
                "})().catch(e=>AndroidBridge.report('LOGIN ERRORE: '+(e&&e.message?e.message:e)));";

        // Do NOT return the Promise to evaluateJavascript: Zone.js would expose it as
        // an object such as __zone_symbol__state/value. The bridge reports the real result.
        webView.evaluateJavascript(js, value -> {
            Toast.makeText(MainActivity.this, "Automazione login avviata", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Script JS avanzato asincrono con pausa tra i click ed eventi touch reali
     */
    private void executeAutoFillScript(WebView webView) {
        if (currentBestResult == null || !currentBestResult.valid) {
            Toast.makeText(this, "Nessuna formazione da inserire", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUrl = webView.getUrl();
        if (currentUrl == null || !currentUrl.contains("/area-gioco/inserisci-formazione")) {
            autoFlowEnabled = true;
            pendingAutoFill = true;
            if (currentUrl != null && (currentUrl.equalsIgnoreCase(LEGA_HOME_URL) || currentUrl.equalsIgnoreCase("https://leghe.fantacalcio.it/yoooo"))) {
                openSchieraFormazioneFromDashboard(webView);
            } else if (currentUrl != null && currentUrl.contains("/login/")) {
                loginAttempted = false;
                executeAutoLoginScript(webView);
            } else {
                webView.loadUrl("https://leghe.fantacalcio.it/login/area-gioco/formazioni");
                Toast.makeText(this, "Apro login e poi entro automaticamente nella lega...", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        ArrayList<String> selectedPlayers = new ArrayList<>();
        for (Player p : currentBestResult.goalkeeper)
            selectedPlayers.add(p.officialName.isEmpty() ? p.excelName : p.officialName);
        for (Player p : currentBestResult.defenders)
            selectedPlayers.add(p.officialName.isEmpty() ? p.excelName : p.officialName);
        for (Player p : currentBestResult.midfielders)
            selectedPlayers.add(p.officialName.isEmpty() ? p.excelName : p.officialName);
        for (Player p : currentBestResult.attackers)
            selectedPlayers.add(p.officialName.isEmpty() ? p.excelName : p.officialName);

        StringBuilder jsArray = new StringBuilder("[");
        for (int i = 0; i < selectedPlayers.size(); i++) {
            if (i > 0) jsArray.append(",");
            jsArray.append("'").append(selectedPlayers.get(i)
                    .replace("\\", "\\\\").replace("'", "\\'")).append("'");
        }
        jsArray.append("]");

        String targetModule = currentBestResult.module
                .replace("\\", "\\\\").replace("'", "\\'");

        String js = "(async function() {" +
                "const moduleName='" + targetModule + "';" +
                "const players=" + jsArray + ";" +
                "const sleep=ms=>new Promise(r=>setTimeout(r,ms));" +
                "const norm=s=>String(s||'').toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').replace(/[^a-z0-9 ]/g,' ').replace(/\\s+/g,' ').trim();" +
                "function visible(e){return e&&e.offsetParent!==null;}" +
                "function fire(e){if(!e)return false;e.scrollIntoView({block:'center',inline:'center'});try{e.click();}catch(x){};['pointerdown','mousedown','pointerup','mouseup','click'].forEach(n=>{try{e.dispatchEvent(new MouseEvent(n,{bubbles:true,cancelable:true,view:window}))}catch(x){}});return true;}" +
                "function text(e){return norm(e.innerText||e.textContent||e.getAttribute('aria-label')||e.getAttribute('title')||e.value);}" +
                "function findModule(){for(const e of document.querySelectorAll('button,a,[role=button],[role=option],[role=radio],select,option,label,[data-module]')){if(!visible(e))continue;const t=text(e);if(t===norm(moduleName)||t.includes(norm(moduleName))){fire(e);return true;}}return false;}" +
                "function findPlusForRole(role){const els=[...document.querySelectorAll('button,a,[role=button],div,span')].filter(visible);for(const e of els){if(text(e)!=='+')continue;let p=e;for(let i=0;i<5&&p;i++,p=p.parentElement){const t=' '+text(p)+' ';if(t.includes(' '+role.toLowerCase()+' '))return e;}}return null;}" +
                "function findPlayer(name){const n=norm(name);const last=n.split(' ').pop();let best=null,bestScore=0;for(const e of document.querySelectorAll('button,a,[role=button],li,tr,div,span,label')){if(!visible(e))continue;const t=text(e);if(!t)continue;let score=0;if(t===n)score=100;if(t.includes(n))score=90;if(t.includes(last))score=Math.max(score,40);if(score>bestScore){best=e;bestScore=score;}}return best;}" +
                "function roleOf(i){return i===0?'P':(i<" + currentBestResult.goalkeeper.size() + "?'D':'X');}" +
                "findModule();await sleep(900);" +
                "let added=0;" +
                "const roleNames=['P','D','C','A'];" +
                "let roleIndex=0, roleCounts={P:" + currentBestResult.goalkeeper.size() + ",D:" + currentBestResult.defenders.size() + ",C:" + currentBestResult.midfielders.size() + ",A:" + currentBestResult.attackers.size() + "};" +
                "for(const wanted of players){" +
                " let role='A';" +
                " if(added<roleCounts.P)role='P';" +
                " else if(added<roleCounts.P+roleCounts.D)role='D';" +
                " else if(added<roleCounts.P+roleCounts.D+roleCounts.C)role='C';" +
                " const plus=findPlusForRole(role);" +
                " if(!plus){AndroidBridge.report('INSERISCI: slot '+role+' non trovato per '+wanted);break;}" +
                " fire(plus);await sleep(500);" +
                " let player=null;" +
                " for(let r=0;r<12&&!player;r++){player=findPlayer(wanted);if(!player)await sleep(250);}" +
                " if(!player){AndroidBridge.report('INSERISCI: '+wanted+' non trovato nella rosa');continue;}" +
                " fire(player);await sleep(650);added++;" +
                "}" +
                "await sleep(500);" +
                "let saved=false;" +
                "for(const e of document.querySelectorAll('button,a,[role=button],input[type=submit]')){if(!visible(e))continue;const t=text(e);if(t.includes('salva formazione')||t==='salva'||t.includes('salva')){fire(e);saved=true;break;}}" +
                "AndroidBridge.report('INSERISCI: '+added+'/'+players.length+' giocatori inseriti | Salvataggio '+(saved?'avviato':'non trovato'));" +
                "})().catch(e=>AndroidBridge.report('INSERISCI ERRORE: '+(e&&e.message?e.message:e)));";

        webView.evaluateJavascript(js, value -> {
            Toast.makeText(MainActivity.this, "Automazione formazione avviata", Toast.LENGTH_SHORT).show();
        });
    }


    private void copyFormationToClipboard() {
        if (currentBestResult == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Modulo: ").append(currentBestResult.module).append("\n\nTitolari:\n");

        for (Player p : currentBestResult.goalkeeper) sb.append("- ").append(p.officialName.isEmpty() ? p.excelName : p.officialName).append("\n");
        for (Player p : currentBestResult.defenders) sb.append("- ").append(p.officialName.isEmpty() ? p.excelName : p.officialName).append("\n");
        for (Player p : currentBestResult.midfielders) sb.append("- ").append(p.officialName.isEmpty() ? p.excelName : p.officialName).append("\n");
        for (Player p : currentBestResult.attackers) sb.append("- ").append(p.officialName.isEmpty() ? p.excelName : p.officialName).append("\n");

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Formazione Fanta", sb.toString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Formazione copiata negli appunti!", Toast.LENGTH_SHORT).show();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != 100 || resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();

        executor.execute(() -> {
            try {
                List<String[]> result = XlsxReader.read(MainActivity.this, uri);

                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (result == null || result.isEmpty()) {
                        resultText.setText("File Excel vuoto o non valido.");
                        return;
                    }

                    formazione = result;
                    StringBuilder sb = new StringBuilder();
                    sb.append("FORMAZIONE ONE PISA\n====================\n\n");

                    for (String[] row : result) {
                        String player = row.length > 1 ? row[1] : "";
                        String cost = row.length > 2 ? row[2] : "";
                        if (player.trim().isEmpty()) continue;

                        sb.append(player);
                        if (cost != null && !cost.trim().isEmpty()) sb.append("  -  ").append(cost);
                        sb.append("\n");
                    }

                    sb.append("\nTotale giocatori: ").append(result.size());
                    resultText.setText(sb.toString());
                    Toast.makeText(MainActivity.this, "Formazione caricata correttamente", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    resultText.setText("Errore lettura Excel:\n" + e.getMessage());
                });
            }
        });
    }

    private void analyzeFormation() {
        sendToLegheButton.setVisibility(View.GONE);
        resultText.setText("Analisi in corso...\n1. Recupero ruoli Classic e cache\n2. Recupero probabili Fantacalcio, Gazzetta, Sky\n3. Ottimizzazione modulo...");

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
                    resultText.setText("Errore analisi:\n" + e.getMessage());
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
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        connection.setUseCaches(true);

        int code = connection.getResponseCode();
        if (code < 200 || code >= 400) throw new Exception("HTTP " + code);

        InputStream inputStream = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder result = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) result.append(line).append('\n');

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
    }

    private static class ProbabilityInfo {
        int percentage = 0;
        boolean starter = false;
        boolean bench = false;
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

        Player(String excelName, String officialName, String team, String role, int quote, int fvm, int probable, int externalAgreement, boolean starter, boolean bench) {
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
            if (starter) score += 20;
            if (bench && !starter) score -= 12;
            if (probable == 0) score -= 25;
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

            Element row = link.closest("tr");
            if (row == null) row = link.parent();

            OfficialPlayer player = new OfficialPlayer();
            player.name = name;
            player.team = extractTeam(row != null ? row.text() : "");
            player.role = roleFromText(link.attr("data-role") + " " + (row != null ? row.outerHtml() : ""));

            String key = normalize(player.name);
            if (!result.containsKey(key)) result.put(key, player);
        }

        return result;
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
        for (String team : teams) if (upper.contains(team)) return team;
        return "";
    }

    private Map<String, ProbabilityInfo> parseFantacalcioProbabili(String html) {
        Map<String, ProbabilityInfo> result = new HashMap<>();
        if (html == null || html.isEmpty()) return result;

        Document document = Jsoup.parse(html);
        Elements playerLinks = document.select("a[href*='/serie-a/squadre/'], .player-name");

        for (Element link : playerLinks) {
            String name = cleanName(link.text());
            if (name.isEmpty()) continue;

            Element container = link.closest("li, tr, .player-item, .player-row, div");
            String blockText = container != null ? container.text() : (link.parent() != null ? link.parent().text() : "");

            int percentage = extractPercentageFromText(blockText);
            boolean bench = blockText.toLowerCase(Locale.ROOT).contains("panchina");

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

            if (textContainsPlayer(text, excelName)) result.put(normalized, 1);
        }

        return result;
    }

    private boolean textContainsPlayer(String text, String player) {
        String a = normalize(text);
        String b = normalize(player);
        if (b.length() < 3) return false;
        return a.contains(b);
    }

    private ArrayList<Player> buildPlayers(Map<String, OfficialPlayer> official, Map<String, ProbabilityInfo> probabilities, Map<String, Integer> gazzetta, Map<String, Integer> sky) {
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
                Player player = new Player(excelName, excelName, "", cachedRole, 0, 0, 0, 0, false, false);
                result.add(player);
                continue;
            }

            String normalizedOfficialName = normalize(officialPlayer.name);
            if (cachedRole.isEmpty()) cachedRole = roleCache.getRole(normalizedOfficialName);

            String role = !cachedRole.isEmpty() ? cachedRole : officialPlayer.role;
            // Se l'utente non ha mai sovrascritto il ruolo, salva come default quello rilevato da Leghe/Fantacalcio.
            if (cachedRole.isEmpty() && !officialPlayer.role.isEmpty()) {
                roleCache.setRole(normalizedExcel, officialPlayer.role);
                roleCache.setRole(normalizedOfficialName, officialPlayer.role);
            }

            ProbabilityInfo probability = probabilities.get(normalizedOfficialName);
            if (probability == null) probability = probabilities.get(normalizedExcel);

            int probable = probability != null ? probability.percentage : 0;
            boolean starter = probability != null && probability.starter;
            boolean bench = probability != null && probability.bench;

            int externalAgreement = 0;
            if (gazzetta.containsKey(normalizedOfficialName) || gazzetta.containsKey(normalizedExcel)) externalAgreement++;
            if (sky.containsKey(normalizedOfficialName) || sky.containsKey(normalizedExcel)) externalAgreement++;

            Player player = new Player(excelName, officialPlayer.name, officialPlayer.team, role, officialPlayer.classicQuote, officialPlayer.fvm, probable, externalAgreement, starter, bench);
            result.add(player);
        }

        return result;
    }

    private OfficialPlayer findOfficialPlayer(String excelName, Map<String, OfficialPlayer> official) {
        String normalized = normalize(excelName);
        OfficialPlayer exact = official.get(normalized);
        if (exact != null) return exact;

        for (Map.Entry<String, OfficialPlayer> entry : official.entrySet()) {
            if (similarNames(normalized, entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private boolean similarNames(String a, String b) {
        if (a.equals(b) || a.contains(b) || b.contains(a)) return true;
        String[] aa = a.split(" ");
        String[] bb = b.split(" ");
        if (aa.length >= 2 && bb.length >= 2) {
            if (aa[0].equals(bb[0]) && aa[aa.length - 1].equals(bb[bb.length - 1])) return true;
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
            if (best == null || result.score > best.score) best = result;
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

        if (goalkeepers.isEmpty() || defenderList.size() < defenders || midfielderList.size() < midfielders || attackerList.size() < attackers) {
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
            if (role.equals(player.role)) result.add(player);
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

    private int roleIndex(String role) {
        if ("P".equalsIgnoreCase(role)) return 0;
        if ("D".equalsIgnoreCase(role)) return 1;
        if ("C".equalsIgnoreCase(role)) return 2;
        if ("A".equalsIgnoreCase(role)) return 3;
        return -1;
    }

    private String roleFromIndex(int which) {
        switch (which) {
            case 0: return "P";
            case 1: return "D";
            case 2: return "C";
            case 3: return "A";
            default: return "";
        }
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
        String[] roleOptions = {"P  —  Portiere", "D  —  Difensore", "C  —  Centrocampista", "A  —  Attaccante"};
        int checked = roleIndex(getSavedOrDetectedRole(player));

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Ruolo di " + player.excelName);
        builder.setSingleChoiceItems(roleOptions, checked, (dialog, which) -> {
            String selectedRole = roleFromIndex(which);
            saveRoleForPlayer(player, selectedRole);
            dialog.dismiss();
            showRoleDialogForPlayer(playersWithoutRole, playerIndex + 1, allPlayers);
        });
        builder.setNegativeButton("Annulla", null);
        builder.show();
    }

    private String getSavedOrDetectedRole(Player player) {
        String keyExcel = normalize(player.excelName);
        String role = roleCache.getRole(keyExcel);
        if (!role.isEmpty()) return role;

        String keyOfficial = normalize(player.officialName == null || player.officialName.isEmpty() ? player.excelName : player.officialName);
        role = roleCache.getRole(keyOfficial);
        if (!role.isEmpty()) return role;

        return player.role == null ? "" : player.role;
    }

    private void saveRoleForPlayer(Player player, String selectedRole) {
        String normalizedExcelName = normalize(player.excelName);
        String normalizedOfficialName = normalize(player.officialName == null || player.officialName.isEmpty() ? player.excelName : player.officialName);

        roleCache.setRole(normalizedExcelName, selectedRole);
        roleCache.setRole(normalizedOfficialName, selectedRole);
        player.role = selectedRole;
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
                String currentRole = getRoleForExcelName(name);

                String label = name + (currentRole.isEmpty() ? " [—]" : " [" + currentRole + "]");
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

    private String getRoleForExcelName(String playerName) {
        String role = roleCache.getRole(normalize(playerName));
        if (!role.isEmpty()) return role;

        if (lastParsedPlayers != null) {
            String key = normalize(playerName);
            for (Player p : lastParsedPlayers) {
                if (normalize(p.excelName).equals(key)) {
                    return p.role == null ? "" : p.role;
                }
            }
        }
        return "";
    }

    private void promptChangeRoleForPlayer(String playerName) {
        String[] roleOptions = {"P  —  Portiere", "D  —  Difensore", "C  —  Centrocampista", "A  —  Attaccante"};
        String currentRole = getRoleForExcelName(playerName);
        int checked = roleIndex(currentRole);

        if (isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ruolo di " + playerName);
        builder.setSingleChoiceItems(roleOptions, checked, (dialog, which) -> {
            String selectedRole = roleFromIndex(which);
            String key = normalize(playerName);
            roleCache.setRole(key, selectedRole);

            if (lastParsedPlayers != null) {
                for (Player p : lastParsedPlayers) {
                    if (normalize(p.excelName).equals(key)) {
                        saveRoleForPlayer(p, selectedRole);
                    }
                }
                FormationResult newBest = calculateBestFormation(lastParsedPlayers);
                currentBestResult = newBest;
                displayResult(lastParsedPlayers, newBest);
            }

            Toast.makeText(this, "Ruolo aggiornato per " + playerName + ": " + selectedRole, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        builder.setNegativeButton("Annulla", null);
        builder.show();
    }

    private void displayFormationResult(StringBuilder sb, FormationResult best) {
        if (best == null || !best.valid) {
            sb.append("NON È STATO POSSIBILE CREARE UNA FORMAZIONE VALIDA.\n\n");
            sb.append("Uno o più ruoli Classic non sono stati riconosciuti o mancano giocatori per completare un modulo.");
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
}