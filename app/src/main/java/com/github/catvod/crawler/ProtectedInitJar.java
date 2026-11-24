package com.github.catvod.crawler;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.tvbox.osc.base.App;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dalvik.system.DexClassLoader;

class ProtectedInitJar {

    private static final byte[] DEX_MAGIC = new byte[]{'d', 'e', 'x'};
    private static final int BUFFER_SIZE = 8192;

    private final ConcurrentHashMap<String, Boolean> jars = new ConcurrentHashMap<>();

    void clear() {
        jars.clear();
    }

    boolean check(String jar) {
        Boolean cached = jars.get(jar);
        if (cached != null) return cached;
        boolean result = scan(jar, App.getInstance().getPackageName());
        jars.put(jar, result);
        return result;
    }

    boolean init(Class<?> clz) {
        Object init = null;
        try {
            Method get = clz.getMethod("get");
            init = get.invoke(null);
        } catch (Throwable ignored) {
        }
        bindContext(clz, init);
        if (!bindDexLoader(clz, init)) return false;
        invokeNoArg(clz, "replaceCloudDiskNames");
        invokeStartGoProxy(clz);
        return true;
    }

    private void bindContext(Class<?> clz, Object init) {
        if (init == null) return;
        try {
            Field context = clz.getDeclaredField("c");
            context.setAccessible(true);
            context.set(init, App.getInstance());
            return;
        } catch (Throwable ignored) {
        }
        for (Field field : clz.getDeclaredFields()) {
            try {
                if (Modifier.isStatic(field.getModifiers()) || !Context.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                field.set(init, App.getInstance());
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean bindDexLoader(Class<?> clz, Object init) {
        if (init == null) return false;
        try {
            Class<?> nativeClass = clz.getClassLoader().loadClass("com.github.catvod.spider.DexNative");
            Method getLoader = nativeClass.getMethod("getLoader", Object.class);
            Object loader = getLoader.invoke(null, App.getInstance());
            if (!(loader instanceof DexClassLoader)) return false;
            boolean bound = false;
            for (Class<?> type = clz; type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !DexClassLoader.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    field.set(init, loader);
                    bound = true;
                }
            }
            return bound;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void invokeNoArg(Class<?> clz, String methodName) {
        try {
            Method method = clz.getMethod(methodName);
            method.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private void invokeStartGoProxy(Class<?> clz) {
        try {
            Method method = clz.getMethod("startGoProxy", Context.class);
            method.invoke(null, App.getInstance());
        } catch (Throwable ignored) {
        }
    }

    private static boolean scan(String jar, String packageName) {
        try {
            File file = new File(jar);
            if (!file.exists()) return false;
            if (isDexFile(file)) {
                InputStream is = null;
                try {
                    is = new FileInputStream(file);
                    return checkDex(readBytes(is), packageName);
                } finally {
                    close(is);
                }
            }
            ZipFile zip = null;
            try {
                zip = new ZipFile(file);
                java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".dex")) continue;
                    InputStream is = null;
                    try {
                        is = zip.getInputStream(entry);
                        if (checkDex(readBytes(is), packageName)) return true;
                    } finally {
                        close(is);
                    }
                }
            } finally {
                close(zip);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean checkDex(byte[] data, String packageName) {
        if (data == null || data.length < 112 || !startsWith(data, DEX_MAGIC)) return false;
        try {
            return new Dex(data, packageName).isProtected();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isDexFile(File file) {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            byte[] magic = new byte[DEX_MAGIC.length];
            int len = is.read(magic);
            return len == DEX_MAGIC.length && startsWith(magic, DEX_MAGIC);
        } catch (Throwable ignored) {
            return false;
        } finally {
            close(is);
        }
    }

    private static byte[] readBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int len;
        while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
        close(is);
        return baos.toByteArray();
    }

    private static boolean startsWith(byte[] data, byte[] pattern) {
        if (data == null || pattern == null || data.length < pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (data[i] != pattern[i]) return false;
        }
        return true;
    }

    private static void close(Closeable closeable) {
        try {
            if (closeable != null) closeable.close();
        } catch (Throwable ignored) {
        }
    }

    private static class Dex {

        private final byte[] data;
        private final int stringIdsSize;
        private final int stringIdsOff;
        private final int typeIdsSize;
        private final int typeIdsOff;
        private final int methodIdsSize;
        private final int methodIdsOff;
        private final int classDefsSize;
        private final int classDefsOff;
        private final Whitelist whitelist;

        Dex(byte[] data, String packageName) {
            this.data = data;
            this.stringIdsSize = uint(0x38);
            this.stringIdsOff = uint(0x3c);
            this.typeIdsSize = uint(0x40);
            this.typeIdsOff = uint(0x44);
            this.methodIdsSize = uint(0x58);
            this.methodIdsOff = uint(0x5c);
            this.classDefsSize = uint(0x60);
            this.classDefsOff = uint(0x64);
            this.whitelist = new Whitelist(packageName);
        }

        boolean isProtected() {
            boolean hasInit = false;
            boolean hasKillProcess = false;
            boolean checkedWhitelist = false;
            for (int i = 0; i < classDefsSize; i++) {
                int off = classDefsOff + i * 32;
                String desc = typeDesc(uint(off));
                if (!desc.startsWith("Lcom/github/catvod/spider/Init")) continue;
                hasInit = hasInit || "Lcom/github/catvod/spider/Init;".equals(desc);
                int classDataOff = uint(off + 24);
                if (classDataOff <= 0) continue;
                if ("Lcom/github/catvod/spider/Init;".equals(desc)) {
                    checkedWhitelist = true;
                    if (scanInitWhitelist(classDataOff)) return false;
                }
                hasKillProcess = hasKillProcess || scanKillProcess(classDataOff);
                if (hasInit && hasKillProcess && checkedWhitelist) return true;
            }
            return hasInit && hasKillProcess;
        }

        private boolean scanInitWhitelist(int off) {
            int[] cursor = new int[]{off};
            int staticFields = uleb(cursor);
            int instanceFields = uleb(cursor);
            int directMethods = uleb(cursor);
            int virtualMethods = uleb(cursor);
            skipFields(cursor, staticFields + instanceFields);
            return scanInitMethods(cursor, directMethods) || scanInitMethods(cursor, virtualMethods);
        }

        private boolean scanInitMethods(int[] cursor, int count) {
            int methodIdx = 0;
            for (int i = 0; i < count; i++) {
                methodIdx += uleb(cursor);
                uleb(cursor);
                int codeOff = uleb(cursor);
                if (codeOff <= 0 || methodIdx >= methodIdsSize) continue;
                if ("init".equals(methodName(methodIdx)) && scanWhitelist(codeOff)) return true;
            }
            return false;
        }

        private boolean scanKillProcess(int off) {
            int[] cursor = new int[]{off};
            int staticFields = uleb(cursor);
            int instanceFields = uleb(cursor);
            int directMethods = uleb(cursor);
            int virtualMethods = uleb(cursor);
            skipFields(cursor, staticFields + instanceFields);
            return scanKillMethods(cursor, directMethods) || scanKillMethods(cursor, virtualMethods);
        }

        private boolean scanKillMethods(int[] cursor, int count) {
            int methodIdx = 0;
            for (int i = 0; i < count; i++) {
                methodIdx += uleb(cursor);
                uleb(cursor);
                int codeOff = uleb(cursor);
                if (codeOff > 0 && scanKillProcessCode(codeOff)) return true;
            }
            return false;
        }

        private boolean scanWhitelist(int codeOff) {
            int insnsSize = uint(codeOff + 12);
            int insnsOff = codeOff + 16;
            int end = insnsOff + insnsSize * 2;
            for (int off = insnsOff; off + 1 < end && off + 1 < data.length; off += 2) {
                int op = data[off] & 0xff;
                if (op == 0x1a && off + 3 < end) {
                    if (whitelist.contains(string(ushort(off + 2)))) return true;
                } else if (op == 0x1b && off + 5 < end) {
                    if (whitelist.contains(string(uint(off + 2)))) return true;
                }
            }
            return false;
        }

        private boolean scanKillProcessCode(int codeOff) {
            int insnsSize = uint(codeOff + 12);
            int insnsOff = codeOff + 16;
            int end = insnsOff + insnsSize * 2;
            for (int off = insnsOff; off + 3 < end && off + 3 < data.length; off += 2) {
                int op = data[off] & 0xff;
                if (op >= 0x6e && op <= 0x72 && isKillProcess(ushort(off + 2))) return true;
            }
            return false;
        }

        private void skipFields(int[] cursor, int count) {
            for (int i = 0; i < count; i++) {
                uleb(cursor);
                uleb(cursor);
            }
        }

        private boolean isKillProcess(int methodIdx) {
            if (methodIdx < 0 || methodIdx >= methodIdsSize) return false;
            return "Landroid/os/Process;".equals(methodClass(methodIdx)) && "killProcess".equals(methodName(methodIdx));
        }

        private String methodClass(int methodIdx) {
            return typeDesc(ushort(methodIdsOff + methodIdx * 8));
        }

        private String methodName(int methodIdx) {
            return string(uint(methodIdsOff + methodIdx * 8 + 4));
        }

        private String typeDesc(int typeIdx) {
            if (typeIdx < 0 || typeIdx >= typeIdsSize) return "";
            return string(uint(typeIdsOff + typeIdx * 4));
        }

        private String string(int stringIdx) {
            if (stringIdx < 0 || stringIdx >= stringIdsSize) return "";
            int[] cursor = new int[]{uint(stringIdsOff + stringIdx * 4)};
            uleb(cursor);
            int start = cursor[0];
            int end = start;
            while (end < data.length && data[end] != 0) end++;
            try {
                return new String(data, start, end - start, "UTF-8");
            } catch (Throwable ignored) {
                return "";
            }
        }

        private int uleb(int[] cursor) {
            int result = 0;
            int shift = 0;
            while (cursor[0] < data.length) {
                int b = data[cursor[0]++] & 0xff;
                result |= (b & 0x7f) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            return result;
        }

        private int ushort(int off) {
            if (off < 0 || off + 1 >= data.length) return 0;
            return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8);
        }

        private int uint(int off) {
            if (off < 0 || off + 3 >= data.length) return 0;
            return (data[off] & 0xff)
                    | ((data[off + 1] & 0xff) << 8)
                    | ((data[off + 2] & 0xff) << 16)
                    | ((data[off + 3] & 0xff) << 24);
        }
    }

    private static class Whitelist {

        private static final String AES_KEY = "1234123412341234";
        private final String packageName;

        Whitelist(String packageName) {
            this.packageName = packageName;
        }

        boolean contains(String value) {
            if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(value)) return false;
            if (packageName.equals(value) || containsItem(value)) return true;
            return containsItem(decrypt(value));
        }

        private boolean containsItem(String text) {
            if (TextUtils.isEmpty(text)) return false;
            int start = 0;
            while (start <= text.length()) {
                int end = text.indexOf(',', start);
                if (end < 0) end = text.length();
                if (packageName.equals(text.substring(start, end).trim())) return true;
                start = end + 1;
            }
            return false;
        }

        private String decrypt(String value) {
            if (!isCipherText(value)) return "";
            try {
                byte[] key = AES_KEY.getBytes("UTF-8");
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
                return new String(cipher.doFinal(Base64.decode(value, Base64.DEFAULT)), "UTF-8");
            } catch (Throwable ignored) {
                return "";
            }
        }

        private boolean isCipherText(String value) {
            int len = value.length();
            if (len < 24 || len % 4 != 0) return false;
            int padding = 0;
            for (int i = 0; i < len; i++) {
                char c = value.charAt(i);
                if (c == '=') {
                    padding++;
                    if (i < len - 2) return false;
                } else if (!isBase64(c)) {
                    return false;
                }
            }
            int rawLen = len * 3 / 4 - padding;
            return rawLen > 0 && rawLen % 16 == 0;
        }

        private boolean isBase64(char c) {
            return (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '+'
                    || c == '/';
        }
    }
}
