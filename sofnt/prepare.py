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

print("prepare: done")
