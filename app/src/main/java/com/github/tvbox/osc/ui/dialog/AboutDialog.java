package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.UpdateChecker;

import org.jetbrains.annotations.NotNull;

public class AboutDialog extends BaseDialog {

    private String latestApkUrl = "";
    private boolean hasNewVersion = false;

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_about);

        final TextView tvVersion = findViewById(R.id.tvVersion);
        final Button btnCheck = findViewById(R.id.btnCheckUpdate);
        final String current = versionName(context);

        if (tvVersion != null) {
            tvVersion.setText("当前版本 " + current + "    最新版本 查询中...");
        }
        if (btnCheck != null) {
            btnCheck.setText("检查更新");
            btnCheck.setOnClickListener(v -> {
                if (hasNewVersion) {
                    // 有新版本：直接下载并安装
                    if (context instanceof Activity) {
                        UpdateChecker.downloadAndInstallLatest((Activity) context, latestApkUrl);
                    }
                } else {
                    queryLatest(context, tvVersion, btnCheck, current);
                }
            });
        }
        queryLatest(context, tvVersion, btnCheck, current);
    }

    private void queryLatest(Context context, final TextView tvVersion, final Button btnCheck,
                             final String current) {
        if (tvVersion != null) {
            tvVersion.setText("当前版本 " + current + "    最新版本 查询中...");
        }
        if (btnCheck != null) {
            btnCheck.setText("检查更新");
        }
        hasNewVersion = false;
        UpdateChecker.queryLatest(new UpdateChecker.LatestCallback() {
            @Override
            public void onLatest(String tag, String notes, String apkUrl) {
                latestApkUrl = apkUrl;
                hasNewVersion = UpdateChecker.isNewer(context, tag);
                if (tvVersion != null) {
                    tvVersion.setText("当前版本 " + current + "    最新版本 " + tag);
                }
                if (btnCheck != null) {
                    btnCheck.setText(hasNewVersion ? "升级到最新" : "检查更新");
                }
            }

            @Override
            public void onFailed(String message) {
                if (tvVersion != null) {
                    tvVersion.setText("当前版本 " + current + "    最新版本 获取失败");
                }
                if (btnCheck != null) {
                    btnCheck.setText("重新检查");
                }
            }
        });
    }

    private String versionName(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
