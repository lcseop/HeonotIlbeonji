package com.heonotilbeonji.admin;
import android.content.Context;

final class PickupWidgetSettings {
    private PickupWidgetSettings() {}
    static int transparency(Context context, int id) {
        return Math.max(0, Math.min(100, context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE).getInt("transparency_" + id, 0)));
    }
    static int alpha(Context context, int id) { return Math.round(255 * (100 - transparency(context, id)) / 100f); }
    static void save(Context context, int id, int value) {
        context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE).edit().putInt("transparency_" + id, Math.max(0, Math.min(100, value))).apply();
    }
    static void remove(Context context, int id) { context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE).edit().remove("transparency_" + id).apply(); }
}
