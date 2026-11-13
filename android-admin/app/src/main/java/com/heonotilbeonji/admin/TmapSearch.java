package com.heonotilbeonji.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** Send only the selected address to TMAP, after an explicit user tap. */
final class TmapSearch {
    static final String PACKAGE = "com.skt.tmap.ku";

    static Intent searchIntent(String address) {
        return new Intent(Intent.ACTION_VIEW, new Uri.Builder().scheme("tmap").authority("search")
                .appendQueryParameter("name", address).build()).setPackage(PACKAGE);
    }

    static void open(Activity activity, String address) {
        if (address == null || address.trim().isEmpty()) {
            Toast.makeText(activity, "등록된 수거 주소가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            activity.startActivity(searchIntent(address));
        } catch (ActivityNotFoundException | SecurityException error) {
            new AlertDialog.Builder(activity).setTitle("티맵을 열 수 없습니다")
                    .setMessage("티맵이 설치되어 있는지 확인해 주세요. 설치되어 있다면 Play 스토어에서 업데이트 후 다시 시도해 주세요.")
                    .setPositiveButton("Play 스토어", (dialog, which) -> openStore(activity))
                    .setNegativeButton("취소", null).show();
        }
    }

    private static void openStore(Activity activity) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + PACKAGE)).setPackage("com.android.vending"));
        } catch (ActivityNotFoundException | SecurityException error) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + PACKAGE)));
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(activity, "Play 스토어에서 티맵을 검색해 주세요.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
