package com.ghoul.carlaunch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 存設定：每個藍牙裝置要開哪些 App */
public class Store {

    public static class Rule {
        public String address = "";
        public String name = "";
        public boolean enabled = true;
        public List<String> apps = new ArrayList<>();
    }

    private static final String DEV = "dev:";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("carlaunch", Context.MODE_PRIVATE);
    }

    private static Rule parse(String address, String json) {
        try {
            JSONObject o = new JSONObject(json);
            Rule r = new Rule();
            r.address = address;
            r.name = o.optString("name", address);
            r.enabled = o.optBoolean("enabled", true);
            JSONArray a = o.optJSONArray("apps");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) r.apps.add(a.getString(i));
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Rule> all(Context c) {
        List<Rule> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : sp(c).getAll().entrySet()) {
            if (e.getKey().startsWith(DEV) && e.getValue() instanceof String) {
                Rule r = parse(e.getKey().substring(DEV.length()), (String) e.getValue());
                if (r != null) out.add(r);
            }
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    public static Rule get(Context c, String address) {
        if (address == null) return null;
        String s = sp(c).getString(DEV + address, null);
        return s == null ? null : parse(address, s);
    }

    public static void save(Context c, Rule r) {
        try {
            JSONObject o = new JSONObject();
            o.put("name", r.name);
            o.put("enabled", r.enabled);
            JSONArray a = new JSONArray();
            for (String p : r.apps) a.put(p);
            o.put("apps", a);
            sp(c).edit().putString(DEV + r.address, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void delete(Context c, String address) {
        sp(c).edit().remove(DEV + address).remove("last:" + address).apply();
    }

    /** 每個 App 之間間隔幾秒 */
    public static int gapSec(Context c) {
        return sp(c).getInt("gap", 3);
    }

    public static void setGapSec(Context c, int v) {
        sp(c).edit().putInt("gap", Math.max(1, Math.min(15, v))).apply();
    }

    public static long lastRun(Context c, String address) {
        return sp(c).getLong("last:" + address, 0);
    }

    public static void setLastRun(Context c, String address, long t) {
        sp(c).edit().putLong("last:" + address, t).commit();
    }

    // ---- 簡單紀錄，方便查「為什麼沒開」 ----
    public static String log(Context c) {
        return sp(c).getString("log", "");
    }

    public static void log(Context c, String msg) {
        String time = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.TAIWAN).format(new Date());
        String old = log(c);
        String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(time).append("  ").append(msg);
        for (int i = 0; i < lines.length && i < 29; i++) sb.append("\n").append(lines[i]);
        sp(c).edit().putString("log", sb.toString()).apply();
    }

    public static void clearLog(Context c) {
        sp(c).edit().remove("log").apply();
    }
}
