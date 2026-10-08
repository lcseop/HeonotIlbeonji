package com.heonotilbeonji.admin;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35})
public class CustomerFlagsTest {
    @Test public void markingUpdatesAllSamePhoneRequestsAndCanBeCleared() throws Exception {
        JSONArray rows=new JSONArray().put(new JSONObject().put("id","a").put("phone","010-1234-5678"))
            .put(new JSONObject().put("id","b").put("phone","+82 10-1234-5678"))
            .put(new JSONObject().put("id","c").put("phone","01099999999"));
        JSONArray marked=CustomerFlags.apply(rows,"01012345678","regular");
        assertEquals("regular",marked.getJSONObject(0).getString("customerFlag"));
        assertEquals("regular",marked.getJSONObject(1).getString("customerFlag"));
        assertFalse(marked.getJSONObject(2).has("customerFlag"));
        assertFalse(rows.getJSONObject(0).has("customerFlag"));
        assertEquals("",CustomerFlags.apply(marked,"01012345678","").getJSONObject(0).getString("customerFlag"));
        assertNotEquals(CustomerFlags.icon("regular"),CustomerFlags.icon("blacklist"));
    }
    @Test public void changingARequestsPhoneUsesTheServerFlagRatherThanPreviousOwner() throws Exception {
        JSONArray rows=new JSONArray().put(new JSONObject().put("id","a").put("phone","01012345678").put("customerFlag","blacklist"));
        JSONObject edit=new JSONObject().put("requestId","a").put("action","edit").put("phone","01099999999").put("customerFlag","");
        assertEquals("",RequestCache.apply(rows,edit).getJSONObject(0).getString("customerFlag"));
    }
}
