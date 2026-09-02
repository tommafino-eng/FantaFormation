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

    public static List<String[]> read(
            Context context,
            Uri uri) throws Exception {

        List<String[]> rows = new ArrayList<>();

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

            if (name.equals("xl/sharedStrings.xml")) {
                sharedStringsXml = readEntry(zip);
            }

            if (name.equals("xl/worksheets/sheet1.xml")) {
                sheetXml = readEntry(zip);
            }
        }

        zip.close();

        if (sheetXml == null) {
            throw new Exception("Foglio Excel non trovato");
        }

        List<String> sharedStrings =
                parseSharedStrings(sharedStringsXml);

        Document sheetDocument =
                parseXml(sheetXml);

        NodeList rowNodes =
                sheetDocument.getElementsByTagName("row");

        for (int i = 0; i < rowNodes.getLength(); i++) {

            Node rowNode = rowNodes.item(i);

            NodeList cells =
                    ((org.w3c.dom.Element) rowNode)
                            .getElementsByTagName("c");

            List<String> values = new ArrayList<>();

            for (int j = 0; j < cells.getLength(); j++) {

                org.w3c.dom.Element cell =
                        (org.w3c.dom.Element) cells.item(j);

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
                            type.getNodeValue()
                                    .equals("s")) {

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

            while (values.size() < 3) {
                values.add("");
            }

            rows.add(new String[]{
                    values.get(0),
                    values.get(1),
                    values.get(2)
            });
        }

        return rows;
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

        List<String> result = new ArrayList<>();

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

            Node stringNode = strings.item(i);

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
