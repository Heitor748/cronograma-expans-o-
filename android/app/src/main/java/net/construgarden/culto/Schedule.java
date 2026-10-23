package net.construgarden.culto;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Mantém a cópia local do cronograma e computa o momento atual/próximo.
 * O JS do site empurra os dados via JsBridge.setSchedule(json).
 */
public class Schedule {

    public static final String PREFS = "cronograma";
    public static final String KEY_DATA = "data";
    public static final String KEY_NOME = "nome";

    public static class Momento {
        public String id;
        public String atividade = "";
        public String responsavel = "";
        public long startMs;         // Long.MIN_VALUE se inválido
        public long endMs;           // Long.MIN_VALUE se sem fim definido
        public boolean mine;
    }

    public static class Snapshot {
        public String cultoLabel = "";
        public String state = "none"; // none|antes|agora|entre|depois
        public Momento now;           // atual (null se antes/entre/depois/none)
        public Momento next;          // próximo (ou null)
        public long nowMs;
    }

    public static void save(Context ctx, String json) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_DATA, json == null ? "" : json).apply();
    }

    public static void saveNome(Context ctx, String nome) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_NOME, nome == null ? "" : nome).apply();
    }

    public static String loadNome(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_NOME, "");
    }

    public static Snapshot compute(Context ctx) {
        Snapshot s = new Snapshot();
        s.nowMs = System.currentTimeMillis();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_DATA, "");
        String nome = sp.getString(KEY_NOME, "").toLowerCase(Locale.ROOT).trim();
        if (raw == null || raw.isEmpty()) return s;

        try {
            JSONArray cultos = new JSONArray(raw);
            JSONObject active = pickActiveCulto(cultos, s.nowMs);
            if (active == null) return s;

            s.cultoLabel = labelOf(active);
            List<Momento> ms = parseMomentos(active, nome);
            if (ms.isEmpty()) return s;

            Collections.sort(ms, new Comparator<Momento>() {
                @Override public int compare(Momento a, Momento b) {
                    return Long.compare(a.startMs, b.startMs);
                }
            });

            long first = ms.get(0).startMs;
            long last = ms.get(ms.size() - 1).startMs;
            long cultoEnd = last + 60L * 60L * 1000L;

            if (s.nowMs < first) {
                s.state = "antes";
                s.next = ms.get(0);
                return s;
            }
            if (s.nowMs > cultoEnd) {
                s.state = "depois";
                return s;
            }
            int agoraIdx = -1;
            for (int i = 0; i < ms.size(); i++) {
                Momento m = ms.get(i);
                long end;
                if (m.endMs != Long.MIN_VALUE) end = m.endMs;
                else if (i + 1 < ms.size()) end = ms.get(i + 1).startMs;
                else end = m.startMs + 30L * 60L * 1000L;
                if (s.nowMs >= m.startMs && s.nowMs < end) { agoraIdx = i; break; }
            }
            if (agoraIdx >= 0) {
                s.state = "agora";
                s.now = ms.get(agoraIdx);
                if (agoraIdx + 1 < ms.size()) s.next = ms.get(agoraIdx + 1);
            } else {
                s.state = "entre";
                for (Momento m : ms) if (m.startMs > s.nowMs) { s.next = m; break; }
            }
        } catch (Exception ignored) {
            // JSON inválido = sem snapshot
        }
        return s;
    }

    private static JSONObject pickActiveCulto(JSONArray arr, long now) throws Exception {
        List<JSONObject> sorted = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) sorted.add(arr.getJSONObject(i));
        Collections.sort(sorted, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                return a.optString("data").compareTo(b.optString("data"));
            }
        });
        for (JSONObject c : sorted) {
            long lastMs = Long.MIN_VALUE;
            JSONArray mo = c.optJSONArray("momentos");
            if (mo == null) continue;
            for (int i = 0; i < mo.length(); i++) {
                long t = toMillis(c.optString("data"), mo.getJSONObject(i).optString("hora"));
                if (t != Long.MIN_VALUE && t > lastMs) lastMs = t;
            }
            if (lastMs == Long.MIN_VALUE) continue;
            if (now < lastMs + 60L * 60L * 1000L) return c;
        }
        return null;
    }

    private static List<Momento> parseMomentos(JSONObject culto, String nomeLower) throws Exception {
        List<Momento> out = new ArrayList<>();
        JSONArray mo = culto.optJSONArray("momentos");
        if (mo == null) return out;
        String data = culto.optString("data");
        for (int i = 0; i < mo.length(); i++) {
            JSONObject m = mo.getJSONObject(i);
            long start = toMillis(data, m.optString("hora"));
            if (start == Long.MIN_VALUE) continue;
            Momento mm = new Momento();
            mm.id = m.optString("id");
            mm.atividade = m.optString("atividade", "");
            mm.responsavel = m.optString("responsavel", "");
            mm.startMs = start;
            mm.endMs = toMillis(data, m.optString("hora_fim"));
            if (!nomeLower.isEmpty()) {
                String resp = mm.responsavel.toLowerCase(Locale.ROOT);
                for (String p : resp.split("[\\r\\n,]+")) {
                    if (p.trim().contains(nomeLower)) { mm.mine = true; break; }
                }
            }
            out.add(mm);
        }
        return out;
    }

    private static long toMillis(String yyyyMmDd, String hhMm) {
        if (yyyyMmDd == null || hhMm == null) return Long.MIN_VALUE;
        if (hhMm.length() < 4) return Long.MIN_VALUE;
        try {
            String[] d = yyyyMmDd.split("-");
            String[] h = hhMm.split(":");
            if (d.length != 3 || h.length < 2) return Long.MIN_VALUE;
            Calendar c = Calendar.getInstance();
            c.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[2]),
                    Integer.parseInt(h[0]), Integer.parseInt(h[1]), 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    private static String labelOf(JSONObject c) {
        String data = c.optString("data");
        String tema = c.optString("tema", "");
        return (tema.isEmpty() ? data : (data + " · " + tema));
    }

    public static String hhmm(long ms) {
        if (ms == Long.MIN_VALUE) return "";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(Locale.ROOT, "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    public static String firstResp(String resp) {
        if (resp == null || resp.isEmpty()) return "";
        String[] parts = resp.split("[\\r\\n,]+");
        return parts.length > 0 ? parts[0].trim() : "";
    }
}
