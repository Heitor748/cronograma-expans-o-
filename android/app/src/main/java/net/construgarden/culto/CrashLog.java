package net.construgarden.culto;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

public class CrashLog {
    private static final String PREFS = "crashes";
    private static final String KEY = "last";

    public static void install(final Context ctx) {
        final Thread.UncaughtExceptionHandler orig = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try { save(ctx, "uncaught:" + t.getName(), e); } catch (Throwable ignored) {}
                if (orig != null) orig.uncaughtException(t, e);
            }
        });
    }

    public static void save(Context ctx, String where, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println(new Date().toString());
            pw.println("where: " + where);
            if (e != null) e.printStackTrace(pw);
            pw.flush();
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String prev = sp.getString(KEY, "");
            sp.edit().putString(KEY, sw.toString() + "\n===\n" + prev).apply();
        } catch (Throwable ignored) {}
    }

    public static String read(Context ctx) {
        try {
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        } catch (Throwable e) { return ""; }
    }

    public static void clear(Context ctx) {
        try { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply(); } catch (Throwable ignored) {}
    }
}
