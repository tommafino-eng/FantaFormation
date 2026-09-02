package it.fantaformation;

import android.content.Context;
import android.net.Uri;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

public class XlsxReader {

    private static final int MAX_PLAYERS = 25;

    public static List<String[]> read(
            Context context,
            Uri uri) throws Exception {

        InputStream input =
                context.getContentResolver()
                        .openInputStream(uri);

        if (input == null) {
            throw new Exception("Impossibile aprire il file");
        }

        ZipInputStream zip =
                new ZipInputStream(input);

        String sharedStringsXml = null;
        String sheetXml = null;

        ZipEntry entry;

        while ((entry = zip.getNextEntry()) != null) {

            String name = entry.getName();

            if ("xl/sharedStrings.xml".equals(name)) {
                sharedStringsXml = readEntry(zip);
            }

            if ("xl/worksheets/sheet1.xml".equals(name)) {
                sheetXml = readEntry(zip);
            }
        }

        zip.close();

        if (sheetXml == null) {
            throw new Exception("Foglio Excel non trovato");
        }

        List<String> sharedStrings =
                parseSharedStrings(sharedStringsXml);

        Document document = parseXml(sheetXml);

        NodeList rows =
                document.getElementsByTagName("row");

        List<String[]> result = new ArrayList<>();

        boolean insideOnePisa = false;

        for (int i = 0; i < rows.getLength(); i++) {

            Node row = rows.item(i);

            List<String> values =
                    getRowValues(row, sharedStrings);

            if (values.isEmpty()) {
                continue;
            }

            String firstValue =
                    values.get(0).trim();

            if (!insideOnePisa) {

                if (isOnePisa(firstValue)) {
                    insideOnePisa = true;
                }

                continue;
            }

            /*
             * Una nuova intestazione indica
             * l'inizio della squadra successiva.
             */
            if (isTeamHeader(firstValue)) {
                break;
            }

            String player = "";

            for (String value : values) {

                String clean =
                        value.trim();

                if (clean.isEmpty()) {
                    continue;
                }

                if (clean.equalsIgnoreCase("costo")) {
                    continue;
                }

                if (isNumber(clean)) {
                    continue;
                }

                player = clean;
                break;
            }

            if (!player.isEmpty()) {

                String cost = "";

                for (String value : values) {

                    String clean =
                            value.trim();

                    if (isNumber(clean)) {
                        cost = clean;
                        break;
                    }
                }

                result.add(new String[]{
                        "",
                        player,
                        cost
                });
            }

            if (result.size() >= MAX_PLAYERS) {
                break;
            }
        }

        if (!insideOnePisa) {
            throw new Exception(
                    "Sezione 'One Pisa' non trovata"
            );
        }

        if (result.isEmpty()) {
            throw new Exception(
                    "Nessun giocatore trovato nella sezione One Pisa"
            );
        }

        return result;
    }

    private static boolean isOnePisa(
            String value) {

        String normalized =
                value.toLowerCase()
                        .replace(" ", "")
                        .trim();

        return normalized.equals("onepisa");
    }

    private static boolean isTeamHeader(
            String value) {

        String normalized =
                value.toLowerCase()
                        .replace(" ", "")
                        .trim();

        if (normalized.isEmpty()) {
            return false;
        }

        if (normalized.equals("costo")) {
            return false;
        }

        /*
         * Evitiamo di considerare un nome giocatore
         * come intestazione.
         *
         * Le intestazioni delle squadre nel tuo file
         * non hanno un costo numerico associato.
         */
        return normalized.contains("farmers")
                || normalized.contains("team")
                || normalized.contains("skibidi")
                || normalized.contains("fatturage");
    }

    private static boolean isNumber(
            String value) {

        try {
            Double.parseDouble(value.replace(",", "."));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> getRowValues(
            Node row,
            List<String> sharedStrings) {

        List<String> values =
                new ArrayList<>();

        NodeList cells =
                ((org.w3c.dom.Element) row)
                        .getElementsByTagName("c");

        for (int i = 0;
             i < cells.getLength();
             i++) {

            org.w3c.dom.Element cell =
                    (org.w3c.dom.Element) cells.item(i);

            String value = "";

            Node type =
                    cell.getAttributes()
                            .getNamedItem("t");

            NodeList valueNodes =
                    cell.getElementsByTagName("v");

            if (valueNodes.getLength() > 0) {

                value =
                        valueNodes.item(0)
                                .getTextContent();

                if (type != null &&
                        "s".equals(
                                type.getNodeValue())) {

                    try {

                        int index =
                                Integer.parseInt(value);

                        if (index >= 0 &&
                                index < sharedStrings.size()) {

                            value =
                                    sharedStrings.get(index);
                        }

                    } catch (Exception ignored) {
                    }
                }
            }

            values.add(value);
        }

        return values;
    }

    private static String readEntry(
            ZipInputStream zip) throws Exception {

        StringBuilder result =
                new StringBuilder();

        byte[] buffer = new byte[4096];

        int count;

        while ((count = zip.read(buffer)) != -1) {

            result.append(
                    new String(
                            buffer,
                            0,
                            count,
                            "UTF-8"
                    )
            );
        }

        return result.toString();
    }

    private static Document parseXml(
            String xml) throws Exception {

        return DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(
                        new java.io.ByteArrayInputStream(
                                xml.getBytes("UTF-8")
                        )
                );
    }

    private static List<String> parseSharedStrings(
            String xml) throws Exception {

        List<String> result =
                new ArrayList<>();

        if (xml == null) {
            return result;
        }

        Document document =
                parseXml(xml);

        NodeList strings =
                document.getElementsByTagName("si");

        for (int i = 0;
             i < strings.getLength();
             i++) {

            Node stringNode =
                    strings.item(i);

            NodeList textNodes =
                    ((org.w3c.dom.Element) stringNode)
                            .getElementsByTagName("t");

            StringBuilder value =
                    new StringBuilder();

            for (int j = 0;
                 j < textNodes.getLength();
                 j++) {

                value.append(
                        textNodes.item(j)
                                .getTextContent()
                );
            }

            result.add(value.toString());
        }

        return result;
    }
}
