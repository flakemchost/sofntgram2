package com.sofnt.messenger;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes the stack trace of an uncaught Java exception to Downloads/sofnt_crash_YYYYMMDD_HHMMSS.txt,
 * so a crash can be read on a phone without a computer. Installed as the very first thing at app start.
 * Native crashes (a signal inside a .so library) cannot be caught here and leave no file.
 */
public final class SofntCrashLog {

    private SofntCrashLog() {}

    public static void install(final Context context) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                write(context, thread, error);
            } catch (Throwable ignore) {
                // the crash report must never hide the real crash
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private static void write(Context context, Thread thread, Throwable error) throws Exception {
        StringWriter text = new StringWriter();
        PrintWriter out = new PrintWriter(text);
        out.println("SoFntGram crash");
        out.println("thread: " + (thread != null ? thread.getName() : "?"));
        out.println("android: " + Build.VERSION.RELEASE + " (sdk " + Build.VERSION.SDK_INT + "), device: " + Build.MANUFACTURER + " " + Build.MODEL);
        out.println();
        error.printStackTrace(out);
        out.flush();
        byte[] bytes = text.toString().getBytes("UTF-8");

        String name = "sofnt_crash_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream stream = context.getContentResolver().openOutputStream(uri)) {
                    if (stream != null) {
                        stream.write(bytes);
                    }
                }
                return;
            }
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        try (FileOutputStream stream = new FileOutputStream(new File(dir, name))) {
            stream.write(bytes);
        }
    }
}
