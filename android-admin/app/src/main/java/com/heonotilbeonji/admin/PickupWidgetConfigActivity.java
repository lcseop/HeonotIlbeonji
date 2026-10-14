package com.heonotilbeonji.admin;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public final class PickupWidgetConfigActivity extends Activity {
    private int widgetId;
    private SeekBar slider;
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        getWindow().setStatusBarColor(0xff18334e);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(0xffeef3f7);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(24), dp(28), dp(24), dp(24)); scroll.addView(panel); setContentView(scroll);
        TextView title = text("위젯 설정", 24, 0xff18334e); title.setTypeface(null, 1); panel.addView(title);
        TextView help = text("배경 투명도를 조절해 주세요.\n글자와 아이콘은 그대로 선명하게 표시됩니다.", 15, 0xff43566a);
        help.setPadding(0, dp(12), 0, dp(24)); panel.addView(help);
        LinearLayout preview = new LinearLayout(this); preview.setOrientation(LinearLayout.VERTICAL); preview.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable background = new GradientDrawable(); background.setColor(0xfffafcfe); background.setCornerRadius(dp(20));
        preview.setBackground(background);
        TextView sampleTitle = text("헌옷일번지", 18, 0xff18334e); sampleTitle.setTypeface(null, 1); preview.addView(sampleTitle);
        TextView sample = text("수거 신청 미리보기\n26.10.15 (목)   ·   새 신청", 15, 0xff18334e); sample.setPadding(0, dp(20), 0, dp(12)); preview.addView(sample);
        panel.addView(preview, new LinearLayout.LayoutParams(-1, dp(170)));
        TextView value = text("", 18, 0xff18334e); value.setPadding(0, dp(26), 0, dp(12)); value.setTypeface(null, 1); panel.addView(value);
        slider = new SeekBar(this); slider.setMax(100); slider.setContentDescription("위젯 배경 투명도"); slider.setMinimumHeight(dp(52)); panel.addView(slider, new LinearLayout.LayoutParams(-1, dp(60)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) { value.setText("배경 투명도 " + progress + "%"); background.setAlpha(Math.round(255 * (100 - progress) / 100f)); }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        int initial = state == null ? PickupWidgetSettings.transparency(this, widgetId) : state.getInt("transparency");
        slider.setProgress(initial); value.setText("배경 투명도 " + initial + "%"); background.setAlpha(Math.round(255 * (100 - initial) / 100f));
        TextView hint = text("0%: 배경 보임     100%: 배경 완전 투명", 13, 0xff43566a); panel.addView(hint);
        TextView refresh = text("자동 갱신: 5분 간격 목표\n절전·네트워크 상태에 따라 갱신이 늦어질 수 있습니다. 새 신청 알림과 앱 수정 시에도 갱신됩니다.", 14, 0xff43566a);
        refresh.setPadding(0, dp(24), 0, dp(24)); panel.addView(refresh);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER); panel.addView(actions);
        Button cancel = new Button(this); cancel.setText("취소"); actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(56), 1)); cancel.setOnClickListener(view -> finish());
        Button save = new Button(this); save.setText("저장"); save.setTextColor(Color.WHITE); save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff18334e));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1); params.leftMargin = dp(12); actions.addView(save, params);
        save.setOnClickListener(view -> {
            PickupWidgetSettings.save(this, widgetId, slider.getProgress()); PickupWidgetProvider.renderAll(this); PickupWidgetProvider.requestRefresh(this);
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish();
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); if (slider != null) state.putInt("transparency", slider.getProgress()); }
}
