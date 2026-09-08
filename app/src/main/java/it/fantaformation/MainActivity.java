package it.fantaformation;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.Gravity;
import android.util.Log;
import android.view.ViewGroup;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.graphics.drawable.GradientDrawable;
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
import java.text.SimpleDateFormat;
import java.util.Date;
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
    private LinearLayout resultContainer;
    private Button sendToLegheButton;

    private List<String[]> formazione;
    private PlayerRoleCache roleCache;
    private CredentialsManager credentialsManager;
    private ArrayList<Player> lastParsedPlayers;
    private FormationResult currentBestResult;
    private boolean pendingAutoFill = false;
    private boolean autoFlowEnabled = false;
    private boolean loginAttempted = false;
    private boolean loginInProgress = false;
    private boolean loginAutoScheduled = false;
    private boolean autoInsertAfterFormationNav = false;
    private boolean lineupLoadTriggered = false;
    private int formationNavAttempts = 0;
    private LinearLayout statusContainer;
    private final Map<String, TextView> statusViews = new LinkedHashMap<>();
    private boolean autoRunRequested = false;
    private boolean saveVerificationStarted = false;
    private boolean notificationPending = false;

    private static final int NOTIFICATION_PERMISSION_REQUEST = 7001;
    private static final String NOTIFICATION_CHANNEL_ID = "formation_status";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String FANTACALCIO_QUOTE = "https://www.fantacalcio.it/quotazioni-fantacalcio/2026-27";
    private static final String FANTACALCIO_PROBABILI = "https://www.fantacalcio.it/probabili-formazioni-serie-a";
    private static final String FANTACALCIO_STATS = "https://www.fantacalcio.it/statistiche-serie-a/2026-27/italia";
    private static final String FANTACALCIO_NEWS = "https://www.fantacalcio.it/news";
    private static final String FANTACALCIO_RECENT_BASE = "https://www.fantacalcio-online.com/it/serie-a/2026-2027/voti";
    private static final String FANTACALCIO_CALENDAR = "https://www.fantacalcio.it/serie-a/calendario";
    private static final String GAZZETTA_PROBABILI = "https://www.gazzetta.it/Calcio/prob_form/";
    private static final String SKY_PROBABILI = "https://sport.sky.it/calcio/serie-a/probabili-formazioni";
    private static final String LEGA_HOME_URL = "https://leghe.fantacalcio.it/yoooo/";

    private static final Set<String> ALLOWED_FORMATIONS = new HashSet<>(Arrays.asList(
            "3-4-3", "4-4-2", "3-5-2", "4-5-1", "5-4-1"
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        roleCache = new PlayerRoleCache(this);
        credentialsManager = new CredentialsManager(this);
        buildInterface();
        if (credentialsManager.hasCredentials()) {
            setStepState("CREDENZIALI", 2, "Credenziali disponibili");
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp((int) radius));
        return drawable;
    }

    private Button darkButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(14), dp(12), dp(14), dp(12));
        button.setBackground(bg(primary ? Color.rgb(30, 136, 229) : Color.rgb(36, 42, 52), 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, 0, 0, dp(10));
        button.setLayoutParams(lp);
        return button;
    }

    private void buildInterface() {
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(12, 15, 20));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(12, 15, 20));

        TextView title = new TextView(this);
        title.setText("FantaFormation");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(6));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("La tua formazione, ottimizzata.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(155, 165, 180));
        subtitle.setPadding(0, 0, 0, dp(14));
        root.addView(subtitle);

        statusContainer = new LinearLayout(this);
        statusContainer.setOrientation(LinearLayout.VERTICAL);
        statusContainer.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusContainer.setBackground(bg(Color.rgb(22, 27, 35), 18));
        root.addView(statusContainer, new LinearLayout.LayoutParams(-1, -2));
        buildStatusPanel();

        TextView section = new TextView(this);
        section.setText("FORMAZIONE");
        section.setTextSize(12);
        section.setTextColor(Color.rgb(120, 180, 255));
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.setPadding(0, 0, 0, dp(10));
        root.addView(section);

        Button loadButton = darkButton("📄  Carica formazione Excel", false);
        root.addView(loadButton);

        Button automateButton = darkButton("⚡  Analizza e ottimizza", true);
        root.addView(automateButton);

        TextView tools = new TextView(this);
        tools.setText("STRUMENTI");
        tools.setTextSize(12);
        tools.setTextColor(Color.rgb(120, 180, 255));
        tools.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tools.setPadding(0, dp(14), 0, dp(10));
        root.addView(tools);

        Button editRolesButton = darkButton("👤  Modifica ruoli salvati", false);
        root.addView(editRolesButton);

        Button setCredsButton = darkButton("🔑  Imposta credenziali Leghe", false);
        root.addView(setCredsButton);

        sendToLegheButton = darkButton("🚀  Carica su Leghe Fantacalcio", true);
        sendToLegheButton.setVisibility(View.GONE);
        root.addView(sendToLegheButton);

        Button openButton = darkButton("🌐  Apri Fantacalcio nel browser", false);
        root.addView(openButton);

        TextView resultHeader = new TextView(this);
        resultHeader.setText("RISULTATO");
        resultHeader.setTextSize(12);
        resultHeader.setTextColor(Color.rgb(120, 180, 255));
        resultHeader.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        resultHeader.setPadding(0, dp(18), 0, dp(10));
        root.addView(resultHeader);

        resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        resultContainer.setPadding(0, 0, 0, dp(8));
        root.addView(resultContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        resultText = new TextView(this);
        resultText.setTextSize(15);
        resultText.setTextColor(Color.rgb(232, 236, 242));
        resultText.setLineSpacing(0, 1.08f);
        resultText.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultText.setBackground(bg(Color.rgb(22, 27, 35), 18));
        resultContainer.addView(resultText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView.addView(root);
        setContentView(scrollView);

        loadButton.setOnClickListener(v -> chooseExcel());
        automateButton.setOnClickListener(v -> {
            if (formazione == null || formazione.isEmpty()) {
                setStepState("EXCEL", 0, "Carica prima il file Excel");
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
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://leghe.fantacalcio.it/")));
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
                // BLOCCO SICUREZZA: l'automazione non deve mai seguire link pubblicitari
                // o uscire dal dominio Leghe. Le risorse/API esterne continuano a funzionare
                // normalmente perché questo metodo riguarda solo la navigazione della pagina.
                if (url == null) return true;
                try {
                    Uri uri = Uri.parse(url);
                    String host = uri.getHost();
                    if (host == null || !host.equalsIgnoreCase("leghe.fantacalcio.it")) {
                        Log.d("FANTA_DEBUG", "NAV BLOCCATA (dominio esterno): " + url);
                        return true;
                    }
                } catch (Exception e) {
                    Log.d("FANTA_DEBUG", "NAV BLOCCATA (URL non valido): " + url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                debugUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d("FANTA_URL", "PAGE = " + url);
                debugUrl(url);
                if (url == null || !autoFlowEnabled) return;

                if (url.contains("/login/")) {
                    /*
                     * La prima apertura può mostrare "sessione scaduta" pur essendo
                     * comunque sulla pagina di login corretta. Non consideriamo questa
                     * situazione un errore e NON richiediamo un secondo click all'utente.
                     *
                     * Aspettiamo che la pagina/Angular abbia finito di costruire i campi,
                     * poi lanciamo automaticamente il normale script di login.
                     * loginAutoScheduled evita loop durante eventuali redirect.
                     */
                    setStepState("LOGIN", 1, "Pagina login pronta");
                    Log.d("FANTA_DEBUG", "LOGIN: pagina pronta (" + url + "), preparo login automatico");

                    if (credentialsManager.hasCredentials() && !loginInProgress && !loginAutoScheduled) {
                        loginAutoScheduled = true;
                        view.postDelayed(() -> {
                            loginAutoScheduled = false;
                            if (!autoFlowEnabled) return;

                            String nowUrl = view.getUrl();
                            if (nowUrl != null && nowUrl.contains("/login/")) {
                                Log.d("FANTA_DEBUG", "LOGIN: esecuzione automatica dopo caricamento pagina");
                                executeAutoLoginScript(view);
                            }
                        }, 1000);
                    }
                    return;
                }

                // Il login è riuscito solo in un'area privata riconoscibile.
                boolean privateArea = url.contains("/area-gioco")
                        || url.contains("/view/")
                        || url.contains("/league")
                        || url.contains("/competition")
                        || isLegheRootUrl(url)
                        || isYooooUrl(url);
                boolean outsideLogin = !url.contains("/login/");

                if (loginInProgress && privateArea && outsideLogin) {
                    loginInProgress = false;
                    CookieManager.getInstance().flush();
                    setStepState("LOGIN", 2, "Login effettuato");
                    setStepState("LEGA", 1, "Accesso alla lega yoooo...");
                    autoInsertAfterFormationNav = true;
                    Log.d("FANTA_DEBUG", "LOGIN riuscito: area privata = " + url + " — avvio flusso automatico formazione");
                    view.postDelayed(() -> openYooooLeague(view), 500);
                    return;
                }

                if (isYooooUrl(url)) {
                    setStepState("LEGA", 2, "Lega yoooo aperta");
                    Log.d("FANTA_DEBUG", "LEGA yoooo aperta: " + url);
                    if (autoFlowEnabled && autoInsertAfterFormationNav && currentBestResult != null && currentBestResult.valid) {
                        setStepState("LEGA", 1, "Seleziono Inserisci formazione...");
                        view.postDelayed(() -> openSchieraFormazioneFromDashboard(view), 700);
                    }
                    return;
                }

                if (url.contains("/lineup/") ||
                        url.contains("/inserisci-formazione") ||
                        url.contains("/formazione")) {
                    setStepState("LEGA", 2, "Pagina formazione aperta");
                    Log.d("FANTA_DEBUG", "Pagina formazione rilevata: " + url);
                    if (autoFlowEnabled && autoInsertAfterFormationNav && currentBestResult != null && currentBestResult.valid) {
                        autoInsertAfterFormationNav = false;
                        setStepState("FORMAZIONE", 1, "Preparo modulo e inserimento automatico...");
                        view.postDelayed(() -> executeAutoFillScript(view), 1400);
                    }
                    return;
                }
            }
        });

        // Bridge used only for reporting asynchronous JavaScript results.
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void report(String message) {
                runOnUiThread(() -> handleAutomationReport(message));
            }

            @JavascriptInterface
            public void openFormation(String url) {
                if (url == null || url.trim().isEmpty()) return;
                String safe = url.trim();
                if (!safe.startsWith("https://leghe.fantacalcio.it/")) return;
                runOnUiThread(() -> {
                    pendingAutoFill = false;
                    setStepState("LEGA", 2, "Pagina formazione aperta");
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
        saveVerificationStarted = false;
        notificationPending = false;
        setStepState("CREDENZIALI", credentialsManager.hasCredentials() ? 2 : 0, credentialsManager.hasCredentials() ? "Credenziali disponibili" : "Non impostate");
        loginAttempted = false;
        loginInProgress = false;
        loginAutoScheduled = false;
        lineupLoadTriggered = false;
        formationNavAttempts = 0;
        pendingAutoFill = false;
        autoInsertAfterFormationNav = false;

        // Manteniamo i cookie: Leghe può usarli per completare l'autenticazione
        // e mantenere la sessione tra redirect e pagine private.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.flush();
        Log.d("FANTA_DEBUG", "LOGIN avviato: apertura pagina di accesso");
        webView.loadUrl("https://leghe.fantacalcio.it/login/area-gioco/formazioni");

        autoLoginButton.setOnClickListener(v -> {
            autoFlowEnabled = true;
            loginAttempted = false;
            loginInProgress = false;
            loginAutoScheduled = false;
            lineupLoadTriggered = false;
            formationNavAttempts = 0;
            pendingAutoFill = false;
            Log.d("FANTA_DEBUG", "LOGIN avviato manualmente");
            executeAutoLoginScript(webView);
        });
        autoFillButton.setOnClickListener(v -> executeAutoFillScript(webView));
        copyButton.setOnClickListener(v -> copyFormationToClipboard());
        closeButton.setOnClickListener(v -> dialog.dismiss());
    }

    private boolean isLegheRootUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.equals("https://leghe.fantacalcio.it")
                || u.equals("https://leghe.fantacalcio.it/");
    }

    private boolean isYooooUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.equals("https://leghe.fantacalcio.it/yoooo")
                || u.equals("https://leghe.fantacalcio.it/yoooo/")
                || u.startsWith("https://leghe.fantacalcio.it/yoooo/");
    }

    /**
     * Dopo il login apre la lega yoooo presente nella pagina di Leghe.
     * Cerca prima il vero link della lega nel DOM; non usa link pubblicitari.
     */
    private void openYooooLeague(WebView webView) {
        Log.d("FANTA_DEBUG", "LEGA: cerco il link della lega yoooo");
        String js = "(function(){" +
                "function norm(s){return String(s||'').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/\s+/g,' ').trim();}" +
                "function visible(e){if(!e||!e.getBoundingClientRect)return false;var r=e.getBoundingClientRect();return r.width>0&&r.height>0;}" +
                "function abs(h){try{return new URL(h,location.href).href;}catch(x){return '';}}" +
                "var links=document.querySelectorAll('a.league[href],a[href]');" +
                "for(var i=0;i<links.length;i++){var a=links[i];if(!visible(a))continue;var t=norm(a.innerText||a.textContent);var h=abs(a.getAttribute('href')||'');" +
                "if(t==='yoooo'||t.indexOf(' yoooo')>=0||t.indexOf('yoooo ')>=0){" +
                " var u=h.toLowerCase();if(u.indexOf('https://leghe.fantacalcio.it/yoooo')===0){AndroidBridge.report('LEGA: yoooo trovata, apertura...');return h;}" +
                "}}" +
                "return 'FALLBACK';" +
                "})()";
        webView.evaluateJavascript(js, value -> {
            String result = cleanJsResult(value, "FALLBACK");
            if (result.startsWith("https://leghe.fantacalcio.it/yoooo")) {
                webView.loadUrl(result);
            } else {
                Log.d("FANTA_DEBUG", "LEGA: link yoooo non trovato, uso URL noto della lega");
                webView.loadUrl(LEGA_HOME_URL);
            }
        });
    }

    private void scheduleAutoInsertWhenReady(WebView webView) {
        webView.postDelayed(() -> {
            if (!autoFlowEnabled || !autoInsertAfterFormationNav || currentBestResult == null || !currentBestResult.valid) return;
            String u = webView.getUrl();
            boolean urlLooksFormation = u != null && (u.contains("/lineup/") || u.contains("/inserisci-formazione") || u.contains("/formazione"));
            if (urlLooksFormation) {
                autoInsertAfterFormationNav = false;
                setStepState("FORMAZIONE", 1, "Pagina pronta: avvio inserimento automatico...");
                executeAutoFillScript(webView);
                return;
            }
            webView.evaluateJavascript("(function(){return !!document.querySelector('ui-lineup-slot[data-lineup-slot]');})()", value -> {
                if (!autoFlowEnabled || !autoInsertAfterFormationNav || currentBestResult == null || !currentBestResult.valid) return;
                if ("true".equals(value)) {
                    autoInsertAfterFormationNav = false;
                    setStepState("FORMAZIONE", 1, "Pagina pronta: avvio inserimento automatico...");
                    executeAutoFillScript(webView);
                } else {
                    scheduleAutoInsertWhenReady(webView);
                }
            });
        }, 900);
    }

    private void openSchieraFormazioneFromDashboard(WebView webView) {
        final int attempt = ++formationNavAttempts;
        Log.d("FANTA_DEBUG", "Ricerca Inserisci formazione - tentativo " + attempt + "/5");
        if (attempt > 5) return;

        String js = "(function(){" +
                "function norm(s){return String(s||'').toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').replace(/\\s+/g,' ').trim();}" +
                "function vis(e){if(!e||!e.getBoundingClientRect)return false;var r=e.getBoundingClientRect(),c=getComputedStyle(e);return r.width>0&&r.height>0&&c.display!=='none'&&c.visibility!=='hidden';}" +
                "function en(e){return !!e&&!e.disabled&&e.getAttribute('aria-disabled')!=='true';}" +
                "function txt(e){return norm(e.innerText||e.textContent||e.getAttribute('aria-label')||e.getAttribute('title')||e.value);}" +
                "function abs(h){try{return new URL(h,location.href).href;}catch(x){return '';}}" +
                "function isFormHref(h){var l=abs(h||'').toLowerCase();return l.indexOf('/lineup/')>=0||l.indexOf('/inserisci-formazione')>=0||l.indexOf('/formazione')>=0;}" +
                "function action(e){if(!e)return null;var a=e.closest&&e.closest('a[href]');if(a&&vis(a)&&en(a))return a;var b=e.closest&&e.closest('button');if(b&&vis(b)&&en(b))return b;var r=e.closest&&e.closest('[role=button]');if(r&&vis(r)&&en(r))return r;return e;}" +
                "function clickHard(e){var t=action(e);if(!t||!vis(t)||!en(t))return false;try{t.scrollIntoView({block:'center',inline:'center'});}catch(x){}try{t.focus();}catch(x){}try{['pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(n){t.dispatchEvent(new MouseEvent(n,{bubbles:true,cancelable:true,view:window}));});}catch(x){}try{t.click();}catch(x){}return true;}" +
                "function openHref(e){var t=action(e),h=t&&t.getAttribute?t.getAttribute('href'):'';if(h&&isFormHref(h)){var u=abs(h);if(window.AndroidBridge)window.AndroidBridge.openFormation(u);return u;}return ''; }" +
                "var links=[...document.querySelectorAll('a[href]')].filter(function(a){return vis(a)&&en(a);});" +
                "for(var i=0;i<links.length;i++){var a=links[i],t=txt(a),h=a.getAttribute('href')||'';if((t.indexOf('inserisci formazione')>=0||t.indexOf('schiera formazione')>=0)&&isFormHref(h)){var u=openHref(a);if(u)return 'direct:'+u;}}" +
                "for(var j=0;j<links.length;j++){var a2=links[j],t2=txt(a2),h2=a2.getAttribute('href')||'';if(t2.indexOf('inserisci formazione')>=0||t2.indexOf('schiera formazione')>=0){if(h2&&h2!=='#'&&!/^javascript:/i.test(h2)){var u2=openHref(a2);if(u2)return 'direct:'+u2;}if(clickHard(a2)){if(window.AndroidBridge)window.AndroidBridge.report('NAV: click reale su Inserisci formazione');return 'clicked';}}}" +
                "var els=[...document.querySelectorAll('button,[role=button],a,li,span,div')].filter(function(e){return vis(e)&&en(e);});" +
                "for(var k=0;k<els.length;k++){var e=els[k],t3=txt(e);if(t3.indexOf('inserisci formazione')<0&&t3.indexOf('schiera formazione')<0)continue;var anc=e.closest&&e.closest('a[href]');if(anc){var u3=openHref(anc);if(u3)return 'direct:'+u3;}var child=e.querySelector&&e.querySelector('a[href]');if(child){var u4=openHref(child);if(u4)return 'direct:'+u4;}if(clickHard(e)){if(window.AndroidBridge)window.AndroidBridge.report('NAV: elemento Inserisci formazione attivato');return 'clicked';}}" +
                "if(window.AndroidBridge)window.AndroidBridge.report('NAV: Inserisci formazione non trovato');return 'false';})()";

        webView.evaluateJavascript(js, value -> {
            String result = cleanJsResult(value, "false");
            Log.d("FANTA_DEBUG", "Risultato navigazione formazione: " + result);
            if (result.startsWith("direct:")) {
                autoInsertAfterFormationNav = true;
                setStepState("LEGA", 1, "Apro Inserisci formazione...");
                scheduleAutoInsertWhenReady(webView);
            } else if ("clicked".equals(result)) {
                autoInsertAfterFormationNav = true;
                setStepState("LEGA", 1, "Inserisci formazione selezionato...");
                webView.postDelayed(() -> scheduleAutoInsertWhenReady(webView), 700);
            } else if (formationNavAttempts < 5) {
                webView.postDelayed(() -> openSchieraFormazioneFromDashboard(webView), 2000);
            }
        });
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
            setStepState("CREDENZIALI", 2, "Credenziali disponibili");
            Toast.makeText(this, "Credenziali salvate!", Toast.LENGTH_SHORT).show();
            if (autoRunRequested) {
                setStepState("CREDENZIALI", 2, "Credenziali disponibili");
                if (formazione != null && !formazione.isEmpty()) analyzeFormation();
            }
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

    private void debugUrl(String url) {
        Log.d("FANTA_DEBUG", url == null ? "null" : url);
    }

    private void executeAutoLoginScript(WebView webView) {
        if (!credentialsManager.hasCredentials()) {
            Toast.makeText(this, "Imposta prima le credenziali col tasto 🔑", Toast.LENGTH_LONG).show();
            promptSaveCredentials();
            return;
        }

        String currentUrl = webView.getUrl();
        if (currentUrl == null || !currentUrl.contains("/login/")) {
            loginAttempted = false;
            loginInProgress = false;
            webView.loadUrl("https://leghe.fantacalcio.it/login/area-gioco/formazioni");
            Toast.makeText(this, "Apro la pagina di login...", Toast.LENGTH_SHORT).show();
            return;
        }

        loginInProgress = true;
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        CookieManager.getInstance().flush();
        Log.d("FANTA_DEBUG", "LOGIN avviato: compilazione credenziali (cookie mantenuti e sincronizzati)");

        String user = credentialsManager.getUsername()
                .replace("\\", "\\\\").replace("'", "\\'");
        String pass = credentialsManager.getPassword()
                .replace("\\", "\\\\").replace("'", "\\'");

        String js = "(async function() {" +
                "const USER='" + user + "';" +
                "const PASS='" + pass + "';" +
                "const sleep=ms=>new Promise(r=>setTimeout(r,ms));" +
                "function visible(e){return !!(e&&e.getBoundingClientRect&&e.getBoundingClientRect().width>0&&e.getBoundingClientRect().height>0);}" +
                "function enabled(e){return !!e&&!e.disabled&&e.getAttribute('aria-disabled')!=='true';}" +
                "function setNative(e,v){" +
                " if(!e)return false;" +
                " const proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;" +
                " const d=Object.getOwnPropertyDescriptor(proto,'value');" +
                " if(d&&d.set)d.set.call(e,v);else e.value=v;" +
                " ['input','change','keyup','blur'].forEach(n=>e.dispatchEvent(new Event(n,{bubbles:true,cancelable:true}))); return true;" +
                "}" +
                "function find(sel){for(const e of document.querySelectorAll(sel)){if(visible(e)&&enabled(e))return e;}return null;}" +
                "let u=null,p=null;" +
                "for(let i=0;i<40&&(!u||!p);i++){" +
                " u=find('input[autocomplete=username],input[autocomplete=email],input[type=email],input[name=username],input[name=email],input[id=username],input[id=email],input[placeholder*=\"Username\" i],input[placeholder*=\"email\" i]');" +
                " p=find('input[autocomplete=current-password],input[type=password],input[name=password],input[id=password],input[placeholder*=\"Password\" i]');" +
                " if(!u||!p)await sleep(300);" +
                "}" +
                "if(!u||!p){AndroidBridge.report('LOGIN: campi Username/Password non trovati');return;}" +
                "setNative(u,USER);setNative(p,PASS);await sleep(700);" +
                "let b=null;" +
                "for(const e of document.querySelectorAll('button,input[type=submit],[role=button]')){" +
                " if(!visible(e)||!enabled(e))continue;" +
                " const t=(e.innerText||e.textContent||e.value||e.getAttribute('aria-label')||'').toLowerCase().trim();" +
                " if(t.includes('login')||t.includes('accedi')||t.includes('entra')||t.includes('sign in')){b=e;break;}" +
                "}" +
                "if(b){b.scrollIntoView({block:'center'});b.click();AndroidBridge.report('LOGIN: invio credenziali...');return;}" +
                "let f=u.closest('form')||p.closest('form');" +
                "if(f){if(f.requestSubmit)f.requestSubmit();else f.submit();AndroidBridge.report('LOGIN: form inviato...');return;}" +
                "AndroidBridge.report('LOGIN: credenziali compilate ma pulsante/form non trovato');" +
                "})().catch(e=>AndroidBridge.report('LOGIN ERRORE: '+(e&&e.message?e.message:e)));";

        webView.evaluateJavascript(js, value ->
                Toast.makeText(MainActivity.this, "Tentativo login avviato", Toast.LENGTH_SHORT).show());
    }

    /**
     * Script JS avanzato asincrono con pausa tra i click ed eventi touch reali
     */
    /**
     * V5: inserimento DOM robusto.
     * - Costruisce prima una mappa reale degli slot titolari e panchina.
     * - I titolari sono identificati dal gruppo data-lineup-slot 0/1/2/3.
     * - La panchina (-1:0 ... -1:7) viene mappata P/P/D/D/C/C/A/A.
     * - Gli slot già occupati vengono sostituiti, non scartati.
     * - La selezione nella Rosa viene verificata prima di passare al giocatore successivo.
     * - I messaggi INSERISCI: restano nei log e non vengono mostrati come Toast.
     */
    private void executeAutoFillScript(WebView webView) {
        if (currentBestResult == null || !currentBestResult.valid) {
            Toast.makeText(this, "Nessuna formazione da inserire", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUrl = webView.getUrl();
        boolean formationPage = currentUrl != null &&
                (currentUrl.contains("/lineup/") || currentUrl.contains("/inserisci-formazione") || currentUrl.contains("/formazione"));

        if (!formationPage) {
            Log.d("FANTA_DEBUG", "INSERISCI: pagina formazione non ancora aperta");
            if (currentUrl != null && currentUrl.contains("/login/")) {
                setStepState("LOGIN", 1, "Effettua prima il LOGIN");
                Toast.makeText(this, "Prima esegui il LOGIN", Toast.LENGTH_LONG).show();
            } else if (isYooooUrl(currentUrl) || (currentUrl != null &&
                    (currentUrl.contains("/area-gioco") || currentUrl.contains("/view/") ||
                     currentUrl.contains("/league") || currentUrl.contains("/competition")))) {
                setStepState("LEGA", 1, "Cerco 'Schiera formazione'...");
                formationNavAttempts = 0;
                openSchieraFormazioneFromDashboard(webView);
            } else {
                setStepState("LOGIN", 1, "Effettua prima il LOGIN");
                Toast.makeText(this, "Prima esegui il LOGIN", Toast.LENGTH_LONG).show();
            }
            return;
        }

        setStepState("FORMAZIONE", 1, "Inserimento formazione...");
        Log.d("FANTA_DEBUG", "V5 inserimento formazione DOM reale: " + currentUrl);

        ArrayList<String[]> selectedPlayers = new ArrayList<>();
        for (Player p : currentBestResult.goalkeeper) selectedPlayers.add(new String[]{"P", playerNameForWeb(p), "T"});
        for (Player p : currentBestResult.defenders) selectedPlayers.add(new String[]{"D", playerNameForWeb(p), "T"});
        for (Player p : currentBestResult.midfielders) selectedPlayers.add(new String[]{"C", playerNameForWeb(p), "T"});
        for (Player p : currentBestResult.attackers) selectedPlayers.add(new String[]{"A", playerNameForWeb(p), "T"});
        for (Player p : currentBestResult.benchGoalkeeper) selectedPlayers.add(new String[]{"P", playerNameForWeb(p), "B"});
        for (Player p : currentBestResult.benchDefenders) selectedPlayers.add(new String[]{"D", playerNameForWeb(p), "B"});
        for (Player p : currentBestResult.benchMidfielders) selectedPlayers.add(new String[]{"C", playerNameForWeb(p), "B"});
        for (Player p : currentBestResult.benchAttackers) selectedPlayers.add(new String[]{"A", playerNameForWeb(p), "B"});

        StringBuilder playersJson = new StringBuilder("[");
        for (int i = 0; i < selectedPlayers.size(); i++) {
            if (i > 0) playersJson.append(",");
            String role = selectedPlayers.get(i)[0];
            String name = selectedPlayers.get(i)[1];
            String type = selectedPlayers.get(i)[2];
            playersJson.append("{\"role\":\"").append(jsQuote(role)).append("\",\"name\":\"")
                    .append(jsQuote(name)).append("\",\"type\":\"").append(jsQuote(type)).append("\"}");
        }
        playersJson.append("]");

        String module = jsQuote(currentBestResult.module);
        String js = "(async function(){" +
                "const moduleName='" + module + "';" +
                "const players=" + playersJson + ";" +
                "const sleep=ms=>new Promise(r=>setTimeout(r,ms));" +
                "const norm=s=>String(s||'').toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').replace(/[^a-z0-9 ]/g,' ').replace(/\\s+/g,' ').trim();" +
                "const raw=s=>String(s||'').replace(/\\s+/g,' ').trim();" +
                "function visible(e){if(!e||!e.getBoundingClientRect)return false;const r=e.getBoundingClientRect();const c=getComputedStyle(e);return r.width>0&&r.height>0&&c.display!=='none'&&c.visibility!=='hidden'&&c.opacity!=='0';}" +
                "function enabled(e){return !!e&&!e.disabled&&e.getAttribute('aria-disabled')!=='true';}" +
                "function fire(e){if(!e||!visible(e)||!enabled(e))return false;try{e.scrollIntoView({block:'center',inline:'center'});}catch(x){}try{e.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true,view:window}));e.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,cancelable:true,view:window}));e.click();return true;}catch(x){try{e.click();return true;}catch(y){return false;}}}" +
                "function formationNames(){return ['3-4-3','3-5-2','3-4-1-2','3-4-2-1','3-5-1-1','4-3-3','4-4-2','4-3-1-2','4-2-3-1','4-1-4-1','4-5-1','5-3-2','5-4-1','5-2-3'];}" +
                "function exactVisibleText(name){const n=norm(name);return [...document.querySelectorAll('button,[role=button],[role=option],[role=menuitem],option,li,span,div')].filter(visible).find(e=>norm(e.innerText||e.textContent||e.value||e.getAttribute('aria-label')||'')===n)||null;}" +
                "function moduleControl(){const candidates=[...document.querySelectorAll('select,button,[role=button],input')].filter(visible).filter(e=>enabled(e));const forms=formationNames();return candidates.find(e=>{const t=norm(e.innerText||e.textContent||e.value||e.getAttribute('aria-label')||e.getAttribute('title')||'');return forms.some(f=>t===norm(f));})||candidates.find(e=>{const t=norm(e.innerText||e.textContent||e.getAttribute('aria-label')||e.getAttribute('title')||'');return t==='modulo'||t.includes('modulo')||t.includes('schema');})||null;}" +
                "async function selectModule(name){const wanted=norm(name);if(!formationNames().includes(name)){AndroidBridge.report('MODULO FALLITO: modulo non riconosciuto '+name);return false;}let current=moduleControl();if(current){const ct=norm(current.innerText||current.textContent||current.value||current.getAttribute('aria-label')||'');if(ct===wanted){AndroidBridge.report('MODULO: '+name+' già selezionato');return true;}if(current.tagName==='SELECT'){const opts=[...current.options];const op=opts.find(o=>norm(o.textContent||o.value)===wanted);if(op){current.value=op.value;current.dispatchEvent(new Event('change',{bubbles:true}));await sleep(900);}}else{fire(current);await sleep(500);}}else{AndroidBridge.report('MODULO: controllo modulo non identificato, cerco opzione direttamente');}for(let i=0;i<30;i++){const opt=exactVisibleText(name);if(opt){fire(opt);await sleep(1000);break;}await sleep(200);}for(let i=0;i<25;i++){const c=moduleControl();const ct=c?norm(c.innerText||c.textContent||c.value||c.getAttribute('aria-label')||''):'';if(ct===wanted){AndroidBridge.report('MODULO_OK:'+name);return true;}const body=norm(document.body.innerText||'');const count=(body.match(new RegExp('\\b'+wanted.replace(/[-]/g,'\\-')+'\\b','g'))||[]).length;if(count>0&&i>8){const active=[...document.querySelectorAll('[aria-selected=\"true\"],.ant-select-selection-item,.ant-select-selection-selected-value')].filter(visible).some(e=>norm(e.innerText||e.textContent||e.getAttribute('title')||'')===wanted);if(active){AndroidBridge.report('MODULO_OK:'+name);return true;}}await sleep(250);}AndroidBridge.report('MODULO FALLITO: impossibile verificare '+name);return false;}" +
                "function slots(){return [...document.querySelectorAll('ui-lineup-slot[data-lineup-slot]')].filter(visible);}" +
                "function slotKey(e){return e.getAttribute('data-lineup-slot')||'';}" +
                "function currentName(slot){const e=slot.querySelector('.player-name');return norm(e?e.innerText||e.textContent:'');}" +
                "function slotIndex(e){const s=slotKey(e).split(':');return {a:parseInt(s[0],10),b:parseInt(s[1],10)};}" +
                "function reserveRole(index){return index===0||index===1?'P':index===2||index===3?'D':index===4||index===5?'C':index===6||index===7?'A':'';}" +
                "function roleOf(slot){const key=slotKey(slot);if(key.indexOf('-1:')===0)return reserveRole(parseInt(key.split(':')[1],10));const ix=slotIndex(slot);if(ix.a===0)return 'P';if(ix.a===1)return 'D';if(ix.a===2)return 'C';if(ix.a===3)return 'A';const r=slot.querySelector('ui-role[data-role]');return r?(r.getAttribute('data-role')||'').toUpperCase():'';}" +
                "function reserveOf(slot){return slot.classList.contains('is-reserve')||slotKey(slot).indexOf('-1:')===0;}" +
                "function describeSlots(){const a=slots().map(s=>({key:slotKey(s),role:roleOf(s),reserve:reserveOf(s),name:currentName(s)}));AndroidBridge.report('MAPPA_SLOT:' + JSON.stringify(a));return a;}" +
                "function targetSlot(item,used){const all=slots();let candidates=all.filter(s=>{const k=slotKey(s);return roleOf(s)===item.role&&reserveOf(s)===(item.type==='B')&&!used.has(k);});if(item.type==='B'){candidates.sort((x,y)=>slotIndex(x).b-slotIndex(y).b);}else{candidates.sort((x,y)=>slotIndex(x).a-slotIndex(y).a||slotIndex(x).b-slotIndex(y).b);}return candidates.find(s=>!currentName(s))||candidates[0]||null;}" +
                "function findExisting(item){const n=norm(item.name);return slots().find(s=>roleOf(s)===item.role&&reserveOf(s)===(item.type==='B')&&nameMatches(currentName(s),n));}" +
                "function drawerRoots(){return [...document.querySelectorAll('nz-drawer,.ant-drawer,.ant-drawer-content-wrapper,.cdk-overlay-pane,[role=dialog]')].filter(visible);}" +
                "function pickerRoot(){const rs=drawerRoots();return rs.length?rs[rs.length-1]:null;}" +
                "function drawerText(root){return norm(root?(root.innerText||root.textContent):'');}" +
                "function setNative(input,value){const p=Object.getPrototypeOf(input);const d=Object.getOwnPropertyDescriptor(p,'value');if(d&&d.set)d.set.call(input,value);else input.value=value;['input','keyup','change'].forEach(n=>input.dispatchEvent(new Event(n,{bubbles:true})));}" +
                "function pickerSearch(root,name){if(!root)return false;const ins=[...root.querySelectorAll('input')].filter(visible);const wanted=ins.find(x=>{const z=norm((x.placeholder||'')+' '+(x.getAttribute('aria-label')||'')+' '+(x.getAttribute('name')||''));return x.type==='search'||z.includes('cerca')||z.includes('ricerca')||z.includes('giocatore')||z.includes('player');});const inp=wanted||ins[0];if(!inp)return false;inp.focus();setNative(inp,name);return true;}" +
                "function nameMatches(text,target){const a=norm(text),b=norm(target);if(!a||!b)return false;if(a===b||a.includes(b)||b.includes(a))return true;const aw=a.split(' ').filter(x=>x.length>2),bw=b.split(' ').filter(x=>x.length>2);if(!aw.length||!bw.length)return false;let hits=0;for(const w of bw)if(aw.some(x=>x===w||x.includes(w)||w.includes(x)))hits++;return hits>=Math.max(1,Math.min(2,bw.length));}" +
                "function candidatePlayer(root,name){const n=norm(name);const selectors='button,[role=button],[role=option],[role=radio],li,[data-player],ui-player-card,.player,.player-item';const els=[...root.querySelectorAll(selectors)].filter(e=>visible(e)&&enabled(e)&&e.tagName!=='A');let best=null,bestScore=-1;for(const e of els){const t=norm((e.innerText||e.textContent||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('data-player-name')||''));if(!t||!nameMatches(t,n))continue;let s=20;if(t===n)s+=500;else if(t.includes(n))s+=300;const words=n.split(' ').filter(w=>w.length>2);for(const w of words)if(t.includes(w))s+=60;if(e.matches('button,[role=button],[role=option],[role=radio],li,[data-player]'))s+=30;if(s>bestScore){bestScore=s;best=e;}}return best;}" +
                "function clickPlayer(e){if(!e)return false;let p=e;for(let i=0;i<8&&p;i++,p=p.parentElement){if(p.tagName==='A')continue;if(p.tagName==='BUTTON'||p.getAttribute('role')==='button'||p.getAttribute('role')==='option'||p.getAttribute('role')==='radio'||p.hasAttribute('data-player')||p.tagName==='LI')return fire(p);}return fire(e);}" +
                "async function waitDrawer(before){for(let i=0;i<40;i++){const r=pickerRoot();if(r&&r!==before)return r;if(r&&drawerText(r).includes('rosa'))return r;await sleep(200);}return null;}" +
                "async function waitDrawerClose(root){for(let i=0;i<30;i++){if(!root||!visible(root)||!drawerText(root).includes('rosa'))return true;await sleep(200);}return false;}" +
                "async function choosePlayer(root,name){for(let pass=0;pass<3;pass++){pickerSearch(root,name);await sleep(400);for(let i=0;i<25;i++){const c=candidatePlayer(root,name);if(c&&clickPlayer(c)){await sleep(700);return true;}await sleep(250);}await sleep(300);}return false;}" +
                "function verify(slot,name){return nameMatches(currentName(slot),norm(name));}" +
                "function findSave(){const els=[...document.querySelectorAll('button,[role=button],input[type=submit]')].filter(e=>visible(e)&&enabled(e));return els.find(e=>{const t=norm(e.innerText||e.textContent||e.value||e.getAttribute('aria-label')||'');return t==='salva formazione'||t.includes('salva formazione');})||null;}" +
                "function saveFeedback(){const roots=[...document.querySelectorAll('.ant-message,.ant-message-notice,.ant-notification,.ant-notification-notice,[role=alert],.ant-alert')].filter(visible);for(const r of roots){const t=norm(r.innerText||r.textContent||'');if(/salvat|success|complet|aggiornat|inserit/.test(t))return t;}return '';}" +
                "function allFilled(){return players.every(item=>{const s=slots().find(x=>roleOf(x)===item.role&&reserveOf(x)===(item.type==='B')&&verify(x,item.name));return !!s;});}" +
                "const moduleOk=await selectModule(moduleName);" +
                "if(!moduleOk){AndroidBridge.report('INSERISCI FALLITO: modulo '+moduleName+' non selezionato.');return;}" +
                "await sleep(900);" +
                "describeSlots();" +
                "let networkOk=false,networkUrl='';" +
                "try{const oo=XMLHttpRequest.prototype.open,os=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(m,u){this.__ff_url=u||'';return oo.apply(this,arguments)};XMLHttpRequest.prototype.send=function(){this.addEventListener('load',function(){if(this.status>=200&&this.status<300){networkOk=true;networkUrl=this.__ff_url||'';}});return os.apply(this,arguments);}}catch(x){}" +
                "try{const of=window.fetch;window.fetch=function(){return of.apply(this,arguments).then(r=>{if(r&&r.ok){networkOk=true;networkUrl=r.url||'';}return r;});}}catch(x){}" +
                "let used=new Set(),ok=0,fail=0;" +
                "for(const item of players){" +
                "let slot=findExisting(item);" +
                "if(slot){ok++;used.add(slotKey(slot));AndroidBridge.report('INSERISCI: '+item.name+' già presente in '+slotKey(slot));continue;}" +
                "slot=targetSlot(item,used);" +
                "if(!slot){AndroidBridge.report('INSERISCI: slot '+item.role+' non trovato per '+item.name);fail++;continue;}" +
                "const key=slotKey(slot);used.add(key);" +
                "let before=pickerRoot();" +
                "if(!fire(slot)){AndroidBridge.report('INSERISCI: click slot fallito '+key+' per '+item.name);fail++;continue;}" +
                "let root=await waitDrawer(before);" +
                "if(!root){AndroidBridge.report('INSERISCI: Rosa non aperta per '+item.name+' slot '+key);fail++;continue;}" +
                "if(!drawerText(root).includes('rosa'))AndroidBridge.report('INSERISCI: drawer aperto, testo Rosa non rilevato per '+item.name);" +
                "if(!(await choosePlayer(root,item.name))){AndroidBridge.report('INSERISCI: '+item.name+' non trovato nella Rosa');fail++;continue;}" +
                "await waitDrawerClose(root);await sleep(800);" +
                "if(verify(slot,item.name)){ok++;AndroidBridge.report('INSERISCI: '+item.name+' inserito nello slot '+key);}else{fail++;AndroidBridge.report('INSERISCI: verifica fallita per '+item.name+' nello slot '+key+'; trovato='+currentName(slot));}" +
                "}" +
                "if(fail>0||ok!==players.length){AndroidBridge.report('INSERISCI FALLITO: '+ok+'/'+players.length+' verificati, errori '+fail+'. Salvataggio NON eseguito.');return;}" +
                "AndroidBridge.report('FORMAZIONE: tutti i titolari verificati');" +
                "AndroidBridge.report('PANCHINA: tutte le riserve verificate');" +
                "if(!allFilled()){AndroidBridge.report('INSERISCI FALLITO: verifica globale della formazione non superata.');return;}" +
                "const save=findSave();if(!save){AndroidBridge.report('SALVATAGGIO FALLITO: pulsante Salva formazione non trovato');return;}" +
                "fire(save);AndroidBridge.report('SALVATAGGIO: comando inviato, attendo conferma del sito...');" +
                "for(let i=0;i<60;i++){await sleep(300);const fb=saveFeedback();if(fb&&/salvat|success|complet|aggiornat|inserit/.test(fb)){AndroidBridge.report('SALVATAGGIO_OK:'+raw(fb));return;}if(networkOk&&allFilled()&&i>=6){AndroidBridge.report('SALVATAGGIO_OK: risposta server '+networkUrl);return;}}" +
                "AndroidBridge.report('SALVATAGGIO_FALLITO: nessuna conferma del sito o risposta server');" +
                "})().catch(e=>AndroidBridge.report('INSERISCI ERRORE: '+(e&&e.message?e.message:e)));";

        saveVerificationStarted = true;
        webView.evaluateJavascript(js, null);
    }

    private String playerNameForWeb(Player p) {
        return p.officialName == null || p.officialName.trim().isEmpty() ? p.excelName : p.officialName;
    }

    private String jsQuote(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
    }

    private void buildStatusPanel() {
        statusViews.clear();
        String[] steps = {"EXCEL", "RUOLI", "ANALISI", "CREDENZIALI", "LOGIN", "LEGA", "FORMAZIONE", "PANCHINA", "SALVATAGGIO", "COMPLETATO"};
        for (String step : steps) {
            TextView row = new TextView(this);
            row.setTextSize(13);
            row.setTextColor(Color.rgb(150, 158, 170));
            row.setPadding(0, dp(3), 0, dp(3));
            row.setText("○  " + step + " — in attesa");
            statusViews.put(step, row);
            statusContainer.addView(row);
        }
    }

    private void setStepState(String step, int state, String detail) {
        runOnUiThread(() -> {
            TextView v = statusViews.get(step);
            if (v == null) return;
            String symbol = state == 2 ? "✓" : state == 1 ? "●" : state == 0 ? "✕" : "○";
            v.setText(symbol + "  " + step + " — " + (detail == null ? "" : detail));
            v.setTextColor(state == 2 ? Color.rgb(120, 220, 150) : state == 0 ? Color.rgb(245, 100, 100) : state == 1 ? Color.rgb(120, 180, 255) : Color.rgb(150, 158, 170));
        });
    }

    private void handleAutomationReport(String message) {
        if (message == null) return;
        Log.d("FANTA_DEBUG", message);
        if (message.startsWith("SALVATAGGIO_OK:")) {
            setStepState("FORMAZIONE", 2, "Titolari verificati");
            setStepState("PANCHINA", 2, "Riserve verificate");
            setStepState("SALVATAGGIO", 2, message.substring("SALVATAGGIO_OK:".length()).trim());
            setStepState("COMPLETATO", 2, "Formazione inserita correttamente");
            notificationPending = true;
            sendSuccessNotificationAndClose();
            return;
        }
        if (message.startsWith("INSERISCI FALLITO:")) {
            setStepState("FORMAZIONE", 0, message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        if (message.startsWith("SALVATAGGIO_FALLITO:")) {
            setStepState("SALVATAGGIO", 0, message.substring("SALVATAGGIO_FALLITO:".length()).trim());
            Toast.makeText(this, "Formazione non confermata dal sito: app non chiusa.", Toast.LENGTH_LONG).show();
            return;
        }
        if (message.startsWith("INSERISCI:")) {
            if (message.contains("Panchina")) setStepState("PANCHINA", 1, "Inserimento riserve...");
            Log.d("FANTA_DEBUG", message);
        } else if (message.startsWith("SALVATAGGIO:")) {
            setStepState("SALVATAGGIO", 1, "Attendo conferma...");
        } else if (message.startsWith("LOGIN:")) {
            setStepState("LOGIN", 1, message.substring(6).trim());
        }
    }

    private void sendSuccessNotificationAndClose() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        postSuccessNotification();
    }

    private void postSuccessNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Stato formazione", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifiche di completamento FantaFormation");
            nm.createNotificationChannel(channel);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⚽ FantaFormation")
                .setContentText("Formazione inserita correttamente!")
                .setStyle(new Notification.BigTextStyle().bigText("11 titolari e panchina salvati correttamente su Leghe Fantacalcio."))
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);
        nm.notify(9001, b.build());
        new android.os.Handler(getMainLooper()).postDelayed(() -> finishAndRemoveTask(), 700);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                postSuccessNotification();
            } else {
                Toast.makeText(this, "Formazione inserita correttamente, ma notifiche Android non autorizzate.", Toast.LENGTH_LONG).show();
                new android.os.Handler(getMainLooper()).postDelayed(() -> finishAndRemoveTask(), 1200);
            }
        }
    }

    private void copyFormationToClipboard() {
        if (currentBestResult == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Modulo: ").append(currentBestResult.module).append("\n\nTitolari:\n");

        for (Player p : currentBestResult.goalkeeper) sb.append("- ").append(p.officialName.isEmpty() ? p.excelName : p.officialName).append("\n");
        for (Player p : currentBestResult.defenders) sb.append("- ").append(p.officialName.isEmpty() ? p.excelName : p.officialName).append("\n");
        for (Player p : currentBestResult.midfielders) sb.append("- ").append(p.officialName.isEmpty() ? p.excelName : p.officialName).append("\n");
        for (Player p : currentBestResult.attackers) sb.append("- ").append(p.officialName.isEmpty() ? p.excelName : p.officialName).append("\n");        sb.append("\nPanchina:\n");
        for (Player p : currentBestResult.benchGoalkeeper) sb.append("- P ").append(p.excelName).append("\n");
        for (Player p : currentBestResult.benchDefenders) sb.append("- D ").append(p.excelName).append("\n");
        for (Player p : currentBestResult.benchMidfielders) sb.append("- C ").append(p.excelName).append("\n");
        for (Player p : currentBestResult.benchAttackers) sb.append("- A ").append(p.excelName).append("\n");

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
                    setStepState("EXCEL", 2, "Rosa caricata: " + result.size() + " righe");
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
        setStepState("ANALISI", 1, "Analisi in corso...");
        resultText.setText("Analisi V4 in corso...\n1. Ruoli + probabili\n2. Statistiche stagionali + ultime 5\n3. News e stato fisico\n4. Avversario + casa/trasferta\n5. Ottimizzazione modulo...");

        executor.execute(() -> {
            try {
                String quotesHtml = downloadSafe(FANTACALCIO_QUOTE);
                String probabiliHtml = downloadSafe(FANTACALCIO_PROBABILI);
                String gazzettaHtml = downloadSafe(GAZZETTA_PROBABILI);
                String skyHtml = downloadSafe(SKY_PROBABILI);
                String statsHtml = downloadSafe(FANTACALCIO_STATS);
                String newsHtml = downloadSafe(FANTACALCIO_NEWS);

                Map<String, OfficialPlayer> officialPlayers = parseOfficialPlayers(quotesHtml);
                Map<String, ProbabilityInfo> probabilities = parseFantacalcioProbabili(probabiliHtml);
                Map<String, Integer> gazzetta = parseExternalSource(gazzettaHtml);
                Map<String, Integer> sky = parseExternalSource(skyHtml);
                Map<String, AdvancedStats> statistics = parseAdvancedStats(statsHtml);
                Map<String, MatchContext> matchContexts = loadMatchContexts(officialPlayers, newsHtml, statsHtml);
                enrichRecentFiveStats(statistics);
                Map<String, NewsSignal> newsSignals = parseNewsSignals(newsHtml, probabilities);

                ArrayList<Player> players = buildPlayers(officialPlayers, probabilities, gazzetta, sky, statistics, newsSignals, matchContexts);
                lastParsedPlayers = players;

                FormationResult best = calculateBestFormation(players);
                currentBestResult = best;

                runOnUiThread(() -> {
                    setStepState("RUOLI", 2, "Ruoli verificati");
                    if (best != null && best.valid) setStepState("ANALISI", 2, "Formazione ottimizzata: " + best.module);
                    if (isFinishing()) return;
                    displayResult(players, best);
                    if (autoRunRequested && best != null && best.valid && credentialsManager.hasCredentials()) {
                        autoRunRequested = false;
                        setStepState("ANALISI", 2, "Formazione ottimizzata: " + best.module);
                        openLegheWebViewDialog();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    setStepState("ANALISI", 0, "Errore: " + e.getMessage());
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

    private static class AdvancedStats {
        int appearances;
        double averageVote;
        double fantasyAverage;
        int goals;
        int assists;
        int yellow;
        int red;
        int penaltiesScored;
        int penaltiesTaken;
        double recentForm;
        double last5Mv;
        double last5Fm;
        int last5Appearances;
        int last5Goals;
        int last5Assists;
        boolean recentDataAvailable;
    }

    private static class NewsSignal {
        int score;
        String reason = "";
    }

    private static class MatchContext {
        String opponent = "";
        boolean home;
        double opponentDifficulty = 50.0;
        double teamForm = 50.0;
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
        double recentForm;
        double opponentFactor;
        double newsFactor;
        double statsFactor;
        String opponent = "";
        String explanation = "";

        Player(String excelName, String officialName, String team, String role, int quote, int fvm, int probable, int externalAgreement, boolean starter, boolean bench) {
            this(excelName, officialName, team, role, quote, fvm, probable, externalAgreement, starter, bench, null, null, null);
        }

        Player(String excelName, String officialName, String team, String role, int quote, int fvm, int probable, int externalAgreement, boolean starter, boolean bench, AdvancedStats stats, NewsSignal news, MatchContext context) {
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
            calculateScore(stats, news, context);
        }

        private void calculateScore(AdvancedStats stats, NewsSignal news, MatchContext context) {
            score = 0;
            recentForm = stats != null ? stats.recentForm : 50.0;
            statsFactor = stats != null ? clamp(stats.recentForm, 0, 100) : 50.0;
            opponentFactor = context != null ? (100.0 - clamp(context.opponentDifficulty, 0, 100)) : 50.0;
            newsFactor = news != null ? news.score : 0.0;
            opponent = context != null ? context.opponent : "";

            // V4: combina qualità attesa, disponibilità e contesto della prossima giornata.
            // La titolarità non è solo un bonus: riduce il rischio di scegliere un giocatore
            // forte ma poco probabile, mentre forma, avversario e news descrivono il rendimento atteso.
            double availability = probable > 0 ? (0.55 + 0.45 * (probable / 100.0)) : 0.62;
            double seasonFactor = stats != null && stats.fantasyAverage > 0
                    ? clamp((stats.fantasyAverage - 5.0) * 20.0, 0, 100) : 50.0;
            double quality = statsFactor * 0.50
                    + seasonFactor * 0.20
                    + opponentFactor * 0.20
                    + clamp(newsFactor + 50.0, 0, 100) * 0.10;

            score += probable * 0.24;
            score += quality * 0.22 * availability;
            score += opponentFactor * 0.10;
            score += clamp(newsFactor, -100, 100) * 0.10;
            score += externalAgreement * 4.0;
            score += Math.min(fvm, 300) * 0.050;
            score += Math.min(quote, 100) * 0.030;

            if (starter) score += 7;
            if (bench && !starter) score -= 10;
            if (probable == 0) score -= 30;

            if (stats != null) {
                double seasonBonus = stats.goals * 1.4 + stats.assists * 1.0 + stats.penaltiesScored * 1.8;
                double recentBonus = stats.last5Goals * 3.0 + stats.last5Assists * 2.0;
                score += Math.min(16.0, seasonBonus + recentBonus);

                // Profilo bonus per ruolo: privilegia upside senza ignorare la continuità.
                if ("A".equals(role)) score += stats.goals * 0.9 + stats.assists * 0.5;
                if ("C".equals(role)) score += stats.assists * 0.8 + stats.goals * 0.7;
                if ("D".equals(role)) score += stats.assists * 0.35 + stats.goals * 0.6;
                if ("P".equals(role)) score += Math.max(0, 4.0 - stats.red * 1.5);
            }

            if (context != null && context.home) score += 2.5;
            explanation = buildExplanation(stats, news, context);
        }

        private String buildExplanation(AdvancedStats stats, NewsSignal news, MatchContext context) {
            ArrayList<String> reasons = new ArrayList<>();
            if (stats != null && stats.appearances > 0) {
                if (stats.recentDataAvailable && stats.last5Appearances > 0) {
                    reasons.add(String.format(Locale.US, "ultime 5: FM %.2f", stats.last5Fm));
                    if (stats.last5Goals > 0 || stats.last5Assists > 0) reasons.add("bonus recenti");
                } else {
                    reasons.add(String.format(Locale.US, "forma %.1f", stats.recentForm));
                }
                if (stats.goals > 0 || stats.assists > 0) reasons.add("profilo bonus");
            }
            if (probable >= 85 || starter) reasons.add("alta titolarità");
            else if (probable >= 60) reasons.add("buona titolarità");
            else if (probable > 0) reasons.add("ballottaggio");
            if (context != null && !context.opponent.isEmpty()) {
                if (context.opponentDifficulty < 40) reasons.add("avversario favorevole");
                else if (context.opponentDifficulty > 70) reasons.add("avversario difficile");
                else reasons.add("avversario medio");
                if (context.home) reasons.add("in casa");
            }
            if (news != null && !news.reason.isEmpty()) reasons.add(news.reason);
            if (reasons.isEmpty()) reasons.add("profilo equilibrato");
            return String.join(" • ", reasons);
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
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

    private ArrayList<Player> buildPlayers(Map<String, OfficialPlayer> official, Map<String, ProbabilityInfo> probabilities, Map<String, Integer> gazzetta, Map<String, Integer> sky, Map<String, AdvancedStats> statistics, Map<String, NewsSignal> newsSignals, Map<String, MatchContext> matchContexts) {
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
                Player player = new Player(excelName, excelName, "", cachedRole, 0, 0, 0, 0, false, false, null, null, null);
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

            AdvancedStats stats = statistics.get(normalizedOfficialName);
            if (stats == null) stats = statistics.get(normalizedExcel);
            NewsSignal news = newsSignals.get(normalizedOfficialName);
            if (news == null) news = newsSignals.get(normalizedExcel);
            MatchContext context = matchContexts.get(normalize(officialPlayer.team));

            Player player = new Player(excelName, officialPlayer.name, officialPlayer.team, role, officialPlayer.classicQuote, officialPlayer.fvm, probable, externalAgreement, starter, bench, stats, news, context);
            result.add(player);
        }

        return result;
    }

    private Map<String, AdvancedStats> parseAdvancedStats(String html) {
        Map<String, AdvancedStats> result = new HashMap<>();
        if (html == null || html.isEmpty()) return result;
        Document document = Jsoup.parse(html);
        for (Element row : document.select("tr")) {
            Elements cells = row.select("th,td");
            if (cells.size() < 5) continue;
            String name = cleanName(cells.get(0).text());
            if (name.length() < 3 || normalize(name).equals("calciatore")) {
                for (Element cell : cells) {
                    String candidate = cleanName(cell.text());
                    if (candidate.length() >= 3 && !candidate.matches(".*\\d.*") && !normalize(candidate).equals("calciatore")) { name = candidate; break; }
                }
            }
            if (name.isEmpty()) continue;

            ArrayList<String> values = new ArrayList<>();
            for (Element cell : cells) values.add(cleanName(cell.text()).replace(',', '.'));
            AdvancedStats st = new AdvancedStats();

            // Trova PV/MV/FM cercando la prima sequenza coerente dopo il nome.
            for (int i = 1; i + 2 < values.size(); i++) {
                int pv = parseIntLoose(values.get(i));
                double mv = parseDouble(values.get(i + 1));
                double fm = parseDouble(values.get(i + 2));
                if (pv >= 0 && pv <= 60 && mv >= 4 && mv <= 10.5 && fm >= 4 && fm <= 20) {
                    st.appearances = pv; st.averageVote = mv; st.fantasyAverage = fm;
                    if (i + 3 < values.size()) st.goals = parseIntLoose(values.get(i + 3));
                    if (i + 7 < values.size()) st.assists = parseIntLoose(values.get(i + 7));
                    if (i + 8 < values.size()) st.yellow = parseIntLoose(values.get(i + 8));
                    if (i + 9 < values.size()) st.red = parseIntLoose(values.get(i + 9));
                    break;
                }
            }

            double seasonForm = st.fantasyAverage > 0 ? (st.fantasyAverage - 5.0) * 20.0 : 50.0;
            double continuity = Math.min(8.0, st.appearances * 0.8);
            st.recentForm = clampDouble(seasonForm + continuity, 0, 100);
            result.put(normalize(name), st);
        }
        return result;
    }

    /**
     * Recupera le statistiche "Ultime 5" per le sole squadre realmente presenti nella rosa.
     * Se la fonte non è disponibile, mantiene il dato stagionale senza inventare numeri.
     */
    private void enrichRecentFiveStats(Map<String, AdvancedStats> statistics) {
        if (statistics == null || statistics.isEmpty()) return;
        String html = downloadSafe(FANTACALCIO_RECENT_BASE);
        if (html.isEmpty()) return;
        parseRecentFiveVotesPage(html, statistics);
    }

    private void parseRecentFiveVotesPage(String html, Map<String, AdvancedStats> statistics) {
        Document d = Jsoup.parse(html);
        for (Element row : d.select("tr")) {
            String rowText = cleanName(row.text());
            if (rowText.length() < 10) continue;
            AdvancedStats st = null;
            String matchedName = null;
            for (Map.Entry<String, AdvancedStats> entry : statistics.entrySet()) {
                String key = entry.getKey();
                if (key.length() >= 4 && rowText.toLowerCase(Locale.ROOT).contains(key)) {
                    st = entry.getValue(); matchedName = key; break;
                }
            }
            if (st == null) continue;

            // Nella tabella Voti Oggettivi la colonna "Ultime 5" è rappresentata
            // da una media seguita dal numero di presenze, ad esempio "7.50 (4)".
            Matcher m = Pattern.compile("(?<!\\d)([4-9](?:\\.[0-9]{1,2})?|10(?:\\.0{1,2})?)\\s*\\((\\d+)\\)").matcher(rowText.replace(',', '.'));
            double best = 0; int apps = 0;
            while (m.find()) {
                try { best = Double.parseDouble(m.group(1)); apps = Integer.parseInt(m.group(2)); } catch (Exception ignored) {}
            }
            if (best > 0 && apps > 0) {
                st.last5Mv = best;
                st.last5Fm = best; // fallback prudente: la fonte espone qui la MV delle ultime 5.
                st.last5Appearances = Math.min(5, apps);
                st.recentDataAvailable = true;

                double base = (best - 5.0) * 20.0;
                double trend = st.fantasyAverage > 0 ? (best - st.fantasyAverage) * 10.0 : 0;
                st.recentForm = clampDouble(base + trend, 0, 100);
            }
        }
    }

    private int findNumericAfter(List<String> values, int start, int min, int max) {
        for (int i = Math.max(0, start); i < values.size(); i++) {
            String v = values.get(i).replace(".", "");
            if (v.matches("\\d+")) {
                try { int x = Integer.parseInt(v); if (x >= min && x <= max) return x; } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private int indexOfValue(List<String> values, String value) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).equals(value)) return i;
        return -1;
    }

    private int parseIntLoose(String s) {
        if (s == null) return 0;
        Matcher m = Pattern.compile("-?\\d+").matcher(s.replace('.', ' '));
        if (m.find()) try { return Integer.parseInt(m.group()); } catch (Exception ignored) {}
        return 0;
    }

    private double parseDouble(String s) {
        if (s == null) return 0;
        try { return Double.parseDouble(s.replace(',', '.')); } catch (Exception e) { return 0; }
    }

    private double clampDouble(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    private Map<String, NewsSignal> parseNewsSignals(String html, Map<String, ProbabilityInfo> probabilities) {
        Map<String, NewsSignal> result = new HashMap<>();
        if (html == null || html.isEmpty() || formazione == null) return result;
        Document document = Jsoup.parse(html);
        String full = document.text();
        String lowerFull = full.toLowerCase(Locale.ROOT);

        for (String[] row : formazione) {
            if (row.length < 2) continue;
            String excelName = row[1];
            String normalized = normalize(excelName);
            if (normalized.length() < 3) continue;

            String official = excelName;
            for (Element a : document.select("a")) {
                if (similarNames(normalized, normalize(a.text()))) { official = a.text(); break; }
            }
            String needle = normalize(official);
            int pos = lowerFull.indexOf(needle);
            if (pos < 0) pos = lowerFull.indexOf(normalized);
            if (pos < 0) continue;

            int from = Math.max(0, pos - 650);
            int to = Math.min(lowerFull.length(), pos + needle.length() + 950);
            String context = lowerFull.substring(from, to);

            NewsSignal signal = new NewsSignal();
            int total = 0;
            ArrayList<String> reasons = new ArrayList<>();
            String[][] signals = {
                    {"titolare", "12", "titolare"}, {"in forma", "9", "in forma"},
                    {"rigorista", "11", "rigorista"}, {"calci da fermo", "7", "calci da fermo"},
                    {"corner", "5", "calci piazzati"}, {"convocato", "5", "convocato"},
                    {"in dubbio", "-18", "⚠ in dubbio"}, {"ballottaggio", "-13", "⚠ ballottaggio"},
                    {"turnover", "-15", "⚠ rischio turnover"}, {"non al meglio", "-12", "⚠ condizione da verificare"},
                    {"infortun", "-35", "⚠ problema fisico"}, {"squalificat", "-45", "⚠ squalificato"},
                    {"riposo", "-12", "⚠ possibile riposo"}, {"preservato", "-14", "⚠ possibile rotazione"}
            };
            for (String[] sig : signals) {
                if (context.contains(sig[0])) {
                    total += Integer.parseInt(sig[1]);
                    if (!reasons.contains(sig[2])) reasons.add(sig[2]);
                }
            }
            signal.score = Math.max(-70, Math.min(40, total));
            if (!reasons.isEmpty()) signal.reason = String.join(" + ", reasons);
            result.put(normalized, signal);
            result.put(normalize(official), signal);
        }
        return result;
    }

    private Map<String, MatchContext> loadMatchContexts(Map<String, OfficialPlayer> official, String newsHtml, String statsHtml) {
        Map<String, MatchContext> result = new HashMap<>();
        if (official == null || official.isEmpty()) return result;

        String calendarHtml = downloadSafe(FANTACALCIO_CALENDAR);
        String source = calendarHtml.isEmpty() ? newsHtml : calendarHtml;
        String text = Jsoup.parse(source == null ? "" : source).text();
        Map<String, Integer> standings = parseCurrentStandings(statsHtml);

        Set<String> teams = new HashSet<>();
        for (OfficialPlayer p : official.values()) if (p != null && p.team != null && !p.team.isEmpty()) teams.add(p.team);

        for (String team : teams) {
            MatchContext c = parseNextMatchContextFromText(text, team);
            if (c == null) { c = new MatchContext(); c.opponentDifficulty = 50; }
            Integer pts = standings.get(normalize(c.opponent));
            if (pts != null) {
                // Correzione dinamica: la classifica corrente sposta leggermente il
                // rating statico dell'avversario, senza far pesare troppo un campione piccolo.
                c.opponentDifficulty = clampDouble(c.opponentDifficulty + (pts - 3) * 2.0, 20, 90);
            }
            result.put(normalize(team), c);
        }
        return result;
    }

    private MatchContext parseNextMatchContextFromText(String text, String team) {
        if (text == null || text.trim().isEmpty() || team == null || team.trim().isEmpty()) return null;

        String source = cleanName(text).replace('\u00a0', ' ');
        String lower = normalize(source);
        String teamName = teamDisplayName(team);
        String teamNorm = normalize(teamName);
        String codeNorm = normalize(team);

        // Cerca una riga/blocco che contenga la squadra. In caso di calendario
        // tabellare prendiamo le squadre immediatamente vicine al nome cercato.
        int pos = lower.indexOf(teamNorm);
        if (pos < 0) pos = lower.indexOf(codeNorm);
        if (pos < 0) return null;

        int from = Math.max(0, pos - 180);
        int to = Math.min(lower.length(), pos + Math.max(teamNorm.length(), codeNorm.length()) + 180);
        String block = lower.substring(from, to);

        String[] names = {
                "inter", "napoli", "milan", "juventus", "roma", "atalanta", "lazio", "fiorentina",
                "bologna", "como", "udinese", "torino", "genoa", "lecce", "parma", "cagliari",
                "sassuolo", "monza", "venezia", "frosinone"
        };

        String own = teamNorm;
        String opponent = "";
        int bestDistance = Integer.MAX_VALUE;
        int ownPos = block.indexOf(own);
        if (ownPos < 0) ownPos = block.indexOf(codeNorm);

        for (String candidate : names) {
            if (candidate.equals(own)) continue;
            int cp = block.indexOf(candidate);
            if (cp >= 0) {
                int distance = ownPos >= 0 ? Math.abs(cp - ownPos) : cp;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    opponent = candidate;
                }
            }
        }

        if (opponent.isEmpty()) return null;

        MatchContext context = new MatchContext();
        context.opponent = opponent;

        // Determina casa/trasferta quando il blocco contiene indicatori espliciti.
        String before = ownPos > 0 ? block.substring(Math.max(0, ownPos - 70), ownPos) : "";
        String after = ownPos >= 0 ? block.substring(Math.min(block.length(), ownPos + own.length()), Math.min(block.length(), ownPos + own.length() + 70)) : "";
        if (before.contains("vs") || before.contains("-")) {
            context.home = before.contains("vs") || before.trim().endsWith("-");
        } else if (after.contains("vs") || after.contains("-")) {
            context.home = after.contains("vs") || after.trim().startsWith("-");
        }

        context.opponentDifficulty = opponentDifficulty(opponent);
        context.teamForm = 50.0;
        return context;
    }

    private String teamDisplayName(String team) {
        String t = normalize(team);
        if (t.equals("int") || t.contains("inter")) return "inter";
        if (t.equals("mil") || t.contains("milan")) return "milan";
        if (t.equals("juv") || t.contains("juventus")) return "juventus";
        if (t.equals("nap") || t.contains("napoli")) return "napoli";
        if (t.equals("rom") || t.equals("roma")) return "roma";
        if (t.equals("ata") || t.contains("atalanta")) return "atalanta";
        if (t.equals("laz") || t.contains("lazio")) return "lazio";
        if (t.equals("fio") || t.contains("fiorentina")) return "fiorentina";
        if (t.equals("bol") || t.contains("bologna")) return "bologna";
        if (t.equals("com") || t.contains("como")) return "como";
        if (t.equals("udi") || t.contains("udinese")) return "udinese";
        if (t.equals("tor") || t.contains("torino")) return "torino";
        if (t.equals("gen") || t.contains("genoa")) return "genoa";
        if (t.equals("lec") || t.contains("lecce")) return "lecce";
        if (t.equals("par") || t.contains("parma")) return "parma";
        if (t.equals("cag") || t.contains("cagliari")) return "cagliari";
        if (t.equals("sas") || t.contains("sassuolo")) return "sassuolo";
        if (t.equals("mon") || t.contains("monza")) return "monza";
        if (t.equals("ven") || t.contains("venezia")) return "venezia";
        if (t.equals("fro") || t.contains("frosinone")) return "frosinone";
        return t;
    }

    private double opponentDifficulty(String opponent) {
        String o = normalize(opponent);
        // Rating iniziale prudente: viene poi corretto dai punti della classifica
        // quando parseCurrentStandings() trova il dato nella fonte delle statistiche.
        if (o.equals("inter")) return 88;
        if (o.equals("napoli")) return 84;
        if (o.equals("juventus")) return 82;
        if (o.equals("milan")) return 80;
        if (o.equals("atalanta")) return 78;
        if (o.equals("roma") || o.equals("lazio")) return 74;
        if (o.equals("fiorentina") || o.equals("bologna")) return 68;
        if (o.equals("como") || o.equals("torino") || o.equals("udinese")) return 58;
        if (o.equals("genoa") || o.equals("parma") || o.equals("cagliari")) return 48;
        if (o.equals("lecce") || o.equals("venezia") || o.equals("monza") || o.equals("frosinone")) return 40;
        if (o.equals("sassuolo")) return 46;
        return 50;
    }

    private Map<String, Integer> parseCurrentStandings(String html) {
        Map<String, Integer> result = new HashMap<>();
        if (html == null || html.isEmpty()) return result;
        Document d = Jsoup.parse(html);
        String[] teams = {"Inter","Napoli","Milan","Juventus","Roma","Atalanta","Lazio","Fiorentina","Bologna","Como","Udinese","Torino","Genoa","Lecce","Parma","Cagliari","Sassuolo","Monza","Venezia","Frosinone"};
        for (Element row : d.select("tr")) {
            String t = cleanName(row.text());
            Matcher rank = Pattern.compile("^(\\d{1,2})\\s+").matcher(t);
            if (!rank.find()) continue;
            Elements cells = row.select("th,td");
            int points = -1;
            for (int i = cells.size() - 1; i >= 0; i--) {
                int x = parseIntLoose(cells.get(i).text());
                if (x >= 0 && x <= 120) { points = x; break; }
            }
            if (points < 0) continue;
            for (String team : teams) {
                if (normalize(t).contains(normalize(team))) { result.put(normalize(team), points); break; }
            }
        }
        return result;
    }

    private String teamSlug(String team) {
        String t = normalize(team);
        if (t.equals("int") || t.contains("inter")) return "inter";
        if (t.equals("mil") || t.contains("milan")) return "milan";
        if (t.equals("juv") || t.contains("juventus")) return "juventus";
        if (t.equals("nap") || t.contains("napoli")) return "napoli";
        if (t.equals("rom") || t.equals("roma")) return "roma";
        if (t.equals("ata") || t.contains("atalanta")) return "atalanta";
        if (t.equals("laz") || t.contains("lazio")) return "lazio";
        if (t.equals("fio") || t.contains("fiorentina")) return "fiorentina";
        if (t.equals("bol") || t.contains("bologna")) return "bologna";
        if (t.equals("com") || t.contains("como")) return "como";
        if (t.equals("udi") || t.contains("udinese")) return "udinese";
        if (t.equals("tor") || t.contains("torino")) return "torino";
        if (t.equals("gen") || t.contains("genoa")) return "genoa";
        if (t.equals("lec") || t.contains("lecce")) return "lecce";
        if (t.equals("par") || t.contains("parma")) return "parma";
        if (t.equals("cag") || t.contains("cagliari")) return "cagliari";
        if (t.equals("sas") || t.contains("sassuolo")) return "sassuolo";
        if (t.equals("mon") || t.contains("monza")) return "monza";
        if (t.equals("ven") || t.contains("venezia")) return "venezia";
        if (t.equals("fro") || t.contains("frosinone")) return "frosinone";
        return "";
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
        ArrayList<Player> benchGoalkeeper = new ArrayList<>();
        ArrayList<Player> benchDefenders = new ArrayList<>();
        ArrayList<Player> benchMidfielders = new ArrayList<>();
        ArrayList<Player> benchAttackers = new ArrayList<>();
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

        // Panchina: massimo 2 giocatori per ruolo, esclusi i titolari.
        result.benchGoalkeeper.addAll(goalkeepers.subList(1, Math.min(goalkeepers.size(), 3)));
        result.benchDefenders.addAll(defenderList.subList(defenders, Math.min(defenderList.size(), defenders + 2)));
        result.benchMidfielders.addAll(midfielderList.subList(midfielders, Math.min(midfielderList.size(), midfielders + 2)));
        result.benchAttackers.addAll(attackerList.subList(attackers, Math.min(attackerList.size(), attackers + 2)));

        result.score = 0;
        for (Player p : result.goalkeeper) result.score += p.score;
        for (Player p : result.defenders) result.score += p.score;
        for (Player p : result.midfielders) result.score += p.score;
        for (Player p : result.attackers) result.score += p.score;

        // Una rosa completa richiede 2 portieri e 2 giocatori per ogni altro ruolo
        // disponibili in panchina. Se non ci sono abbastanza giocatori, la formazione
        // resta comunque valida ma mostriamo chiaramente quanti posti sono coperti.
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
            sb.append("   Conferme Gazzetta/Sky: ").append(player.externalAgreement).append("/2\n");
            sb.append("   V4: ").append(String.format(Locale.US, "%.1f", player.score)).append(" punti");
            if (!player.opponent.isEmpty()) sb.append(" • vs ").append(player.opponent);
            sb.append("\n");
            sb.append("   Motivo: ").append(player.explanation).append("\n");
            if (player.recentForm > 0) sb.append("   Forma V4: ").append(String.format(Locale.US, "%.1f/100", player.recentForm)).append("\n");
            sb.append("   Avversario: ").append(player.opponent.isEmpty() ? "non disponibile" : player.opponent).append("\n\n");
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
            setStepState("RUOLI", 2, "Ruoli verificati");
            if (autoRunRequested && recalculatedBest != null && recalculatedBest.valid && credentialsManager.hasCredentials()) {
                autoRunRequested = false;
                openLegheWebViewDialog();
            }
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
        if (resultContainer == null) return;
        resultContainer.removeAllViews();

        if (best == null || !best.valid) {
            resultText = new TextView(this);
            resultText.setText(sb.toString() + "\n\nNON È STATO POSSIBILE CREARE UNA FORMAZIONE VALIDA.\n\nUno o più ruoli Classic non sono stati riconosciuti o mancano giocatori per completare un modulo.");
            resultText.setTextColor(Color.rgb(232, 236, 242));
            resultText.setTextSize(15);
            resultText.setLineSpacing(0, 1.08f);
            resultText.setPadding(dp(16), dp(16), dp(16), dp(16));
            resultText.setBackground(bg(Color.rgb(22, 27, 35), 18));
            resultContainer.addView(resultText);
            return;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(bg(Color.rgb(22, 25, 31), 20));

        TextView head = new TextView(this);
        head.setText("⚽  FANTAFORMATION");
        head.setTextColor(Color.WHITE);
        head.setTextSize(24);
        head.setTypeface(null, Typeface.BOLD);
        card.addView(head);

        TextView sub = new TextView(this);
        sub.setText("FORMAZIONE OTTIMALE  •  ALGORITMO V4");
        sub.setTextColor(Color.rgb(120, 220, 150));
        sub.setTextSize(13);
        sub.setPadding(0, dp(6), 0, dp(16));
        card.addView(sub);

        TextView module = new TextView(this);
        module.setText(best.module + "   •   " + String.format(Locale.US, "%.1f punti", best.score));
        module.setTextColor(Color.WHITE);
        module.setTextSize(20);
        module.setTypeface(null, Typeface.BOLD);
        module.setPadding(dp(16), dp(14), dp(16), dp(14));
        module.setBackground(bg(Color.rgb(32, 42, 50), 16));
        card.addView(module);

        card.addView(sectionTitle("⚽  TITOLARI"));

        LinearLayout pitch = new LinearLayout(this);
        pitch.setOrientation(LinearLayout.VERTICAL);
        pitch.setGravity(Gravity.CENTER_HORIZONTAL);
        pitch.setBackground(bg(Color.rgb(20, 65, 48), 20));
        pitch.setPadding(dp(10), dp(12), dp(10), dp(12));

        addFieldLine(pitch, "ATTACCO", best.attackers, "⚡");
        addFieldLine(pitch, "CENTROCAMPO", best.midfielders, "⚙️");
        addFieldLine(pitch, "DIFESA", best.defenders, "🛡️");
        addFieldLine(pitch, "PORTA", best.goalkeeper, "🧤");
        card.addView(pitch, new LinearLayout.LayoutParams(-1, -2));

        card.addView(sectionTitle("🧠  PERCHÉ QUESTI 11"));
        TextView reasons = new TextView(this);
        StringBuilder rb = new StringBuilder();
        for (Player p : best.goalkeeper) rb.append("🧤 ").append(p.excelName).append(" — ").append(p.explanation).append("\n");
        for (Player p : best.defenders) rb.append("🛡️ ").append(p.excelName).append(" — ").append(p.explanation).append("\n");
        for (Player p : best.midfielders) rb.append("⚙️ ").append(p.excelName).append(" — ").append(p.explanation).append("\n");
        for (Player p : best.attackers) rb.append("⚡ ").append(p.excelName).append(" — ").append(p.explanation).append("\n");
        reasons.setText(rb.toString());
        reasons.setTextColor(Color.rgb(190, 198, 208));
        reasons.setTextSize(12);
        reasons.setLineSpacing(0, 1.15f);
        reasons.setPadding(dp(8), 0, dp(8), dp(8));
        card.addView(reasons);

        card.addView(sectionTitle("🪑  PANCHINA"));
        addBenchRow(card, "🧤  PORTIERI", best.benchGoalkeeper);
        addBenchRow(card, "🛡️  DIFENSORI", best.benchDefenders);
        addBenchRow(card, "⚙️  CENTROCAMPISTI", best.benchMidfielders);
        addBenchRow(card, "⚡  ATTACCANTI", best.benchAttackers);

        int totalBench = best.benchGoalkeeper.size() + best.benchDefenders.size() +
                best.benchMidfielders.size() + best.benchAttackers.size();
        TextView info = new TextView(this);
        info.setText("11 titolari  •  " + totalBench + " panchinari\nMassimo 2 per ruolo  •  Totale " + (11 + totalBench) + " giocatori");
        info.setTextColor(Color.rgb(170, 178, 190));
        info.setTextSize(13);
        info.setPadding(dp(4), dp(14), dp(4), 0);
        card.addView(info);

        resultContainer.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(4, 24, 4, 10);
        return t;
    }

    private void addFieldLine(LinearLayout parent, String label, List<Player> players, String icon) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(Color.rgb(190, 235, 205));
        title.setTextSize(10);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(4));
        parent.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (Player p : players) {
            LinearLayout playerCard = new LinearLayout(this);
            playerCard.setOrientation(LinearLayout.VERTICAL);
            playerCard.setGravity(Gravity.CENTER);
            playerCard.setPadding(dp(5), dp(7), dp(5), dp(7));

            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.rgb(32, 85, 63));
            g.setCornerRadius(dp(14));
            playerCard.setBackground(g);

            TextView iconView = new TextView(this);
            iconView.setText(icon);
            iconView.setTextColor(Color.WHITE);
            iconView.setTextSize(14);
            iconView.setGravity(Gravity.CENTER);
            playerCard.addView(iconView, new LinearLayout.LayoutParams(-1, -2));

            TextView nameView = new TextView(this);
            nameView.setText(p.excelName);
            nameView.setTextColor(Color.WHITE);
            nameView.setTextSize(10.5f);
            nameView.setTypeface(null, Typeface.BOLD);
            nameView.setGravity(Gravity.CENTER);
            nameView.setMaxLines(2);
            nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nameView.setPadding(0, dp(2), 0, 0);
            playerCard.addView(nameView, new LinearLayout.LayoutParams(-1, -2));

            TextView scoreView = new TextView(this);
            scoreView.setText(String.format(Locale.US, "%.1f", p.score));
            scoreView.setTextColor(Color.rgb(190, 235, 205));
            scoreView.setTextSize(10);
            scoreView.setTypeface(null, Typeface.BOLD);
            scoreView.setGravity(Gravity.CENTER);
            scoreView.setPadding(0, dp(3), 0, 0);
            playerCard.addView(scoreView, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(playerCard, lp);
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private String shortReason(Player p) {
        if (p == null) return "";
        if (p.explanation == null || p.explanation.isEmpty()) return "";
        String s = p.explanation;
        return s.length() > 42 ? s.substring(0, 42) + "…" : s;
    }

    private void addBenchRow(LinearLayout parent, String icon, List<Player> players) {
        if (players == null || players.isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView role = new TextView(this);
        role.setText(icon);
        role.setTextSize(20);
        role.setTextColor(Color.WHITE);
        role.setGravity(android.view.Gravity.CENTER);
        row.addView(role, new LinearLayout.LayoutParams(44, 60));
        for (Player p : players) {
            TextView v = new TextView(this);
            v.setText(p.excelName + "\n" + p.probable + "%");
            v.setTextColor(Color.WHITE);
            v.setTextSize(12);
            v.setGravity(android.view.Gravity.CENTER_VERTICAL);
            v.setPadding(14, 6, 14, 6);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.rgb(30, 34, 41));
            g.setCornerRadius(16);
            v.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 60, 1f);
            lp.setMargins(4, 4, 4, 4);
            row.addView(v, lp);
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void appendSelectedPlayer(StringBuilder sb, Player p) {
        String name = p.officialName == null || p.officialName.isEmpty() ? p.excelName : p.officialName;
        sb.append("• ").append(name).append("  ").append(p.probable).append("%");
        if (p.externalAgreement > 0) {
            sb.append("  [").append(p.externalAgreement).append("/2]");
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
