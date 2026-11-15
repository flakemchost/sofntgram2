#!/usr/bin/env python3
"""
SoFntGram: turns an official Telegram-Android tree into the SoFntGram build base.
Safe to run many times (every step checks whether it is already done).
Run it from the root of the official tree: python3 ../sofnt/prepare.py

Secrets are taken from the environment: SOFNT_APP_ID, SOFNT_APP_HASH (api_id / api_hash from my.telegram.org).
"""
import json, os, re, shutil, sys

PACKAGE = "com.sofnt.messenger"
APP_NAME = "SoFntGram"
KEY_ALIAS = "sofnt"

def read(p):
    return open(p, encoding="utf-8").read()

def write(p, s):
    open(p, "w", encoding="utf-8").write(s)

def sub_once(path, pattern, repl, flags=0, required=True):
    s = read(path)
    new, n = re.subn(pattern, repl, s, count=1, flags=flags)
    if n == 0 and required and repl not in s:
        sys.exit("prepare: pattern not found in %s: %s" % (path, pattern[:60]))
    if n:
        write(path, new)

# 1. application id and signing key (our own key replaces Telegram's public test key)
sub_once("gradle.properties", r"^APP_PACKAGE=.*$", "APP_PACKAGE=" + PACKAGE, re.M)
ks_file, ks_pass = os.environ.get("SOFNT_KEYSTORE_FILE"), os.environ.get("SOFNT_KEYSTORE_PASSWORD")
if ks_file and ks_pass and os.path.exists(ks_file):
    shutil.copyfile(ks_file, "TMessagesProj/config/release.keystore")
    sub_once("gradle.properties", r"^RELEASE_KEY_ALIAS=.*$", "RELEASE_KEY_ALIAS=" + KEY_ALIAS, re.M)
    sub_once("gradle.properties", r"^RELEASE_KEY_PASSWORD=.*$", "RELEASE_KEY_PASSWORD=" + ks_pass, re.M)
    sub_once("gradle.properties", r"^RELEASE_STORE_PASSWORD=.*$", "RELEASE_STORE_PASSWORD=" + ks_pass, re.M)
else:
    print("prepare: no SOFNT_KEYSTORE_FILE / SOFNT_KEYSTORE_PASSWORD, the APK will be signed with Telegram's public test key")

# 2. api_id / api_hash of our own app
build_vars = "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
app_id, app_hash = os.environ.get("SOFNT_APP_ID"), os.environ.get("SOFNT_APP_HASH")
if app_id and app_hash:
    sub_once(build_vars, r"public static int APP_ID = \d+;", "public static int APP_ID = %d;" % int(app_id))
    sub_once(build_vars, r'public static String APP_HASH = "[0-9a-fA-F]*";', 'public static String APP_HASH = "%s";' % app_hash.strip().strip('"'))
else:
    print("prepare: SOFNT_APP_ID / SOFNT_APP_HASH not set, Telegram's test keys stay (fine for a quick look, not for real use)")

# 3. application name
strings = "TMessagesProj/src/main/res/values/strings.xml"
sub_once(strings, r'(<string name="AppName">)[^<]*(</string>)', r"\g<1>" + APP_NAME + r"\g<2>")
sub_once(strings, r'(<string name="AppNameBeta">)[^<]*(</string>)', r"\g<1>" + APP_NAME + r"\g<2>")

# 4. Firebase config: the plugin wants a client entry for every application id we build
gs = "TMessagesProj_App/google-services.json"
data = json.load(open(gs, encoding="utf-8"))
have = {c["client_info"]["android_client_info"]["package_name"] for c in data["client"]}
template = next(c for c in data["client"] if c["client_info"]["android_client_info"]["package_name"] == "org.telegram.messenger.beta")
for suffix in ("", ".beta", ".web"):
    name = PACKAGE + suffix
    if name not in have:
        c = json.loads(json.dumps(template))
        c["client_info"]["android_client_info"]["package_name"] = name
        data["client"].append(c)
json.dump(data, open(gs, "w", encoding="utf-8"), indent=2)

# 5. build only the architecture of a modern phone (4x faster); remove this step to build for all
app_gradle = "TMessagesProj_App/build.gradle"
s = read(app_gradle)
s2 = re.sub(r'(\n        afat \{\n            ndk \{\n                abiFilters )"[^\n]*"', r'\1"arm64-v8a"', s, count=1)
if s2 != s:
    write(app_gradle, s2)

# 6. keep the stack trace of a Java crash in Downloads (see SofntCrashLog)
app_loader = "TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java"
if "SofntCrashLog" not in read(app_loader):
    sub_once(app_loader, r"(protected void attachBaseContext\(Context base\) \{\n        super\.attachBaseContext\(base\);\n)",
             r"\1        com.sofnt.messenger.SofntCrashLog.install(this);\n")


# 7. fork settings: no Google Play billing, no passkeys (they only work for official application ids)
sub_once(build_vars, r"IS_BILLING_UNAVAILABLE = false;", "IS_BILLING_UNAVAILABLE = true;")
sub_once(build_vars, r"SUPPORTS_PASSKEYS = true;", "SUPPORTS_PASSKEYS = false;")


# 8. SoFntGram code hooks in the official sources (every patch is checked and skipped when already applied)
def patch(path, old, new, marker, count=1):
    s = read(path)
    if new in s:
        return  # already applied
    if s.count(old) != count:
        sys.exit("prepare: expected %d x in %s, found %d: %s" % (count, path, s.count(old), old[:70].replace("\n", "\\n")))
    write(path, s.replace(old, new))

J = "TMessagesProj/src/main/java/org/telegram/"

patch(J + "messenger/NotificationCenter.java",
      "    public static final int communitySwitchedCollapsed = totalEvents++;\n",
      "    public static final int communitySwitchedCollapsed = totalEvents++;\n"
      "    // SoFntGram: config value changed (arg: String key), profile data changed (args: Long userId, Boolean own)\n"
      "    public static final int sofntConfigChanged = totalEvents++;\n"
      "    public static final int sofntProfileDataChanged = totalEvents++;\n",
      "sofntConfigChanged")

PA = J + "ui/ProfileActivity.java"
M = "sofntRow"
patch(PA, "import org.telegram.ui.ActionBar.ActionBar;\n",
      "import org.telegram.ui.ActionBar.ActionBar;\n"
      "import com.sofnt.messenger.SofntConfig;\nimport com.sofnt.messenger.profile.DisplayResolver;\n"
      "import com.sofnt.messenger.profile.SofntUsername;\nimport com.sofnt.messenger.ui.SofntEditors;\nimport com.sofnt.messenger.ui.SofntSettingsActivity;\nimport com.sofnt.messenger.ui.SofntValueSheet;\n", M)
patch(PA, "    private int chatRow;\n", "    private int chatRow;\n    private int sofntRow;\n", M)
patch(PA, "        chatRow = -1;\n", "        chatRow = -1;\n        sofntRow = -1;\n", M)
patch(PA, "                settingsSectionRow2 = rowCount++;\n                chatRow = rowCount++;\n",
      "                settingsSectionRow2 = rowCount++;\n                sofntRow = rowCount++;\n                chatRow = rowCount++;\n", M)
patch(PA, "            } else if (position == chatRow) {\n                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));\n",
      "            } else if (position == sofntRow) {\n                presentFragment(new SofntSettingsActivity());\n"
      "            } else if (position == chatRow) {\n                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));\n", M)
patch(PA, "                    } else if (position == chatRow) {\n                        textCell.setTextAndIcon(LocaleController.getString(R.string.ChatSettings), R.drawable.msg2_discussion, true);\n",
      "                    } else if (position == sofntRow) {\n                        textCell.setTextAndIcon(LocaleController.getString(R.string.SofntSettings), R.drawable.sofnt_settings_letters, true);\n"
      "                    } else if (position == chatRow) {\n                        textCell.setTextAndIcon(LocaleController.getString(R.string.ChatSettings), R.drawable.msg2_discussion, true);\n", M)
patch(PA, "position == versionRow || position == dataRow || position == chatRow ||", "position == versionRow || position == dataRow || position == chatRow || position == sofntRow ||", M)
patch(PA, "position == languageRow || position == dataRow || position == chatRow ||", "position == languageRow || position == dataRow || position == chatRow || position == sofntRow ||", M)
patch(PA, "            put(++pointer, chatRow, sparseIntArray);\n", "            put(++pointer, sofntRow, sparseIntArray);\n            put(++pointer, chatRow, sparseIntArray);\n", M)

# redraw at once when SoFntGram values change
patch(PA, "        getNotificationCenter().addWeakObserver(this, NotificationCenter.updateInterfaces);\n",
      "        getNotificationCenter().addWeakObserver(this, NotificationCenter.updateInterfaces);\n"
      "        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.sofntProfileDataChanged);\n"
      "        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.sofntConfigChanged);\n", M)
patch(PA, "        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);\n",
      "        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);\n"
      "        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.sofntProfileDataChanged);\n"
      "        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.sofntConfigChanged);\n", M)
patch(PA, "        if (id == NotificationCenter.uploadStoryEnd || id == NotificationCenter.chatWasBoostedByUser) {\n            checkCanSendStoryForPosting();\n",
      "        if (id == NotificationCenter.sofntProfileDataChanged || id == NotificationCenter.sofntConfigChanged) {\n            sofntRefresh();\n"
      "        } else if (id == NotificationCenter.uploadStoryEnd || id == NotificationCenter.chatWasBoostedByUser) {\n            checkCanSendStoryForPosting();\n", M)

# the phone row: a number changed in SoFntGram, streamer mode
patch(PA, "boolean hasPhone = user != null && (!TextUtils.isEmpty(user.phone) || !TextUtils.isEmpty(vcardPhone));",
      "boolean hasPhone = user != null && (!TextUtils.isEmpty(user.phone) || !TextUtils.isEmpty(vcardPhone) || DisplayResolver.hasChangedPhone(user.id, user.id == UserConfig.getInstance(currentAccount).getClientUserId()));", M)
patch(PA, "                        String phoneNumber;\n                        if (user != null && !TextUtils.isEmpty(vcardPhone)) {",
      "                        String phoneNumber;\n                        DisplayResolver.Value shownPhone = null;\n                        if (user != null && !TextUtils.isEmpty(vcardPhone)) {", M)
patch(PA, "                        } else if (user != null && !TextUtils.isEmpty(user.phone)) {\n                            text = PhoneFormat.getInstance().format(\"+\" + user.phone);\n                            phoneNumber = user.phone;\n",
      "                        } else if (user != null && !(shownPhone = DisplayResolver.phone(user.id, user.id == UserConfig.getInstance(currentAccount).getClientUserId(), user.phone)).isHidden() && !TextUtils.isEmpty(shownPhone.text)) {\n                            text = PhoneFormat.getInstance().format(\"+\" + shownPhone.text);\n                            phoneNumber = shownPhone.text;\n", M)
patch(PA, "                        if (user != null && user.phone != null && user.phone.length() != 0) {\n                            value = PhoneFormat.getInstance().format(\"+\" + user.phone);\n                        } else {\n                            value = LocaleController.getString(R.string.NumberUnknown);\n                        }\n",
      "                        DisplayResolver.Value shownNumber = user == null ? null : DisplayResolver.phone(user.id, true, user.phone);\n"
      "                        if (SofntConfig.isStreamerMode()) {\n                            value = LocaleController.getString(R.string.PhoneHidden);\n"
      "                        } else if (shownNumber != null && !TextUtils.isEmpty(shownNumber.text)) {\n                            value = PhoneFormat.getInstance().format(\"+\" + shownNumber.text);\n"
      "                        } else {\n                            value = LocaleController.getString(R.string.NumberUnknown);\n                        }\n", M)

# tap on a changed value opens the SoFntGram sheet; holding the own number or username offers to change it
patch(PA, "            } else {\n                processOnClickOrPress(position, view, x, y);\n            }\n        });\n\n        listView.setOnItemLongClickListener(",
      "            } else if (sofntOnTap(position)) {\n                // handled by SoFntGram: the value was changed in SoFntGram\n"
      "            } else {\n                processOnClickOrPress(position, view, x, y);\n            }\n        });\n\n        listView.setOnItemLongClickListener(", M)
patch(PA, "            } else if (position == numberRow) {\n                presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER));\n",
      "            } else if (position == numberRow) {\n                if (!sofntOnTap(position)) {\n                    presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER));\n                }\n", M)
patch(PA, "        } else if (position == usernameRow) {\n            itemOptions.add(R.drawable.msg_edit, getString(R.string.ProfileUsernameEdit), () -> {\n                presentFragment(new ChangeUsernameActivity());\n            });\n",
      "        } else if (position == usernameRow) {\n            itemOptions.add(R.drawable.msg_edit, getString(R.string.ProfileUsernameEdit), () -> {\n                presentFragment(new ChangeUsernameActivity());\n            });\n"
      "            itemOptions.add(R.drawable.msg_edit, getString(R.string.SofntEditUsernamesAsk), () -> SofntEditors.editUsernames(this));\n", M)
patch(PA, "        } else if (position == phoneRow) {\n            itemOptions.add(R.drawable.menu_storage_path, getString(R.string.ProfilePhoneEdit), () -> {\n                presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER));\n            });\n",
      "        } else if (position == phoneRow) {\n            itemOptions.add(R.drawable.menu_storage_path, getString(R.string.ProfilePhoneEdit), () -> {\n                presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER));\n            });\n"
      "            itemOptions.add(R.drawable.msg_edit, getString(R.string.SofntEditPhoneAsk), () -> SofntEditors.editPhone(this));\n", M)
patch(PA, "            o.setLongPressSelectionEnabled(false);\n            if (position == phoneRow) {\n                if (userInfo != null && userInfo.phone_calls_available) {",
      "            o.setLongPressSelectionEnabled(false);\n            if (user.id == getUserConfig().getClientUserId()) {\n                o.add(R.drawable.msg_edit, getString(R.string.SofntEditPhoneAsk), () -> SofntEditors.editPhone(this));\n            }\n"
      "            if (position == phoneRow) {\n                if (userInfo != null && userInfo.phone_calls_available) {", M)
patch(PA, "            if (user == null || user.phone == null || user.phone.length() == 0 || getParentActivity() == null) {\n                return false;\n            }\n\n            if (position == phoneRow && user.phone.startsWith(\"888\")) {",
      "            if (user == null || user.phone == null || user.phone.length() == 0 || getParentActivity() == null || SofntConfig.isStreamerMode()) {\n                return false;\n            }\n\n            if (position == phoneRow && user.phone.startsWith(\"888\")) {", M)
patch(PA, "Uri.parse(\"tel:+\" + user.phone)", "Uri.parse(\"tel:+\" + sofntShownPhone(user))", M, count=1)
patch(PA, "android.content.ClipData.newPlainText(\"label\", \"+\" + user.phone)", "android.content.ClipData.newPlainText(\"label\", \"+\" + sofntShownPhone(user))", M)
patch(PA, "            textToCopy = user.phone;\n", "            textToCopy = sofntShownPhone(user);\n", M)

# additional usernames that exist only in SoFntGram
patch(PA, "                            usernames = user == null ? new ArrayList<>() : new ArrayList<>(user.usernames);\n",
      "                            usernames = user == null ? new ArrayList<>() : new ArrayList<>(user.usernames);\n"
      "                            if (user != null) {\n                                usernames = DisplayResolver.withFakeUsernames(user.id, user.id == getUserConfig().getClientUserId(), usernames);\n                            }\n", M)
patch(PA, "        public ClickableSpan makeUsernameLinkSpan(TLRPC.TL_username usernameObj) {\n            ClickableSpan span = usernameSpans.get(usernameObj);\n            if (span != null) return span;\n",
      "        public ClickableSpan makeUsernameLinkSpan(TLRPC.TL_username usernameObj) {\n            ClickableSpan span = usernameSpans.get(usernameObj);\n            if (span != null) return span;\n"
      "            if (usernameObj instanceof SofntUsername) {\n"
      "                final String fakeName = usernameObj.username;\n"
      "                span = new ClickableSpan() {\n                    @Override\n                    public void onClick(@NonNull View view) {\n"
      "                        long self = getUserConfig().getClientUserId();\n"
      "                        SofntValueSheet.show(ProfileActivity.this, SofntValueSheet.KIND_USERNAME, fakeName, null, userId, userId == self);\n"
      "                    }\n\n                    @Override\n                    public void updateDrawState(@NonNull TextPaint ds) {\n                        ds.setUnderlineText(false);\n                    }\n                };\n"
      "                usernameSpans.put(usernameObj, span);\n                return span;\n            }\n", M)

# helpers
patch(PA, "    private void updateRowsIds() {\n",
      "    /** SoFntGram: the number as the user sees it (changed in SoFntGram or the real one), used for calling and copying */\n"
      "    private String sofntShownPhone(TLRPC.User user) {\n"
      "        DisplayResolver.Value v = DisplayResolver.phone(user.id, user.id == UserConfig.getInstance(currentAccount).getClientUserId(), user.phone);\n"
      "        return v.text != null && !v.isHidden() ? v.text : user.phone;\n    }\n\n"
      "    /** SoFntGram: a tap on a number that was changed in SoFntGram opens the sheet instead of the usual menu */\n"
      "    private boolean sofntOnTap(int position) {\n"
      "        if (position != phoneRow && position != numberRow) {\n            return false;\n        }\n"
      "        TLRPC.User user = position == numberRow ? getUserConfig().getCurrentUser() : getMessagesController().getUser(userId);\n"
      "        if (user == null) {\n            return false;\n        }\n"
      "        boolean own = user.id == getUserConfig().getClientUserId();\n"
      "        DisplayResolver.Value v = DisplayResolver.phone(user.id, own, user.phone);\n"
      "        if (!v.isChanged()) {\n            return false;\n        }\n"
      "        SofntValueSheet.show(this, SofntValueSheet.KIND_PHONE, v.text, user.phone, user.id, own);\n"
      "        return true;\n    }\n\n"
      "    private void sofntRefresh() {\n        updateRowsIds();\n        if (listAdapter != null) {\n            listAdapter.notifyDataSetChanged();\n        }\n    }\n\n"
      "    private void updateRowsIds() {\n", M)

# app icons: SoFntGram first (colour and black-and-white), every icon selectable without Premium
LI = J + "ui/LauncherIconController.java"
patch(LI, 'DEFAULT("DefaultIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconDefault),',
      'DEFAULT("DefaultIcon", R.drawable.sofnt_icon_bg, R.mipmap.sofnt_icon_fg, R.string.SofntIconDefault),\n'
      '        SOFNT_MONO("SofntMonoIcon", R.drawable.sofnt_mono_bg, R.mipmap.sofnt_mono_fg, R.string.SofntIconMono),\n'
      '        SOFNT_MONO_LIGHT("SofntMonoLightIcon", R.drawable.sofnt_mono_light_bg, R.mipmap.sofnt_mono_light_fg, R.string.SofntIconMonoLight),', "SOFNT_MONO")
for key in ("AppIconPremium", "AppIconTurbo", "AppIconNox"):
    s = read(LI)
    write(LI, s.replace("R.string.%s, true)" % key, "R.string.%s)" % key))

MF = "TMessagesProj/src/main/AndroidManifest.xml"
alias = ('        <activity-alias\n            android:enabled="false"\n            android:name="org.telegram.messenger.%s"\n'
         '            android:targetActivity="org.telegram.ui.LaunchActivity"\n            android:icon="@mipmap/%s_launcher"\n'
         '            android:roundIcon="@mipmap/%s_launcher_round"\n            android:exported="true">\n\n'
         '            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n'
         '                <category android:name="android.intent.category.LAUNCHER" />\n'
         '                <category android:name="android.intent.category.MULTIWINDOW_LAUNCHER" />\n            </intent-filter>\n'
         '            <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />\n        </activity-alias>\n\n')
vintage = '        <activity-alias\n            android:enabled="false"\n            android:name="org.telegram.messenger.VintageIcon"'
patch(MF, vintage, alias % ("SofntMonoIcon", "sofnt_mono", "sofnt_mono") + alias % ("SofntMonoLightIcon", "sofnt_mono_light", "sofnt_mono_light") + vintage, "SofntMonoIcon")

print("prepare: done")
