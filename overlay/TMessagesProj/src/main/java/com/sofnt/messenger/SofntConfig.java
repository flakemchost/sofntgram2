/*

 SoFntGram: central app-level settings store.

 - Offline first: everything lives on the device (SharedPreferences), nothing needs the network.
 - Everything is cached in memory, so a change is visible to the next read immediately, no restart.
 - Every change posts NotificationCenter.sofntConfigChanged (arg: String key) so open screens can refresh.
 - Per-profile data (phone, usernames, Stars, TON, gifts, frame...) is in profile/localdata/ProfileDataStore.

*/

package com.sofnt.messenger;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SofntConfig {

    // NFR-6: consent to send Telegram ID / personalization data to the SoFntGram server
    public static final int CONSENT_UNKNOWN = 0; // not asked yet -> show the first-launch notice
    public static final int CONSENT_GRANTED = 1;
    public static final int CONSENT_DENIED = 2;  // work with stock Telegram values and local settings only

    private static final Object sync = new Object();
    private static boolean configLoaded;

    private static SharedPreferences preferences;
    private static final Map<String, Object> cache = new ConcurrentHashMap<>();

    static {
        loadConfig();
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded) {
                return;
            }
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("sofntconfig", Activity.MODE_PRIVATE);
            for (Map.Entry<String, ?> e : preferences.getAll().entrySet()) {
                if (e.getValue() != null) {
                    cache.put(e.getKey(), e.getValue());
                }
            }
            configLoaded = true;
        }
    }

    // ---------- typed reads (from memory)

    public static boolean getBoolean(String key) {
        Object v = cache.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        Object def = SofntKeys.DEFAULTS.get(key);
        return def instanceof Boolean && (Boolean) def;
    }

    public static int getInt(String key) {
        Object v = cache.get(key);
        if (v instanceof Integer) {
            return (Integer) v;
        }
        Object def = SofntKeys.DEFAULTS.get(key);
        return def instanceof Integer ? (Integer) def : 0;
    }

    public static long getLong(String key) {
        Object v = cache.get(key);
        if (v instanceof Long) {
            return (Long) v;
        }
        Object def = SofntKeys.DEFAULTS.get(key);
        return def instanceof Long ? (Long) def : 0L;
    }

    public static String getString(String key) {
        Object v = cache.get(key);
        if (v instanceof String) {
            return (String) v;
        }
        Object def = SofntKeys.DEFAULTS.get(key);
        return def instanceof String ? (String) def : "";
    }

    // ---------- writes: memory first, then disk, then notify

    public static void put(String key, boolean value) {
        write(key, value, edit -> edit.putBoolean(key, value));
    }

    public static void put(String key, int value) {
        write(key, value, edit -> edit.putInt(key, value));
    }

    public static void put(String key, long value) {
        write(key, value, edit -> edit.putLong(key, value));
    }

    public static void put(String key, String value) {
        final String v = value == null ? "" : value;
        write(key, v, edit -> edit.putString(key, v));
    }

    /** Back to the default value from SofntKeys. */
    public static void reset(String key) {
        synchronized (sync) {
            cache.remove(key);
            preferences.edit().remove(key).apply();
        }
        notifyChanged(key);
    }

    private interface Writer {
        void write(SharedPreferences.Editor editor);
    }

    private static void write(String key, Object value, Writer writer) {
        synchronized (sync) {
            Object old = cache.get(key);
            if (value.equals(old)) {
                return;
            }
            cache.put(key, value);
            SharedPreferences.Editor editor = preferences.edit();
            writer.write(editor);
            editor.apply();
        }
        notifyChanged(key);
    }

    private static void notifyChanged(String key) {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.sofntConfigChanged, key));
    }

    // ---------- shortcuts for the most used values

    /** True only after the user explicitly agreed (NFR-6). Every server call must check this first. */
    public static boolean hasServerConsent() {
        return getInt(SofntKeys.SERVER_CONSENT) == CONSENT_GRANTED;
    }

    public static boolean isStreamerMode() {
        return getBoolean(SofntKeys.STREAMER_MODE);
    }
}
