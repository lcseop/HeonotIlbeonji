package com.heonotilbeonji.admin;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.os.Build;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Read-only market references, separate from the operator's own collection prices. */
public final class MetalPricesActivity extends Activity {
    private static final int NAVY=0xff18334e, MUTED=0xff52687b, BLUE=0xff346fae;
    private LinearLayout body, loading;
    private TextView status;
    private ImageView reload;
    private LoadingTasks io;
    private boolean pending;
    private JSONObject data=new JSONObject();
    private String failure="";
    private int dp(int n) { return AdminPlanner.dp(this,n); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        if (getSharedPreferences("admin",MODE_PRIVATE).getString("session","").isEmpty()) { finish(); return; }
        LinearLayout root=AdminPlanner.column(this); root.setBackgroundColor(0xfff0f4f8);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top=Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.statusBars()).top:insets.getSystemWindowInsetTop();
            int bottom=Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            view.setPadding(0,top,0,bottom); return insets;
        });
        setContentView(root); root.requestApplyInsets();
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(8),dp(6),dp(8),dp(6)); header.setBackgroundColor(Color.WHITE);
        ImageView back=AdminPlanner.iconButton(this,R.drawable.ic_material_chevron_left,"시세 화면 닫기");
        header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48))); back.setOnClickListener(v->finish());
        header.addView(AdminPlanner.text(this,"금속 시세",22,NAVY,true),new LinearLayout.LayoutParams(0,-2,1));
        reload=AdminPlanner.iconButton(this,R.drawable.ic_material_refresh,"시세 새로고침");
        header.addView(reload,new LinearLayout.LayoutParams(dp(48),dp(48))); reload.setOnClickListener(v->refresh()); root.addView(header);
        loading=LoadingTasks.indicator(this,"시세를 확인하는 중…"); loading.setPadding(dp(20),dp(8),dp(20),dp(8)); root.addView(loading);
        ScrollView scroll=new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=AdminPlanner.column(this); body.setPadding(dp(16),dp(18),dp(16),dp(24)); scroll.addView(body);
        io=new LoadingTasks(this,n->loading.setVisibility(n>0?View.VISIBLE:View.GONE));
        try { data=new JSONObject(getSharedPreferences("metal_prices",MODE_PRIVATE).getString("last", "{}")); } catch(Exception ignored) { }
        if (data.has("feeds")) failure="이전에 확인한 가격 · 최신 시세 확인 중";
        render(); refresh();
    }
    @Override public void onDestroy() { if(io!=null)io.shutdownNow(); super.onDestroy(); }
    private void refresh() {
        if(pending)return;
        pending=true; reload.setEnabled(false);
        final String token=getSharedPreferences("admin",MODE_PRIVATE).getString("session","");
        status.setText("최신 고시 가격을 확인하고 있어요…");
        io.execute(()->{
            try {
                JSONObject result=ApiClient.request(this,"GET","/api/admin/native-metal-prices",null,token);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(!token.equals(getSharedPreferences("admin",MODE_PRIVATE).getString("session",""))) {finish();return;}
                    data=result; failure="";
                    getSharedPreferences("metal_prices",MODE_PRIVATE).edit().putString("last",result.toString()).apply(); render();
                });
            } catch(Exception error) {
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(error instanceof ApiClient.ApiException && ((ApiClient.ApiException)error).status==401) { Toast.makeText(this,"다시 로그인해 주세요.",Toast.LENGTH_LONG).show(); finish(); return; }
                    failure=data.has("feeds")?"연결 실패 · 이전에 확인한 가격입니다":"시세 연결 실패 · 새로고침을 눌러 주세요"; render();
                });
            } finally {runOnUiThread(()->{pending=false;if(reload!=null)reload.setEnabled(true);});}
        });
    }
    private void render() {
        body.removeAllViews();
        status=AdminPlanner.text(this,failure.isEmpty()?"공개 고시가 · 15분 간격으로 조회":failure,13,failure.isEmpty()?MUTED:0xffb34535,false);
        body.addView(status);
        JSONArray feeds=data.optJSONArray("feeds");
        for(String name:new String[]{"금","은","동","스텐","알루미늄"}) {
            LinearLayout card=AdminPlanner.column(this); card.setPadding(dp(18),dp(16),dp(18),dp(16)); card.setBackground(AdminPlanner.bg(this,Color.WHITE,16));
            int color="금".equals(name)?0xff946a14:"동".equals(name)?0xffa7512f:"알루미늄".equals(name)?BLUE:0xff466078;
            LinearLayout title=new LinearLayout(this); title.setGravity(Gravity.CENTER_VERTICAL);
            TextView symbol=AdminPlanner.text(this,"금".equals(name)?"Au":"은".equals(name)?"Ag":"동".equals(name)?"Cu":"스텐".equals(name)?"STS":"Al",16,color,true);
            symbol.setGravity(Gravity.CENTER); symbol.setBackground(AdminPlanner.bg(this,0xffeff3f7,10)); title.addView(symbol,new LinearLayout.LayoutParams(dp(48),dp(44)));
            TextView heading=AdminPlanner.text(this,name,20,NAVY,true); heading.setPadding(dp(12),0,0,0); title.addView(heading); card.addView(title);
            JSONObject source=null;
            if(feeds!=null) for(int i=0;i<feeds.length();i++) {
                JSONObject feed=feeds.optJSONObject(i); if(feed==null)continue; JSONArray quotes=feed.optJSONArray("quotes"); if(quotes==null)continue;
                for(int j=0;j<quotes.length();j++) {
                    JSONObject quote=quotes.optJSONObject(j); if(quote==null||!name.equals(quote.optString("name")))continue;
                    source=feed; card.addView(AdminPlanner.text(this,quote.optString("grade"),13,MUTED,false),AdminPlanner.space(this,14));
                    TextView price=AdminPlanner.text(this,NumberFormat.getIntegerInstance(Locale.KOREA).format(quote.optDouble("price"))+" 원",26,color,true);
                    card.addView(price,AdminPlanner.space(this,3)); card.addView(AdminPlanner.text(this,quote.optString("unit").replace("원 / ","" )+" 기준",12,MUTED,false));
                }
            }
            if(source==null) { card.addView(AdminPlanner.text(this,"시세 확인 중 또는 조회 불가",14,MUTED,false),AdminPlanner.space(this,16)); }
            else {
                card.addView(AdminPlanner.text(this,"고시일 "+source.optString("asOf")+" · "+source.optString("source"),12,MUTED,false),AdminPlanner.space(this,14));
                card.addView(AdminPlanner.text(this,source.optString("basis"),12,MUTED,false),AdminPlanner.space(this,4));
                long fetched=source.optLong("fetchedAt");
                if(fetched>0) { SimpleDateFormat format=new SimpleDateFormat("yy.MM.dd HH:mm",Locale.KOREA);format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));card.addView(AdminPlanner.text(this,"확인 "+format.format(new Date(fetched)),12,MUTED,false),AdminPlanner.space(this,4)); }
                if(source.optBoolean("stale"))card.addView(AdminPlanner.text(this,source.optString("error","이전 가격입니다"),13,0xffb34535,true),AdminPlanner.space(this,6));
            }
            final String url=source!=null?source.optString("url"):"금".equals(name)||"은".equals(name)?"https://www.koreagoldx.co.kr/price/gold":"https://allmetal.co.kr/";
            LinearLayout link=new LinearLayout(this); link.setGravity(Gravity.CENTER_VERTICAL);
            link.setPadding(dp(12),dp(10),dp(12),dp(10)); link.setMinimumHeight(dp(48));
            link.setBackground(AdminPlanner.bg(this,0xffeff4fa,12)); link.setFocusable(true); link.setContentDescription(name+" 시세 출처에서 확인");
            ImageView linkIcon=new ImageView(this); var linkDrawable=getDrawable(R.drawable.ic_material_trending_up).mutate(); linkDrawable.setTint(BLUE); linkIcon.setImageDrawable(linkDrawable);
            link.addView(linkIcon,new LinearLayout.LayoutParams(dp(22),dp(22)));
            TextView linkTitle=AdminPlanner.text(this,"출처에서 확인",14,BLUE,true);linkTitle.setPadding(dp(8),0,0,0);link.addView(linkTitle,new LinearLayout.LayoutParams(0,-2,1));
            link.setOnClickListener(v->{try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){Toast.makeText(this,"브라우저를 찾지 못했습니다.",Toast.LENGTH_SHORT).show();}});
            card.addView(link,AdminPlanner.space(this,12)); body.addView(card,AdminPlanner.space(this,14));
        }
        body.addView(AdminPlanner.text(this,"공개 가격은 참고용입니다. 실제 매입가는 순도·등급·상태·물량·운송 조건에 따라 달라집니다.",13,MUTED,false),AdminPlanner.space(this,16));
    }
}
