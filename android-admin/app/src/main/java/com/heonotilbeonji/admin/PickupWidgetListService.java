package com.heonotilbeonji.admin;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import org.json.JSONArray;

public final class PickupWidgetListService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private JSONArray rows = new JSONArray();
            public void onCreate() { onDataSetChanged(); }
            public void onDataSetChanged() { rows = PickupWidgetData.read(PickupWidgetListService.this); }
            public void onDestroy() { rows = new JSONArray(); }
            public int getCount() { return Math.min(rows.length(), 100); }
            public RemoteViews getViewAt(int position) { return position >= 0 && position < getCount() ? PickupWidgetProvider.row(PickupWidgetListService.this, rows.optJSONObject(position)) : null; }
            public RemoteViews getLoadingView() { return null; }
            public int getViewTypeCount() { return 1; }
            public long getItemId(int position) { return position; }
            public boolean hasStableIds() { return false; }
        };
    }
}
