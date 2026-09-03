package it.fantaformation;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
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
        autoFillButton.setText("⚡ INSERISCI AUTOMATICO");

        Button copyButton = new Button(this);
        copyButton.setText("📋 COPIA APPUNTI");

        bar.addView(autoFillButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));
        bar.addView(copyButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        layout.addView(bar);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
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
     * Script di automazione JavaScript potenziato per l'inserimento e il salvataggio automatico.
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
                "  var insertedCount = 0;" +
                "  " +
                "  /* 1. Selezione Modulo */" +
                "  var moduleOptions = document.querySelectorAll('.module-option, [data-module], .radio-module, option, .btn-module');" +
                "  moduleOptions.forEach(function(opt) {" +
                "    var text = (opt.innerText || opt.value || opt.getAttribute('data-module') || '').trim();" +
                "    if (text === targetModule) {" +
                "      opt.click();" +
                "      if(opt.tagName === 'OPTION') { opt.selected = true; opt.dispatchEvent(new Event('change', {bubbles: true})); }" +
                "    }" +
                "  });" +
                "  " +
                "  /* 2. Clic sui giocatori titolari nella rosa */" +
                "  var allClickables = document.querySelectorAll('.player-row, .player-item, .player-card, tr[data-player], div[data-player-id], .list-group-item, .item-player, td');" +
                "  players.forEach(function(playerName) {" +
                "    var cleanTarget = playerName.toLowerCase().replace(/[^a-z0-9 ]/g, '').trim();" +
                "    var found = false;" +
                "    allClickables.forEach(function(el) {" +
                "      if (found) return;" +
                "      var elText = (el.innerText || '').toLowerCase().replace(/[^a-z0-9 ]/g, '').trim();" +
                "      if (elText.length > 2 && elText.indexOf(cleanTarget) !== -1) {" +
                "        var btn = el.querySelector('button, .btn, .icon-add, .add-player, .action-add') || el;" +
                "        btn.click();" +
                "        found = true;" +
                "        insertedCount++;" +
                "      }" +
                "    });" +
                "  });" +
                "  " +
                "  /* 3. Tentativo di salvataggio automatico */" +
                "  setTimeout(function() {" +
                "    var saveBtn = document.querySelector('.btn-save, .save-formation, [data-action=\"save\"], #save-button');" +
                "    if (saveBtn) {" +
                "      saveBtn.click();" +
                "    }" +
                "  }, 1500);" +
                "  " +
                "  return 'Modulo: ' + targetModule + ' | Inseriti: ' + insertedCount + '/' + players.length;" +
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