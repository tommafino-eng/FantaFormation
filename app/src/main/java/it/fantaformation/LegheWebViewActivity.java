package it.fantaformation;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.webkit.CookieManager;
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

        Button injectButton = new Button(this);
        injectButton.setText("INIETTA IN PAGINA");

        Button copyButton = new Button(this);
        copyButton.setText("COPIA NEGLI APPUNTI");

        bar.addView(injectButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(copyButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        layout.addView(bar);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

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
        injectButton.setOnClickListener(v -> injectScriptToPage());
    }

    /**
     * Copia la formazione negli appunti dello smartphone per un facile riferimento/incollo.
     */
    private void copyFormationToClipboard() {
        StringBuilder sb = new StringBuilder();
        sb.append("Modulo: ").append(module != null ? module : "").append("\n\n");
        sb.append("Formazione:\n");
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
     * Inietta codice JavaScript all'interno della pagina di Leghe Fantacalcio
     * per evidenziare o selezionare i calciatori suggeriti.
     */
    private void injectScriptToPage() {
        if (selectedPlayers.isEmpty()) {
            Toast.makeText(this, "Nessuna formazione caricata", Toast.LENGTH_SHORT).show();
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

        String js = "javascript:(function() {" +
                "  var players = " + jsArray.toString() + ";" +
                "  console.log('Inserimento formazione:', players);" +
                "  var elements = document.querySelectorAll('.player-name, .pl-name, td, div');" +
                "  var count = 0;" +
                "  elements.forEach(function(el) {" +
                "    players.forEach(function(p) {" +
                "      if (el.innerText && el.innerText.toLowerCase().trim().indexOf(p.toLowerCase()) !== -1) {" +
                "        el.style.backgroundColor = '#d4edda';" +
                "        el.style.border = '2px solid #28a745';" +
                "        count++;" +
                "      }" +
                "    });" +
                "  });" +
                "  alert('Trovati ed evidenziati ' + count + ' calciatori nella pagina!');" +
                "})();";

        webView.evaluateJavascript(js, null);
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