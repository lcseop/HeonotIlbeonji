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
            assertEquals("1월 5일 (월요일) "+method+" 확인 및 접수되었습니다. 대단히 감사합니다~~\n코스 시간이 정해지면 문자 드리겠습니다.",intent.getStringExtra("sms_body"));
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
            assertEquals("10월 15일 (목요일) 비대면 수거 확인 및 접수되었습니다. 대단히 감사합니다~~\n코스 시간이 정해지면 문자 드리겠습니다.",intent.getStringExtra("sms_body"));
            assertEquals("new",item.getString("status"));
            dialog.dismiss();
        }
    }
    @Test public void settingsIncludeLockStarInstructionsAndInstallationLink()throws Exception {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            var activity=controller.get();var help=MainActivity.class.getDeclaredMethod("showLockWidgetHelp");help.setAccessible(true);help.invoke(activity);
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            android.widget.TextView message=dialog.findViewById(android.R.id.message);
            assertTrue(message.getText().toString().contains("LockStar"));
            assertTrue(message.getText().toString().contains("헌옷일번지 신청 목록"));
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            Intent intent=shadowOf(activity).getNextStartedActivity();
            assertEquals("https://galaxystore.samsung.com/detail/com.samsung.android.goodlock",intent.getData().toString());
        }
    }
}
