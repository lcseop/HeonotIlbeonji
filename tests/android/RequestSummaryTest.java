package com.heonotilbeonji.admin;

public final class RequestSummaryTest {
    private static void expect(String wanted, String actual) {
        if (!wanted.equals(actual)) throw new AssertionError("Expected: " + wanted + "; got: " + actual);
    }

    public static void main(String[] args) {
        expect("경기 김포시 고촌읍 전호로56번길 30",
                RequestSummary.address("경기도 김포시 고촌읍 전호로56번길 30, 1층 (우측)"));
        expect("서울 강서구 화곡로 123",
                RequestSummary.address("(07654) 서울특별시 강서구 화곡로 123 예시아파트 101동 1001호"));
        expect("경기 고양시 일산동구 중앙로 123-4",
                RequestSummary.address("경기도 고양시 일산동구 중앙로 123-4 (장항동) 2층"));
        expect("경기 파주시 운정동 12-3", RequestSummary.address("경기도 파주시 운정동 12-3 2층"));
        expect("주소 미기재", RequestSummary.address("   "));
        expect("26년 10월 7일 (수)", RequestSummary.date("2026-10-07"));
        expect("날짜 협의", RequestSummary.date(""));
        expect("2026-02-30", RequestSummary.date("2026-02-30"));
        expect("일정 협의", RequestSummary.date("일정 협의"));
        System.out.println("Request summary: 9 cases passed");
    }
}
