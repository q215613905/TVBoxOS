package com.github.tvbox.osc.ui.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.core.content.FileProvider;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.server.RemoteServer;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import android.widget.Toast;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class TransferActivity extends BaseActivity {
    private LinearLayout fileList;
    private String address;
    private ProgressBar progress;
    private TextView status;
    private String progressName;
    private String fileSignature;
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() { @Override public void run() { loadFiles(); handler.postDelayed(this, 2000); } };
    private final Runnable progressRefresh = new Runnable() { @Override public void run() { new ProgressTask().execute(); handler.postDelayed(this, 800); } };

    @Override protected int getLayoutResID() { return R.layout.activity_transfer; }

    @Override protected void init() {
        ImageView qr = findViewById(R.id.ivTransferQr);
        TextView addressView = findViewById(R.id.tvTransferAddress);
        fileList = findViewById(R.id.transferFileList);
        progress = findViewById(R.id.transferProgress);
        status = findViewById(R.id.tvTransferStatus);
        address = ControlManager.get().getAddress(false);
        String page = address + "transfer.html";
        addressView.setText("手机或电脑扫描二维码，或访问：\n" + page);
        qr.setImageBitmap(QRCodeGen.generateBitmap(page, 420, 420));
        loadFiles();
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); handler.post(progressRefresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); handler.removeCallbacks(progressRefresh); super.onPause(); }

    private class ProgressTask extends AsyncTask<Void, Void, String> {
        @Override protected String doInBackground(Void... ignored) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address + "transfer/progress").openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line = reader.readLine(); reader.close(); return line;
            } catch (Throwable ignoredError) { return null; }
            finally { if (connection != null) connection.disconnect(); }
        }
        @Override protected void onPostExecute(String value) {
            if (value == null) return;
            try {
                JSONObject object = new JSONObject(value);
                int percent = object.optInt("progress", 0);
                String name = object.optString("name", "");
                progressName = name;
                progress.setProgress(percent);
                if (percent > 0 && percent < 100 && name.length() > 0) status.setText("正在上传 " + name + "（" + percent + "%）");
                else if (percent >= 100) status.setText("最近一次上传完成");
                else status.setText("等待网页上传");
            } catch (Throwable ignored) { }
        }
    }

    private void loadFiles() {
        File dir = RemoteServer.getTransferDirectory();
        File[] fs = dir.listFiles();
        if (progress.getProgress() > 0 && progressName != null && !progressName.isEmpty() && fs != null) {
            for (File f : fs) {
                if (f.isFile() && progressName.equals(f.getName())) {
                    progressName = "";
                    progress.setProgress(0);
                    status.setText("等待网页上传");
                    break;
                }
            }
        }
        StringBuilder signature = new StringBuilder();
        if (fs != null) for (File f : fs) if (f.isFile()) signature.append(f.getName()).append(':').append(f.length()).append(';');
        if (fileSignature != null && signature.toString().equals(fileSignature)) return;
        fileSignature = signature.toString();
        fileList.removeAllViews();
        if (fs == null || fs.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("暂无文件"); empty.setTextColor(0xffb0bec5); empty.setTextSize(20); empty.setPadding(8, 18, 8, 18);
            fileList.addView(empty); return;
        }
        for (final File f : fs) if (f.isFile()) addFileRow(f);
    }

    private void addFileRow(final File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(8, 5, 8, 5);
        TextView name = new TextView(this);
        name.setId(View.generateViewId());
        name.setText(file.getName() + "  " + readable(file.length())); name.setTextColor(0xffffffff); name.setTextSize(20); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); name.setGravity(android.view.Gravity.CENTER_VERTICAL); name.setFocusable(true); name.setClickable(true); name.setBackgroundResource(R.drawable.shape_setting_model_focus); name.setPadding(12, 4, 12, 4);
        row.addView(name, new LinearLayout.LayoutParams(0, getResources().getDimensionPixelSize(R.dimen.vs_60), 1));
        Button delete = new Button(this); delete.setId(View.generateViewId()); delete.setText("删除"); delete.setTextSize(18); delete.setTextColor(0xffffffff); delete.setBackgroundResource(R.drawable.button_danmu_setting); delete.setFocusable(true); delete.setContentDescription("删除 " + file.getName());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(getResources().getDimensionPixelSize(R.dimen.vs_100), getResources().getDimensionPixelSize(R.dimen.vs_60)); deleteParams.leftMargin = getResources().getDimensionPixelSize(R.dimen.vs_8); row.addView(delete, deleteParams);
        name.setNextFocusRightId(delete.getId()); delete.setNextFocusLeftId(name.getId());
        name.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openFile(file); } });
        delete.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (file.delete()) loadFiles(); } });
        fileList.addView(row);
        if (fileList.getChildCount() == 1) name.requestFocus();
    }

    private void openFile(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW); Uri uri;
        if (android.os.Build.VERSION.SDK_INT >= 24) { uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); } else uri = Uri.fromFile(file);
        intent.setDataAndType(uri, mimeType(file.getName()));
        try { startActivity(intent); } catch (ActivityNotFoundException e) { Toast.makeText(this, "没有找到可以打开此文件的应用", Toast.LENGTH_SHORT).show(); }
    }

    private String mimeType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi")) return "video/*";
        if (lower.endsWith(".mp3") || lower.endsWith(".flac")) return "audio/*";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) return "image/*";
        return "*/*";
    }

    private String readable(long n) { return n < 1024 ? n + " B" : n < 1048576 ? (n / 1024) + " KB" : (n / 1048576) + " MB"; }

}
