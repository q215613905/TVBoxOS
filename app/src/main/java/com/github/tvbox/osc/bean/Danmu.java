package com.github.tvbox.osc.bean;

import android.text.TextUtils;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Danmu {
    public interface CancelChecker {
        boolean isCancelled();
    }

    private static final Pattern D_TAG_PATTERN = Pattern.compile(
            "<d\\s+[^>]*\\bp\\s*=\\s*(['\"])(.*?)\\1[^>]*>(.*?)</d>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final List<Data> data = new ArrayList<>();

    public static Danmu fromXml(String xml) {
        return fromXml(xml, 0);
    }

    public static Danmu fromXml(String xml, long startPosition) {
        return fromXml(xml, startPosition, null);
    }

    public static Danmu fromXml(String xml, long startPosition, CancelChecker cancelChecker) {
        Danmu danmu = new Danmu();
        if (TextUtils.isEmpty(xml)) return danmu;
        long position = Math.max(0, startPosition);
        if (position > 0) {
            parseByTag(danmu, xml, position, cancelChecker);
            if (isCancelled(cancelChecker)) return danmu;
            if (!danmu.data.isEmpty()) return danmu;
        }
        String fixedXml = escapeIllegalEntities(xml);
        if (parseByXmlPull(danmu, fixedXml, position, cancelChecker)) return danmu;
        if (isCancelled(cancelChecker)) return danmu;
        danmu.data.clear();
        parseByTag(danmu, xml, position, cancelChecker);
        return danmu;
    }

    private static boolean parseByXmlPull(Danmu danmu, String xml, long startPosition,
                                          CancelChecker cancelChecker) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (isCancelled(cancelChecker)) return false;
                if (eventType == XmlPullParser.START_TAG && "d".equals(parser.getName())) {
                    String param = parser.getAttributeValue(null, "p");
                    String text = parser.nextText();
                    if (!TextUtils.isEmpty(param) && !TextUtils.isEmpty(text)
                            && (startPosition <= 0 || getTime(param) >= startPosition)) {
                        danmu.data.add(new Data(param, text));
                    }
                }
                eventType = parser.next();
            }
            return !danmu.data.isEmpty();
        } catch (Throwable th) {
            return false;
        }
    }

    private static void parseByTag(Danmu danmu, String xml, long startPosition,
                                   CancelChecker cancelChecker) {
        Matcher matcher = D_TAG_PATTERN.matcher(xml);
        while (matcher.find()) {
            if (isCancelled(cancelChecker)) return;
            String param = decodeXmlString(matcher.group(2));
            if (TextUtils.isEmpty(param) || (startPosition > 0 && getTime(param) < startPosition)) continue;
            String text = matcher.group(3);
            if (!TextUtils.isEmpty(text)) danmu.data.add(new Data(param, text));
        }
    }

    private static boolean isCancelled(CancelChecker cancelChecker) {
        return cancelChecker != null && cancelChecker.isCancelled();
    }

    private static long getTime(String param) {
        int end = param == null ? -1 : param.indexOf(',');
        if (end <= 0) return Long.MIN_VALUE;
        try {
            return (long) (Float.parseFloat(param.substring(0, end)) * 1000);
        } catch (Throwable ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static String escapeIllegalEntities(String xml) {
        int length = xml.length();
        int firstIllegalEntity = -1;
        for (int i = 0; i < length; i++) {
            char ch = xml.charAt(i);
            if (ch == '&' && !isLegalEntity(xml, i + 1)) {
                firstIllegalEntity = i;
                break;
            }
        }
        if (firstIllegalEntity < 0) return xml;
        StringBuilder builder = new StringBuilder(length + 16);
        builder.append(xml, 0, firstIllegalEntity);
        for (int i = firstIllegalEntity; i < length; i++) {
            char ch = xml.charAt(i);
            if (ch == '&' && !isLegalEntity(xml, i + 1)) {
                builder.append("&amp;");
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static boolean isLegalEntity(String text, int start) {
        int end = text.indexOf(';', start);
        if (end < 0 || end - start > 10) return false;
        String entity = text.substring(start, end);
        if ("amp".equals(entity) || "lt".equals(entity) || "gt".equals(entity)
                || "quot".equals(entity) || "apos".equals(entity)) {
            return true;
        }
        if (entity.startsWith("#x") || entity.startsWith("#X")) {
            return isHexNumber(entity, 2);
        }
        return entity.startsWith("#") && isDecimalNumber(entity, 1);
    }

    private static boolean isDecimalNumber(String text, int start) {
        if (text.length() <= start) return false;
        for (int i = start; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isHexNumber(String text, int start) {
        if (text.length() <= start) return false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isDigit(ch)
                    && (ch < 'a' || ch > 'f')
                    && (ch < 'A' || ch > 'F')) {
                return false;
            }
        }
        return true;
    }

    private static String decodeXmlString(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&gt;", ">")
                .replace("&lt;", "<");
    }

    public List<Data> getData() {
        return data.isEmpty() ? Collections.emptyList() : data;
    }

    public static class Data {
        private final String param;
        private final String text;

        Data(String param, String text) {
            this.param = param;
            this.text = text;
        }

        public String getParam() {
            return TextUtils.isEmpty(param) ? "" : param;
        }

        public String getText() {
            return TextUtils.isEmpty(text) ? "" : text;
        }
    }
}
