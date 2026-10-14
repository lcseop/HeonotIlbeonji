package com.heonotilbeonji.admin;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

final class PickupSms {
    private PickupSms() {}
    static Intent draft(JSONObject item) {
        String day=item.optString("date"), method=item.optString("pickupMethod");
        if(!day.matches("\\d{4}-\\d{2}-\\d{2}") || !("대면 수거".equals(method)||"비대면 수거".equals(method)))
            throw new IllegalArgumentException("희망 날짜와 대면·비대면 수거 방식을 먼저 입력해 주세요.");
        String phone=item.optString("phone").replaceAll("[^0-9+]", "");
        if(phone.isEmpty()) throw new IllegalArgumentException("연락처를 먼저 입력해 주세요.");
        try {
            SimpleDateFormat parser=new SimpleDateFormat("yyyy-MM-dd",Locale.KOREA);parser.setLenient(false);parser.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            SimpleDateFormat format=new SimpleDateFormat("M월 d일",Locale.KOREA);format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            String body=format.format(parser.parse(day))+" "+method+" 접수 완료되었습니다.";
            return new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",phone,null)).putExtra("sms_body",body);
        }catch(java.text.ParseException error){throw new IllegalArgumentException("희망 날짜를 확인해 주세요.");}
    }
}
