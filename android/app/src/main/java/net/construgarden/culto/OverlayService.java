package net.construgarden.culto;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Locale;

public class OverlayService extends Service {

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP  = "stop";
    public static final String ACTION_TICK  = "tick";

    private static final String CHANNEL = "cronograma-live";
    private static final int NOTIF_ID = 1001;

    private static boolean running = false;
    public static boolean isRunning() { return running; }

    private WindowManager wm;
    private View bubble;
    private Handler handler;
    private Runnable ticker;
    private String lastMineAnnouncedId = "";
    private boolean preAnnouncedForId = false;

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) { stopEverything(); return START_NOT_STICKY; }

        if (!running) {
            running = true;
            android.app.Notification n = buildNotification("Cronograma ao vivo", "Preparando...");
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    startForeground(NOTIF_ID, n);
                }
            } catch (Throwable t) {
                try { startForeground(NOTIF_ID, n); } catch (Throwable ignored) {}
            }
            attachBubble();
            scheduleTicks();
        }
        tick();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        detachBubble();
        if (handler != null && ticker != null) handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    private void stopEverything() {
        running = false;
        detachBubble();
        if (handler != null && ticker != null) handler.removeCallbacks(ticker);
        if (Build.VERSION.SDK_INT >= 24) stopForeground(Service.STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.channel_live), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setShowBadge(false);
        ch.setDescription("Mostra o momento atual e o próximo do culto.");
        AudioAttributes aa = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).build();
        Uri sound = Settings.System.DEFAULT_NOTIFICATION_URI;
        ch.setSound(sound, aa);
        nm.createNotificationChannel(ch);
    }

    private android.app.Notification buildNotification(String title, String body) {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp, flags);

        Intent stop = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent piStop = PendingIntent.getService(this, 1, stop, flags);

        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setOngoing(true)
                .setContentIntent(pi)
                .addAction(0, "Parar", piStop)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    private void updateNotification(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(title, body));
    }

    private void scheduleTicks() {
        ticker = new Runnable() {
            @Override public void run() {
                tick();
                handler.postDelayed(this, 15_000L);
            }
        };
        handler.postDelayed(ticker, 15_000L);
    }

    private int touchX, touchY;
    private int paramX, paramY;

    private void attachBubble() {
        if (bubble != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            // Sem permissão de overlay — só mantém a notificação fixa.
            return;
        }
        bubble = LayoutInflater.from(this).inflate(R.layout.overlay, null);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 24;
        lp.y = 120;

        bubble.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchX = (int) e.getRawX(); touchY = (int) e.getRawY();
                        paramX = lp.x; paramY = lp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = paramX + ((int) e.getRawX() - touchX);
                        lp.y = paramY + ((int) e.getRawY() - touchY);
                        wm.updateViewLayout(bubble, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        return true;
                }
                return false;
            }
        });

        bubble.findViewById(R.id.close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(OverlayService.this, OverlayService.class).setAction(ACTION_STOP);
                startService(i);
            }
        });

        try { wm.addView(bubble, lp); } catch (Exception ignored) {}
    }

    private void detachBubble() {
        try { if (bubble != null) wm.removeView(bubble); } catch (Exception ignored) {}
        bubble = null;
    }

    private void tick() {
        Schedule.Snapshot s = Schedule.compute(this);
        String title; String body;

        if ("agora".equals(s.state) && s.now != null) {
            String faixa = Schedule.hhmm(s.now.startMs)
                    + (s.now.endMs != Long.MIN_VALUE ? " – " + Schedule.hhmm(s.now.endMs) : "");
            title = "⏱ AGORA • " + s.now.atividade;
            StringBuilder sb = new StringBuilder(faixa);
            if (!s.now.responsavel.isEmpty()) sb.append(" · ").append(Schedule.firstResp(s.now.responsavel));
            if (s.next != null) sb.append("\nA seguir ").append(Schedule.hhmm(s.next.startMs))
                    .append(" · ").append(s.next.atividade)
                    .append(s.next.responsavel.isEmpty() ? "" : (" · " + Schedule.firstResp(s.next.responsavel)));
            body = sb.toString();
        } else if ("antes".equals(s.state) && s.next != null) {
            long diff = Math.max(0, s.next.startMs - s.nowMs);
            long mins = diff / 60000L;
            title = "Começa em " + formatDuration(mins) + " • " + s.next.atividade;
            body = Schedule.hhmm(s.next.startMs)
                    + (s.next.responsavel.isEmpty() ? "" : (" · " + Schedule.firstResp(s.next.responsavel)));
        } else if ("entre".equals(s.state) && s.next != null) {
            title = "Intervalo • Próximo em " + Math.max(0, (s.next.startMs - s.nowMs) / 60000L) + " min";
            body = s.next.atividade + " · " + Schedule.hhmm(s.next.startMs);
        } else if ("depois".equals(s.state)) {
            title = "Culto encerrado";
            body = "Até o próximo!";
        } else {
            title = "Cronograma ao vivo";
            body = "Nenhum culto próximo encontrado.";
        }
        if (!s.cultoLabel.isEmpty()) body = body + "\n" + s.cultoLabel;

        updateNotification(title, body);
        updateBubble(s);
        maybeFireAlerts(s);
    }

    private void maybeFireAlerts(Schedule.Snapshot s) {
        // AGORA e é seu → alerta "agora"
        if ("agora".equals(s.state) && s.now != null && s.now.mine) {
            if (!lastMineAnnouncedId.equals(s.now.id)) {
                lastMineAnnouncedId = s.now.id;
                preAnnouncedForId = false;
                buzz();
            }
        }
        // PRÓXIMO é seu e falta <=2min → pré-aviso
        if (s.next != null && s.next.mine) {
            long diff = s.next.startMs - s.nowMs;
            if (diff > 0 && diff <= 2L * 60L * 1000L) {
                if (!preAnnouncedForId || !lastMineAnnouncedId.equals(s.next.id)) {
                    preAnnouncedForId = true;
                    lastMineAnnouncedId = s.next.id;
                    buzz();
                }
            }
        }
    }

    private void buzz() {
        try {
            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib != null) {
                if (Build.VERSION.SDK_INT >= 26) {
                    vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 180, 90, 180}, -1));
                } else {
                    vib.vibrate(new long[]{0, 180, 90, 180}, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    private void updateBubble(Schedule.Snapshot s) {
        if (bubble == null) return;
        TextView label = bubble.findViewById(R.id.label);
        TextView title = bubble.findViewById(R.id.title);
        TextView sub = bubble.findViewById(R.id.subtitle);
        TextView resp = bubble.findViewById(R.id.responsavel);
        TextView hl = bubble.findViewById(R.id.highlight);

        resp.setVisibility(View.GONE);
        hl.setVisibility(View.GONE);

        if ("agora".equals(s.state) && s.now != null) {
            label.setText("⏱ AGORA");
            title.setText(s.now.atividade);
            String faixa = Schedule.hhmm(s.now.startMs)
                    + (s.now.endMs != Long.MIN_VALUE ? " – " + Schedule.hhmm(s.now.endMs) : "");
            String nxt = s.next != null ? "   •   A seguir " + Schedule.hhmm(s.next.startMs) + " " + s.next.atividade : "";
            sub.setText(faixa + nxt);
            if (!s.now.responsavel.isEmpty()) {
                resp.setText("→ " + Schedule.firstResp(s.now.responsavel));
                resp.setVisibility(View.VISIBLE);
            }
            if (s.now.mine) { hl.setText("⚡ VOCÊ ESTÁ NESSE MOMENTO"); hl.setVisibility(View.VISIBLE); }
            else if (s.next != null && s.next.mine) { hl.setText("⚡ VOCÊ É O PRÓXIMO"); hl.setVisibility(View.VISIBLE); }
        } else if ("antes".equals(s.state) && s.next != null) {
            long mins = Math.max(0, (s.next.startMs - s.nowMs) / 60000L);
            label.setText("COMEÇA EM " + formatDuration(mins));
            title.setText(s.next.atividade);
            sub.setText(Schedule.hhmm(s.next.startMs));
            if (!s.next.responsavel.isEmpty()) {
                resp.setText("→ " + Schedule.firstResp(s.next.responsavel));
                resp.setVisibility(View.VISIBLE);
            }
            if (s.next.mine) { hl.setText("⚡ VOCÊ É O PRÓXIMO"); hl.setVisibility(View.VISIBLE); }
        } else if ("entre".equals(s.state) && s.next != null) {
            label.setText("INTERVALO");
            title.setText("Próximo: " + s.next.atividade);
            sub.setText(Schedule.hhmm(s.next.startMs));
            if (s.next.mine) { hl.setText("⚡ VOCÊ É O PRÓXIMO"); hl.setVisibility(View.VISIBLE); }
        } else if ("depois".equals(s.state)) {
            label.setText("ENCERRADO");
            title.setText("Culto encerrado");
            sub.setText("Até o próximo!");
        } else {
            label.setText("SEM CULTO");
            title.setText("Nenhum culto cadastrado");
            sub.setText("");
        }
    }

    private String formatDuration(long mins) {
        long h = mins / 60; long m = mins % 60;
        if (h > 0) return String.format(Locale.ROOT, "%dh %02dmin", h, m);
        return String.format(Locale.ROOT, "%d min", m);
    }
}
