package it.fantaformation;

import org.jsoup.nodes.Element;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for extracting player roles from HTML elements.
 * Handles role detection from various sources (attributes, text content, parent elements).
 */
public class RoleExtractor {

    // Comprehensive role keyword mapping with Italian and English variants
    private static final Map<String, String> ROLE_KEYWORDS = new HashMap<String, String>() {{
        // Goalkeepers / Portieri
        put("portiere", "P");
        put("goalkeeper", "P");
        put("gardien", "P");
        put("pg", "P");

        // Defenders / Difensori
        put("difensore", "D");
        put("defender", "D");
        put("terzino", "D");
        put("centrale", "D");
        put("cb", "D");
        put("lb", "D");
        put("rb", "D");

        // Midfielders / Centrocampisti
        put("centrocampista", "C");
        put("midfielder", "C");
        put("mediano", "C");
        put("mezzala", "C");
        put("regista", "C");
        put("cm", "C");

        // Forwards / Attaccanti
        put("attaccante", "A");
        put("forward", "A");
        put("ala", "A");
        put("esterno", "A");
        put("punta", "A");
        put("seconda punta", "A");
        put("st", "A");
        put("lw", "A");
        put("rw", "A");
    }};

    /**
     * Extract role from raw text using keyword matching.
     * Handles normalization and accent removal.
     *
     * @param text Raw text that may contain role information
     * @return Role code ("P", "D", "C", "A") or empty string if not found
     */
    public static String extractRole(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String normalized = normalizeForRole(text.toLowerCase(Locale.ROOT));

        // Direct keyword matching
        for (Map.Entry<String, String> entry : ROLE_KEYWORDS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "";
    }

    /**
     * Extract role from a player link element and its context.
     * Tries multiple sources: element attributes, parent row, ancestor elements.
     *
     * @param playerLink The player link element
     * @param row The table row containing the player (can be null)
     * @return Role code ("P", "D", "C", "A") or empty string if not found
     */
    public static String extractFromPlayerProfile(Element playerLink, Element row) {
        if (playerLink == null) {
            return "";
        }

        // Try element attributes first
        String[] attributes = {
                "data-role",
                "data-ruolo",
                "role-type",
                "role",
                "title",
                "aria-label",
                "class"
        };

        for (String attr : attributes) {
            String value = playerLink.attr(attr);
            String role = extractRole(value);
            if (!role.isEmpty()) {
                return role;
            }
        }

        // Try the row containing the player
        if (row != null) {
            String role = extractRole(row.text());
            if (!role.isEmpty()) {
                return role;
            }

            // Try individual cells in the row
            for (Element cell : row.select("td, th")) {
                role = extractRole(cell.text());
                if (!role.isEmpty()) {
                    return role;
                }
            }
        }

        // Walk up the DOM tree looking for role information
        Element current = playerLink.parent();
        for (int i = 0; i < 8 && current != null; i++) {
            String role = extractRole(current.text());
            if (!role.isEmpty()) {
                return role;
            }
            current = current.parent();
        }

        return "";
    }

    /**
     * Normalize text for role comparison.
     * Removes accents, special characters, and extra whitespace.
     *
     * @param text Raw text to normalize
     * @return Normalized text
     */
    private static String normalizeForRole(String text) {
        // Remove accents using NFD normalization
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Remove special characters and convert to lowercase
        normalized = normalized.replaceAll("[^a-z0-9 ]", " ");

        // Normalize whitespace
        normalized = normalized.replaceAll("\\s+", " ").trim();

        return normalized;
    }
}
