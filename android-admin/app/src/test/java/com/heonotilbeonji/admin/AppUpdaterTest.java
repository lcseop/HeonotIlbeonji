package com.heonotilbeonji.admin;

import org.junit.Test;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class AppUpdaterTest {
    private JSONObject release() throws Exception {
        return new JSONObject().put("packageName", BuildConfig.APPLICATION_ID).put("versionCode", BuildConfig.VERSION_CODE + 1)
                .put("versionName", "1.3.2").put("apkPath", "/admin-app/heonot-admin-1.3.2.apk")
                .put("sha256", "a".repeat(64)).put("size", 100);
    }
    @Test public void rejectsOtherAppsUnsafePathsAndUnboundedDownloads() throws Exception {
        assertTrue(AppUpdater.validRelease(release()));
        assertFalse(AppUpdater.validRelease(release().put("packageName", "other.app")));
        assertFalse(AppUpdater.validRelease(release().put("apkPath", "https://other.example/file.apk")));
        assertFalse(AppUpdater.validRelease(release().put("apkPath", "/admin-app/../other.apk")));
        assertFalse(AppUpdater.validRelease(release().put("size", 30 * 1024 * 1024)));
        assertFalse(AppUpdater.validRelease(release().put("sha256", "not-a-hash")));
    }
    @Test public void corruptedAndIncompleteCachedFilesNeverMatch() throws Exception {
        File file = File.createTempFile("update-test", ".apk");
        try {
            byte[] bytes = "signed-apk-example".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(file.toPath(), bytes); StringBuilder hash = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format("%02x", b & 255));
            JSONObject meta = release().put("size", bytes.length).put("sha256", hash.toString());
            assertTrue(AppUpdater.matchesFile(file, meta));
            Files.write(file.toPath(), "tampered-apk-dataxx".getBytes()); assertFalse(AppUpdater.matchesFile(file, meta));
            Files.write(file.toPath(), new byte[1]); assertFalse(AppUpdater.matchesFile(file, meta));
        } finally { file.delete(); }
    }
}
