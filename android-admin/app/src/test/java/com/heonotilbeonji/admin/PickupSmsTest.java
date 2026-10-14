package com.heonotilbeonji.admin;
import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35})
public class PickupSmsTest {
    private JSONObject request(String date,String method)throws Exception {
        return new JSONObject().put("id","sms-test").put("name","홍길동").put("phone","010-1234-5678")
            .put("date",date).put("pickupMethod",method).put("status","new");
    }
    @Test public void preparesBothMethodsAndRecipientWithoutSending()throws Exception {
        for(String method:new String[]{"대면 수거","비대면 수거"}) {
            Intent intent=PickupSms.draft(request("2026-01-05",method));
            assertEquals(Intent.ACTION_SENDTO,intent.getAction());
            assertEquals("smsto:01012345678",intent.getData().toString());
            assertEquals("1월 5일 "+method+" 접수 완료되었습니다.",intent.getStringExtra("sms_body"));
        }
    }
    @Test public void missingAndInvalidDatesOrMethodsAreNotUsedInConfirmation()throws Exception {
        for(JSONObject item:new JSONObject[]{request("","대면 수거"),request("2026-02-30","대면 수거"),request("2026-10-15","")}) {
            try {PickupSms.draft(item);fail("Invalid confirmation should not open a draft");}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void tappingSmsInDetailsOpensTheMatchingDraftAndPreservesStatus()throws Exception {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            var activity=controller.get();JSONObject item=request("2026-10-15","비대면 수거");
            var show=MainActivity.class.getDeclaredMethod("showDetail",JSONObject.class);show.setAccessible(true);show.invoke(activity,item);
            Dialog dialog=ShadowDialog.getLatestDialog();View root=dialog.getWindow().getDecorView();
            View sms=PlannerInteractionTest.description(root,"문자 보내기");assertNotNull(sms);sms.performClick();
            Intent intent=shadowOf(activity).getNextStartedActivity();
            assertEquals(Intent.ACTION_SENDTO,intent.getAction());
            assertEquals("smsto:01012345678",intent.getData().toString());
            assertEquals("10월 15일 비대면 수거 접수 완료되었습니다.",intent.getStringExtra("sms_body"));
            assertEquals("new",item.getString("status"));
            dialog.dismiss();
        }
    }
}
