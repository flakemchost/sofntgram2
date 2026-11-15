package com.sofnt.messenger.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.sofnt.messenger.profile.localdata.ProfileData;
import com.sofnt.messenger.profile.localdata.ProfileDataStore;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * The sheet that slides up when the user taps a value that was changed in SoFntGram.
 * It shows the SoFntGram letters, the value, where else it leads, and (in the own profile)
 * "Your value", which can be erased by a tap and typed again.
 */
public class SofntValueSheet extends BottomSheet {

    public static final int KIND_PHONE = 0;
    public static final int KIND_USERNAME = 1;

    private final BaseFragment fragment;
    private final int kind;
    private final String shown;
    private final long ownId;

    private TextView yourValue;
    private EditText input;
    private LinearLayout buttons;

    /**
     * @param shown    the value as it is displayed (phone digits, or a username without @)
     * @param original the real Telegram value, or null
     * @param holderId the profile the value is shown on
     */
    public static void show(BaseFragment fragment, int kind, String shown, String original, long holderId, boolean own) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        SofntValueSheet sheet = new SofntValueSheet(fragment, kind, shown, original, holderId, own);
        fragment.showDialog(sheet);
    }

    private SofntValueSheet(BaseFragment fragment, int kind, String shown, String original, long holderId, boolean own) {
        super(fragment.getParentActivity(), true);
        this.fragment = fragment;
        this.kind = kind;
        this.shown = shown;
        this.ownId = UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId();

        Context context = fragment.getParentActivity();
        int text = Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
        int gray = Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        // the SoFntGram letters, black and white only: they take the text colour of the theme
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.sofnt_letters_big);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setColorFilter(new PorterDuffColorFilter(text, PorterDuff.Mode.SRC_IN));
        root.addView(logo, LayoutHelper.createLinear(120, 44, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        TextView title = label(context, LocaleController.getString(R.string.SofntChangedBadge), 20, text, true);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 12, 21, 0));

        String shownText = kind == KIND_USERNAME ? "@" + shown : "+" + shown;
        TextView value = label(context, shownText, 24, text, true);
        root.addView(value, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 6, 21, 0));

        if (original != null && !original.isEmpty() && !original.equals(shown)) {
            String originalText = (kind == KIND_USERNAME ? LocaleController.getString(R.string.SofntOriginalUsername) : LocaleController.getString(R.string.SofntOriginalValue))
                    + ": " + (kind == KIND_USERNAME ? "@" + original : "+" + original);
            root.addView(label(context, originalText, 14, gray, false), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 4, 21, 0));
        }

        if (own) {
            buildOwnSection(context, root, text, gray);
        }

        buildHolders(context, root, holderId, text, gray);

        TextView close = new TextView(context);
        close.setGravity(Gravity.CENTER);
        close.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        close.setTypeface(AndroidUtilities.bold());
        ThemeBind.textColor(close, Theme.key_featuredStickers_buttonText);
        close.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 12));
        close.setText(LocaleController.getString(R.string.Close));
        close.setOnClickListener(v -> dismiss());
        root.addView(close, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, Gravity.BOTTOM, 16, 20, 16, 16));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        setCustomView(scroll);
    }

    private TextView label(Context context, String text, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(AndroidUtilities.bold());
        }
        view.setText(text);
        return view;
    }

    /** "Your value": a tap erases it and lets the user type another one, which is saved. */
    private void buildOwnSection(Context context, LinearLayout root, int text, int gray) {
        root.addView(label(context, LocaleController.getString(R.string.SofntYourValue), 13, gray, false),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 22, 21, 0));

        yourValue = label(context, kind == KIND_USERNAME ? "@" + shown : "+" + shown, 18, text, false);
        yourValue.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        yourValue.setBackground(Theme.AdaptiveRipple.rect(Theme.getColor(Theme.key_listSelector, resourcesProvider), 10));
        root.addView(yourValue, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 4, 21, 0));

        input = new EditText(context);
        input.setTextColor(text);
        input.setHintTextColor(gray);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setInputType(kind == KIND_USERNAME ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_CLASS_PHONE);
        input.setHint(LocaleController.getString(kind == KIND_USERNAME ? R.string.SofntUsernameHint : R.string.SofntPhoneHint));
        input.setVisibility(View.GONE);
        root.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 4, 21, 0));

        buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.addView(actionButton(context, LocaleController.getString(R.string.Save), this::save), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0, 6, 0, 6, 0));
        buttons.addView(actionButton(context, LocaleController.getString(R.string.SofntReset), this::reset), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0, 6, 0, 6, 0));
        root.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 8, 21, 0));

        yourValue.setOnClickListener(v -> {
            yourValue.setVisibility(View.GONE);
            input.setVisibility(View.VISIBLE);
            input.setText("");
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        });
    }

    private TextView actionButton(Context context, String title, Runnable action) {
        TextView button = new TextView(context);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setTypeface(AndroidUtilities.bold());
        button.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        ThemeBind.textColor(button, Theme.key_dialogTextBlue);
        button.setBackground(Theme.AdaptiveRipple.rect(Theme.getColor(Theme.key_listSelector, resourcesProvider), 10));
        button.setText(title);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void save() {
        String typed = input.getVisibility() == View.VISIBLE ? input.getText().toString() : "";
        if (kind == KIND_PHONE) {
            String digits = typed.replaceAll("\\D", "");
            ProfileDataStore.edit(ownId).setPhone(digits.isEmpty() ? null : digits).apply();
        } else {
            List<String> names = new ArrayList<>(ProfileDataStore.getOwn(ownId).secondUsernames);
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).equalsIgnoreCase(shown)) {
                    names.remove(i--);
                }
            }
            for (String name : SofntEditors.parseUsernames(typed)) {
                names.add(name);
            }
            ProfileDataStore.edit(ownId).setSecondUsernames(names).apply();
        }
        dismiss();
    }

    /** back to what Telegram shows */
    private void reset() {
        if (kind == KIND_PHONE) {
            ProfileDataStore.edit(ownId).setPhone(null).apply();
        } else {
            List<String> names = new ArrayList<>(ProfileDataStore.getOwn(ownId).secondUsernames);
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).equalsIgnoreCase(shown)) {
                    names.remove(i--);
                }
            }
            ProfileDataStore.edit(ownId).setSecondUsernames(names).apply();
        }
        dismiss();
    }

    /** Other accounts that lead to the same value. For each one its original (Telegram) username is shown. */
    private void buildHolders(Context context, LinearLayout root, long holderId, int text, int gray) {
        root.addView(label(context, LocaleController.getString(R.string.SofntAlsoLeadsTo), 13, gray, false),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 22, 21, 0));

        List<Long> holders = kind == KIND_USERNAME
                ? ProfileDataStore.findHoldersOfUsername(shown, holderId)
                : ProfileDataStore.findHoldersOfPhone(shown, holderId);
        if (holders.isEmpty()) {
            root.addView(label(context, LocaleController.getString(R.string.SofntNoOtherHolders), 15, gray, false),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 8, 21, 0));
            return;
        }
        MessagesController controller = MessagesController.getInstance(fragment.getCurrentAccount());
        for (final long id : holders) {
            TLRPC.User user = controller.getUser(id);
            String name = user != null ? UserObject.getUserName(user) : "id " + id;
            String original = user != null ? UserObject.getPublicUsername(user) : null;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(8), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
            row.setBackground(Theme.AdaptiveRipple.rect(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0));
            TextView nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTextColor(text);
            nameView.setText(name);
            row.addView(nameView);
            if (original != null) {
                TextView usernameView = new TextView(context);
                usernameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                usernameView.setTextColor(gray);
                usernameView.setText("@" + original);
                row.addView(usernameView);
            }
            row.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putLong("user_id", id);
                dismiss();
                fragment.presentFragment(new ProfileActivity(args));
            });
            root.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }
}
