package com.heonotilbeonji.admin;
import android.view.View;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35})
public class CalendarMethodTest {
    @Test public void bothMethodsAreVisibleBeforeExpandingCalendarRequests() throws Exception {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a=controller.get(); String day=AdminPlanner.iso(AdminPlanner.calendar());
            JSONArray rows=new JSONArray();
            rows.put(new JSONObject().put("id","a").put("name","대면 고객").put("status","new").put("date",day)
                .put("pickupMethod","대면 수거").put("reservedTime","10:30"));
            rows.put(new JSONObject().put("id","b").put("name","비대면 고객").put("status","new").put("date",day)
                .put("pickupMethod","비대면 수거").put("timeSlot","오후").put("customerFlag","regular"));
            set(a,"requests",rows);set(a,"calendarPage",true);set(a,"selectedDay",day);
            var show=MainActivity.class.getDeclaredMethod("showInbox");show.setAccessible(true);show.invoke(a);
            View list=(View)get(a,"list");
            TextView face=(TextView)PlannerInteractionTest.description(list,"10:30 · 대면 수거");
            TextView contactless=(TextView)PlannerInteractionTest.description(list,"오후 · 비대면 수거");
            assertNotNull(face);assertNotNull(contactless);assertTrue(face.isShown());assertTrue(contactless.isShown());
            assertNotNull(face.getCompoundDrawables()[0]);assertNotNull(contactless.getCompoundDrawables()[0]);
            assertNotNull(PlannerInteractionTest.description(list,"단골"));
            ((View)face.getParent().getParent()).performClick();assertTrue(face.isShown());assertTrue(contactless.isShown());
        }
    }
    private static void set(MainActivity a,String name,Object value)throws Exception {var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);}
    private static Object get(MainActivity a,String name)throws Exception {var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
}
