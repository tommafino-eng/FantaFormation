package it.fantaformation;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;

public class LegheWebViewActivity extends Activity {

    private WebView webView;
    private ArrayList<String> selectedPlayers;
    private String module;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        selectedPlayers = getIntent().getStringArrayListExtra("PLAYERS");
        module = getIntent().getStringExtra("MODULE");

        if (selectedPlayers == null) {
            selectedPlayers = new ArrayList<>();
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        Button autoFillButton = new Button(this);
        autoFillButton.setText("⚡ INSERISCI FORMAZIONE AUTOMATICA");

        Button copyButton = new Button(this);
        copyButton.setText("📋 COPIA APPUNTI");

        bar.addView(autoFillButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));
        bar.addView(copyButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        layout.addView(bar);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Notifica quando la pagina è caricata
            }
        });

        webView.loadUrl("https://leghe.fantacalcio.it/");

        layout.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(layout);

        copyButton.setOnClickListener(v -> copyFormationToClipboard());
        autoFillButton.setOnClickListener(v -> executeAutoFillScript());
    }

    /**
     * Copia negli appunti la formazione come opzione di riserva.
     */
    private void copyFormationToClipboard() {
        StringBuilder sb = new StringBuilder();
        sb.append("Modulo: ").append(module != null ? module : "").append("\n\n");
        sb.append("Titolari:\n");
        for (String p : selectedPlayers) {
            sb.append("- ").append(p).append("\n");
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Formazione Fanta", sb.toString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Formazione copiata negli appunti!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Esegue lo script JavaScript che simula i click dell'utente per impostare il modulo
     * e selezionare tutti i titolari calcolati dall'algoritmo.
     */
    private void executeAutoFillScript() {
        if (selectedPlayers.isEmpty()) {
            Toast.makeText(this, "Nessuna formazione da inserire", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder jsArray = new StringBuilder("[");
        for (int i = 0; i < selectedPlayers.size(); i++) {
            jsArray.append("'").append(selectedPlayers.get(i).replace("'", "\\'")).append("'");
            if (i < selectedPlayers.size() - 1) {
                jsArray.append(",");
            }
        }
        jsArray.append("]");

        String targetModule = module != null ? module : "";

        String js = "(function() {" +
                "  var targetModule = '" + targetModule + "';" +
                "  var players = " + jsArray.toString() + ";" +
                "  var log = [];" +
                "  " +
                "  /* 1. Reset o pulizia eventuale formazione esistente */" +
                "  var resetBtn = document.querySelector('.btn-reset, [data-action=\"reset\"], .reset-formation');" +
                "  if (resetBtn) { resetBtn.click(); }" +
                "  " +
                "  /* 2. Selezione del modulo tattico nella pagina web */" +
                "  var moduleOptions = document.querySelectorAll('.module-option, [data-module], .radio-module, option');" +
                "  var moduleFound = false;" +
                "  moduleOptions.forEach(function(opt) {" +
                "    var text = (opt.innerText || opt.value || opt.getAttribute('data-module') || '').trim();" +
                "    if (text === targetModule) {" +
                "      opt.click();" +
                "      if(opt.tagName === 'OPTION') { opt.selected = true; opt.dispatchEvent(new Event('change', {bubbles: true})); }" +
                "      moduleFound = true;" +
                "    }" +
                "  });" +
                "  " +
                "  /* 3. Inserimento Titolari tramite simulazione dei click */" +
                "  var insertedCount = 0;" +
                "  var allClickables = document.querySelectorAll('.player-row, .player-item, .player-card, tr[data-player], div[data-player-id], .list-group-item, td');" +
                "  " +
                "  players.forEach(function(playerName) {" +
                "    var cleanTarget = playerName.toLowerCase().replace(/[^a-z0-9 ]/g, '').trim();" +
                "    var found = false;" +
                "    " +
                "    allClickables.forEach(function(el) {" +
                "      if (found) return;" +
                "      var elText = (el.innerText || '').toLowerCase().replace(/[^a-z0-9 ]/g, '').trim();" +
                "      if (elText.length > 2 && elText.indexOf(cleanTarget) !== -1) {" +
                "        /* Trova l'elemento cliccabile o il pulsante + */" +
                "        var btn = el.querySelector('button, .btn, .icon-add, .add-player') || el;" +
                "        btn.click();" +
                "        found = true;" +
                "        insertedCount++;" +
                "      }" +
                "    });" +
                "  });" +
                "  " +
                "  return 'Modulo: ' + targetModule + ' | Giocatori inseriti: ' + insertedCount + '/' + players.length;" +
                "})();";

        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                String msg = value != null ? value.replace("\"", "") : "Comando eseguito";
                Toast.makeText(LegheWebViewActivity.this, "Esito: " + msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}