package it.fantaformation;

import android.content.Context;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.io.File;
import android.os.ParcelFileDescriptor;

/**
 * Lettore XLSX minimale e indipendente da Apache POI.
 * Mantiene le colonne vuote, quindi il formato delle rose a blocchi
 * "Squadra | costo | vuota" viene letto senza perdere la posizione delle colonne.
 */
public final class XlsxReader {
    private XlsxReader() {}

    public static List<String[]> read(Context context, Uri uri) throws Exception {
        File file = null;
        ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) throw new IllegalArgumentException("Impossibile aprire il file Excel");

        java.io.FileInputStream fis = new java.io.FileInputStream(pfd.getFileDescriptor());
        byte[] data;
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) out.write(buffer, 0, n);
            data = out.toByteArray();
        } finally {
            try { fis.close(); } catch (Exception ignored) {}
            try { pfd.close(); } catch (Exception ignored) {}
        }

        File tmp = new File(context.getCacheDir(), "fantaformation_import.xlsx");
        java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp);
        try { fos.write(data); } finally { fos.close(); }

        try {
            return readZip(tmp);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static List<String[]> readZip(File file) throws Exception {
        ZipFile zip = new ZipFile(file);
        try {
            List<String> sharedStrings = readSharedStrings(zip);
            String sheetPath = findFirstWorksheet(zip);
            if (sheetPath == null) throw new IllegalArgumentException("Foglio XLSX non trovato");
            return readSheet(zip, sheetPath, sharedStrings);
        } finally {
            zip.close();
        }
    }

    private static String findFirstWorksheet(ZipFile zip) throws Exception {
        ZipEntry wbEntry = zip.getEntry("xl/workbook.xml");
        ZipEntry relEntry = zip.getEntry("xl/_rels/workbook.xml.rels");
        if (wbEntry == null || relEntry == null) return "xl/worksheets/sheet1.xml";

        Map<String,String> rels = new HashMap<>();
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser p = f.newPullParser();
        p.setInput(zip.getInputStream(relEntry), "UTF-8");
        for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
            if (e == XmlPullParser.START_TAG && "Relationship".equals(p.getName())) {
                String id = p.getAttributeValue(null, "Id");
                String target = p.getAttributeValue(null, "Target");
                if (id != null && target != null) rels.put(id, target);
            }
        }

        String relId = null;
        p = f.newPullParser();
        p.setInput(zip.getInputStream(wbEntry), "UTF-8");
        for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
            if (e == XmlPullParser.START_TAG && "sheet".equals(p.getName())) {
                relId = p.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                if (relId == null) relId = p.getAttributeValue(null, "r:id");
                if (relId != null) break;
            }
        }
        String target = rels.get(relId);
        if (target == null) return "xl/worksheets/sheet1.xml";
        if (target.startsWith("/")) target = target.substring(1);
        if (!target.startsWith("xl/")) target = "xl/" + target;
        return target.replace("xl//", "xl/");
    }

    private static List<String> readSharedStrings(ZipFile zip) throws Exception {
        ArrayList<String> result = new ArrayList<>();
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) return result;

        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser p = f.newPullParser();
        p.setInput(zip.getInputStream(entry), "UTF-8");
        StringBuilder current = null;
        for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
            if (e == XmlPullParser.START_TAG) {
                if ("si".equals(p.getName())) current = new StringBuilder();
                else if ("t".equals(p.getName()) && current != null) current.append(p.nextText());
            } else if (e == XmlPullParser.END_TAG && "si".equals(p.getName()) && current != null) {
                result.add(current.toString());
                current = null;
            }
        }
        return result;
    }

    private static List<String[]> readSheet(ZipFile zip, String path, List<String> shared) throws Exception {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) throw new IllegalArgumentException("Foglio non trovato: " + path);

        ArrayList<Map<Integer,String>> rows = new ArrayList<>();
        int currentRow = -1;
        Map<Integer,String> cells = null;
        int maxCol = 0;
        String cellRef = null;
        String cellType = null;
        String value = null;

        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser p = f.newPullParser();
        p.setInput(zip.getInputStream(entry), "UTF-8");

        for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
            if (e == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if ("row".equals(tag)) {
                    currentRow = parseInt(p.getAttributeValue(null, "r"), rows.size() + 1) - 1;
                    while (rows.size() <= currentRow) rows.add(new HashMap<Integer,String>());
                    cells = rows.get(currentRow);
                } else if ("c".equals(tag)) {
                    cellRef = p.getAttributeValue(null, "r");
                    cellType = p.getAttributeValue(null, "t");
                    value = "";
                } else if ("v".equals(tag) && cellRef != null) {
                    value = p.nextText();
                } else if ("t".equals(tag) && "inlineStr".equals(cellType) && cellRef != null) {
                    value = p.nextText();
                }
            } else if (e == XmlPullParser.END_TAG && "c".equals(p.getName()) && cellRef != null && cells != null) {
                int col = columnIndex(cellRef);
                String out = value == null ? "" : value;
                if ("s".equals(cellType)) {
                    int idx = parseInt(out, -1);
                    out = (idx >= 0 && idx < shared.size()) ? shared.get(idx) : "";
                } else if ("str".equals(cellType)) {
                    // already textual
                }
                cells.put(col, out);
                maxCol = Math.max(maxCol, col + 1);
                cellRef = null;
                cellType = null;
                value = null;
            }
        }

        ArrayList<String[]> result = new ArrayList<>();
        for (Map<Integer,String> row : rows) {
            String[] arr = new String[maxCol];
            for (int i=0;i<maxCol;i++) arr[i] = row.containsKey(i) ? row.get(i) : "";
            result.add(arr);
        }
        return result;
    }

    private static int columnIndex(String ref) {
        int n = 0;
        for (int i=0;i<ref.length();i++) {
            char ch = ref.charAt(i);
            if (ch < 'A' || ch > 'Z') break;
            n = n * 26 + (ch - 'A' + 1);
        }
        return Math.max(0, n - 1);
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }
}
