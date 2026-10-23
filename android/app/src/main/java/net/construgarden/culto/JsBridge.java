package net.construgarden.culto;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

/**
 * Objeto exposto para o JS do site via WebView.addJavascriptInterface.
 * O site chama window.CultoNative.* para empurrar dados e controlar a bolha.
 */
public class JsBridge {
    private final Context ctx;
    public JsBridge(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    @JavascriptInterface
    public void setSchedule(String json) {
        Schedule.save(ctx, json);
        pokeIfRunning();
    }

    @JavascriptInterface
    public void setNome(String nome) {
        Schedule.saveNome(ctx, nome);
        pokeIfRunning();
    }

    private void pokeIfRunning() {
        if (!OverlayService.isRunning()) return;
        try {
            Intent i = new Intent(ctx, OverlayService.class);
            i.setAction(OverlayService.ACTION_TICK);
            ctx.startService(i);
        } catch (Throwable ignored) {}
    }

    @JavascriptInterface
    public String getNome() {
        return Schedule.loadNome(ctx);
    }

    @JavascriptInterface
    public boolean isOverlayActive() {
        return OverlayService.isRunning();
    }

    @JavascriptInterface
    public void startOverlay() {
        try {
            Intent i = new Intent(ctx, OverlayService.class);
            i.setAction(OverlayService.ACTION_START);
            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Throwable ignored) {}
    }

    @JavascriptInterface
    public void stopOverlay() {
        Intent i = new Intent(ctx, OverlayService.class);
        i.setAction(OverlayService.ACTION_STOP);
        ctx.startService(i);
    }

    @JavascriptInterface
    public boolean hasOverlayPermission() {
        return android.os.Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx);
    }

    @JavascriptInterface
    public void requestOverlayPermission() {
        if (android.os.Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)) return;
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + ctx.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    @JavascriptInterface
    public String platform() { return "android-native"; }

    @JavascriptInterface
    public String getCrashLog() { return CrashLog.read(ctx); }

    @JavascriptInterface
    public void clearCrashLog() { CrashLog.clear(ctx); }
}
