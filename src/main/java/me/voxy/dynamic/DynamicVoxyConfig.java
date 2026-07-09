package me.voxy.dynamic;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DynamicVoxyConfig {
    public Map<String, Map<String, String>> namedAreaOverrides = new HashMap<>();

    public static DynamicVoxyConfig defaultConfig() {
        return new DynamicVoxyConfig();
    }

    public void initializeDefaults() {
        if (namedAreaOverrides == null) {
            namedAreaOverrides = new HashMap<>();
        }
    }

    public Map<String, String> getNamedAreas(String serverId) {
        if (serverId == null) {
            return Collections.emptyMap();
        }
        Map<String, String> areas = namedAreaOverrides.get(serverId.toLowerCase());
        return areas != null ? areas : Collections.emptyMap();
    }

    public void saveNamedArea(String serverId, String name, String areaValue) {
        namedAreaOverrides.computeIfAbsent(serverId.toLowerCase(), k -> new HashMap<>()).put(name, areaValue);
    }

    public void deleteNamedArea(String serverId, String name) {
        Map<String, String> areas = namedAreaOverrides.get(serverId.toLowerCase());
        if (areas != null) {
            areas.remove(name);
        }
    }

    public boolean renameNamedArea(String serverId, String oldName, String newName) {
        Map<String, String> areas = namedAreaOverrides.get(serverId.toLowerCase());
        if (areas == null || !areas.containsKey(oldName) || areas.containsKey(newName)) {
            return false;
        }
        String area = areas.remove(oldName);
        areas.put(newName, area);
        return true;
    }
}
