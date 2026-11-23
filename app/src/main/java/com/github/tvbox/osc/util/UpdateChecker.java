package com.github.tvbox.osc.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 软件自动更新检查：启动时查询 GitHub Release，有新版本则弹窗提示。
 */
public class UpdateChecker {

    private static final String REPO = "bkys/TVBoxOS";
    private static final String LATEST_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases";
    private static final long REMIND_INTERVAL = 7L * 24 * 60 * 60 * 1000;

    private static boolean checked = false;

    public static void check(final Activity activity) {
        if (checked || activity == null || activity.isFinishing()) {
            return;
        }
        checked = true;
        // 用户选择了“7 天后再提醒”
        if (System.currentTimeMillis() < Hawk.get(HawkConfig.UPDATE_REMIND_TIME, 0L)) {
            return;
        }
        OkGo.<String>get(LATEST_API)
                .headers("Accept", "application/vnd.github+json")
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (!root.has("tag_name")) {
                                return;
                            }
                            String tag = root.get("tag_name").getAsString();
                            if (isSkipped(tag) || !isNewer(activity, tag)) {
                                return;
                            }
                            String notes = root.has("body") && !root.get("body").isJsonNull()
                                    ? root.get("body").getAsString() : "";
                            String apkUrl = pickAsset(root);
                            showUpdateDialog(activity, tag, notes, apkUrl);
                        } catch (Exception e) {
                            LOG.e("update check parse error: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        LOG.e("update check request error");
                    }
                });
    }

    private static boolean isSkipped(String tag) {
        return tag.equals(Hawk.get(HawkConfig.UPDATE_SKIP_VERSION, ""));
    }

    private static boolean isNewer(Activity activity, String tag) {
        try {
            String remote = tag.replaceAll("^[vV]", "");
            String current = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
            return compareVersion(remote, current) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int compareVersion(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int len = Math.max(as.length, bs.length);
        for (int i = 0; i < len; i++) {
            int ai = i < as.length ? parseIntSafe(as[i]) : 0;
            int bi = i < bs.length ? parseIntSafe(bs[i]) : 0;
            if (ai != bi) {
                return ai - bi;
            }
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 按设备 ABI 与当前版本类型（java / python）选择最合适的 APK。
     */
    private static String pickAsset(JsonObject root) {
        if (!root.has("assets") || !root.get("assets").isJsonArray()) {
            return null;
        }
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
        String abiKey = abi.contains("arm64") ? "64" : (abi.contains("armeabi") ? "32" : "");
        boolean python = isPythonBuild();
        JsonArray assets = root.getAsJsonArray("assets");
        String fallback = null;
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            if (!asset.has("name") || !asset.has("browser_download_url")) {
                continue;
            }
            String name = asset.get("name").getAsString().toLowerCase();
            if (!name.endsWith(".apk")) {
                continue;
            }
            String url = asset.get("browser_download_url").getAsString();
            if (fallback == null) {
                fallback = url;
            }
            boolean flavorOk = python ? name.contains("python") : name.contains("java");
            boolean abiOk = abiKey.isEmpty() || name.contains(abiKey);
            if (flavorOk && abiOk) {
                return url;
            }
        }
        return fallback;
    }

    private static boolean isPythonBuild() {
        try {
            Class.forName("com.chaquo.python.Python");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void showUpdateDialog(final Activity activity, final String tag,
                                         String notes, final String apkUrl) {
        if (activity.isFinishing()) {
            return;
        }
        String message = notes == null ? "" : notes.trim();
        if (message.isEmpty()) {
            message = "发现新版本 " + tag;
        }
        if (message.length() > 300) {
            message = message.substring(0, 300) + "...";
        }
        new AlertDialog.Builder(activity)
                .setTitle("发现新版本 " + tag)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("立即更新", (dialog, which) -> {
                    if (apkUrl != null) {
                        downloadAndInstall(activity, apkUrl);
                    } else {
                        openReleasesPage(activity);
                    }
                })
                .setNeutralButton("7天后再提醒", (dialog, which) ->
                        Hawk.put(HawkConfig.UPDATE_REMIND_TIME, System.currentTimeMillis() + REMIND_INTERVAL))
                .setNegativeButton("跳过此版本", (dialog, which) ->
                        Hawk.put(HawkConfig.UPDATE_SKIP_VERSION, tag))
                .show();
    }

    private static void downloadAndInstall(final Activity activity, final String url) {
        final File dir = new File(activity.getExternalCacheDir(), "apk");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        final File apk = new File(dir, "update.apk");
        Toast.makeText(activity, "开始下载更新...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setInstanceFollowRedirects(true);
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(apk);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                out.close();
                in.close();
                runOnUiThread(() -> install(activity, apk));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(activity, "下载失败，请前往发布页手动下载", Toast.LENGTH_LONG).show();
                    openReleasesPage(activity);
                });
            }
        }).start();
    }

    private static void install(Activity activity, File apk) {
        try {
            if (!apk.exists() || apk.length() == 0) {
                openReleasesPage(activity);
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "请允许安装未知来源应用后重试", Toast.LENGTH_LONG).show();
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settings);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apk);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            openReleasesPage(activity);
        }
    }

    private static void openReleasesPage(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private static void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
