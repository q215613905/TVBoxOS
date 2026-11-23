package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.UpdateChecker;

import org.jetbrains.annotations.NotNull;

public class AboutDialog extends BaseDialog {

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_about);
        TextView tvVersion = findViewById(R.id.tvVersion);
        if (tvVersion != null) {
            tvVersion.setText("当前版本 " + versionName(context));
        }
        Button btnCheck = findViewById(R.id.btnCheckUpdate);
        if (btnCheck != null) {
            btnCheck.setOnClickListener(v -> {
                if (context instanceof Activity) {
                    UpdateChecker.checkNow((Activity) context);
                } else {
                    Toast.makeText(context, "当前环境无法检查更新", Toast.LENGTH_SHORT).show();
                }
            });
        }
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
