package com.heonotilbeonji.admin;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display-only formatting: the original address remains available in the detail dialog. */
final class RequestSummary {
    private static final Pattern ROAD = Pattern.compile("^(.+?(?:로|길)(?:\\d+(?:번길)?)?\\s+\\d+(?:-\\d+)?)(?:\\s|$)");

    static String address(String original) {
        if (original == null || original.trim().isEmpty()) return "주소 미기재";
        String value = original.trim().replaceAll("\\s+", " ")
                .replaceFirst("^\\(?\\d{5}\\)?\\s*", "")
                .split("[,（(]", 2)[0].trim();
        Matcher road = ROAD.matcher(value);
        if (road.find()) value = road.group(1);
        else value = value.replaceFirst("\\s+\\d+(?:동|층|호)(?:\\s.*)?$", "");
        return value.replaceFirst("^서울특별시\\s", "서울 ")
                .replaceFirst("^경기도\\s", "경기 ");
    }

    static String date(String original) {
        if (original == null || original.trim().isEmpty()) return "날짜 협의";
        if (!original.matches("\\d{4}-\\d{2}-\\d{2}")) return original;
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        input.setLenient(false);
        input.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        try {
            Date date = input.parse(original);
            SimpleDateFormat output = new SimpleDateFormat("yy년 M월 d일 (E)", Locale.KOREA);
            output.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            return output.format(date);
        } catch (ParseException ignored) {
            return original;
        }
    }
}
