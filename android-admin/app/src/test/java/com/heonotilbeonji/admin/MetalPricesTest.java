package com.heonotilbeonji.admin;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import org.json.JSONArray;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35})
public class MetalPricesTest {
 private String text(View view){StringBuilder s=new StringBuilder();if(view instanceof TextView)s.append(((TextView)view).getText()).append('\n');if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)s.append(text(((ViewGroup)view).getChildAt(i)));return s.toString();}
 @Test public void cachedPricesHaveAllMetalsDatesUnitsAndStaleLabels()throws Exception {
  Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("admin",0).edit().putString("session","test").putString("api_base_url","http://127.0.0.1:9").commit();
  JSONArray quotes=new JSONArray();for(String name:new String[]{"금","은","동","스텐","알루미늄"})quotes.put(new JSONObject().put("name",name).put("grade",name.equals("스텐")?"STS304":"기준 품목").put("price",12345).put("unit",name.equals("금")||name.equals("은")?"원 / 1돈 (3.75g)":"원 / kg"));
  JSONObject feed=new JSONObject().put("quotes",quotes).put("asOf","2026-10-13").put("source","시험 출처").put("basis","도착도 · 부가세 별도").put("stale",true).put("error","조회 실패 · 이전에 확인한 가격입니다").put("url","https://allmetal.co.kr/");
  context.getSharedPreferences("metal_prices",0).edit().putString("last",new JSONObject().put("feeds",new JSONArray().put(feed)).toString()).commit();
  try(var controller=Robolectric.buildActivity(MetalPricesActivity.class).setup()) {
   var a=controller.get();String displayed=text(a.findViewById(android.R.id.content));for(String name:new String[]{"금","은","동","스텐","알루미늄"})assertTrue(displayed.contains(name));
   assertTrue(displayed.contains("12,345 원"));assertTrue(displayed.contains("3.75g"));assertTrue(displayed.contains("kg 기준"));assertTrue(displayed.contains("2026-10-13"));assertTrue(displayed.contains("이전에 확인한 가격입니다"));
   View close=PlannerInteractionTest.description(a.findViewById(android.R.id.content),"시세 화면 닫기");assertNotNull(close);close.performClick();assertTrue(a.isFinishing());
  }finally{context.getSharedPreferences("admin",0).edit().clear().commit();context.getSharedPreferences("metal_prices",0).edit().clear().commit();}
 }
 @Test public void cannotOpenWithoutAdminLogin(){RuntimeEnvironment.getApplication().getSharedPreferences("admin",0).edit().clear().commit();try(var controller=Robolectric.buildActivity(MetalPricesActivity.class).create()){assertTrue(controller.get().isFinishing());}}
}
