package com.heonotilbeonji.admin;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;

final class CustomerFlags {
    static String label(String flag) { return "regular".equals(flag) ? "단골" : "blacklist".equals(flag) ? "블랙리스트" : "표시 없음"; }
    static int icon(String flag) { return "regular".equals(flag) ? R.drawable.ic_customer_star : "blacklist".equals(flag) ? R.drawable.ic_customer_block : R.drawable.ic_material_person; }
    static int color(String flag) { return "regular".equals(flag) ? Color.rgb(171, 115, 12) : "blacklist".equals(flag) ? Color.rgb(194, 54, 54) : Color.rgb(100, 122, 140); }
    static JSONArray apply(JSONArray rows, String phone, String flag) {
        JSONArray updated = new JSONArray(); String normalized = AdminPlanner.normalizePhone(phone);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            try {
                JSONObject copy = new JSONObject(row.toString());
                if (normalized.equals(AdminPlanner.normalizePhone(row.optString("phone")))) copy.put("customerFlag", flag);
                updated.put(copy);
            } catch (Exception error) { throw new IllegalArgumentException(error); }
        }
        return updated;
    }
}
