package it.fantaformation;

import android.content.Context;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PlayerRoleCache {

    private static final String CACHE_FILE_NAME = "player_roles_cache.json";
    private final Context context;
    private Map<String, String> cache;

    public PlayerRoleCache(Context context) {
        this.context = context;
        this.cache = new HashMap<>();
        loadCache();
    }

    public String getRole(String playerName) {
        if (playerName == null) {
            return "";
        }
        return cache.getOrDefault(playerName, "");
    }

    public boolean hasRole(String playerName) {
        if (playerName == null) {
            return false;
        }
        return cache.containsKey(playerName) && !cache.get(playerName).isEmpty();
    }

    public void setRole(String playerName, String role) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        cache.put(playerName, role);
        saveCache();
    }

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
            cache.clear();
        }
    }

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
            // Ignora eccezioni durante il salvataggio
        }
    }

    public void clear() {
        cache.clear();
        saveCache();
    }

    public int size() {
        return cache.size();
    }
}