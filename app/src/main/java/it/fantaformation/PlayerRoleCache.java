package it.fantaformation;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages a cache of player roles stored in JSON format.
 * Allows reading and writing player role mappings to persistent storage.
 */
public class PlayerRoleCache {

    private static final String CACHE_FILE_NAME = "player_roles_cache.json";
    private final Context context;
    private Map<String, String> cache;

    /**
     * Constructor that loads existing cache from file.
     *
     * @param context Android context for file operations
     */
    public PlayerRoleCache(Context context) {
        this.context = context;
        this.cache = new HashMap<>();
        loadCache();
    }

    /**
     * Get cached role for a player.
     *
     * @param playerName Normalized player name
     * @return Role code ("P", "D", "C", "A") or empty string if not cached
     */
    public String getRole(String playerName) {
        if (playerName == null) {
            return "";
        }
        return cache.getOrDefault(playerName, "");
    }

    /**
     * Check if a player's role is cached.
     *
     * @param playerName Normalized player name
     * @return true if player role is in cache, false otherwise
     */
    public boolean hasRole(String playerName) {
        if (playerName == null) {
            return false;
        }
        return cache.containsKey(playerName) && !cache.get(playerName).isEmpty();
    }

    /**
     * Save a player's role to cache and persist to file.
     *
     * @param playerName Normalized player name
     * @param role Role code ("P", "D", "C", "A")
     */
    public void setRole(String playerName, String role) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        cache.put(playerName, role);
        saveCache();
    }

    /**
     * Load cache from JSON file in app's private files directory.
     */
    private void loadCache() {
        try {
            File file = new File(
                    context.getFilesDir(),
                    CACHE_FILE_NAME
            );

            if (!file.exists()) {
                return;
            }

            BufferedReader reader = new BufferedReader(
                    new FileReader(file)
            );

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(
                    content.toString()
            );

            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                cache.put(key, json.getString(key));
            }

        } catch (Exception e) {
            // If cache loading fails, just start with empty cache
            cache.clear();
        }
    }

    /**
     * Save cache to JSON file in app's private files directory.
     */
    private void saveCache() {
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }

            File file = new File(
                    context.getFilesDir(),
                    CACHE_FILE_NAME
            );

            FileWriter writer = new FileWriter(file);
            writer.write(json.toString(2));
            writer.close();

        } catch (Exception e) {
            // If save fails, continue without persisting
        }
    }

    /**
     * Clear all cached roles.
     */
    public void clear() {
        cache.clear();
        saveCache();
    }

    /**
     * Get total number of cached players.
     *
     * @return Number of entries in cache
     */
    public int size() {
        return cache.size();
    }
}
