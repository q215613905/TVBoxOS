package com.github.tvbox.osc.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
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
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * 软件自动更新检查：启动时查询 GitHub Release，有新版本则弹窗提示。
 */
public class UpdateChecker {

    private static final String REPO = "bkys/TVBoxOS";
    private static final String LATEST_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases";
    private static final String LATEST_PAGE = "https://github.com/" + REPO + "/releases/latest";
    private static final long REMIND_INTERVAL = 7L * 24 * 60 * 60 * 1000;

    private static boolean checked = false;

    public static void check(final Activity activity) {
        check(activity, false);
    }

    /**
     * 手动检查更新（关于页面按钮）：忽略“7 天后再提醒”和“跳过此版本”，并给出结果提示。
     */
    public static void checkNow(final Activity activity) {
        check(activity, true);
    }

    private static void check(final Activity activity, final boolean manual) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (!manual) {
            if (checked) {
                return;
            }
            // 用户选择了“7 天后再提醒”
            if (System.currentTimeMillis() < Hawk.get(HawkConfig.UPDATE_REMIND_TIME, 0L)) {
                return;
            }
        }
        checked = true;
        // 先用发布页跳转取版本号（无频率限制），失败再走 GitHub API（能拿到更新说明）
        OkGo.<String>get(LATEST_PAGE)
                .headers("Accept", "text/html")
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        String tag = tagFromUrl(response.getRawResponse().request().url().toString());
                        if (tag == null) {
                            checkByApi(activity, manual);
                            return;
                        }
                        handleUpdate(activity, tag, "", apkUrlOfTag(tag), manual);
                    }

                    @Override
                    public void onError(Response<String> response) {
                        LOG.e("update redirect check error: "
                                + (response == null ? "null" : response.code()));
                        checkByApi(activity, manual);
                    }
                });
    }

    /**
     * 备用方案：GitHub API（匿名每小时 60 次，同一出口 IP 共享，可能被限流）。
     */
    private static void checkByApi(final Activity activity, final boolean manual) {
        OkGo.<String>get(LATEST_API)
                .headers("Accept", "application/vnd.github+json")
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (!root.has("tag_name")) {
                                if (manual) {
                                    toast(activity, "未获取到版本信息，请稍后重试");
                                }
                                return;
                            }
                            String tag = root.get("tag_name").getAsString();
                            String notes = root.has("body") && !root.get("body").isJsonNull()
                                    ? root.get("body").getAsString() : "";
                            handleUpdate(activity, tag, notes, pickAsset(root), manual);
                        } catch (Exception e) {
                            LOG.e("update check parse error: " + e.getMessage());
                            if (manual) {
                                toast(activity, "未获取到版本信息，请稍后重试");
                            }
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        LOG.e("update check request error: "
                                + (response == null ? "null" : response.code()));
                        if (manual) {
                            toast(activity, "检查更新失败，请检查网络");
                        }
                    }
                });
    }

    private static void handleUpdate(final Activity activity, String tag, String notes,
                                     String apkUrl, boolean manual) {
        if (!manual && isSkipped(tag)) {
            return;
        }
        if (!isNewer(activity, tag)) {
            if (manual) {
                toast(activity, "当前已是最新版本 " + tag);
            }
            return;
        }
        showUpdateDialog(activity, tag, notes, apkUrl);
    }

    /**
     * 从 https://github.com/bkys/TVBoxOS/releases/tag/v1.0.4 中取出 v1.0.4
     */
    private static String tagFromUrl(String url) {
        if (url == null) {
            return null;
        }
        int index = url.indexOf("/releases/tag/");
        if (index < 0) {
            return null;
        }
        String tag = url.substring(index + "/releases/tag/".length());
        int cut = tag.indexOf('?');
        if (cut >= 0) {
            tag = tag.substring(0, cut);
        }
        cut = tag.indexOf('#');
        if (cut >= 0) {
            tag = tag.substring(0, cut);
        }
        tag = tag.trim();
        return tag.isEmpty() ? null : tag;
    }

    /**
     * 按固定命名规则（与 CI 产物一致）拼出安装包地址，通用包始终存在。
     */
    private static String apkUrlOfTag(String tag) {
        String flavor = isPythonFlavor() ? "python" : "java";
        return "https://github.com/" + REPO + "/releases/download/" + tag + "/"
                + "NingJiaTV_release-" + flavor + ".apk";
    }

    /**
     * python flavor 才会打包 Chaquopy 运行时，据此判断该下哪个安装包。
     */
    private static boolean isPythonFlavor() {
        try {
            Class.forName("com.chaquo.python.Python");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void toast(Activity activity, String message) {
        runOnUiThread(() -> {
            try {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
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
        final File dir = activity.getExternalCacheDir() != null
                ? new File(activity.getExternalCacheDir(), "apk")
                : new File(activity.getCacheDir(), "apk");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(activity, "无法创建下载目录", Toast.LENGTH_LONG).show();
            return;
        }
        final File apk = new File(dir, "update.apk");
        if (apk.exists() && !apk.delete()) {
            LOG.e("update apk delete failed");
        }
        Toast.makeText(activity, "开始下载更新...", Toast.LENGTH_SHORT).show();

        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setTitle("下载更新");
        dialog.setMessage("正在连接...");
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setMax(100);
        dialog.setProgress(0);
        dialog.setCancelable(false);
        dialog.show();

        new Thread(() -> {
            HttpURLConnection conn = null;
            FileOutputStream out = null;
            try {
                conn = openConnection(url);
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new IOException("HTTP " + code);
                }
                long total = parseLong(conn.getHeaderField("Content-Length"));
                InputStream in = conn.getInputStream();
                out = new FileOutputStream(apk);
                byte[] buffer = new byte[16384];
                long downloaded = 0;
                long lastBytes = 0;
                long lastTime = System.currentTimeMillis();
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                    downloaded += len;
                    long now = System.currentTimeMillis();
                    if (now - lastTime >= 400) {
                        long speed = (downloaded - lastBytes) * 1000L / Math.max(1L, now - lastTime);
                        final int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
                        final long cur = downloaded;
                        final long sp = speed;
                        final long tot = total;
                        runOnUiThread(() -> {
                            dialog.setProgress(percent);
                            dialog.setMessage(sizeText(cur) + (tot > 0 ? " / " + sizeText(tot) : "")
                                    + "\n" + sizeText(sp) + "/s");
                        });
                        lastBytes = downloaded;
                        lastTime = now;
                    }
                }
                out.flush();
                out.close();
                out = null;
                in.close();
                if (downloaded <= 0) {
                    throw new IOException("下载内容为空");
                }
                if (total > 0 && downloaded < total) {
                    throw new IOException("下载不完整 " + downloaded + "/" + total);
                }
                runOnUiThread(() -> {
                    dismissQuietly(dialog);
                    Toast.makeText(activity, "下载完成，正在安装...", Toast.LENGTH_SHORT).show();
                    install(activity, apk);
                });
            } catch (Exception e) {
                final String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                LOG.e("update download error: " + detail);
                runOnUiThread(() -> {
                    dismissQuietly(dialog);
                    showDownloadFailedDialog(activity, detail);
                });
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception ignored) {
                    }
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    /**
     * 跟随重定向打开连接（可跨 http/https 与域名），并带 UA 避免被 CDN 拒绝。
     */
    private static HttpURLConnection openConnection(String url) throws IOException {
        String current = url;
        for (int i = 0; i < 6; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ") NingJiaTV");
            conn.setRequestProperty("Accept", "*/*");
            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null) {
                    throw new IOException("重定向缺少地址");
                }
                current = location.startsWith("http") ? location
                        : new URL(new URL(current), location).toString();
                continue;
            }
            return conn;
        }
        throw new IOException("重定向次数过多");
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String sizeText(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.2fGB", bytes / 1073741824.0);
        }
        if (bytes >= 1024L * 1024) {
            return String.format(Locale.getDefault(), "%.1fMB", bytes / 1048576.0);
        }
        if (bytes >= 1024L) {
            return String.format(Locale.getDefault(), "%.0fKB", bytes / 1024.0);
        }
        return bytes + "B";
    }

    private static void dismissQuietly(ProgressDialog dialog) {
        try {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Exception ignored) {
        }
    }

    private static void showDownloadFailedDialog(Activity activity, String detail) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("下载失败")
                .setMessage("更新包下载失败：" + detail + "\n\n可前往发布页用浏览器手动下载安装。")
                .setPositiveButton("浏览器下载", (dialog, which) -> openReleasesPage(activity))
                .setNegativeButton("取消", null)
                .show();
    }

    private static void install(Activity activity, File apk) {
        try {
            if (!apk.exists() || apk.length() == 0) {
                showDownloadFailedDialog(activity, "安装包不存在");
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("需要安装权限")
                        .setMessage("请在系统设置中允许本应用安装未知来源应用，然后重新点击更新。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            try {
                                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivity(settings);
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apk);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            LOG.e("update install error: " + e.getMessage());
            Toast.makeText(activity, "安装失败：" + e.getMessage() + "，请前往发布页手动下载",
                    Toast.LENGTH_LONG).show();
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
