package com.github.catvod.crawler;

import com.github.catvod.crawler.python.IPyLoader;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.LOG;
import com.undcover.freedom.pyramid.PythonLoader;
import com.undcover.freedom.pyramid.PythonSpider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class pyLoader implements IPyLoader {
    private PythonLoader pythonLoader;
    private final ConcurrentHashMap<String, Spider> spiders;
    private String lastConfig = null; // 记录上次的配置

    public pyLoader() {
        spiders = new ConcurrentHashMap<>();
    }

    @Override
    public synchronized void clear() {
        spiders.clear();
        if (pythonLoader != null) {
            pythonLoader.clear();
        }
        lastConfig = null;
        recentPyKey = null;
    }

    @Override
    public void setConfig(String jsonStr) {
        if (jsonStr != null && !jsonStr.equals(lastConfig)) {
            LOG.i("echo-setConfig 初始化json ");
            getPythonLoader().setConfig(jsonStr);
            lastConfig = jsonStr;
        }
    }

    private String recentPyKey;
    @Override
    public void setRecentPyKey(String key) {
        recentPyKey = key;
    }

    @Override
    public synchronized Spider getSpider(String key, String cls, String ext) {
        if (spiders.containsKey(key)) {
            LOG.i("echo-getSpider spider缓存: " + key);
            return spiders.get(key);
        }
        try {
            LOG.i("echo-getSpider url: " + cls);
            Spider sp = getPythonLoader().getSpider(key, cls, ext);
            if (sp == null) return new SpiderNull();
            if (sp instanceof SpiderNull) return sp;
//            LOG.i("echo-getSpider homeContent: " + sp.homeContent(true));
            spiders.put(key, sp);
            LOG.i("echo-getSpider 加载spider: " + key);
            return sp;
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return new SpiderNull();
    }

    @Override
    public Object[] proxyInvoke(Map<String, String> params){
        return proxyInvoke(params, recentPyKey);
    }

    @Override
    public Object[] proxyInvoke(Map<String, String> params, String key){
        if(key==null || key.isEmpty())return null;
        LOG.i("echo-recentPyKey" + key);
        try {
            Spider spider = spiders.get(key);
            if (!(spider instanceof PythonSpider)) return null;
            PythonSpider originalSpider = (PythonSpider) spider;
            return originalSpider.proxyLocal(params);
        } catch (Throwable th) {
            LOG.i("echo-proxyInvoke_Throwable:---" + th.getMessage());
            th.printStackTrace();
        }
        return null;
    }

    private PythonLoader getPythonLoader() {
        if (pythonLoader == null) {
            pythonLoader = PythonLoader.getInstance().setApplication(App.getInstance());
        }
        return pythonLoader;
    }

}
