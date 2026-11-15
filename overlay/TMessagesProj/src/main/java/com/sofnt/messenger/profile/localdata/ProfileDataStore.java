package com.sofnt.messenger.profile.localdata;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Map;

/**
 * Offline-first store of profile data.
 *
 * - Own profiles (every logged in account, keyed by Telegram user id) and a cache of other users' data
 *   received from the server (FR-60) are kept on the device, so everything shows without internet.
 * - Everything read once is held in memory; an edit is visible to the next read at once, no restart needed.
 * - Every edit posts NotificationCenter.sofntProfileDataChanged (args: Long userId, Boolean own).
 *
 * Reads never return null. Records in the cache are never mutated after commit: edit() works on a copy.
 */
public class ProfileDataStore {

    private static final Object sync = new Object();
    private static final Map<Long, ProfileData> own = new ConcurrentHashMap<>();
    private static final Map<Long, ProfileData> remote = new ConcurrentHashMap<>();

    private static SharedPreferences ownPrefs;
    private static SharedPreferences remotePrefs;

    private static void ensurePrefs() {
        if (ownPrefs == null) {
            synchronized (sync) {
                if (ownPrefs == null) {
                    remotePrefs = ApplicationLoader.applicationContext.getSharedPreferences("sofnt_profile_remote", Activity.MODE_PRIVATE);
                    ownPrefs = ApplicationLoader.applicationContext.getSharedPreferences("sofnt_profile_own", Activity.MODE_PRIVATE);
                }
            }
        }
    }

    // ---------- reads

    /** Data of an account the user is logged in with. */
    public static ProfileData getOwn(long userId) {
        ensurePrefs();
        ProfileData d = own.get(userId);
        if (d == null) {
            d = ProfileData.fromJson(ownPrefs.getString("u" + userId, null), false);
            ProfileData prev = own.putIfAbsent(userId, d);
            if (prev != null) {
                d = prev;
            }
        }
        return d;
    }

    public static ProfileData getOwnForAccount(int account) {
        return getOwn(UserConfig.getInstance(account).getClientUserId());
    }

    public static ProfileData getOwnCurrent() {
        return getOwnForAccount(UserConfig.selectedAccount);
    }

    /** Last data received from the server about somebody else's profile (empty if never received). */
    public static ProfileData getRemote(long userId) {
        ensurePrefs();
        ProfileData d = remote.get(userId);
        if (d == null) {
            d = ProfileData.fromJson(remotePrefs.getString("u" + userId, null), true);
            ProfileData prev = remote.putIfAbsent(userId, d);
            if (prev != null) {
                d = prev;
            }
        }
        return d;
    }

    /** Own profiles with changes the server has not received yet. */
    public static List<Long> getDirtyOwnIds() {
        ensurePrefs();
        List<Long> ids = new ArrayList<>();
        for (String key : ownPrefs.getAll().keySet()) {
            if (key.startsWith("u")) {
                long id = Long.parseLong(key.substring(1));
                if (getOwn(id).dirty) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }


    // ---------- who else carries a value (used by the "changed in SoFntGram" sheet)

    /** Telegram ids of known profiles (own accounts and cached ones) that show this phone number; excludeUserId is skipped. */
    public static List<Long> findHoldersOfPhone(String digits, long excludeUserId) {
        List<Long> out = new ArrayList<>();
        if (digits == null || digits.isEmpty()) {
            return out;
        }
        collectHolders(ownPrefs(), true, digits, null, excludeUserId, out);
        collectHolders(remotePrefs(), false, digits, null, excludeUserId, out);
        return out;
    }

    /** Same for a username (without @), compared case-insensitively. */
    public static List<Long> findHoldersOfUsername(String username, long excludeUserId) {
        List<Long> out = new ArrayList<>();
        if (username == null || username.isEmpty()) {
            return out;
        }
        collectHolders(ownPrefs(), true, null, username, excludeUserId, out);
        collectHolders(remotePrefs(), false, null, username, excludeUserId, out);
        return out;
    }

    private static SharedPreferences ownPrefs() {
        ensurePrefs();
        return ownPrefs;
    }

    private static SharedPreferences remotePrefs() {
        ensurePrefs();
        return remotePrefs;
    }

    private static void collectHolders(SharedPreferences prefs, boolean isOwn, String phone, String username, long exclude, List<Long> out) {
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith("u")) {
                continue;
            }
            long id;
            try {
                id = Long.parseLong(key.substring(1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (id == exclude || out.contains(id)) {
                continue;
            }
            ProfileData d = isOwn ? getOwn(id) : getRemote(id);
            boolean match = false;
            if (phone != null) {
                match = phone.equals(d.phone);
            } else if (username != null) {
                for (String name : d.secondUsernames) {
                    if (name.equalsIgnoreCase(username)) {
                        match = true;
                        break;
                    }
                }
            }
            if (match) {
                out.add(id);
            }
        }
    }

    // ---------- writes

    public static Editor edit(long userId) {
        return new Editor(userId);
    }

    public static Editor editCurrent() {
        return new Editor(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
    }

    /** Called by the sync layer when the server confirmed our data. */
    public static void markSynced(long userId) {
        ensurePrefs();
        ProfileData d = getOwn(userId).copy();
        if (!d.dirty) {
            return;
        }
        d.dirty = false;
        commitOwn(userId, d);
    }

    /** Called by the sync layer with another user's public data (never contains Stars/TON, see ProfileData.fromJson). */
    public static void putRemote(long userId, String json) {
        ensurePrefs();
        ProfileData d = ProfileData.fromJson(json, true);
        remote.put(userId, d);
        remotePrefs.edit().putString("u" + userId, d.toJson(true).toString()).apply();
        notifyChanged(userId, false);
    }

    private static void commitOwn(long userId, ProfileData d) {
        own.put(userId, d);
        ownPrefs.edit().putString("u" + userId, d.toJson(false).toString()).apply();
        notifyChanged(userId, true);
    }

    private static void notifyChanged(long userId, boolean isOwn) {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.sofntProfileDataChanged, userId, isOwn));
    }

    public static class Editor {
        private final long userId;
        private final ProfileData d;
        private boolean syncableChanged;
        private boolean anyChanged;

        private Editor(long userId) {
            ensurePrefs();
            this.userId = userId;
            this.d = getOwn(userId).copy();
        }

        // FR-1..5
        public Editor setPhone(String phone) { d.phone = phone; return touch(true); }
        public Editor setSecondUsernames(List<String> names) { d.secondUsernames = new ArrayList<>(names); return touch(true); }
        public Editor setGiftsCount(Integer count) { d.giftsCount = count; return touch(true); }
        /** Local only: never sent anywhere. */
        public Editor setStars(Long stars) { d.stars = stars; return touch(false); }
        /** Local only: never sent anywhere. Decimal string, e.g. "12.5". */
        public Editor setTonBalance(String ton) { d.tonBalance = ton; return touch(false); }

        // FR-7..9
        public Editor setFakeGiftsJson(String json) { d.fakeGiftsJson = json; return touch(true); }

        // FR-12..17, FR-20
        public Editor setFrameJson(String json) { d.frameJson = json; return touch(true); }
        public Editor setBannerJson(String json) { d.bannerJson = json; return touch(true); }
        public Editor setMenuStyleJson(String json) { d.menuStyleJson = json; return touch(true); }
        public Editor setSocialLinksJson(String json) { d.socialLinksJson = json; return touch(true); }
        public Editor setBadgesJson(String json) { d.badgesJson = json; return touch(true); }
        public Editor setProfileTheme(String theme) { d.profileTheme = theme == null ? "" : theme; return touch(true); }

        private Editor touch(boolean syncable) {
            anyChanged = true;
            if (syncable) {
                syncableChanged = true;
            }
            return this;
        }

        /** Commit: memory, disk, then notify. Safe to call from any thread. */
        public void apply() {
            if (!anyChanged) {
                return;
            }
            d.updatedAt = System.currentTimeMillis();
            if (syncableChanged) {
                d.dirty = true; // Stars/TON edits never mark the profile for sync
            }
            synchronized (sync) {
                commitOwn(userId, d);
            }
        }
    }
}
