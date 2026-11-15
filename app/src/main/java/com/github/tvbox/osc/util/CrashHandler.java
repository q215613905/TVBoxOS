package com.github.tvbox.osc.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * @author pj567
 * @date :2026/09/16
 * @description: 未捕获异常本地日志兜底
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final String CRASH_DIR_NAME = "crash";
    private static final int MAX_CRASH_FILES = 5;
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            saveCrashLog(thread, throwable);
        } catch (Throwable ignored) {
        }
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private void saveCrashLog(Thread thread, Throwable throwable) {
        File dir = context.getExternalFilesDir(CRASH_DIR_NAME);
        if (dir == null) {
            dir = new File(context.getFilesDir(), CRASH_DIR_NAME);
        }
        if (dir == null) {
            Log.e(TAG, "no writable dir to store crash log, dir=null");
            return;
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "crash-" + timestamp + ".log";
        File file = new File(dir, fileName);

        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        try {
            FileWriter writer = new FileWriter(file, false);
            PrintWriter pw = new PrintWriter(writer);
            pw.println("=================== " + time + " ===================");
            pw.println("Thread: " + thread.getName() + "(" + thread.getId() + ")");
            pw.println(throwable.toString());
            for (StackTraceElement element : throwable.getStackTrace()) {
                pw.println("\tat " + element.toString());
            }
            Throwable cause = throwable.getCause();
            while (cause != null) {
                pw.println("Caused by: " + cause.toString());
                for (StackTraceElement element : cause.getStackTrace()) {
                    pw.println("\tat " + element.toString());
                }
                cause = cause.getCause();
            }
            pw.println();
            pw.flush();
            pw.close();
            Log.i(TAG, "crash log saved to " + file.getAbsolutePath());
            cleanupOldCrashFiles(dir);
        } catch (Throwable e) {
            Log.e(TAG, "failed to save crash log: " + e.getMessage());
        }
    }

    private void cleanupOldCrashFiles(File dir) {
        File[] files = dir.listFiles((d, name) -> name.startsWith("crash-") && name.endsWith(".log"));
        if (files == null || files.length <= MAX_CRASH_FILES) {
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (int i = 0; i < files.length - MAX_CRASH_FILES; i++) {
            if (files[i].delete()) {
                Log.i(TAG, "deleted old crash log: " + files[i].getName());
            }
        }
    }
}