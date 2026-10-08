package com.heonotilbeonji.admin;
import org.json.JSONArray;
import org.json.JSONObject;

/** Apply only acknowledged changes, preserving fields omitted by the editor. */
final class RequestCache {
    static JSONArray remove(JSONArray rows, String id) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && !id.equals(row.optString("id"))) result.put(row);
        }
        return result;
    }
    static JSONArray apply(JSONArray rows, JSONObject data) {
        String id = data.optString("requestId", data.optString("id"));
        JSONArray result = new JSONArray(); boolean found = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            if (id.equals(row.optString("id"))) { result.put(merge(row, data, id)); found = true; }
            else result.put(row);
        }
        if (!found && "create".equals(data.optString("action"))) result.put(merge(new JSONObject(), data, id));
        return result;
    }
    private static JSONObject merge(JSONObject original, JSONObject data, String id) {
        try {
            JSONObject row = new JSONObject(original.toString()); row.put("id", id);
            if ("create".equals(data.optString("action"))) {
                if (!row.has("created_at")) row.put("created_at", System.currentTimeMillis());
                if (!row.has("status")) row.put("status", "new");
            }
            String[] fields = "adminDetails".equals(data.optString("action")) ?
                new String[]{"reservedTime", "adminNote"} : data.has("status") ? new String[]{"status"} :
                new String[]{"name", "phone", "address", "amount", "date", "timeSlot", "pickupMethod", "message"};
            for (String field : fields) if (data.has(field)) row.put(field, data.get(field));
            if (data.has("customerFlag")) row.put("customerFlag", data.getString("customerFlag"));
            if (data.has("adminNote")) row.put("adminNoteUpdatedAt", System.currentTimeMillis());
            return row;
        } catch (Exception error) { throw new IllegalArgumentException(error); }
    }
}
