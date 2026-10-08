package com.heonotilbeonji.admin;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35}, qualifiers="w320dp-h800dp")
public class RequestListControlsTest {
 @Test public void everyStatusCombinationIncludingNoneWorks() throws Exception {
  String[] statuses={"new","contacted","done"};
  for(int mask=0;mask<=7;mask++) for(int i=0;i<3;i++)
   assertEquals((mask&(1<<i))!=0,AdminPlanner.matchesFilter(new JSONObject().put("status",statuses[i]),mask));
 }
 @Test public void presetChecksTwoStatesAndSortsTheirDatesWithUndatedLast() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
   MainActivity a=controller.get();
   JSONArray requests=new JSONArray().put(row("late","늦은 고객","new","2026-10-10",1))
    .put(row("early","빠른 고객","contacted","2026-10-08",2))
    .put(row("done","완료 고객","done","2026-10-07",3))
    .put(row("undated","날짜 미정","new","",4));
   set(a,"requests",requests); var show=MainActivity.class.getDeclaredMethod("showInbox");show.setAccessible(true);show.invoke(a);
   Spinner spinner=(Spinner)get(a,"sortControl"); spinner.setSelection(3); shadowOf(Looper.getMainLooper()).idle();
   assertEquals(3,get(a,"requestFilter")); assertEquals(3,get(a,"requestSort"));
   View filters=(View)get(a,"filterControls");
   assertTrue(((CheckBox)PlannerInteractionTest.text(filters,"새 신청")).isChecked());
   assertTrue(((CheckBox)PlannerInteractionTest.text(filters,"연락 완료")).isChecked());
   assertFalse(((CheckBox)PlannerInteractionTest.text(filters,"처리 완료")).isChecked());
   LinearLayout list=(LinearLayout)get(a,"list");
   assertNotNull(PlannerInteractionTest.text(list.getChildAt(0),"빠른 고객 님"));
   assertNotNull(PlannerInteractionTest.text(list.getChildAt(1),"늦은 고객 님"));
   assertNotNull(PlannerInteractionTest.text(list.getChildAt(2),"날짜 미정 님"));
   assertNull(PlannerInteractionTest.text(list,"완료 고객 님"));
   PlannerInteractionTest.text(filters,"처리 완료").performClick(); shadowOf(Looper.getMainLooper()).idle();
   assertEquals(7,get(a,"requestFilter")); assertEquals(2,get(a,"requestSort"));
   assertNotNull(PlannerInteractionTest.text(list.getChildAt(0),"완료 고객 님"));
   assertEquals(2,spinner.getSelectedItemPosition());
  }
 }
 @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
 @Config(sdk=35, qualifiers="w320dp-h800dp")
 @Test public void largeFontCheckboxesRemainReadableAndUpdateImmediately() {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
   MainActivity a=controller.get(); var config=new android.content.res.Configuration(a.getResources().getConfiguration());
   config.fontScale=1.8f;a.getResources().updateConfiguration(config,a.getResources().getDisplayMetrics());
   AtomicInteger result=new AtomicInteger(7); RequestListControls.Filters filters=new RequestListControls.Filters(a,7,result::set);
   // Explicit size also exercises overflow on runtimes that do not emulate font scaling.
   for(String name:new String[]{"새 신청","연락 완료","처리 완료"})
    ((CheckBox)PlannerInteractionTest.text(filters,name)).setTextSize(24);
   int width=AdminPlanner.dp(a,288);
   filters.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
   filters.layout(0,0,width,filters.getMeasuredHeight());
   for(String name:new String[]{"새 신청","연락 완료","처리 완료"}) {
    CheckBox chip=(CheckBox)PlannerInteractionTest.text(filters,name);
    assertEquals(1,chip.getLineCount());assertEquals(0,chip.getLayout().getEllipsisCount(0));
    assertTrue(chip.getWidth()-chip.getCompoundPaddingLeft()-chip.getCompoundPaddingRight()>=chip.getPaint().measureText(name));
   }
   View first=PlannerInteractionTest.text(filters,"새 신청");
   assertEquals(first.getTop(),PlannerInteractionTest.text(filters,"연락 완료").getTop());
   assertEquals(first.getTop(),PlannerInteractionTest.text(filters,"처리 완료").getTop());
   assertTrue(filters.getChildAt(0).getWidth()>filters.getWidth());
   PlannerInteractionTest.text(filters,"처리 완료").performClick();assertEquals(3,result.get());
   PlannerInteractionTest.text(filters,"새 신청").performClick();assertEquals(2,result.get());
   PlannerInteractionTest.text(filters,"연락 완료").performClick();assertEquals(0,result.get());
  }
 }
 @Test public void combinedFilterCountsOnlySelectedStatesInCalendar() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
   var month=AdminPlanner.calendar();month.set(2026,java.util.Calendar.OCTOBER,1);
   JSONArray requests=new JSONArray().put(row("n","새","new","2026-10-08",1))
    .put(row("c","연락","contacted","2026-10-08",2)).put(row("d","완료","done","2026-10-08",3));
   View calendar=AdminPlanner.month(controller.get(),month,"2026-10-08",requests,3,new JSONArray(),d->{},m->{});
   assertNotNull(PlannerInteractionTest.description(calendar,"26년 10월 8일 (목), 신청 2건"));
  }
 }
 @Test public void choicesSurviveAnActivityRestartWithoutSavedState() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
   MainActivity a=controller.get();
   var show=MainActivity.class.getDeclaredMethod("showInbox");show.setAccessible(true);show.invoke(a);
   Spinner spinner=(Spinner)get(a,"sortControl");spinner.setSelection(3);shadowOf(Looper.getMainLooper()).idle();
   PlannerInteractionTest.text((View)get(a,"filterControls"),"새 신청").performClick();
   PlannerInteractionTest.text((View)get(a,"root"),"메뉴 접기").performClick();
   set(a,"memoPage",true);show.invoke(a);spinner=(Spinner)get(a,"sortControl");spinner.setSelection(2);shadowOf(Looper.getMainLooper()).idle();
   try(var restarted=Robolectric.buildActivity(MainActivity.class).setup()) {
    MainActivity fresh=restarted.get();
    assertEquals(2,get(fresh,"requestFilter"));assertEquals(2,get(fresh,"requestSort"));
    assertEquals(2,get(fresh,"memoSort"));assertEquals(true,get(fresh,"menuCollapsed"));
   }
  }
 }
 private static JSONObject row(String id,String name,String status,String date,int created)throws Exception{
  return new JSONObject().put("id",id).put("name",name).put("status",status).put("date",date).put("created_at",created);
 }
 private static Object get(MainActivity a,String name)throws Exception{var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
 private static void set(MainActivity a,String name,Object value)throws Exception{var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);}
}
