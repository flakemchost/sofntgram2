package com.sofnt.messenger.profile.localdata;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything SoFntGram keeps about one profile (TZ 2.1, 2.2, 2.4).
 * A null field means "not set": the real Telegram value is shown.
 *
 * Instances that sit in the store are treated as immutable: change them only through ProfileDataStore.edit().
 *
 * NFR-1 / TZ 1: Stars and Gram/TON are LOCAL ONLY. They are written to the device and to .sofntbackup,
 * but toJson(true) (the only way data leaves the device) never contains them, and fromJson ignores them
 * for data that came from the server.
 */
public class ProfileData {

    public static final int SCHEMA_VERSION = 1;

    // FR-1..5 (2.1)
    public String phone;                       // FR-1 displayed phone number
    public List<String> secondUsernames = new ArrayList<>(); // FR-2, claimed through the server registry (2.16)
    public Integer giftsCount;                 // FR-3
    public Long stars;                         // FR-4, LOCAL ONLY
    public String tonBalance;                  // FR-5, LOCAL ONLY, decimal string like "12.5" (no float rounding)

    // FR-7..9 (2.2): list of fake / custom gifts, owned by the fake_items module
    public String fakeGiftsJson = "[]";

    // FR-12..17, FR-20 (2.4, 2.6): owned by the profile modules, stored as opaque JSON
    public String frameJson = "";              // FR-12 avatar frame (static or animated)
    public String bannerJson = "";             // FR-13 profile banner
    public String menuStyleJson = "";          // FR-14 Steam/Discord style menu
    public String socialLinksJson = "[]";      // FR-15 link buttons
    public String badgesJson = "[]";           // FR-16 custom badges / tags
    public String profileTheme = "";           // FR-20 / FR-60 theme the profile card is drawn with

    public long updatedAt;                     // millis, set on every local change
    public boolean dirty;                      // changed locally and not yet sent to the server

    public ProfileData copy() {
        ProfileData c = new ProfileData();
        c.phone = phone;
        c.secondUsernames = new ArrayList<>(secondUsernames);
        c.giftsCount = giftsCount;
        c.stars = stars;
        c.tonBalance = tonBalance;
        c.fakeGiftsJson = fakeGiftsJson;
        c.frameJson = frameJson;
        c.bannerJson = bannerJson;
        c.menuStyleJson = menuStyleJson;
        c.socialLinksJson = socialLinksJson;
        c.badgesJson = badgesJson;
        c.profileTheme = profileTheme;
        c.updatedAt = updatedAt;
        c.dirty = dirty;
        return c;
    }

    /**
     * @param forSync true = the payload that goes to the SoFntGram server: no Stars, no TON, no local flags.
     *                false = full copy for the device (storage, backup).
     */
    public JSONObject toJson(boolean forSync) {
        JSONObject o = new JSONObject();
        try {
            o.put("schema", SCHEMA_VERSION);
            if (phone != null) o.put("phone", phone);
            if (!secondUsernames.isEmpty()) o.put("secondUsernames", new JSONArray(secondUsernames));
            if (giftsCount != null) o.put("giftsCount", giftsCount.intValue());
            o.put("fakeGifts", new JSONArray(orDefault(fakeGiftsJson, "[]")));
            putJson(o, "frame", frameJson);
            putJson(o, "banner", bannerJson);
            putJson(o, "menuStyle", menuStyleJson);
            o.put("socialLinks", new JSONArray(orDefault(socialLinksJson, "[]")));
            o.put("badges", new JSONArray(orDefault(badgesJson, "[]")));
            if (!profileTheme.isEmpty()) o.put("profileTheme", profileTheme);
            o.put("updatedAt", updatedAt);
            if (!forSync) {
                if (stars != null) o.put("stars", stars.longValue());
                if (tonBalance != null) o.put("ton", tonBalance);
                o.put("dirty", dirty);
            }
        } catch (JSONException e) {
            throw new IllegalStateException("ProfileData has invalid JSON in one of its fields", e);
        }
        return o;
    }

    /**
     * @param fromServer true = data of another user that came from the server: Stars/TON are ignored even if present.
     */
    public static ProfileData fromJson(String json, boolean fromServer) {
        ProfileData d = new ProfileData();
        if (json == null || json.isEmpty()) {
            return d;
        }
        try {
            JSONObject o = new JSONObject(json);
            d.phone = o.has("phone") ? o.getString("phone") : null;
            JSONArray names = o.optJSONArray("secondUsernames");
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    d.secondUsernames.add(names.getString(i));
                }
            }
            d.giftsCount = o.has("giftsCount") ? Integer.valueOf(o.getInt("giftsCount")) : null;
            d.fakeGiftsJson = arrayOrDefault(o, "fakeGifts");
            d.frameJson = jsonOrEmpty(o, "frame");
            d.bannerJson = jsonOrEmpty(o, "banner");
            d.menuStyleJson = jsonOrEmpty(o, "menuStyle");
            d.socialLinksJson = arrayOrDefault(o, "socialLinks");
            d.badgesJson = arrayOrDefault(o, "badges");
            d.profileTheme = o.optString("profileTheme", "");
            d.updatedAt = o.optLong("updatedAt", 0);
            if (!fromServer) {
                d.stars = o.has("stars") ? Long.valueOf(o.getLong("stars")) : null;
                d.tonBalance = o.has("ton") ? o.getString("ton") : null;
                d.dirty = o.optBoolean("dirty", false);
            }
        } catch (JSONException e) {
            return new ProfileData(); // corrupted record: fall back to "nothing changed", never crash the profile screen
        }
        return d;
    }

    private static String orDefault(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
    }

    private static void putJson(JSONObject o, String key, String json) throws JSONException {
        if (json != null && !json.isEmpty()) {
            o.put(key, new JSONObject(json));
        }
    }

    private static String jsonOrEmpty(JSONObject o, String key) {
        JSONObject v = o.optJSONObject(key);
        return v == null ? "" : v.toString();
    }

    private static String arrayOrDefault(JSONObject o, String key) {
        JSONArray v = o.optJSONArray(key);
        return v == null ? "[]" : v.toString();
    }
}
