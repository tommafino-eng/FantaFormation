package it.fantaformation;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    private static final String PREFS_APP = "fanta_formation_prefs";
    private static final String PREF_SAVED_FORMATION = "saved_formation";
    private static final String PREF_SELECTED_TEAM = "selected_team";
    private static final String PREF_AUTO_WEEKLY = "auto_weekly_enabled";
    private static final String PREF_AUTO_HOUR = "auto_weekly_hour";
    private static final String PREF_AUTO_MINUTE = "auto_weekly_minute";
    private static final int WEEKLY_ALARM_REQUEST = 4210;
    private static final String NOTIFICATION_CHANNEL_ID = "fantaformation_status";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 4310;
    private static final String ACTION_SCHEDULED_AUTO = "it.fantaformation.ACTION_SCHEDULED_AUTO";
    private static final String LEGA_HOME_URL = "https://leghe.fantacalcio.it/yoooo";

    private static final List<String> ALLOWED_FORMATIONS = Arrays.asList(
            "3-4-3", "3-5-2", "4-3-3", "4-4-2", "4-5-1", "5-3-2", "5-4-1", "5-2-3");

    // URL verificate su fantacalcio.it e sport.sky.it (26/09/2026).
    private static final String FANTACALCIO_QUOTE = "https://www.fantacalcio.it/quotazioni-fantacalcio";
    private static final String FANTACALCIO_PROBABILI = "https://www.fantacalcio.it/probabili-formazioni-serie-a";
    private static final String FANTACALCIO_STATS = "https://www.fantacalcio.it/statistiche-serie-a";
    private static final String FANTACALCIO_STATS_SUMMARY = FANTACALCIO_STATS;
    private static final String FANTACALCIO_NEWS = "https://www.fantacalcio.it/news";
    private static final String FANTACALCIO_CONSIGLI = "https://www.fantacalcio.it/consigli-fantacalcio";
    private static final String FANTACALCIO_CALENDAR = "https://www.fantacalcio.it/serie-a/calendario";
    private static final String FANTACALCIO_RECENT_BASE = "https://www.fantacalcio.it/voti-fantacalcio-serie-a";
    private static final String GAZZETTA_PROBABILI = "https://www.fantacalcio.it/probabili-formazioni-serie-a";
    private static final String SKY_PROBABILI = "https://sport.sky.it/calcio/serie-a/probabili-formazioni";

    private final ExecutorService executor = Executors.newFixedThreadPool(6);

    private PowerManager.WakeLock automationWakeLock;
    private LinearLayout statusContainer;
    private final Map<String, TextView> statusViews = new LinkedHashMap<>();
    private Spinner teamSpinner;
    private Map<String, List<String[]>> detectedTeams = new LinkedHashMap<>();
    private List<String> detectedTeamNames = new ArrayList<>();
    private String selectedTeamName = "";
    private boolean updatingTeamSpinner = false;

    private boolean autoFlowEnabled = false;
    private boolean autoRunRequested = false;
    private boolean loginAttempted = false;
    private boolean loginInProgress = false;
    private boolean loginAutoScheduled = false;
    private boolean lineupLoadTriggered = false;
    private int formationNavAttempts = 0;
    private boolean saveVerificationStarted = false;
    private boolean notificationPending = false;
    private boolean pendingAutoFill = false;
    private boolean autoInsertAfterFormationNav = false;

    private FormationResult currentBestResult;
    private ArrayList<Player> lastParsedPlayers;

    private void acquireAutomationWakeLock() {
        try {
            if (automationWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                automationWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FantaFormation:AutomationWakeLock");
            }
            if (!automationWakeLock.isHeld()) {
                automationWakeLock.acquire(10 * 60 * 1000L);
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseAutomationWakeLock() {
        try {
            if (automationWakeLock != null && automationWakeLock.isHeld()) {
                automationWakeLock.release();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Keeps the app process alive while the already-launched WebView automation
     * continues with the display off/locked. The WebView itself remains in the
     * Activity; the foreground service prevents the process from being reclaimed. */
    private void startAutomationKeeperService() {
        try {
            Intent i = new Intent(this, FantaAutomationService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Throwable e) {
            Log.d("FANTA_DEBUG", "Automation keeper non avviato: " + e.getMessage());
        }
    }

    private void stopAutomationKeeperService() {
        try {
            stopService(new Intent(this, FantaAutomationService.class));
        } catch (Throwable ignored) {
        }
    }

    /**
     * The automation must keep running when the user presses Home or switches app.
     * Do NOT call WebView.onPause(), pauseTimers() or destroy() here: the foreground
     * service keeps the process alive while the existing WebView continues its work.
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (autoFlowEnabled || autoRunRequested) {
            Log.d("FANTA_DEBUG", "APP IN BACKGROUND: automazione ancora attiva");
        }
    }

    private boolean isBackgroundAutomationIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        return "it.fantaformation.ACTION_WIDGET_AUTO".equals(action)
                || "it.fantaformation.ACTION_SCHEDULED_AUTO".equals(action);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // V15.1: allow the automation Activity to operate while the device is locked.
        // No TURN_SCREEN_ON flag is used, so we do not deliberately wake the display.
        try {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } catch (Throwable ignored) {
        }


        roleCache = new PlayerRoleCache(this);
        credentialsManager = new CredentialsManager(this);
        buildInterface();
        if (formazione != null && !formazione.isEmpty()) {
            setStepState("EXCEL", 2, "Rosa salvata disponibile: " + formazione.size() + " righe");
            if (selectedTeamName != null && !selectedTeamName.isEmpty()) showSelectedTeamSummary(); else showSavedFormationSummary();
        }
        if (credentialsManager.hasCredentials()) {
            setStepState("CREDENZIALI", 2, "Credenziali disponibili");
        }

        scheduleWeeklyAutomationIfEnabled();

        if (getIntent() != null && ("it.fantaformation.ACTION_WIDGET_AUTO".equals(getIntent().getAction()) || ACTION_SCHEDULED_AUTO.equals(getIntent().getAction()))) {
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (!isFinishing()) startFullAutomaticFlow();
            }, 450);
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

        TextView teamLabel = new TextView(this);
        teamLabel.setText("🏆  LA TUA SQUADRA");
        teamLabel.setTextSize(12);
        teamLabel.setTextColor(Color.rgb(155, 165, 180));
        teamLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        teamLabel.setPadding(dp(2), dp(12), dp(2), dp(6));
        root.addView(teamLabel);

        teamSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        teamSpinner.setVisibility(View.GONE);
        teamSpinner.setBackground(bg(Color.rgb(22, 27, 35), 14));
        teamSpinner.setPrompt("🏆 Seleziona la tua squadra");
        teamSpinner.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        teamSpinner.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(teamSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        Button automateButton = darkButton("⚡  Analizza e ottimizza", true);
        root.addView(automateButton);

        Button fullAutoButton = darkButton("🤖  Fai tutto automaticamente", true);
        root.addView(fullAutoButton);

        Button scheduleButton = darkButton("🕐  Automazione ogni venerdì", false);
        root.addView(scheduleButton);

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
        teamSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingTeamSpinner || position < 0 || position >= detectedTeamNames.size()) return;
                String name = detectedTeamNames.get(position);
                List<String[]> rows = detectedTeams.get(name);
                if (rows == null || rows.isEmpty()) return;
                selectedTeamName = name;
                formazione = new ArrayList<>(rows);
                persistSelectedTeamAndFormation();
                showSelectedTeamSummary();
                setStepState("EXCEL", 2, "Squadra selezionata: " + selectedTeamName + " (" + countRealPlayers(formazione) + " giocatori)");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        automateButton.setOnClickListener(v -> {
            if (formazione == null || formazione.isEmpty()) {
                setStepState("EXCEL", 0, "Carica prima il file Excel");
                Toast.makeText(MainActivity.this, "Prima carica la formazione Excel", Toast.LENGTH_LONG).show();
                return;
            }
            analyzeFormation();
        });
        fullAutoButton.setOnClickListener(v -> startFullAutomaticFlow());
        scheduleButton.setOnClickListener(v -> showWeeklyAutomationDialog(scheduleButton));
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
        Log.d("FANTA_DEBUG", "Ricerca Inserisci formazione - tentativo " + attempt + "/12");

        String js = "(function(){" +
                "function norm(s){return String(s||'').toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').replace(/\\s+/g,' ').trim();}" +
                "function vis(e){if(!e||!e.getBoundingClientRect)return false;var r=e.getBoundingClientRect(),c=getComputedStyle(e);return r.width>0&&r.height>0&&c.display!=='none'&&c.visibility!=='hidden';}" +
                "function en(e){return !!e&&!e.disabled&&e.getAttribute('aria-disabled')!=='true';}" +
                "function txt(e){return norm(e.innerText||e.textContent||e.getAttribute('aria-label')||e.getAttribute('title')||e.value);}" +
                "function abs(h){try{return new URL(h,location.href).href;}catch(x){return '';}}" +
                "function isFormHref(h){var l=abs(h||'').toLowerCase();return l.indexOf('/lineup/')>=0||l.indexOf('/inserisci-formazione')>=0||l.indexOf('/formazione')>=0;}" +
                "function isWanted(e){var t=txt(e);return t==='inserisci formazione'||t==='schiera formazione'||t.indexOf('inserisci formazione')>=0||t.indexOf('schiera formazione')>=0;}" +
                "function buttonFor(e){if(!e)return null;var b=e.closest&&e.closest('button');if(b&&en(b))return b;var r=e.closest&&e.closest('[role=button]');if(r&&en(r))return r;var a=e.closest&&e.closest('a[href]');if(a&&en(a))return a;return null;}" +
                "function findButton(){" +
                " var bs=document.querySelectorAll('button,[role=button],a[href]');" +
                " for(var i=0;i<bs.length;i++){var b=bs[i];if(vis(b)&&en(b)&&isWanted(b))return b;}" +
                " var els=document.querySelectorAll('span,div,p,li,strong,label');" +
                " for(var j=0;j<els.length;j++){var e=els[j];if(!vis(e)||!isWanted(e))continue;var b=buttonFor(e);if(b)return b;}" +
                " return null;}" +
                "function clickOnly(b){if(!b)return false;try{b.click();return true;}catch(x){}try{HTMLElement.prototype.click.call(b);return true;}catch(y){}return false;}" +
                "function scrollParent(el){" +
                " var p=el;" +
                " while(p&&p!==document.body&&p!==document.documentElement){var cs=getComputedStyle(p);if((cs.overflowY==='auto'||cs.overflowY==='scroll'||cs.overflowY==='overlay')&&p.scrollHeight>p.clientHeight+5)return p;p=p.parentElement;}" +
                " return document.scrollingElement||document.documentElement;}" +
                "function doSmallScroll(b){" +
                " var target=scrollParent(b||document.body);" +
                " var before=target===document.scrollingElement?(window.scrollY||0):target.scrollTop;" +
                " if(b){var r=b.getBoundingClientRect(),vh=window.innerHeight||document.documentElement.clientHeight;if(r.top>=0&&r.bottom<=vh)return 'visible';" +
                "   if(r.top>vh){target.scrollTop+=110;return 'down';}" +
                "   if(r.bottom<0){target.scrollTop-=110;return 'up';}}" +
                " target.scrollTop+=110;" +
                " var after=target===document.scrollingElement?(window.scrollY||0):target.scrollTop;" +
                " if(after===before){window.scrollBy(0,110);setTimeout(function(){window.scrollBy(0,110);},160);return 'window_down';}" +
                " return 'container_down';" +
                "}" +
                "function findHref(){var links=document.querySelectorAll('a[href]');for(var i=0;i<links.length;i++){var a=links[i];if(!en(a))continue;var t=txt(a),h=a.getAttribute('href')||'';if((t.indexOf('inserisci formazione')>=0||t.indexOf('schiera formazione')>=0)&&isFormHref(h))return abs(h);}return ''; }" +
                "var href=findHref();" +
                "if(href){AndroidBridge.report('NAV: link formazione diretto trovato');AndroidBridge.openFormation(href);return 'direct:'+href;}" +
                "var b=findButton();" +
                "if(b){var r=b.getBoundingClientRect(),vh=window.innerHeight||document.documentElement.clientHeight;" +
                " if(r.top>=0&&r.bottom<=vh){AndroidBridge.report('NAV: pulsante trovato e visibile, click');return clickOnly(b)?'clicked':'click_error';}" +
                " var sr=doSmallScroll(b);AndroidBridge.report('NAV: scroll piccolo '+sr+' per trovare Inserisci formazione');return sr;}" +
                " var root=document.scrollingElement||document.documentElement;var before=root.scrollTop||window.scrollY||0;" +
                " root.scrollTop+=110;" +
                " if((root.scrollTop||window.scrollY||0)===before){window.scrollBy(0,110);setTimeout(function(){window.scrollBy(0,110);},160);}" +
                " AndroidBridge.report('NAV: pulsante non ancora nel DOM/viewport, piccolo scroll');return 'search_scroll';})()";

        webView.evaluateJavascript(js, value -> {
            String result = cleanJsResult(value, "false");
            Log.d("FANTA_DEBUG", "Risultato navigazione formazione V10: " + result);
            if (result.startsWith("direct:")) {
                autoInsertAfterFormationNav = true;
                setStepState("LEGA", 1, "Apro Inserisci formazione...");
                scheduleAutoInsertWhenReady(webView);
            } else if ("clicked".equals(result)) {
                autoInsertAfterFormationNav = true;
                setStepState("LEGA", 1, "Inserisci formazione selezionato...");
                webView.postDelayed(() -> scheduleAutoInsertWhenReady(webView), 900);
            } else if (formationNavAttempts < 12) {
                webView.postDelayed(() -> openSchieraFormazioneFromDashboard(webView), 550);
            } else {
                setStepState("LEGA", 0, "Voce Inserisci formazione non trovata");
                Toast.makeText(MainActivity.this, "Non trovo 'Inserisci formazione' nella pagina.", Toast.LENGTH_LONG).show();
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
                "async function waitFor(check,timeout,interval){const started=Date.now();while(Date.now()-started<timeout){const value=check();if(value)return value;await sleep(interval);}return null;}" +
                "async function waitDrawer(before){return await waitFor(()=>{const r=pickerRoot();if(r&&r!==before)return r;if(r&&drawerText(r).includes('rosa'))return r;return null;},8000,100);}" +
                "async function waitDrawerClose(root){return !!(await waitFor(()=>{if(!root||!visible(root)||!drawerText(root).includes('rosa'))return true;return null;},6000,100));}" +
                "async function choosePlayer(root,name){for(let pass=0;pass<3;pass++){pickerSearch(root,name);const clicked=await waitFor(()=>{const c=candidatePlayer(root,name);return c&&clickPlayer(c);},4000,100);if(clicked){await sleep(250);return true;}}return false;}" +
                "function verify(slot,name){return nameMatches(currentName(slot),norm(name));}" +
                "function occupiedCount(){return slots().filter(s=>!!currentName(s)).length;}" +
                "function clearButton(){const scope=document.querySelector('view-lineup')||document;const els=[...scope.querySelectorAll('button,[role=button],a,[title],[aria-label]')].filter(e=>visible(e)&&enabled(e)&&!norm(e.innerText||e.textContent||'').includes('salva formazione'));let best=null,bestScore=-1;for(const e of els){const t=norm((e.innerText||e.textContent||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.getAttribute('data-testid')||'')+' '+(e.getAttribute('data-test')||''));const html=norm(e.outerHTML||'');let sc=0;if(t.includes('svuota'))sc+=100;if(t.includes('cestino'))sc+=100;if(t.includes('trash'))sc+=95;if(t.includes('clear'))sc+=85;if(t.includes('reset'))sc+=80;if(t.includes('azzera'))sc+=80;if(t.includes('elimina formazione'))sc+=120;if(html.includes('trash')||html.includes('delete')||html.includes('remove'))sc+=45;if(e.querySelector('svg'))sc+=5;if(sc>bestScore){bestScore=sc;best=e;}}return bestScore>=45?best:null;}" +
                "async function confirmClearIfNeeded(){for(let pass=0;pass<8;pass++){const roots=[...document.querySelectorAll('[role=dialog],nz-modal,.ant-modal,.ant-popconfirm,.cdk-overlay-pane')].filter(visible);if(!roots.length){await sleep(100);continue;}const buttons=[...document.querySelectorAll('button,[role=button],input[type=button],input[type=submit]')].filter(e=>visible(e)&&enabled(e));const b=buttons.find(e=>{const t=norm(e.innerText||e.textContent||e.value||e.getAttribute('aria-label')||'');return t==='conferma'||t==='si'||t==='sì'||t==='ok'||t==='svuota'||t==='azzera'||t==='elimina'||t==='continua'||t==='conferma svuotamento';});if(b){fire(b);return true;}await sleep(100);}return false;}" +
                "async function clearFormationBeforeModule(){const before=occupiedCount();if(before===0){AndroidBridge.report('SVUOTA: formazione già vuota');return true;}AndroidBridge.report('SVUOTA: trovati '+before+' giocatori, cerco il tasto cestino...');const b=clearButton();if(!b){AndroidBridge.report('SVUOTA FALLITO: tasto cestino/svuota non trovato');return false;}if(!fire(b)){AndroidBridge.report('SVUOTA FALLITO: impossibile premere il cestino');return false;}await confirmClearIfNeeded();const cleared=await waitFor(()=>occupiedCount()===0?true:null,10000,100);if(cleared){AndroidBridge.report('FORMAZIONE SVUOTATA: pronta per cambio modulo');return true;}AndroidBridge.report('SVUOTA FALLITO: dopo il cestino restano '+occupiedCount()+' giocatori');return false;}" +
                "function findSave(){const els=[...document.querySelectorAll('button,[role=button],input[type=submit]')].filter(e=>visible(e)&&enabled(e));return els.find(e=>{const t=norm(e.innerText||e.textContent||e.value||e.getAttribute('aria-label')||'');return t==='salva formazione'||t.includes('salva formazione');})||null;}" +
                "function saveFeedback(){const roots=[...document.querySelectorAll('.ant-message,.ant-message-notice,.ant-notification,.ant-notification-notice,[role=alert],.ant-alert')].filter(visible);for(const r of roots){const t=norm(r.innerText||r.textContent||'');if(/salvat|success|complet|aggiornat|inserit/.test(t))return t;}return '';}" +
                "function allFilled(){return players.every(item=>{const s=slots().find(x=>roleOf(x)===item.role&&reserveOf(x)===(item.type==='B')&&verify(x,item.name));return !!s;});}" +
                "const cleared=await clearFormationBeforeModule();" +
                "if(!cleared){AndroidBridge.report('INSERISCI FALLITO: formazione non svuotata, cambio modulo annullato.');return;}" +
                "const moduleOk=await selectModule(moduleName);" +
                "if(!moduleOk){AndroidBridge.report('INSERISCI FALLITO: modulo '+moduleName+' non selezionato.');return;}" +
                "await sleep(300);" +
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
                "await waitDrawerClose(root);" +
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
        stopAutomationKeeperService();
        releaseAutomationWakeLock();
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
                stopAutomationKeeperService();
                releaseAutomationWakeLock();
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

    private void showWeeklyAutomationDialog(Button button) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(PREF_AUTO_WEEKLY, false);
        int hour = prefs.getInt(PREF_AUTO_HOUR, 18);
        int minute = prefs.getInt(PREF_AUTO_MINUTE, 30);

        TimePickerDialog picker = new TimePickerDialog(this, (view, selectedHour, selectedMinute) -> {
            prefs.edit().putBoolean(PREF_AUTO_WEEKLY, true)
                    .putInt(PREF_AUTO_HOUR, selectedHour)
                    .putInt(PREF_AUTO_MINUTE, selectedMinute).apply();
            scheduleWeeklyAutomationIfEnabled();
            updateScheduleButton(button);
            Toast.makeText(this, String.format(Locale.ROOT, "Automazione ogni venerdì alle %02d:%02d attivata", selectedHour, selectedMinute), Toast.LENGTH_LONG).show();
        }, hour, minute, true);
        picker.setTitle("Ogni venerdì alle...");
        picker.setButton(AlertDialog.BUTTON_NEGATIVE, enabled ? "Disattiva" : "Annulla", (d, w) -> {
            if (enabled) {
                prefs.edit().putBoolean(PREF_AUTO_WEEKLY, false).apply();
                cancelWeeklyAutomation();
                updateScheduleButton(button);
                Toast.makeText(this, "Automazione settimanale disattivata", Toast.LENGTH_SHORT).show();
            }
        });
        picker.setButton(AlertDialog.BUTTON_NEUTRAL, "Annulla", (d, w) -> d.dismiss());
        picker.show();
    }

    private void updateScheduleButton(Button button) {
        if (button == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        if (prefs.getBoolean(PREF_AUTO_WEEKLY, false)) {
            int h = prefs.getInt(PREF_AUTO_HOUR, 18), m = prefs.getInt(PREF_AUTO_MINUTE, 30);
            button.setText(String.format(Locale.ROOT, "🕐  Ogni venerdì alle %02d:%02d  ✓", h, m));
        } else {
            button.setText("🕐  Automazione ogni venerdì");
        }
    }

    private void scheduleWeeklyAutomationIfEnabled() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_AUTO_WEEKLY, false)) return;
        int hour = prefs.getInt(PREF_AUTO_HOUR, 18);
        int minute = prefs.getInt(PREF_AUTO_MINUTE, 30);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        int day = next.get(Calendar.DAY_OF_WEEK);
        int daysUntilFriday = (Calendar.FRIDAY - day + 7) % 7;
        if (daysUntilFriday == 0 && next.getTimeInMillis() <= System.currentTimeMillis()) daysUntilFriday = 7;
        next.add(Calendar.DAY_OF_YEAR, daysUntilFriday);

        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(this, FantaFormationScheduleReceiver.class);
        intent.setAction(ACTION_SCHEDULED_AUTO);
        PendingIntent pi = PendingIntent.getBroadcast(this, WEEKLY_ALARM_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        am.cancel(pi);
        if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
        else am.set(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
        Log.d("FANTA_DEBUG", "Automazione programmata per venerdì " + next.getTime());
    }

    private void cancelWeeklyAutomation() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(this, FantaFormationScheduleReceiver.class);
        intent.setAction(ACTION_SCHEDULED_AUTO);
        PendingIntent pi = PendingIntent.getBroadcast(this, WEEKLY_ALARM_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        am.cancel(pi);
    }

    private void startFullAutomaticFlow() {
        startAutomationKeeperService();
        acquireAutomationWakeLock();
        autoRunRequested = true;
        autoFlowEnabled = true;
        pendingAutoFill = true;
        loginAttempted = false;
        loginInProgress = false;
        loginAutoScheduled = false;
        lineupLoadTriggered = false;
        formationNavAttempts = 0;
        saveVerificationStarted = false;
        notificationPending = false;

        if (formazione == null || formazione.isEmpty()) {
            setStepState("EXCEL", 1, "Nessuna rosa salvata: scegli il file Excel");
            Toast.makeText(this, "È la prima volta: scegli il file Excel", Toast.LENGTH_LONG).show();
            chooseExcel();
            return;
        }

        if (!credentialsManager.hasCredentials()) {
            promptSaveCredentialsAndContinueAuto();
            return;
        }

        setStepState("EXCEL", 2, "Rosa salvata caricata automaticamente");
        setStepState("CREDENZIALI", 2, "Credenziali disponibili");
        Toast.makeText(this, "Avvio automatico completo...", Toast.LENGTH_SHORT).show();
        analyzeFormation();
    }

    private void promptSaveCredentialsAndContinueAuto() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10);

        final EditText userInput = new EditText(this);
        userInput.setHint("Email / Username Fantacalcio");
        layout.addView(userInput);

        final EditText passInput = new EditText(this);
        passInput.setHint("Password");
        passInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Prima configura le credenziali")
                .setMessage("Le credenziali verranno salvate per le prossime volte.")
                .setView(layout)
                .setPositiveButton("Salva e continua", null)
                .setNegativeButton("Annulla", (d, w) -> autoRunRequested = false)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String user = userInput.getText().toString().trim();
            String pass = passInput.getText().toString();
            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Inserisci username e password", Toast.LENGTH_SHORT).show();
                return;
            }
            credentialsManager.saveCredentials(user, pass);
            setStepState("CREDENZIALI", 2, "Credenziali salvate");
            dialog.dismiss();
            Toast.makeText(this, "Credenziali salvate. Continuo automaticamente...", Toast.LENGTH_SHORT).show();
            analyzeFormation();
        }));
        dialog.show();
    }

    private void saveFormation(List<String[]> rows) {
        try {
            JSONArray outer = new JSONArray();
            if (rows != null) {
                for (String[] row : rows) {
                    JSONArray item = new JSONArray();
                    if (row != null) {
                        for (String value : row) item.put(value == null ? "" : value);
                    }
                    outer.put(item);
                }
            }
            getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_SAVED_FORMATION, outer.toString())
                    .apply();
            Log.d("FANTA_DEBUG", "EXCEL salvato localmente: " + (rows == null ? 0 : rows.size()) + " righe");
        } catch (Exception e) {
            Log.e("FANTA_DEBUG", "Errore salvataggio Excel locale", e);
        }
    }

    private void loadSavedFormation() {
        try {
            String raw = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                    .getString(PREF_SAVED_FORMATION, "");
            if (raw == null || raw.trim().isEmpty()) return;

            JSONArray outer = new JSONArray(raw);
            ArrayList<String[]> rows = new ArrayList<>();
            for (int i = 0; i < outer.length(); i++) {
                JSONArray item = outer.optJSONArray(i);
                if (item == null) continue;
                String[] row = new String[item.length()];
                for (int j = 0; j < item.length(); j++) row[j] = item.optString(j, "");
                rows.add(row);
            }
            if (!rows.isEmpty()) formazione = rows;
        } catch (Exception e) {
            Log.e("FANTA_DEBUG", "Errore caricamento Excel salvato", e);
        }
    }

    private void showSavedFormationSummary() {
        if (resultText == null || formazione == null || formazione.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("ROSA SALVATA\n==================\n\n");
        int count = 0;
        for (String[] row : formazione) {
            if (row == null || row.length < 2) continue;
            String player = row[1] == null ? "" : row[1].trim();
            if (player.isEmpty()) continue;
            sb.append(player).append("\n");
            count++;
        }
        sb.append("\nGiocatori salvati: ").append(count);
        sb.append("\n\nPuoi premere 🤖 Fai tutto automaticamente.");
        resultText.setText(sb.toString());
    }

    private void handleExcelLoaded(List<String[]> result) {
        if (result == null || result.isEmpty()) {
            resultText.setText("File Excel vuoto o non valido.");
            hideTeamSpinner();
            return;
        }

        detectedTeams = detectTeams(result);
        detectedTeamNames = new ArrayList<>(detectedTeams.keySet());
        setupTeamSpinner();

        String savedTeam = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .getString(PREF_SELECTED_TEAM, "").trim();

        if (!savedTeam.isEmpty()) {
            String matchedSavedTeam = null;
            for (String teamName : detectedTeamNames) {
                if (normalize(teamName).equals(normalize(savedTeam))) {
                    matchedSavedTeam = teamName;
                    break;
                }
            }
            if (matchedSavedTeam != null) {
                selectTeamInSpinner(matchedSavedTeam);
                return;
            }
        }

        if (detectedTeams.size() >= 2) {
            // Struttura a squadre riconosciuta: attendiamo la scelta dell'utente,
            // non carichiamo mai le righe grezze (colonna "costo" al posto del giocatore).
            selectedTeamName = "";
            setStepState("EXCEL", 1, "Seleziona la tua squadra dal menu");
            resultText.setText("Ho trovato " + detectedTeamNames.size() + " squadre.\n\nSeleziona la tua squadra dal menu a tendina qui sopra.");
            Toast.makeText(this, "Seleziona la tua squadra dal menu a tendina", Toast.LENGTH_LONG).show();
            return;
        }

        // Nessuna colonna squadra riconosciuta: manteniamo il comportamento di fallback.
        selectedTeamName = "";
        formazione = new ArrayList<>(result);
        saveFormation(formazione);
        hideTeamSpinner();
        showSavedFormationSummary();
        setStepState("EXCEL", 2, "Rosa caricata: " + countRealPlayers(formazione) + " giocatori");
        Toast.makeText(this, "Excel caricato. Non ho trovato una colonna squadra riconoscibile.", Toast.LENGTH_LONG).show();
    }

    private void setupTeamSpinner() {
        if (teamSpinner == null) return;
        if (detectedTeamNames == null || detectedTeamNames.size() <= 1) {
            teamSpinner.setVisibility(detectedTeamNames != null && detectedTeamNames.size() == 1 ? View.VISIBLE : View.GONE);
            if (detectedTeamNames != null && detectedTeamNames.size() == 1) {
                ArrayAdapter<String> adapter = buildTeamSpinnerAdapter();
                teamSpinner.setAdapter(adapter);
            }
            return;
        }
        teamSpinner.setVisibility(View.VISIBLE);
        teamSpinner.setAdapter(buildTeamSpinnerAdapter());
        try {
            teamSpinner.setPopupBackgroundDrawable(bg(Color.rgb(22, 27, 35), 12));
            teamSpinner.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        } catch (Throwable ignored) {
        }
    }

    private ArrayAdapter<String> buildTeamSpinnerAdapter() {
        List<String> labels = detectedTeamNames != null ? detectedTeamNames : new ArrayList<>();
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(Color.WHITE);
                v.setTextSize(15);
                v.setGravity(Gravity.CENTER_VERTICAL);
                v.setPadding(dp(14), 0, dp(10), 0);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(Color.WHITE);
                v.setTextSize(15);
                v.setGravity(Gravity.CENTER_VERTICAL);
                v.setPadding(dp(16), dp(14), dp(16), dp(14));
                v.setBackgroundColor(Color.rgb(22, 27, 35));
                return v;
            }
        };
    }

    private void selectTeamInSpinner(String teamName) {
        if (teamName == null || teamName.trim().isEmpty() || detectedTeams == null) return;
        List<String[]> rows = detectedTeams.get(teamName);
        if (rows == null || rows.isEmpty()) return;
        selectedTeamName = teamName;
        formazione = new ArrayList<>(rows);
        persistSelectedTeamAndFormation();
        if (teamSpinner != null && detectedTeamNames != null) {
            int index = detectedTeamNames.indexOf(teamName);
            if (index >= 0) {
                updatingTeamSpinner = true;
                teamSpinner.setSelection(index, false);
                updatingTeamSpinner = false;
            }
            teamSpinner.setVisibility(View.VISIBLE);
        }
        showSelectedTeamSummary();
        setStepState("EXCEL", 2, "Squadra salvata: " + selectedTeamName + " (" + countRealPlayers(formazione) + " giocatori)");
        Toast.makeText(this, "Squadra " + selectedTeamName + " selezionata", Toast.LENGTH_SHORT).show();
    }

    private void hideTeamSpinner() {
        if (teamSpinner != null) teamSpinner.setVisibility(View.GONE);
        detectedTeamNames.clear();
        detectedTeams.clear();
    }

    private LinkedHashMap<String, List<String[]>> detectTeams(List<String[]> rows) {
        LinkedHashMap<String, List<String[]>> teams = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) return teams;

        /*
         * FORMATO REALE DEL FILE:
         *
         * Riga 1:
         *   Squadra | costo | vuota | Squadra | costo | vuota | ...
         *
         * Righe successive:
         *   giocatore | prezzo | vuota | giocatore | prezzo | vuota | ...
         *
         * Alcune rose hanno 25 giocatori, altre terminano con "totale"
         * dopo 24 giocatori. NON assumiamo quindi che ci siano sempre 25.
         *
         * IMPORTANTE:
         * XlsxReader può restituire o meno le celle vuote. Per questo NON
         * usiamo più "col += 3". Cerchiamo invece tutte le colonne la cui
         * cella di intestazione è seguita da "costo". Questo rende il parser
         * indipendente dalle colonne vuote presenti nel file.
         */
        String[] header = rows.get(0);
        if (header == null || header.length == 0) return teams;

        ArrayList<Integer> teamColumns = new ArrayList<>();

        for (int col = 0; col < header.length; col++) {
            String h = cleanName(header[col]);
            if (h.isEmpty()) continue;

            String hn = normalize(h);
            if ("costo".equals(hn) || "totale".equals(hn) || isLikelyHeader(h)) continue;

            boolean nextIsCosto = false;
            if (col + 1 < header.length) {
                nextIsCosto = "costo".equals(normalize(cleanName(header[col + 1])));
            }

            /*
             * Caso normale: [Squadra, costo, vuota].
             * Caso XlsxReader senza celle vuote: [Squadra, costo, Squadra, costo].
             * In entrambi i casi questa condizione identifica l'inizio della rosa.
             */
            if (nextIsCosto) {
                teamColumns.add(col);
            }
        }

        /*
         * Fallback molto conservativo nel caso in cui il lettore Excel abbia
         * perso anche la cella "costo": riconosciamo comunque nomi distinti
         * sulla prima riga solo se sotto esiste una colonna con molti giocatori.
         */
        if (teamColumns.isEmpty()) {
            for (int col = 0; col < header.length; col++) {
                String teamName = cleanName(header[col]);
                if (teamName.isEmpty() || "costo".equals(normalize(teamName))) continue;

                int validPlayers = 0;
                for (int r = 1; r < rows.size(); r++) {
                    String[] source = rows.get(r);
                    if (source == null || source.length <= col) continue;
                    String player = cleanName(source[col]);
                    String pn = normalize(player);
                    if (player.isEmpty()) continue;
                    if ("totale".equals(pn)) break;
                    if (!isLikelyHeader(player)) validPlayers++;
                }

                if (validPlayers >= 20) teamColumns.add(col);
            }
        }

        /*
         * Elimina eventuali duplicati e ordina per posizione, così il menu
         * rispetta esattamente l'ordine delle squadre nell'Excel.
         */
        LinkedHashMap<Integer, Boolean> unique = new LinkedHashMap<>();
        for (Integer col : teamColumns) {
            if (col != null && col >= 0 && col < header.length) unique.put(col, true);
        }
        teamColumns = new ArrayList<>(unique.keySet());
        Collections.sort(teamColumns);

        for (Integer colObj : teamColumns) {
            int col = colObj;
            String teamName = cleanName(header[col]);
            if (teamName.isEmpty()) continue;

            ArrayList<String[]> players = new ArrayList<>();

            for (int r = 1; r < rows.size(); r++) {
                String[] source = rows.get(r);
                if (source == null || source.length <= col) continue;

                String player = cleanName(source[col]);
                if (player.isEmpty()) continue;

                String pn = normalize(player);

                // La rosa termina esattamente alla riga "totale".
                if ("totale".equals(pn)) break;

                if (isLikelyHeader(player)) continue;

                String cost = "";
                if (col + 1 < source.length) {
                    cost = cleanName(source[col + 1]);
                }

                int index = players.size();

                // Struttura reale del listone: 3 P + 8 D + 8 C + 6 A.
                String role;
                if (index < 3) role = "P";
                else if (index < 11) role = "D";
                else if (index < 19) role = "C";
                else role = "A";

                players.add(new String[]{teamName, player, cost, role});

                // Una rosa non può avere più di 25 giocatori nel formato supportato.
                if (players.size() >= 25) break;
            }

            /*
             * Il file fornito contiene sia rose da 25 sia rose da 24 giocatori.
             * Accettiamo entrambe, ma scartiamo colonne casuali che non sembrano
             * davvero una rosa.
             */
            if (players.size() >= 24 && players.size() <= 25) {
                String uniqueName = teamName;
                int suffix = 2;
                while (teams.containsKey(uniqueName)) {
                    uniqueName = teamName + " (" + suffix++ + ")";
                }
                teams.put(uniqueName, players);
                Log.d("FANTA_DEBUG",
                        "EXCEL TEAM: " + uniqueName +
                        " -> " + players.size() +
                        " giocatori, colonna " + col);
            }
        }

        /*
         * Se abbiamo trovato almeno 2 rose, consideriamo questa la struttura
         * ufficiale del file e NON usiamo il vecchio fallback per colonne.
         */
        if (teams.size() >= 2) {
            Log.d("FANTA_DEBUG", "EXCEL TEAM TOTAL = " + teams.size());
            return teams;
        }

        /*
         * Fallback per eventuali Excel futuri con una vera colonna "squadra".
         */
        LinkedHashMap<String, List<String[]>> fallback = new LinkedHashMap<>();
        int maxCols = 0;
        for (String[] row : rows) {
            if (row != null) maxCols = Math.max(maxCols, row.length);
        }

        int bestScore = Integer.MIN_VALUE;

        for (int col = 0; col < maxCols; col++) {
            LinkedHashMap<String, List<String[]>> groups = new LinkedHashMap<>();

            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                if (row == null || row.length <= col) continue;

                String team = cleanName(row[col]);
                if (team.isEmpty()) continue;

                String player = row.length > 1 ? cleanName(row[1]) : "";
                if (player.isEmpty() || "totale".equals(normalize(player)) || isLikelyHeader(player)) continue;

                groups.computeIfAbsent(team, k -> new ArrayList<>()).add(row);
            }

            if (groups.size() < 2 || groups.size() > 30) continue;

            int score = 0;
            int goodGroups = 0;

            for (List<String[]> group : groups.values()) {
                int n = countRealPlayers(group);
                if (n == 25) {
                    score += 250;
                    goodGroups++;
                } else if (n == 24) {
                    score += 230;
                    goodGroups++;
                } else if (n >= 20 && n <= 30) {
                    score += 50;
                    goodGroups++;
                }
                score -= Math.abs(n - 25);
            }

            if (goodGroups >= Math.max(1, groups.size() / 2) && score > bestScore) {
                bestScore = score;
                fallback = groups;
            }
        }

        return fallback;
    }

    private boolean isLikelyHeader(String value) {
        String n = normalize(value);
        return n.equals("giocatore") || n.equals("giocatori") || n.equals("nome") ||
                n.equals("player") || n.equals("squadra") || n.equals("team");
    }

    private void showTeamSelectionDialog(LinkedHashMap<String, List<String[]>> teams) {
        ArrayList<String> names = new ArrayList<>(teams.keySet());
        String[] items = names.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🏆 Seleziona la tua squadra")
                .setMessage("Ho trovato " + names.size() + " squadre. Scegli quella composta dai tuoi 25 giocatori.")
                .setSingleChoiceItems(items, -1, null)
                .setNegativeButton("Annulla", null)
                .create();

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Salva squadra", (d, which) -> {});
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int selected = dialog.getListView().getCheckedItemPosition();
                if (selected < 0 || selected >= names.size()) {
                    Toast.makeText(this, "Seleziona una squadra", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedTeamName = names.get(selected);
                List<String[]> selectedRows = teams.get(selectedTeamName);
                if (selectedRows == null || selectedRows.isEmpty()) return;

                formazione = new ArrayList<>(selectedRows);
                persistSelectedTeamAndFormation();
                showSelectedTeamSummary();
                setStepState("EXCEL", 2, "Squadra salvata: " + selectedTeamName + " (" + formazione.size() + " giocatori)");
                Toast.makeText(this, "Squadra " + selectedTeamName + " salvata. Non dovrai riselezionarla.", Toast.LENGTH_LONG).show();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void persistSelectedTeamAndFormation() {
        getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                .putString(PREF_SELECTED_TEAM, selectedTeamName == null ? "" : selectedTeamName)
                .apply();
        saveFormation(formazione);
    }

    private void showSelectedTeamSummary() {
        if (resultText == null || formazione == null || formazione.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("🏆 SQUADRA SELEZIONATA\n========================\n\n");
        if (selectedTeamName != null && !selectedTeamName.isEmpty()) {
            sb.append(selectedTeamName).append("\n");
        }
        sb.append("\nGiocatori salvati: ").append(countRealPlayers(formazione)).append("\n\n");
        sb.append("La squadra è stata salvata sul dispositivo.\n");
        sb.append("Dalle prossime volte verrà caricata automaticamente.\n\n");
        sb.append("Puoi premere 🤖 Fai tutto automaticamente.");
        resultText.setText(sb.toString());
    }

    private int countRealPlayers(List<String[]> rows) {
        int count = 0;
        if (rows == null) return 0;
        for (String[] row : rows) {
            if (row != null && row.length > 1 && !cleanName(row[1]).isEmpty() && !isLikelyHeader(row[1])) count++;
        }
        return count;
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

                    handleExcelLoaded(result);
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
        setStepState("ANALISI", 1, "Ricerca dati online e ottimizzazione in corso...");
        resultText.setText("ANALISI AVANZATA in corso...\n\n" +
                "🌐 Raccolta dati online\n" +
                "• statistiche ufficiali Fantacalcio\n" +
                "• probabili formazioni + consenso fonti\n" +
                "• ultime 5 / forma recente\n" +
                "• calendario, avversario e casa/trasferta\n" +
                "• news, infortuni, squalifiche e turnover\n" +
                "• bonus, rigori, assist e continuità\n" +
                "• consigli editoriali Fantacalcio: schierare / scommesse / evitare\n" +
                "• priorità a gol, assist, rigoristi e giocatori offensivi\n\n" +
                "Poi confronto i moduli possibili e scelgo gli 11 con rendimento atteso più alto.");

        executor.execute(() -> {
            try {
                Map<String, String> sources = new LinkedHashMap<>();
                sources.put("stats", FANTACALCIO_STATS);
                sources.put("statsSummary", FANTACALCIO_STATS_SUMMARY);
                sources.put("quotes", FANTACALCIO_QUOTE);
                sources.put("probabili", FANTACALCIO_PROBABILI);
                sources.put("gazzetta", GAZZETTA_PROBABILI);
                sources.put("sky", SKY_PROBABILI);
                sources.put("news", FANTACALCIO_NEWS);
                sources.put("consigli", FANTACALCIO_CONSIGLI);
                sources.put("calendar", FANTACALCIO_CALENDAR);
                sources.put("recent", FANTACALCIO_RECENT_BASE);

                Map<String, Future<String>> futures = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : sources.entrySet()) {
                    futures.put(entry.getKey(), executor.submit(() -> downloadSafe(entry.getValue())));
                }
                Map<String, String> html = new LinkedHashMap<>();
                int onlineOk = 0;
                for (Map.Entry<String, Future<String>> entry : futures.entrySet()) {
                    String value = entry.getValue().get(25, TimeUnit.SECONDS);
                    html.put(entry.getKey(), value == null ? "" : value);
                    if (value != null && !value.isEmpty()) onlineOk++;
                }

                Map<String, OfficialPlayer> officialPlayers = parseOfficialPlayers(html.get("quotes"));
                Map<String, ProbabilityInfo> probabilities = parseFantacalcioProbabili(html.get("probabili"));
                Map<String, Integer> gazzetta = parseExternalSource(html.get("gazzetta"));
                Map<String, Integer> sky = parseExternalSource(html.get("sky"));
                Map<String, AdvancedStats> statistics = parseAdvancedStats(html.get("stats"));
                if (statistics.isEmpty()) statistics = parseAdvancedStats(html.get("statsSummary"));
                enrichRecentFiveStats(statistics, html.get("recent"));
                Map<String, NewsSignal> newsSignals = parseNewsSignals(html.get("news"), probabilities);
                Map<String, AdviceSignal> adviceSignals = parseFantacalcioAdvice(html.get("consigli"));
                Map<String, MatchContext> matchContexts = loadMatchContexts(officialPlayers, html.get("news"), html.get("stats"), html.get("calendar"));

                ArrayList<Player> players = buildPlayers(officialPlayers, probabilities, gazzetta, sky, statistics, newsSignals, adviceSignals, matchContexts);
                lastParsedPlayers = players;

                FormationResult best = calculateBestFormation(players);
                currentBestResult = best;
                final int sourcesOnline = onlineOk;
                final int playerStats = statistics.size();
                final int probableCount = probabilities.size();

                runOnUiThread(() -> {
                    setStepState("RUOLI", 2, "Ruoli verificati");
                    if (best != null && best.valid) setStepState("ANALISI", 2, "Ottimizzata " + best.module + " • " + sourcesOnline + "/" + sources.size() + " fonti online");
                    if (isFinishing()) return;
                    displayResult(players, best);
                    appendOnlineAnalysisSummary(sourcesOnline, sources.size(), playerStats, probableCount);
                    if (autoRunRequested && best != null && best.valid && credentialsManager.hasCredentials()) {
                        autoRunRequested = false;
                        openLegheWebViewDialog();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    setStepState("ANALISI", 0, "Errore: " + e.getMessage());
                    resultText.setText("Errore analisi online:\n" + e.getMessage() + "\n\nRiprova: i dati online possono temporaneamente non essere disponibili.");
                });
            }
        });
    }

    private void appendOnlineAnalysisSummary(int online, int total, int statPlayers, int probablePlayers) {
        if (resultContainer == null) return;
        TextView onlineView = new TextView(this);
        onlineView.setText("🌐 DATI ONLINE USATI\n" +
                "Fonti raggiunte: " + online + "/" + total + "\n" +
                "Giocatori con statistiche: " + statPlayers + "\n" +
                "Giocatori con probabili: " + probablePlayers + "\n\n" +
                "Il motore AI locale confronta tutti i moduli disponibili e prova combinazioni alternative dei migliori candidati, pesando affidabilità, bonus e rischio titolarità. " +
                "Il punteggio combina dati stagionali, forma recente, probabilità di impiego, consigli Fantacalcio, bonus, rigori, avversario e fattore casa. A parità di qualità privilegia centrocampo/attacco e limita la difesa a 5.");
        onlineView.setTextColor(Color.rgb(165, 190, 210));
        onlineView.setTextSize(12);
        onlineView.setPadding(dp(12), dp(12), dp(12), dp(12));
        onlineView.setBackground(bg(Color.rgb(20, 28, 36), 14));
        resultContainer.addView(onlineView, new LinearLayout.LayoutParams(-1, -2));
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
        int last5CleanSheets;
        int penaltiesMissed;
        double consistency;
        boolean recentDataAvailable;
    }

    private static class NewsSignal {
        int score;
        String reason = "";
    }

    private static class AdviceSignal {
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
        double confidence = 0.0;

        Player(String excelName, String officialName, String team, String role, int quote, int fvm, int probable, int externalAgreement, boolean starter, boolean bench) {
            this(excelName, officialName, team, role, quote, fvm, probable, externalAgreement, starter, bench, null, null, null);
        }

        Player(String excelName, String officialName, String team, String role, int quote, int fvm, int probable, int externalAgreement, boolean starter, boolean bench, AdvancedStats stats, NewsSignal news, MatchContext context) {
            this(excelName, officialName, team, role, quote, fvm, probable, externalAgreement, starter, bench, stats, news, context, null);
        }

        Player(String excelName, String officialName, String team, String role, int quote, int fvm, int probable, int externalAgreement, boolean starter, boolean bench, AdvancedStats stats, NewsSignal news, MatchContext context, AdviceSignal advice) {
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
            calculateScore(stats, news, context, advice);
        }

        private void calculateScore(AdvancedStats stats, NewsSignal news, MatchContext context, AdviceSignal advice) {
            score = 0;
            recentForm = stats != null ? stats.recentForm : 50.0;
            statsFactor = stats != null ? clamp(stats.recentForm, 0, 100) : 45.0;
            opponentFactor = context != null ? (100.0 - clamp(context.opponentDifficulty, 0, 100)) : 50.0;
            newsFactor = news != null ? news.score : 0.0;
            opponent = context != null ? context.opponent : "";

            double availability = probable > 0 ? (0.35 + 0.65 * (probable / 100.0)) : (starter ? 0.62 : 0.35);
            double seasonFactor = stats != null && stats.fantasyAverage > 0 ? clamp((stats.fantasyAverage - 5.0) * 20.0, 0, 100) : 45.0;
            double bonusFactor = stats != null ? clamp(50.0 + stats.goals * 5.0 + stats.assists * 3.0 + stats.penaltiesScored * 6.0, 0, 100) : 45.0;
            double consistencyFactor = stats != null ? clamp(stats.consistency, 0, 100) : 50.0;
            double newsPositive = clamp(newsFactor + 50.0, 0, 100);
            double adviceScore = advice != null ? clamp(advice.score, -40, 40) : 0.0;

            double quality = statsFactor * 0.30
                    + seasonFactor * 0.20
                    + bonusFactor * 0.16
                    + consistencyFactor * 0.10
                    + opponentFactor * 0.16
                    + newsPositive * 0.08;

            score += probable * 0.34;
            score += quality * 0.34 * availability;
            score += opponentFactor * 0.10;
            score += clamp(newsFactor, -100, 100) * 0.08;
            score += externalAgreement * 5.0;
            // I consigli editoriali sono un segnale aggiuntivo, non sostituiscono i dati.
            score += adviceScore * 0.55;
            score += Math.min(fvm, 350) * 0.035;
            score += Math.min(quote, 100) * 0.025;
            if (starter) score += 8;
            if (bench && !starter) score -= 7;
            if (probable == 0 && !starter) score -= 24;

            if (stats != null) {
                // Premio esplicito al potenziale bonus: gol, assist e rigori contano più
                // della sola media voto, soprattutto per C/A.
                double seasonBonus = stats.goals * 1.8 + stats.assists * 1.3 + stats.penaltiesScored * 2.5 - stats.penaltiesMissed * 0.8;
                double recentBonus = stats.last5Goals * 3.5 + stats.last5Assists * 2.5;
                score += Math.min(20.0, Math.max(-5.0, seasonBonus + recentBonus));
                if ("A".equals(role)) score += stats.goals * 1.1 + stats.assists * 0.6;
                if ("C".equals(role)) score += stats.assists * 0.9 + stats.goals * 0.8;
                if ("D".equals(role)) score += stats.assists * 0.4 + stats.goals * 0.7;
                if ("P".equals(role)) score += stats.last5CleanSheets * 1.8 - stats.red * 1.2;
                if ("A".equals(role)) score += stats.goals * 1.4 + stats.assists * 0.8;
                if ("C".equals(role)) score += stats.goals * 1.1 + stats.assists * 1.0;
            }
            if (context != null && context.home) score += 3.0;
            confidence = clamp((probable * 0.45) + (externalAgreement * 18.0) + (stats != null ? 22.0 : 0.0) + (news != null ? 8.0 : 0.0), 0, 100);
            explanation = buildExplanation(stats, news, context, advice);
        }

        private String buildExplanation(AdvancedStats stats, NewsSignal news, MatchContext context, AdviceSignal advice) {
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
        String text = normalize(Jsoup.parse(html).text());
        for (String[] row : formazione) {
            if (row == null || row.length < 2) continue;
            String name = cleanName(row[1]);
            if (name.length() < 3) continue;
            String n = normalize(name);
            if (n.length() < 3) continue;
            if (text.contains(n)) { result.put(n, 1); continue; }
            String[] parts = n.split(" ");
            if (parts.length >= 2) {
                String shortName = parts[0] + " " + parts[parts.length - 1];
                if (text.contains(shortName)) result.put(n, 1);
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

    private ArrayList<Player> buildPlayers(Map<String, OfficialPlayer> official, Map<String, ProbabilityInfo> probabilities, Map<String, Integer> gazzetta, Map<String, Integer> sky, Map<String, AdvancedStats> statistics, Map<String, NewsSignal> newsSignals, Map<String, AdviceSignal> adviceSignals, Map<String, MatchContext> matchContexts) {
        ArrayList<Player> result = new ArrayList<>();
        if (formazione == null) return result;

        for (String[] row : formazione) {
            if (row.length < 2) continue;
            String excelName = row[1];
            if (excelName == null || excelName.trim().isEmpty()) continue;

            String normalizedExcel = normalize(excelName);
            String cachedRole = roleCache.getRole(normalizedExcel);
            // Ruolo strutturale certo: rows[3] = 3 P + 8 D + 8 C + 6 A, calcolato in detectTeams().
            // Ha sempre la precedenza sulla cache, che può contenere ruoli sbagliati salvati
            // da tentativi di scraping precedenti a questa correzione.
            String excelRole = row.length > 3 && row[3] != null ? row[3].trim() : "";
            OfficialPlayer officialPlayer = findOfficialPlayer(excelName, official);

            if (officialPlayer == null) {

                Player player =
                        new Player(
                                excelName,
                                excelName,
                                "",
                                !excelRole.isEmpty() ? excelRole : cachedRole,
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

                String normalizedOfficialName = normalize(officialPlayer.name);
                String role = !excelRole.isEmpty() ? excelRole : (!cachedRole.isEmpty() ? cachedRole : officialPlayer.role);
            // Il ruolo Excel non va mai salvato come override utente: si ricalcola sempre dal file.
            // Solo in assenza di struttura Excel usiamo la cache per ricordare scelte manuali.
            if (excelRole.isEmpty() && cachedRole.isEmpty() && !officialPlayer.role.isEmpty()) {
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
            AdviceSignal advice = adviceSignals.get(normalizedOfficialName);
            if (advice == null) advice = adviceSignals.get(normalizedExcel);
            MatchContext context = matchContexts.get(normalize(officialPlayer.team));

            Player player = new Player(excelName, officialPlayer.name, officialPlayer.team, role, officialPlayer.classicQuote, officialPlayer.fvm, probable, externalAgreement, starter, bench, stats, news, context, advice);
            result.add(player);
        }

            return result;
        }

    private Map<String, AdvancedStats> parseAdvancedStats(String html) {
        Map<String, AdvancedStats> result = new HashMap<>();
        if (html == null || html.isEmpty()) return result;
        Document document = Jsoup.parse(html);

        for (Element table : document.select("table")) {
            Element header = table.select("thead tr").first();
            if (header == null) header = table.select("tr").first();
            if (header == null) continue;

            Elements headerCells = header.select("th,td");
            if (headerCells.size() < 4) continue;

            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headerCells.size(); i++) {
                String h = normalize(headerCells.get(i).text());
                if (h.contains("calciatore") || h.equals("nome") || h.equals("giocatore")) idx.put("name", i);
                if (h.equals("pv") || h.contains("presenze")) idx.put("pv", i);
                if (h.equals("mv") || h.contains("media voto")) idx.put("mv", i);
                if (h.equals("fm") || h.contains("media fantavoto")) idx.put("fm", i);
                if (h.equals("gol") || h.contains("reti")) idx.put("goals", i);
                if (h.startsWith("ass")) idx.put("assists", i);
                if (h.equals("rig") || h.contains("rigori segnati")) idx.put("pen", i);
                if (h.contains("rigori tirati")) idx.put("penTaken", i);
                if (h.equals("amm") || h.contains("ammon")) idx.put("yellow", i);
                if (h.equals("esp") || h.contains("espuls")) idx.put("red", i);
                if (h.contains("rigori sbagliati")) idx.put("penMiss", i);
            }

            if (!idx.containsKey("name")) continue;
            for (Element row : table.select("tr")) {
                Elements cells = row.select("th,td");
                Integer nameIndex = idx.get("name");
                if (nameIndex == null || nameIndex >= cells.size()) continue;

                String name = cleanName(cells.get(nameIndex).text());
                if (name.isEmpty() || isLikelyHeader(name)) continue;

                AdvancedStats stats = new AdvancedStats();
                stats.appearances = valueAt(cells, idx, "pv", 0);
                stats.averageVote = doubleAt(cells, idx, "mv", 0);
                stats.fantasyAverage = doubleAt(cells, idx, "fm", 0);
                stats.goals = valueAt(cells, idx, "goals", 0);
                stats.assists = valueAt(cells, idx, "assists", 0);
                stats.penaltiesScored = valueAt(cells, idx, "pen", 0);
                stats.penaltiesTaken = valueAt(cells, idx, "penTaken", 0);
                stats.yellow = valueAt(cells, idx, "yellow", 0);
                stats.red = valueAt(cells, idx, "red", 0);
                stats.penaltiesMissed = valueAt(cells, idx, "penMiss", 0);
                stats.consistency = stats.averageVote > 0 ? Math.max(0, 100 - Math.abs(stats.averageVote - 6.0) * 25) : 50;
                stats.recentForm = stats.fantasyAverage > 0 ? clampDouble((stats.fantasyAverage - 4.0) * 20.0, 0, 100) : 50;

                result.put(normalize(name), stats);
            }
        }
        return result;
    }

    private int valueAt(Elements cells, Map<String,Integer> idx, String key, int fallback) {
        Integer i = idx.get(key);
        if (i == null || i < 0 || i >= cells.size()) return fallback;
        return parseIntLoose(cells.get(i).text());
    }

    private double doubleAt(Elements cells, Map<String,Integer> idx, String key, double fallback) {
        Integer i = idx.get(key);
        if (i == null || i < 0 || i >= cells.size()) return fallback;
        return parseDouble(cells.get(i).text());
    }

    /**
     * Recupera le statistiche "Ultime 5" per le sole squadre realmente presenti nella rosa.
     * Se la fonte non è disponibile, mantiene il dato stagionale senza inventare numeri.
     */
    private void enrichRecentFiveStats(Map<String, AdvancedStats> statistics, String html) {
        if (statistics == null || statistics.isEmpty() || html == null || html.isEmpty()) return;
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

    private Map<String, AdviceSignal> parseFantacalcioAdvice(String html) {
        Map<String, AdviceSignal> result = new HashMap<>();
        if (html == null || html.isEmpty() || formazione == null) return result;
        Document document = Jsoup.parse(html);
        String full = document.text().toLowerCase(Locale.ROOT);

        for (String[] row : formazione) {
            if (row.length < 2) continue;
            String excelName = row[1];
            if (excelName == null || excelName.trim().isEmpty()) continue;
            String normalized = normalize(excelName);
            int pos = full.indexOf(normalized);
            if (pos < 0) {
                for (Element e : document.select("a")) {
                    if (similarNames(normalized, normalize(e.text()))) {
                        pos = full.indexOf(normalize(e.text()));
                        break;
                    }
                }
            }
            if (pos < 0) continue;

            int from = Math.max(0, pos - 900);
            int to = Math.min(full.length(), pos + normalized.length() + 1200);
            String context = full.substring(from, to);
            AdviceSignal signal = new AdviceSignal();
            ArrayList<String> reasons = new ArrayList<>();

            if (context.contains("da schierare") || context.contains("schierare")) { signal.score += 24; reasons.add("consigliato da Fantacalcio"); }
            if (context.contains("scommess")) { signal.score += 16; reasons.add("scommessa Fantacalcio"); }
            if (context.contains("rigorista")) { signal.score += 12; reasons.add("rigorista"); }
            if (context.contains("calci piazzati") || context.contains("punizioni") || context.contains("corner")) { signal.score += 8; reasons.add("palle inattive"); }
            if (context.contains("da evitare") || context.contains("sconsigliato") || context.contains("panca")) { signal.score -= 25; reasons.add("sconsigliato da Fantacalcio"); }
            if (context.contains("bonus")) { signal.score += 8; reasons.add("potenziale bonus"); }

            signal.score = Math.max(-40, Math.min(40, signal.score));
            signal.reason = String.join(" + ", reasons);
            result.put(normalized, signal);
        }
        return result;
    }

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

    private Map<String, MatchContext> loadMatchContexts(Map<String, OfficialPlayer> official, String newsHtml, String statsHtml, String calendarHtml) {
        Map<String, MatchContext> result = new HashMap<>();
        if (official == null || official.isEmpty()) return result;

        String source = (calendarHtml == null || calendarHtml.isEmpty()) ? newsHtml : calendarHtml;
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

    /**
     * V16 AI Formation Engine.
     * Non si limita a prendere i primi giocatori per ruolo: prova combinazioni
     * ristrette ai migliori candidati, valuta il potenziale bonus e confronta
     * tutti i moduli disponibili. È un ottimizzatore locale, quindi non richiede
     * API esterne né invia la rosa a servizi AI.
     */
    private FormationResult calculateBestFormation(ArrayList<Player> players) {
        FormationResult best = null;
        ArrayList<String> modules = new ArrayList<>(ALLOWED_FORMATIONS);
        Collections.sort(modules);

        for (String module : modules) {
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
        FormationResult greedy = calculateGreedyFormation(module, players);
        if (!greedy.valid) return greedy;

        String[] parts = module.split("-");
        int defenders = Integer.parseInt(parts[0]);
        int midfielders = Integer.parseInt(parts[1]);
        int attackers = Integer.parseInt(parts[2]);

        List<Player> goalkeepers = rankedCandidates(playersForRole(players, "P"), 3);
        List<Player> defenderList = rankedCandidates(playersForRole(players, "D"), 6);
        List<Player> midfielderList = rankedCandidates(playersForRole(players, "C"), 6);
        List<Player> attackerList = rankedCandidates(playersForRole(players, "A"), 6);

        FormationResult best = greedy;

        // Il portiere è singolo: testiamo i migliori candidati.
        for (Player gk : goalkeepers) {
            FormationResult candidate = buildCandidateFormation(module, gk,
                    defenderList, defenders, midfielderList, midfielders, attackerList, attackers, players);
            if (candidate.valid && candidate.score > best.score) best = candidate;
        }

        // Ricerca combinatoria controllata: prova le combinazioni migliori
        // per reparto e le combina con i migliori portieri.
        ArrayList<ArrayList<Player>> dSets = topCombinations(defenderList, defenders, 50);
        ArrayList<ArrayList<Player>> cSets = topCombinations(midfielderList, midfielders, 50);
        ArrayList<ArrayList<Player>> aSets = topCombinations(attackerList, attackers, 50);

        for (ArrayList<Player> ds : dSets) {
            for (ArrayList<Player> cs : cSets) {
                for (ArrayList<Player> as : aSets) {
                    for (Player gk : goalkeepers) {
                        FormationResult candidate = new FormationResult();
                        candidate.module = module;
                        candidate.goalkeeper.add(gk);
                        candidate.defenders.addAll(ds);
                        candidate.midfielders.addAll(cs);
                        candidate.attackers.addAll(as);
                        finalizeFormationCandidate(candidate, players);
                        if (candidate.valid && candidate.score > best.score) best = candidate;
                    }
                }
            }
        }

        return best;
    }

    private FormationResult calculateGreedyFormation(String module, ArrayList<Player> players) {
        FormationResult result = new FormationResult();
        result.module = module;
        String[] parts = module.split("-");
        int defenders = Integer.parseInt(parts[0]);
        int midfielders = Integer.parseInt(parts[1]);
        int attackers = Integer.parseInt(parts[2]);
        List<Player> g = rankedCandidates(playersForRole(players, "P"), 50);
        List<Player> d = rankedCandidates(playersForRole(players, "D"), 50);
        List<Player> c = rankedCandidates(playersForRole(players, "C"), 50);
        List<Player> a = rankedCandidates(playersForRole(players, "A"), 50);
        if (g.isEmpty() || d.size() < defenders || c.size() < midfielders || a.size() < attackers) {
            result.valid = false;
            return result;
        }
        result.goalkeeper.add(g.get(0));
        result.defenders.addAll(d.subList(0, defenders));
        result.midfielders.addAll(c.subList(0, midfielders));
        result.attackers.addAll(a.subList(0, attackers));
        finalizeFormationCandidate(result, players);
        return result;
    }

    private List<Player> rankedCandidates(List<Player> source, int max) {
        ArrayList<Player> copy = new ArrayList<>(source);
        copy.sort((x, y) -> Double.compare(aiPlayerValue(y), aiPlayerValue(x)));
        return new ArrayList<>(copy.subList(0, Math.min(max, copy.size())));
    }

    private double aiPlayerValue(Player p) {
        if (p == null) return -9999;
        double v = p.score;
        // L'AI engine usa affidabilità e potenziale bonus come tie-breaker.
        v += p.confidence * 0.10;
        v += offensiveBonusPotential(p) * ("A".equals(p.role) ? 1.35 : "C".equals(p.role) ? 1.10 : "D".equals(p.role) ? 0.45 : 0.15);
        if (p.probable >= 85) v += 1.5;
        if (p.probable < 60 && p.probable > 0) v -= 2.5;
        return v;
    }

    private ArrayList<ArrayList<Player>> topCombinations(List<Player> pool, int need, int maxSets) {
        ArrayList<ArrayList<Player>> out = new ArrayList<>();
        if (need <= 0) { out.add(new ArrayList<>()); return out; }
        generateCombinations(pool, need, 0, new ArrayList<Player>(), out, maxSets);
        out.sort((x, y) -> Double.compare(combinationScore(y), combinationScore(x)));
        return out;
    }

    private void generateCombinations(List<Player> pool, int need, int start, ArrayList<Player> chosen, ArrayList<ArrayList<Player>> out, int maxSets) {
        if (out.size() >= maxSets) return;
        if (chosen.size() == need) {
            out.add(new ArrayList<>(chosen));
            return;
        }
        int remaining = need - chosen.size();
        for (int i = start; i <= pool.size() - remaining; i++) {
            chosen.add(pool.get(i));
            generateCombinations(pool, need, i + 1, chosen, out, maxSets);
            chosen.remove(chosen.size() - 1);
            if (out.size() >= maxSets) return;
        }
    }

    private double combinationScore(List<Player> players) {
        double v = 0;
        for (Player p : players) v += aiPlayerValue(p);
        return v;
    }

    private FormationResult buildCandidateFormation(String module, Player gk, List<Player> d, int dn, List<Player> c, int cn, List<Player> a, int an, ArrayList<Player> players) {
        FormationResult r = new FormationResult();
        r.module = module;
        r.goalkeeper.add(gk);
        r.defenders.addAll(d.subList(0, Math.min(dn, d.size())));
        r.midfielders.addAll(c.subList(0, Math.min(cn, c.size())));
        r.attackers.addAll(a.subList(0, Math.min(an, a.size())));
        if (r.defenders.size() != dn || r.midfielders.size() != cn || r.attackers.size() != an) { r.valid = false; return r; }
        finalizeFormationCandidate(r, players);
        return r;
    }

    private void finalizeFormationCandidate(FormationResult result, ArrayList<Player> allPlayers) {
        result.score = 0;
        for (Player p : result.goalkeeper) result.score += aiPlayerValue(p);
        for (Player p : result.defenders) result.score += aiPlayerValue(p);
        for (Player p : result.midfielders) result.score += aiPlayerValue(p);
        for (Player p : result.attackers) result.score += aiPlayerValue(p);

        int d = result.defenders.size(), c = result.midfielders.size(), a = result.attackers.size();
        if (a >= 3) result.score += 8.0;
        if (c >= 4) result.score += 3.0;
        if (d == 3) result.score += 5.0;
        if (d >= 5) result.score -= 12.0;

        double bonus = 0;
        for (Player p : result.midfielders) bonus += offensiveBonusPotential(p) * 1.15;
        for (Player p : result.attackers) bonus += offensiveBonusPotential(p) * 1.45;
        for (Player p : result.defenders) bonus += offensiveBonusPotential(p) * 0.45;
        result.score += Math.min(30.0, bonus);

        // Penalità per troppi giocatori con bassa probabilità di partire.
        int risky = 0;
        ArrayList<Player> starters = new ArrayList<>();
        starters.addAll(result.goalkeeper); starters.addAll(result.defenders); starters.addAll(result.midfielders); starters.addAll(result.attackers);
        for (Player p : starters) if (p.probable > 0 && p.probable < 65) risky++;
        result.score -= risky * 3.5;

        // Premia la copertura di bonus distribuita tra più reparti.
        int bonusSources = 0;
        for (Player p : result.midfielders) if (offensiveBonusPotential(p) >= 2.0) bonusSources++;
        for (Player p : result.attackers) if (offensiveBonusPotential(p) >= 2.0) bonusSources++;
        result.score += Math.min(10, bonusSources * 1.8);

        fillBench(result, allPlayers);
        result.valid = true;
    }

    private void fillBench(FormationResult result, ArrayList<Player> allPlayers) {
        List<Player> g = rankedCandidates(playersForRole(allPlayers, "P"), 50);
        List<Player> d = rankedCandidates(playersForRole(allPlayers, "D"), 50);
        List<Player> c = rankedCandidates(playersForRole(allPlayers, "C"), 50);
        List<Player> a = rankedCandidates(playersForRole(allPlayers, "A"), 50);
        result.benchGoalkeeper.clear(); result.benchDefenders.clear(); result.benchMidfielders.clear(); result.benchAttackers.clear();
        addBench(result.benchGoalkeeper, g, result.goalkeeper, 2);
        addBench(result.benchDefenders, d, result.defenders, 2);
        addBench(result.benchMidfielders, c, result.midfielders, 2);
        addBench(result.benchAttackers, a, result.attackers, 2);
    }

    private void addBench(ArrayList<Player> out, List<Player> pool, List<Player> starters, int max) {
        for (Player p : pool) {
            if (starters.contains(p)) continue;
            out.add(p);
            if (out.size() >= max) break;
        }
    }

    private double offensiveBonusPotential(Player p) {
        if (p == null) return 0;
        double v = 0;
        if (p.recentForm > 65) v += 1.5;
        if (p.newsFactor > 5) v += 1.0;
        if (p.externalAgreement > 0) v += p.externalAgreement * 0.7;
        if (p.score > 75) v += 1.5;
        return v;
    }

    private List<Player> playersForRole(ArrayList<Player> players, String role) {
        ArrayList<Player> result = new ArrayList<>();
        for (Player player : players) {
            if (role.equals(player.role)) result.add(player);
        }
        return result;
    }

    private void displayResult(ArrayList<Player> players, FormationResult best) {
        StringBuilder sb = new StringBuilder();
        sb.append("ANALISI ONLINE + AI COMPLETATA\n==============================\n\nRUOLI + DATI WEB + CONTESTO GIORNATA + OTTIMIZZATORE AI\n\n");

        ArrayList<Player> playersWithoutRole = new ArrayList<>();

        for (Player player : players) {
            sb.append(player.excelName).append(" → ");

            if (player.role == null || player.role.trim().isEmpty()) {
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
            sb.append("   SCORE: ").append(String.format(Locale.US, "%.1f", player.score)).append(" punti • Affidabilità ").append(String.format(Locale.US, "%.0f%%", player.confidence));
            if (!player.opponent.isEmpty()) sb.append(" • vs ").append(player.opponent);
            sb.append("\n");
            sb.append("   Motivo: ").append(player.explanation).append("\n");
            if (player.recentForm > 0) sb.append("   Forma recente: ").append(String.format(Locale.US, "%.1f/100", player.recentForm)).append("\n");
            sb.append("   Avversario: ").append(player.opponent.isEmpty() ? "non disponibile" : player.opponent).append("\n\n");
        }

        sb.append("\n");

        if (!playersWithoutRole.isEmpty()) {

            showRoleAssignmentDialog(
                    playersWithoutRole,
                    sb,
                    best
            );

            return;
        }

        displayFormationResult(sb, best);
    }

    private void showRoleAssignmentDialog(
            ArrayList<Player> playersWithoutRole,
            StringBuilder previousResult,
            FormationResult best
    ) {

        int playerIndex = 0;

        showRoleDialogForPlayer(
                playersWithoutRole,
                playerIndex,
                previousResult,
                best
        );
    }

    private void showRoleDialogForPlayer(
            ArrayList<Player> playersWithoutRole,
            int playerIndex,
            StringBuilder previousResult,
            FormationResult best
    ) {

        if (playerIndex >= playersWithoutRole.size()) {

            displayFormationResult(
                    previousResult,
                    best
            );

            return;
        }

        Player player =
                playersWithoutRole.get(
                        playerIndex
                );

        String[] roleOptions = {
                "PORTIERE (P)",
                "DIFENSORE (D)",
                "CENTROCAMPISTA (C)",
                "ATTACCANTE (A)"
        };

        AlertDialog.Builder builder =
                new AlertDialog.Builder(
                        MainActivity.this
                );

        builder.setTitle(
                "Assegna ruolo a: " +
                player.excelName
        );

        builder.setItems(
                roleOptions,
                (dialog, which) -> {

                    String selectedRole = "";

                    switch (which) {

                        case 0:
                            selectedRole = "P";
                            break;

                        case 1:
                            selectedRole = "D";
                            break;

                        case 2:
                            selectedRole = "C";
                            break;

                        case 3:
                            selectedRole = "A";
                            break;
                    }

                    String normalizedName =
                            normalize(
                                    player.officialName.isEmpty()
                                            ? player.excelName
                                            : player.officialName
                            );

                    roleCache.setRole(
                            normalizedName,
                            selectedRole
                    );

                    player.role =
                            selectedRole;

                    showRoleDialogForPlayer(
                            playersWithoutRole,
                            playerIndex + 1,
                            previousResult,
                            best
                    );
                }
        );

        builder.setCancelable(false);

        builder.show();
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
        sub.setText("FORMAZIONE OTTIMALE  •  ALGORITMO V5 ONLINE");
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
    // PlayerRoleCache e CredentialsManager sono classi top-level separate nel package.
}
