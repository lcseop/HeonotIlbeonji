package com.heonotilbeonji.admin;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

final class PickupWidgetData {
    private PickupWidgetData() {}
    static SharedPreferences prefs(Context context) { return context.getSharedPreferences("pickup_widget", Context.MODE_PRIVATE); }
    static JSONArray sorted(JSONArray input) {
        ArrayList<JSONObject> rows = new ArrayList<>();
        for (int i = 0; i < input.length(); i++) {
            JSONObject item = input.optJSONObject(i);
            if (item != null && ("new".equals(item.optString("status")) || "contacted".equals(item.optString("status")))) rows.add(item);
        }
        rows.sort(Comparator.comparing((JSONObject row) -> dateKey(row.optString("date")))
            .thenComparingInt(row -> "new".equals(row.optString("status")) ? 0 : 1)
            .thenComparingLong(row -> row.optLong("created_at"))
            .thenComparing(row -> row.optString("id")));
        JSONArray result = new JSONArray();
        for (JSONObject row : rows) result.put(row);
        return result;
    }
    static String dateKey(String day) { return day.matches("\\d{4}-\\d{2}-\\d{2}") ? day : "9999-99-99"; }
    static String dateLabel(String day) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            parser.setLenient(false); parser.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            SimpleDateFormat label = new SimpleDateFormat("yy.MM.dd (E)", Locale.KOREA);
            label.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            return label.format(parser.parse(day));
        } catch (Exception ignored) { return "날짜 미정"; }
    }
    static synchronized JSONArray read(Context context) {
        if (context.getSharedPreferences("admin", Context.MODE_PRIVATE).getString("session", "").isEmpty()) return new JSONArray();
        try { return new JSONArray(prefs(context).getString("rows", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    static synchronized void store(Context context, JSONArray input) {
        JSONArray safe = new JSONArray();
        if (!context.getSharedPreferences("admin", Context.MODE_PRIVATE).getString("session", "").isEmpty()) {
            JSONArray rows = sorted(input);
            for (int i = 0; i < rows.length(); i++) try {
                JSONObject row = rows.getJSONObject(i);
                safe.put(new JSONObject().put("id", row.optString("id")).put("name", row.optString("name"))
                    .put("date", row.optString("date")).put("status", row.optString("status")));
            } catch (Exception ignored) { }
        }
        prefs(context).edit().putString("rows", safe.toString()).putLong("updated", System.currentTimeMillis())
            .putLong("revision", prefs(context).getLong("revision", 0) + 1).putString("error", "").apply();
        PickupWidgetProvider.renderAll(context);
    }
    static synchronized void clear(Context context) {
        prefs(context).edit().putString("rows", "[]").putLong("updated", 0)
            .putLong("revision", prefs(context).getLong("revision", 0) + 1).putString("error", "").apply();
        PickupWidgetProvider.renderAll(context);
    }
}
