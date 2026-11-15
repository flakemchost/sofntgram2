package com.sofnt.messenger.ui;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import com.sofnt.messenger.profile.localdata.ProfileDataStore;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Small dialogs that change values shown in SoFntGram (the real Telegram data is never touched). */
public final class SofntEditors {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,32}");

    private SofntEditors() {}

    private static long ownId(BaseFragment fragment) {
        return UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId();
    }

    /** "Change number?" question, then the editor. Used for a long press on the own number. */
    public static void askChangePhone(BaseFragment fragment) {
        ask(fragment, R.string.SofntEditPhoneAsk, () -> editPhone(fragment));
    }

    public static void askChangeUsernames(BaseFragment fragment) {
        ask(fragment, R.string.SofntEditUsernamesAsk, () -> editUsernames(fragment));
    }

    private static void ask(BaseFragment fragment, int titleRes, Runnable yes) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider());
        builder.setTitle(LocaleController.getString(titleRes));
        builder.setMessage(LocaleController.getString(R.string.SofntEditInfo));
        builder.setPositiveButton(LocaleController.getString(R.string.Edit), (dialog, which) -> yes.run());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    public static void editPhone(BaseFragment fragment) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final long id = ownId(fragment);
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setSingleLine(true);
        input.setHint(LocaleController.getString(R.string.SofntPhoneHint));
        String current = ProfileDataStore.getOwn(id).phone;
        if (current != null) {
            input.setText(current);
            input.setSelection(current.length());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.SofntEditPhone));
        builder.setMessage(LocaleController.getString(R.string.SofntEditPhoneInfo));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            String digits = input.getText().toString().replaceAll("\\D", "");
            ProfileDataStore.edit(id).setPhone(digits.isEmpty() ? null : digits).apply();
        });
        builder.setNeutralButton(LocaleController.getString(R.string.SofntReset), (dialog, which) -> ProfileDataStore.edit(id).setPhone(null).apply());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    /** Second usernames that exist only in SoFntGram, typed separated by commas or spaces. */
    public static void editUsernames(BaseFragment fragment) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final long id = ownId(fragment);
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint(LocaleController.getString(R.string.SofntUsernamesHint));
        StringBuilder current = new StringBuilder();
        for (String name : ProfileDataStore.getOwn(id).secondUsernames) {
            if (current.length() > 0) {
                current.append(", ");
            }
            current.append('@').append(name);
        }
        input.setText(current.toString());
        input.setSelection(input.getText().length());
        AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.SofntEditUsernames));
        builder.setMessage(LocaleController.getString(R.string.SofntEditUsernamesInfo));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            ProfileDataStore.edit(id).setSecondUsernames(parseUsernames(input.getText().toString())).apply();
        });
        builder.setNeutralButton(LocaleController.getString(R.string.SofntReset), (dialog, which) -> ProfileDataStore.edit(id).setSecondUsernames(new ArrayList<>()).apply());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    static List<String> parseUsernames(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("[,;\\s]+")) {
            String name = part.startsWith("@") ? part.substring(1) : part;
            if (USERNAME.matcher(name).matches()) {
                boolean duplicate = false;
                for (String have : out) {
                    if (have.equalsIgnoreCase(name)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    out.add(name);
                }
            }
        }
        return out;
    }
}
