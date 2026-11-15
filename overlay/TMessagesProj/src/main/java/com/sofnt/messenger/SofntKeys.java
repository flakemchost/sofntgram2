package com.sofnt.messenger;

import java.util.HashMap;
import java.util.Map;

/**
 * Catalog of every app-level setting SoFntGram stores on the device, grouped by the FR it serves.
 * Each key is registered once together with its default value, so SofntConfig.getXxx(KEY) needs no default at the call site.
 * Per-profile values (phone, usernames, Stars, TON, gifts, frame, banner, badges...) live in ProfileData, not here.
 * Values that are JSON blobs are owned by the module named in the comment; the config only stores them.
 */
public final class SofntKeys {

    static final Map<String, Object> DEFAULTS = new HashMap<>();

    private static String reg(String key, Object def) {
        DEFAULTS.put(key, def);
        return key;
    }

    private SofntKeys() {}

    // ---- Server / consent (FR-59..63, NFR-6)
    public static final String SERVER_CONSENT = reg("server_consent", 0);       // SofntConfig.CONSENT_*
    public static final String SERVER_BASE_URL = reg("server_base_url", "");

    // ---- Presence (FR-10, FR-11)
    public static final String PRESENCE_TRACKING = reg("presence_tracking", false);
    public static final String PRESENCE_HISTORY_DAYS = reg("presence_history_days", 30);

    // ---- Heroku (FR-18, FR-19); secrets (tokens) must not be stored here, use a keystore-backed store later
    public static final String HEROKU_USERBOT_AUTOCONNECT = reg("heroku_userbot_autoconnect", false);
    public static final String HEROKU_CHANNEL_APP = reg("heroku_channel_app", "");

    // ---- Themes and appearance (FR-20..30)
    public static final String ACTIVE_FTHEME = reg("active_ftheme", "");               // path/id of the applied .ftheme
    public static final String CUSTOM_FONT = reg("custom_font", "");                    // FR-21
    public static final String BUBBLE_SHAPE_JSON = reg("bubble_shape_json", "");        // FR-22 (radius, cornerStyle, tailStyle)
    public static final String TYPING_ANIM_JSON = reg("typing_anim_json", "");          // FR-23
    public static final String PARALLAX_ENABLED = reg("parallax_enabled", false);       // FR-24
    public static final String PARALLAX_STRENGTH = reg("parallax_strength", 50);
    public static final String AUTO_THEME_TRIGGER = reg("auto_theme_trigger", 0);       // FR-25: 0 off, 1 current track, 2 mood
    public static final String AUTO_THEME_MAP_JSON = reg("auto_theme_map_json", "{}");
    public static final String SIDEBAR_STYLE = reg("sidebar_style", 0);                 // FR-26: 0 stock, 1 old Telegram style
    public static final String CONFIG_PROFILES_JSON = reg("config_profiles_json", "[]"); // FR-27
    public static final String ACTIVE_CONFIG_PROFILE = reg("active_config_profile", "");
    public static final String WALLPAPER_PARTICLES_JSON = reg("wallpaper_particles_json", ""); // FR-28
    public static final String STATUS_BAR_JSON = reg("status_bar_json", "");            // FR-29
    public static final String APP_NAME_INDEX = reg("app_name_index", 0);               // FR-30
    public static final String CUSTOM_APP_NAME = reg("custom_app_name", "");
    public static final String APP_ICON_INDEX = reg("app_icon_index", 0);
    public static final String CUSTOM_APP_ICON = reg("custom_app_icon", "");

    // ---- Plugins and automation (FR-31..36)
    public static final String PLUGINS_ENABLED = reg("plugins_enabled", true);
    public static final String PLUGINS_STATE_JSON = reg("plugins_state_json", "{}");
    public static final String MACROS_JSON = reg("macros_json", "[]");                  // FR-32
    public static final String AUTORESPONDER_ENABLED = reg("autoresponder_enabled", false); // FR-33
    public static final String AUTORESPONDER_JSON = reg("autoresponder_json", "");
    public static final String AI_ASSISTANT_ENABLED = reg("ai_assistant_enabled", false);   // FR-34
    public static final String AI_SORT_ENABLED = reg("ai_sort_enabled", false);             // FR-35
    public static final String COUNTERS_JSON = reg("counters_json", "[]");                  // FR-36

    // ---- Messages and chats (FR-37..44)
    public static final String MARKDOWN_INPUT = reg("markdown_input", true);            // FR-37
    public static final String CHAT_TAGS_JSON = reg("chat_tags_json", "{}");            // FR-38
    public static final String OFFLINE_TRANSLATE = reg("offline_translate", false);     // FR-39
    public static final String OFFLINE_TRANSLATE_LANG = reg("offline_translate_lang", "");
    public static final String OFFLINE_TRANSCRIBE = reg("offline_transcribe", false);   // FR-40
    public static final String REMINDERS_JSON = reg("reminders_json", "[]");            // FR-41
    public static final String MULTISELECT_ACTIONS_JSON = reg("multiselect_actions_json", ""); // FR-42
    public static final String REACTION_EFFECTS_JSON = reg("reaction_effects_json", ""); // FR-43

    // ---- Sound and media (FR-46..48)
    public static final String NOTIFICATION_SOUNDS_JSON = reg("notification_sounds_json", "{}"); // FR-46
    public static final String UI_SOUNDS_ENABLED = reg("ui_sounds_enabled", false);     // FR-47
    public static final String UI_SOUND_PACK = reg("ui_sound_pack", "");
    public static final String NOW_PLAYING_ENABLED = reg("now_playing_enabled", false); // FR-48
    public static final String NOW_PLAYING_SOURCE = reg("now_playing_source", 0);       // 0 Spotify, 1 Yandex Music

    // ---- Interface (FR-49, FR-50)
    public static final String PING_INDICATOR = reg("ping_indicator", false);
    public static final String FAST_UI_MODE = reg("fast_ui_mode", false);

    // ---- Privacy and security (FR-51..57); only hashes of codes are ever stored
    public static final String PIN_LENGTH = reg("pin_length", 4);                       // FR-51: 4, 6 or 8
    public static final String CHAT_LOCK_JSON = reg("chat_lock_json", "{}");            // FR-53: chat id -> lock type
    public static final String HIDDEN_FOLDERS_JSON = reg("hidden_folders_json", "[]");  // FR-54
    public static final String DURESS_ENABLED = reg("duress_enabled", false);           // FR-55
    public static final String DURESS_PASSCODE_HASH = reg("duress_passcode_hash", "");
    public static final String AUTO_LOCK_EVENT = reg("auto_lock_event", 0);             // FR-56: 0 timer only
    public static final String STREAMER_MODE = reg("streamer_mode", false);             // FR-57

    // ---- Backup (FR-58)
    public static final String BACKUP_LAST_EXPORT = reg("backup_last_export", 0L);
}
