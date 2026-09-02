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

import java.util.List;

public class MainActivity extends Activity {

    private static final int PICK_XLSX = 1001;
    private TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);

        TextView title = new TextView(this);
        title.setText("FantaFormation");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView description = new TextView(this);
        description.setText(
                "\nCarica il file Excel (.xlsx) con la tua formazione.\n\n" +
                "Colonne richieste:\n" +
                "ruolo | giocatore | titolare\n"
        );
        description.setTextSize(16);
        layout.addView(description);

        Button loadButton = new Button(this);
        loadButton.setText("CARICA FORMAZIONE EXCEL");
        layout.addView(loadButton);

        Button fantacalcioButton = new Button(this);
        fantacalcioButton.setText("APRI FANTACALCIO");
        layout.addView(fantacalcioButton);

        resultText = new TextView(this);
        resultText.setTextSize(16);
        resultText.setPadding(0, 30, 0, 20);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(resultText);

        layout.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(layout);

        loadButton.setOnClickListener(v -> openFilePicker());

        fantacalcioButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://leghe.fantacalcio.it/")
            );
            startActivity(intent);
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );

        startActivityForResult(intent, PICK_XLSX);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_XLSX &&
                resultCode == RESULT_OK &&
                data != null &&
                data.getData() != null) {

            Uri uri = data.getData();

            try {
                List<String[]> players =
                        XlsxReader.read(this, uri);

                StringBuilder result = new StringBuilder();

                result.append("FORMAZIONE LETTA\n");
                result.append("====================\n\n");

                for (String[] player : players) {
                    result.append(player[0])
                            .append(" - ")
                            .append(player[1])
                            .append(" - ")
                            .append(player[2])
                            .append("\n");
                }

                resultText.setText(result.toString());

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
}
