package com.sofnt.messenger.profile;

import com.sofnt.messenger.SofntConfig;
import com.sofnt.messenger.profile.localdata.ProfileData;
import com.sofnt.messenger.profile.localdata.ProfileDataStore;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

/**
 * One place that decides what the profile UI shows (MUL 1.1, 1.2). Real values are never touched:
 * getClientPhone() and TLRPC.User.usernames stay as they are, this layer only answers "what to display".
 *
 * Priority for every value: streamer mode / hidden by the user  >  value changed in SoFntGram  >  real value.
 * The result carries its source, so the UI can show the NFR-1 plate (ChangedBadgeView) without extra checks.
 *
 * Own profile reads ProfileDataStore.getOwn, somebody else's reads the server cache (getRemote), so it also works offline.
 * Stars and Gram/TON are only ever resolved for the own profile (they are local only).
 */
public final class DisplayResolver {

    public enum Source {
        REAL,    // Telegram's own value
        CHANGED, // changed in SoFntGram, must carry the plate
        HIDDEN   // hidden by streamer mode or by the old "hide phone number" switch
    }

    public static final String HIDDEN_TEXT = "•••";

    public static final class Value {
        public final String text;     // null when there is nothing to show
        public final Source source;

        Value(String text, Source source) {
            this.text = text;
            this.source = source;
        }

        public boolean isChanged() {
            return source == Source.CHANGED;
        }

        public boolean isHidden() {
            return source == Source.HIDDEN;
        }
    }

    public static final class Username {
        public final String text;
        public final boolean fromSofnt; // true = claimed in SoFntGram (not confirmed by Telegram), false = real

        Username(String text, boolean fromSofnt) {
            this.text = text;
            this.fromSofnt = fromSofnt;
        }
    }

    private DisplayResolver() {}

    private static ProfileData dataFor(long userId, boolean isOwn) {
        return isOwn ? ProfileDataStore.getOwn(userId) : ProfileDataStore.getRemote(userId);
    }

    /** @param realPhone digits as Telegram has them (user.phone), may be null */
    public static Value phone(long userId, boolean isOwn, String realPhone) {
        if (SofntConfig.isStreamerMode()) {
            return new Value(HIDDEN_TEXT, Source.HIDDEN);
        }
        String fake = dataFor(userId, isOwn).phone;
        if (fake != null && !fake.isEmpty() && !fake.equals(realPhone)) {
            return new Value(fake, Source.CHANGED);
        }
        return new Value(realPhone, Source.REAL);
    }

    /** True when there is a changed phone for this profile (so the phone row must exist even if Telegram gives no number). */
    public static boolean hasChangedPhone(long userId, boolean isOwn) {
        String fake = dataFor(userId, isOwn).phone;
        return fake != null && !fake.isEmpty();
    }

    /** @param realCount the number Telegram reports, or null when unknown */
    public static Value giftsCount(long userId, boolean isOwn, Integer realCount) {
        Integer fake = dataFor(userId, isOwn).giftsCount;
        if (fake != null && !fake.equals(realCount)) {
            return new Value(String.valueOf(fake), Source.CHANGED);
        }
        return new Value(realCount == null ? null : String.valueOf(realCount), Source.REAL);
    }

    /** Own profile only: Stars never leave the device. */
    public static Value stars(long ownUserId, Long realStars) {
        Long fake = ProfileDataStore.getOwn(ownUserId).stars;
        if (fake != null && !fake.equals(realStars)) {
            return new Value(String.valueOf(fake), Source.CHANGED);
        }
        return new Value(realStars == null ? null : String.valueOf(realStars), Source.REAL);
    }

    /** Own profile only: Gram/TON never leave the device. Values are decimal strings. */
    public static Value tonBalance(long ownUserId, String realBalance) {
        String fake = ProfileDataStore.getOwn(ownUserId).tonBalance;
        if (fake != null && !fake.equals(realBalance)) {
            return new Value(fake, Source.CHANGED);
        }
        return new Value(realBalance, Source.REAL);
    }

    /**
     * Real usernames first (in Telegram's order), then the ones claimed in SoFntGram.
     * Hidden completely in streamer mode (returns an empty list).
     * Claimed names that equal a real one are not repeated.
     */
    public static List<Username> usernames(long userId, boolean isOwn, List<String> realUsernames) {
        List<Username> out = new ArrayList<>();
        if (SofntConfig.isStreamerMode()) {
            return out;
        }
        if (realUsernames != null) {
            for (String name : realUsernames) {
                out.add(new Username(name, false));
            }
        }
        for (String name : dataFor(userId, isOwn).secondUsernames) {
            boolean duplicate = false;
            for (Username u : out) {
                if (u.text.equalsIgnoreCase(name)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                out.add(new Username(name, true));
            }
        }
        return out;
    }

    /** Real usernames as Telegram has them, then the ones that exist only in SoFntGram. Streamer mode hides the extra ones. */
    public static ArrayList<TLRPC.TL_username> withFakeUsernames(long userId, boolean isOwn, ArrayList<TLRPC.TL_username> real) {
        ArrayList<TLRPC.TL_username> out = real == null ? new ArrayList<>() : new ArrayList<>(real);
        if (SofntConfig.isStreamerMode()) {
            return out;
        }
        for (String name : dataFor(userId, isOwn).secondUsernames) {
            boolean duplicate = false;
            for (TLRPC.TL_username u : out) {
                if (u.username != null && u.username.equalsIgnoreCase(name)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                out.add(new SofntUsername(name));
            }
        }
        return out;
    }
}
