package com.github.tvbox.osc.util;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchHelper {

    private static final Pattern SEASON_SUFFIX_PATTERN = Pattern.compile(
            "^(.*?)[\\s._-]*(?:(第\\s*)?([0-9]{1,2}|[零一二三四五六七八九十百千万两]+)(\\s*季)|([0-9]{1,2}))$");

    private static final String[] CHINESE_NUMBERS = {
            "零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十"
    };

    public static HashMap<String, String> getSourcesForSearch() {
        HashMap<String, String> mCheckSources;
        try {
            String api = Hawk.get(HawkConfig.API_URL, "");
            if(api.isEmpty())return null;
            HashMap<String, HashMap<String, String>> mCheckSourcesForApi = Hawk.get(HawkConfig.SOURCES_FOR_SEARCH, new HashMap<>());
            mCheckSources = mCheckSourcesForApi.get(api);
        } catch (Exception e) {
            return null;
        }
        if (mCheckSources == null || mCheckSources.isEmpty()) {
            mCheckSources = getSources();
        }
//        else {
//            HashMap<String, String> newSources = getSources();
//            for (Map.Entry<String, String> entry : newSources.entrySet()) {
//                String newKey = entry.getKey();
//                String newValue = entry.getValue();
//                if (!mCheckSources.containsKey(newKey)) {
//                    mCheckSources.put(newKey, newValue);
//                }
//            }
//            Iterator<Map.Entry<String, String>> iterator = mCheckSources.entrySet().iterator();
//            while (iterator.hasNext()) {
//                Map.Entry<String, String> oldEntry = iterator.next();
//                String oldKey = oldEntry.getKey();
//                if (!newSources.containsKey(oldKey)) {
//                    iterator.remove();
//                }
//            }
//        }
        return mCheckSources;
    }

    public static void putCheckedSources(HashMap<String, String> mCheckSources,boolean isAll) {
        String api = Hawk.get(HawkConfig.API_URL, "");
        if (api.isEmpty()) {
            return;
        }
        HashMap<String, HashMap<String, String>> mCheckSourcesForApi = Hawk.get(HawkConfig.SOURCES_FOR_SEARCH,null);

        if(isAll){
            if (mCheckSourcesForApi == null) return;
            if (mCheckSourcesForApi.containsKey(api)) mCheckSourcesForApi.remove(api);
        }else {
            if (mCheckSourcesForApi == null) mCheckSourcesForApi = new HashMap<>();
            mCheckSourcesForApi.put(api, mCheckSources);
        }
        SearchActivity.setCheckedSourcesForSearch(mCheckSources);
        Hawk.put(HawkConfig.SOURCES_FOR_SEARCH, mCheckSourcesForApi);
    }

    public static HashMap<String, String> getSources(){
        HashMap<String, String> mCheckSources = new HashMap<>();
        for (SourceBean bean : ApiConfig.get().getSourceBeanList()) {
            if (!bean.isSearchable()) {
                continue;
            }
            mCheckSources.put(bean.getKey(), "1");
        }
        return mCheckSources;
    }

    public static List<String> splitWords(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        result.add(text);
        String[] parts = text.split("\\W+");
        if (parts.length > 1) {
            result.addAll(Arrays.asList(parts));
        }
        Matcher matcher = SEASON_SUFFIX_PATTERN.matcher(text.trim());
        if (matcher.matches()) {
            String baseName = matcher.group(1).trim();
            if (!baseName.isEmpty()
                    && !Character.isDigit(baseName.charAt(baseName.length() - 1))
                    && !result.contains(baseName)) {
                result.add(baseName);
            }
            String season = matcher.group(3) == null ? matcher.group(5) : matcher.group(3);
            if (!baseName.isEmpty() && season != null) {
                String nextSeason = nextSeason(season);
                if (nextSeason != null) {
                    String prefix = matcher.group(3) == null || matcher.group(2) == null ? "" : matcher.group(2);
                    String suffix = matcher.group(3) == null || matcher.group(4) == null ? "" : matcher.group(4);
                    String nextTitle = baseName + prefix + nextSeason + suffix;
                    if (!result.contains(nextTitle)) {
                        result.add(nextTitle);
                    }
                }
            }
        }
        return result;
    }

    private static String nextSeason(String season) {
        String value = season.trim();
        try {
            return String.valueOf(Integer.parseInt(value) + 1);
        } catch (NumberFormatException ignored) {
            if ("两".equals(value)) {
                return "三";
            }
            for (int i = 0; i < CHINESE_NUMBERS.length - 1; i++) {
                if (CHINESE_NUMBERS[i].equals(value)) {
                    return CHINESE_NUMBERS[i + 1];
                }
            }
            return null;
        }
    }

}
