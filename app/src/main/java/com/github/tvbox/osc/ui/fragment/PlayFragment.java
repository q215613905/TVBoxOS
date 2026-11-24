package com.github.tvbox.osc.ui.fragment;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;

import com.github.catvod.crawler.Spider;
import com.google.gson.JsonObject;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.Subtitle;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.dlna.CastVideo;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.ExoPlayer;
import com.github.tvbox.osc.player.IjkMediaPlayer;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.player.MusicPlaybackService;
import com.github.tvbox.osc.player.TrackInfo;
import com.github.tvbox.osc.player.TrackInfoBean;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.player.danmu.DanmuLoadController;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.dialog.CastDeviceDialog;
import com.github.tvbox.osc.ui.dialog.DanmuSettingDialog;
import com.github.tvbox.osc.ui.dialog.EpisodeDialog;
import com.github.tvbox.osc.ui.dialog.SearchDanmuDialog;
import com.github.tvbox.osc.ui.dialog.SearchSubtitleDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.SubtitleDialog;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.github.tvbox.osc.util.XWalkUtils;
import com.github.tvbox.osc.util.parser.SuperParse;
import com.github.tvbox.osc.util.thunder.Jianpian;
import com.github.tvbox.osc.util.thunder.Thunder;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.Response;
import com.obsez.android.lib.filechooser.ChooserDialog;
import com.orhanobut.hawk.Hawk;
import com.google.android.exoplayer2.text.Cue;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xwalk.core.XWalkJavascriptResult;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkSettings;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkWebResourceRequest;
import org.xwalk.core.XWalkWebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.io.File;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.jessyan.autosize.AutoSize;
import master.flame.danmaku.ui.widget.DanmakuView;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;
import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.ProgressManager;
import xyz.doikki.videoplayer.player.VideoView;

public class PlayFragment extends BaseLazyFragment {
    private static final int MSG_PARSE_TIMEOUT = 100;
    private static final int MSG_RESOLVE_PLAY_URL_TIMEOUT = 101;
    private static final int MSG_SWITCH_LINE_PLAY_TIMEOUT = 102;
    private static final long RESOLVE_PLAY_URL_TIMEOUT_MS = 15 * 1000L;
    private static final long SWITCH_LINE_PLAY_TIMEOUT_MS = 20 * 1000L;
    private MyVideoView mVideoView;
    private TextView mPlayLoadTip;
    private ImageView mPlayLoadErr;
    private ProgressBar mPlayLoading;
    private VodController mController;
    private SourceViewModel sourceViewModel;
    private Observer<JSONObject> playResultObserver;
    private JSONObject qualityResult;
    private Handler mHandler;
    private boolean exitingPreview = false;
    private boolean audioPlayback;
    private boolean switchingPlayback;
    private boolean reusePlayerOnSwitch;
    private boolean releasePlayerOnSwitch;
    private boolean previewMode;
    private String playLyric;
    private String lyricCacheKey;
    private String playArtwork;
    private DanmakuView mDanmuView;
    private DanmuLoadController danmuLoadController;
    private final List<Cue> exoCues = new ArrayList<>();
    private boolean exoInternalSubtitle;

    private final long videoDuration = -1;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_play;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE) {
            mController.mSubtitleView.setTextSize((int) event.obj);
        }
        if (event.type == RefreshEvent.TYPE_SET_DANMU_SETTINGS) {
            setDanmuViewSettings(event.obj instanceof Boolean && (Boolean) event.obj);
        } else if (event.type == RefreshEvent.TYPE_DANMU_REFRESH) {
            checkDanmu(event.obj instanceof String ? (String) event.obj : "");
        }
    }

    @Override
    protected void init() {
        initView();
        initDanmuView();
        initViewModel();
        initData();
    }

    private void initDanmuView() {
        mDanmuView = findViewById(R.id.danmaku);
        danmuLoadController = new DanmuLoadController(mVideoView, mController, mDanmuView);
    }

    private void setDanmuViewSettings(boolean reload) {
        if (danmuLoadController != null) danmuLoadController.applySettings(reload);
    }

    private void checkDanmu(String danmu) {
        checkDanmu(danmu, null);
    }

    private void checkDanmu(String danmu, DanmuLoadController.LoadCallback callback) {
        if (danmuLoadController != null) {
            VodInfo.VodSeries series = mVodInfo == null ? null : getCurrentSeries(mVodInfo.playFlag, mVodInfo.playIndex);
            danmuLoadController.check(danmu, mVodInfo == null ? "" : mVodInfo.name, series == null ? "" : series.name, callback);
        }
    }

    private void startDanmuIfReady() {
        if (danmuLoadController != null) danmuLoadController.startIfReady();
    }

    private void resetDanmuState() {
        if (danmuLoadController != null) danmuLoadController.reset();
    }

    private void reloadDanmuForPlayback() {
        if (danmuLoadController != null) danmuLoadController.reloadForPlayback();
    }

    public long getSavedProgress(String url) {
        int st = 0;
        try {
            st = mVodPlayerCfg.getInt("st");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        long skip = st * 1000L;
        Object theCache=CacheManager.getCache(MD5.string2MD5(url));
        if (theCache == null) {
            return skip;
        }
        long rec = 0;
        if (theCache instanceof Long) {
            rec = (Long) theCache;
        } else if (theCache instanceof String) {
            try {
                rec = Long.parseLong((String) theCache);
            } catch (NumberFormatException e) {
                LOG.i("echo-String value is not a valid long.");
            }
        } else {
            LOG.i("echo-Value cannot be converted to long.");
        }
        return Math.max(rec, skip);
    }

    private void initView() {
        EventBus.getDefault().register(this);
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_PARSE_TIMEOUT:
                        stopParse();
                        errorWithRetry("嗅探错误", false);
                        break;
                    case MSG_RESOLVE_PLAY_URL_TIMEOUT:
                        handleResolvePlayUrlTimeout();
                        break;
                    case MSG_SWITCH_LINE_PLAY_TIMEOUT:
                        handleSwitchLinePlayTimeout();
                        break;
                }
                return false;
            }
        });
        mVideoView = findViewById(R.id.mVideoView);
        mPlayLoadTip = findViewById(R.id.play_load_tip);
        mPlayLoading = findViewById(R.id.play_loading);
        mPlayLoadErr = findViewById(R.id.play_load_error);
        mController = new VodController(requireContext());
        View.OnTouchListener passThroughTouch = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return mController != null && mController.onTouchEvent(event);
            }
        };
        mPlayLoadTip.setOnTouchListener(passThroughTouch);
        mPlayLoadErr.setOnTouchListener(passThroughTouch);
        mController.mLyricView.setTextSize(previewMode ? 16 : 24);
        mController.setCanChangePosition(true);
        mController.setEnableInNormal(true);
        mController.setGestureEnabled(true);
        ProgressManager progressManager = new ProgressManager() {
            @Override
            public void saveProgress(String url, long progress) {
                CacheManager.save(MD5.string2MD5(url), progress);
                if (webPlayUrl != null && progress > 0) {
                    markPlaybackStarted();
                    hideTipOnUiThread();
                }
            }

            @Override
            public long getSavedProgress(String url) {
                return PlayFragment.this.getSavedProgress(url);
            }
        };
        mVideoView.setProgressManager(progressManager);
        mVideoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                if (webPlayUrl != null && isStartedPlayState(playState)) {
                    markPlaybackStarted();
                    hideTipOnUiThread();
                }
                if (switchingPlayback) {
                    if (playState == VideoView.STATE_PLAYBACK_COMPLETED) {
                        LOG.i("echo-music keep session while resolving next episode");
                        return;
                    } else if (playState == VideoView.STATE_ERROR) {
                        switchingPlayback = false;
                        audioPlayback = false;
                    } else if (isStartedPlayState(playState)) {
                        Boolean audioOnly = getAudioOnlyPlayback();
                        if (audioOnly != null) {
                            switchingPlayback = false;
                            audioPlayback = audioOnly;
                        }
                    }
                }
                if (!switchingPlayback) updateMusicSession();
                startDanmuIfReady();
            }
        });
        mController.setListener(new VodController.VodControlListener() {
            @Override
            public void showDanmuSetting() {
                DanmuSettingDialog dialog = new DanmuSettingDialog(requireContext());
                dialog.setDanmuSearchListener(new DanmuSettingDialog.DanmuSearchListener() {
                    @Override
                    public void openSearchDanmuDialog() {
                        SearchDanmuDialog searchDanmuDialog = new SearchDanmuDialog(requireContext());
                        searchDanmuDialog.setDanmuLoader(new SearchDanmuDialog.DanmuLoader() {
                            @Override
                            public void loadDanmu(String danmu) {
                                if (!isAdded()) return;
                                checkDanmu(danmu);
                            }
                        });
                        VodInfo.VodSeries series = mVodInfo == null ? null : getCurrentSeries(mVodInfo.playFlag, mVodInfo.playIndex);
                        searchDanmuDialog.setEpisode(series == null ? "" : series.name);
                        searchDanmuDialog.setSearchWord(mVodInfo == null ? "" : mVodInfo.name);
                        searchDanmuDialog.show();
                    }
                });
                dialog.show();
            }

            @Override
            public boolean toggleDanmu() {
                return danmuLoadController != null && danmuLoadController.toggle();
            }

            @Override
            public void searchDanmuUi(boolean longClick) {
                VodInfo.VodSeries series = mVodInfo == null ? null : getCurrentSeries(mVodInfo.playFlag, mVodInfo.playIndex);
                ApiConfig.get().searchDanmuUi(mVodInfo == null ? "" : mVodInfo.name, series == null ? "" : series.name, longClick);
            }

            @Override
            public void playNext(boolean rmProgress) {
                String preProgressKey = progressKey;
                PlayFragment.this.playNext(rmProgress);
                if (rmProgress && preProgressKey != null)
                    CacheManager.delete(MD5.string2MD5(preProgressKey), 0);
            }

            @Override
            public void playPre() {
                PlayFragment.this.playPrevious();
            }

            @Override
            public void showEpisodeDialog() {
                PlayFragment.this.showEpisodeDialog();
            }

            @Override
            public void changeParse(ParseBean pb) {
                autoRetryCount = 0;
                hasAutoSwitchedPlayer = false;
                triedLineFlags.clear();
                doParse(pb);
            }

            @Override
            public void updatePlayerCfg() {
                mVodInfo.playerCfg = mVodPlayerCfg.toString();
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodPlayerCfg));
            }

            @Override
            public void replay(boolean replay) {
                autoRetryCount = 0;
                hasAutoSwitchedPlayer = false;
                triedLineFlags.clear();
                if(replay){
                    play(true);
                }else {
                    reloadDanmuForPlayback();
                    if(webPlayUrl!=null && !webPlayUrl.isEmpty()) {
                        stopParse();
                        initParseLoadFound();
                        if(mVideoView!=null) mVideoView.release();
                        goPlayUrl(webPlayUrl,webHeaderMap);
                    }else {
                        play(false);
                    }
                }
            }

            @Override
            public void errReplay() {
                errorWithRetry("视频播放出错", false);
            }

            @Override
            public void selectSubtitle() {
                try {
                    selectMySubtitle();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void selectAudioTrack() {
                selectMyAudioTrack();
            }

            @Override
            public void selectVideoTrack() {
                selectMyVideoTrack();
            }

            @Override
            public void prepared() {
                initSubtitleView();
                if (mVideoView != null) mVideoView.prepared();
                startDanmuIfReady();
            }
            @Override
            public void startPlayUrl(String url, HashMap<String, String> headers) {
                if (!TextUtils.isEmpty(m3u8SourceUrl) && !isM3u8ProxyUrl(url)) clearM3u8ProxyUrl();
                goPlayUrl(url, headers);
            }

            @Override
            public void onM3u8ProxyUrl(String proxyUrl, String sourceUrl) {
                m3u8ProxyUrl = proxyUrl;
                m3u8SourceUrl = sourceUrl;
            }

            @Override
            public void clickCast() {
                showCastDialog();
            }

            @Override
            public void setAllowSwitchPlayer(boolean isAllow){allowSwitchPlayer=isAllow;}
        });
        mVideoView.setVideoController(mController);
    }

    private void showCastDialog() {
        if (TextUtils.isEmpty(webPlayUrl)) {
            Toast.makeText(mContext, "暂无可投屏播放地址", Toast.LENGTH_SHORT).show();
            return;
        }
        HashMap<String, String> headers = webHeaderMap == null ? null : new HashMap<>(webHeaderMap);
        CastVideo video = new CastVideo(getCastUrl(webPlayUrl), getCastTitle(), headers, getCastPosition());
        CastDeviceDialog dialog = new CastDeviceDialog(requireActivity(), video);
        dialog.setOnCastListener(new CastDeviceDialog.OnCastListener() {
            @Override
            public void onCastSuccess() {
                if (mVideoView != null) mVideoView.pause();
            }

            @Override
            public void onCastFailed() {
            }
        });
        dialog.show();
    }

    private String getCastTitle() {
        if (mVodInfo == null) return "TVBox";
        try {
            VodInfo.VodSeries series = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
            return mVodInfo.name + " " + series.name;
        } catch (Exception e) {
            return TextUtils.isEmpty(mVodInfo.name) ? "TVBox" : mVodInfo.name;
        }
    }

    private long getCastPosition() {
        try {
            return mVideoView == null ? 0 : mVideoView.getCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
    }

    private String getCastUrl(String url) {
        if (TextUtils.isEmpty(url)) return url;
        if (isM3u8ProxyUrl(url) && !TextUtils.isEmpty(m3u8SourceUrl)) return m3u8SourceUrl;
        String local = ControlManager.get().getAddress(true);
        String server = ControlManager.get().getAddress(false);
        if (!TextUtils.isEmpty(local) && !TextUtils.isEmpty(server) && url.startsWith(local)) {
            return server + url.substring(local.length());
        }
        return url;
    }

    private boolean isM3u8ProxyUrl(String url) {
        return !TextUtils.isEmpty(m3u8ProxyUrl) && url.equals(m3u8ProxyUrl);
    }

    private void clearM3u8ProxyUrl() {
        m3u8ProxyUrl = null;
        m3u8SourceUrl = null;
    }

    //设置字幕
    void setSubtitle(String path) {
        if (path != null && path .length() > 0) {
            hideExoInternalSubtitle();
            // 设置字幕
            mController.mSubtitleView.setVisibility(View.GONE);
            mController.mSubtitleView.setSubtitlePath(path);
            mController.mSubtitleView.setVisibility(View.VISIBLE);
        }
    }

    void selectMySubtitle() throws Exception {
        SubtitleDialog subtitleDialog = new SubtitleDialog(getActivity());
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        boolean hasInternal = mController.mSubtitleView.hasInternal || hasExoInternalSubtitle(mediaPlayer);
        if (hasInternal) {
            subtitleDialog.selectInternal.setVisibility(View.VISIBLE);
        } else {
            subtitleDialog.selectInternal.setVisibility(View.GONE);
        }
        subtitleDialog.setExoInternalSubtitle(mediaPlayer instanceof ExoPlayer && exoInternalSubtitle);
        subtitleDialog.setSubtitleViewListener(new SubtitleDialog.SubtitleViewListener() {
            @Override
            public void setTextSize(int size) {
                mController.mSubtitleView.setTextSize(size);
            }
            @Override
            public void setSubtitleDelay(int milliseconds) {
                if (mediaPlayer instanceof ExoPlayer && exoInternalSubtitle) {
                    ((ExoPlayer) mediaPlayer).setInternalSubtitleDelay(SubtitleHelper.getTimeDelay());
                } else {
                    mController.mSubtitleView.setSubtitleDelay(milliseconds);
                }
            }
            @Override
            public void selectInternalSubtitle() {
                selectMyInternalSubtitle();
            }
            @Override
            public void setTextStyle(int style) {
                setSubtitleViewTextStyle(style);
            }

            @Override
            public void setSubtitleScale(int scale) {
                if (mediaPlayer instanceof ExoPlayer && exoInternalSubtitle) {
                    applyExoSubtitleSettings();
                }
            }

            @Override
            public void moveSubtitle(float offset) {
                if (mediaPlayer instanceof ExoPlayer && exoInternalSubtitle) {
                    applyExoSubtitleSettings();
                }
            }
        });
        subtitleDialog.setSearchSubtitleListener(new SubtitleDialog.SearchSubtitleListener() {
            @Override
            public void openSearchSubtitleDialog() {
                SearchSubtitleDialog searchSubtitleDialog = new SearchSubtitleDialog(getActivity());
                searchSubtitleDialog.setSubtitleLoader(new SearchSubtitleDialog.SubtitleLoader() {
                    @Override
                    public void loadSubtitle(Subtitle subtitle) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String zimuUrl = subtitle.getUrl();
                                LOG.i("echo-Remote Subtitle Url: " + zimuUrl);
                                setSubtitle(zimuUrl);//设置字幕
                                searchSubtitleDialog.dismiss();
                            }
                        });
                    }
                });
                if(mVodInfo.playFlag.contains("Ali")||mVodInfo.playFlag.contains("parse")){
                    searchSubtitleDialog.setSearchWord(mVodInfo.playNote);
                }else {
                    searchSubtitleDialog.setSearchWord(mVodInfo.name);
                }
                searchSubtitleDialog.show();
            }
        });
        subtitleDialog.setLocalFileChooserListener(new SubtitleDialog.LocalFileChooserListener() {
            @Override
            public void openLocalFileChooserDialog() {
                new ChooserDialog(getActivity())
                        .withFilter(false, false, "srt", "ass", "scc", "stl", "ttml")
                        .withStartFile("/storage/emulated/0/Download")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                LOG.i("echo-Local Subtitle Path: " + path);
                                setSubtitle(path);//设置字幕
                            }
                        })
                        .build()
                        .show();
            }
        });
        subtitleDialog.show();
    }

    @SuppressLint("UseCompatLoadingForColorStateLists")
    void setSubtitleViewTextStyle(int style) {
        if (style == 0) {
            mController.mSubtitleView.setTextColor(getContext().getResources().getColorStateList(R.color.color_FFFFFF));
        } else if (style == 1) {
            mController.mSubtitleView.setTextColor(getContext().getResources().getColorStateList(R.color.color_FFB6C1));
        }
    }

    private boolean isSameTrack(TrackInfoBean left, TrackInfoBean right) {
        return left.renderId == right.renderId
                && left.trackGroupId == right.trackGroupId
                && left.trackId == right.trackId;
    }

    void selectMyAudioTrack() {
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        TrackInfo trackInfo = null;
        if (mediaPlayer instanceof IjkMediaPlayer) {
            trackInfo = ((IjkMediaPlayer)mediaPlayer).getTrackInfo();
        }
        if (mediaPlayer instanceof ExoPlayer) {
            trackInfo = ((ExoPlayer)mediaPlayer).getTrackInfo();
        }
        if (trackInfo == null) {
            Toast.makeText(mContext, "没有音轨", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TrackInfoBean> bean = trackInfo.getAudio();
        if (bean.size() < 1) return;
        SelectDialog<TrackInfoBean> dialog = new SelectDialog<>(getActivity());
        dialog.setTip("切换音轨");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<TrackInfoBean>() {
            @Override
            public void click(TrackInfoBean value, int pos) {
                try {
                    for (TrackInfoBean audio : bean) {
                        audio.selected = isSameTrack(audio, value);
                    }
                    mediaPlayer.pause();
                    long progress = mediaPlayer.getCurrentPosition();//保存当前进度，ijk 切换轨道 会有快进几秒
                    if (mediaPlayer instanceof IjkMediaPlayer)((IjkMediaPlayer)mediaPlayer).setTrack(value.trackId,progressKey);
                    if (mediaPlayer instanceof ExoPlayer)((ExoPlayer)mediaPlayer).setTrack(value,progressKey);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if(mediaPlayer instanceof IjkMediaPlayer)mediaPlayer.seekTo(progress);
                            mediaPlayer.start();
                        }
                    }, 200);
                    dialog.dismiss();
                } catch (Exception e) {
                    LOG.e("切换音轨出错");
                }
            }

            @Override
            public String getDisplay(TrackInfoBean val) {
                return val.name;
            }
        }, new DiffUtil.ItemCallback<TrackInfoBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return isSameTrack(oldItem, newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return isSameTrack(oldItem, newItem);
            }
        }, bean, trackInfo.getAudioSelected(false));
        dialog.show();
    }

    void selectMyVideoTrack() {
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        TrackInfo trackInfo = null;
        if (mediaPlayer instanceof IjkMediaPlayer) {
            trackInfo = ((IjkMediaPlayer) mediaPlayer).getTrackInfo();
        } else if (mediaPlayer instanceof ExoPlayer) {
            trackInfo = ((ExoPlayer) mediaPlayer).getTrackInfo();
        }
        if (trackInfo == null || trackInfo.getVideo().isEmpty()) {
            Toast.makeText(mContext, "没有视轨", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TrackInfoBean> tracks = trackInfo.getVideo();
        SelectDialog<TrackInfoBean> dialog = new SelectDialog<>(getActivity());
        dialog.setTip("切换视轨");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<TrackInfoBean>() {
            @Override
            public void click(TrackInfoBean value, int pos) {
                try {
                    for (TrackInfoBean track : tracks) {
                        track.selected = isSameTrack(track, value);
                    }
                    mediaPlayer.pause();
                    long progress = mediaPlayer.getCurrentPosition();
                    if (mediaPlayer instanceof IjkMediaPlayer) {
                        ((IjkMediaPlayer) mediaPlayer).setTrack(value.trackId);
                    } else if (mediaPlayer instanceof ExoPlayer) {
                        ((ExoPlayer) mediaPlayer).setTrack(value, "");
                    }
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mediaPlayer.seekTo(progress);
                            mediaPlayer.start();
                        }
                    }, 200);
                    dialog.dismiss();
                } catch (Exception e) {
                    LOG.e("echo-switch-video-track-error:" + e.getMessage());
                }
            }

            @Override
            public String getDisplay(TrackInfoBean val) {
                return val.name;
            }
        }, new DiffUtil.ItemCallback<TrackInfoBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return isSameTrack(oldItem, newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return isSameTrack(oldItem, newItem);
            }
        }, tracks, trackInfo.getVideoSelected(false));
        dialog.show();
    }

    void selectMyInternalSubtitle() {
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        TrackInfo trackInfo = null;
        if (mediaPlayer instanceof IjkMediaPlayer) {
            trackInfo = ((IjkMediaPlayer) mediaPlayer).getTrackInfo();
        } else if (mediaPlayer instanceof ExoPlayer) {
            trackInfo = ((ExoPlayer) mediaPlayer).getTrackInfo();
        }
        if (trackInfo == null) {
            Toast.makeText(mContext, "没有内置字幕", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TrackInfoBean> bean = trackInfo.getSubtitle();
        if (bean.size() < 1) return;
        SelectDialog<TrackInfoBean> dialog = new SelectDialog<>(getActivity());
        dialog.setTip("切换内置字幕");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<TrackInfoBean>() {
            @Override
            public void click(TrackInfoBean value, int pos) {
                try {
                    for (TrackInfoBean subtitle : bean) {
                        subtitle.selected = isSameTrack(subtitle, value);
                    }
                    if (mediaPlayer instanceof IjkMediaPlayer) {
                        mediaPlayer.pause();
                        long progress = mediaPlayer.getCurrentPosition();
                        mController.mSubtitleView.destroy();
                        mController.mSubtitleView.clearSubtitleCache();
                        mController.mSubtitleView.isInternal = true;
                        ((IjkMediaPlayer) mediaPlayer).setTrack(value.trackId);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mediaPlayer.seekTo(progress);
                                mediaPlayer.start();
                            }
                        }, 800);
                    } else if (mediaPlayer instanceof ExoPlayer) {
                        mController.mSubtitleView.setVisibility(View.GONE);
                        mController.mSubtitleView.destroy();
                        mController.mSubtitleView.clearSubtitleCache();
                        mController.mSubtitleView.isInternal = false;
                        exoInternalSubtitle = true;
                        ((ExoPlayer) mediaPlayer).setTrack(value, "");
                        ((ExoPlayer) mediaPlayer).setInternalSubtitleDelay(SubtitleHelper.getTimeDelay());
                        mController.mExoSubtitleView.setVisibility(View.VISIBLE);
                        applyExoSubtitleSettings();
                    }
                    dialog.dismiss();
                } catch (Exception e) {
                    LOG.e("echo-switch-internal-subtitle-error:" + e.getMessage());
                }
            }

            @Override
            public String getDisplay(TrackInfoBean val) {
                return val.name;
            }
        }, new DiffUtil.ItemCallback<TrackInfoBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return isSameTrack(oldItem, newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return isSameTrack(oldItem, newItem);
            }
        }, bean, trackInfo.getSubtitleSelected(false));
        dialog.show();
    }

    private boolean hasExoInternalSubtitle(AbstractPlayer mediaPlayer) {
        if (!(mediaPlayer instanceof ExoPlayer)) return false;
        TrackInfo trackInfo = ((ExoPlayer) mediaPlayer).getTrackInfo();
        return trackInfo != null && !trackInfo.getSubtitle().isEmpty();
    }

    private void hideExoInternalSubtitle() {
        exoInternalSubtitle = false;
        exoCues.clear();
        if (mController != null && mController.mExoSubtitleView != null) {
            mController.mExoSubtitleView.setCues(exoCues);
            mController.mExoSubtitleView.setVisibility(View.GONE);
        }
    }

    private void onExoCues(List<Cue> cues) {
        if (!isAdded() || !exoInternalSubtitle) return;
        exoCues.clear();
        if (cues != null) exoCues.addAll(cues);
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                applyExoSubtitleSettings();
            }
        });
    }

    private void applyExoSubtitleSettings() {
        if (!exoInternalSubtitle || mController == null || mController.mExoSubtitleView == null) return;
        float scale = SubtitleHelper.getExoSubtitleScale() / 100f;
        float position = SubtitleHelper.getExoSubtitlePosition();
        mController.mExoSubtitleView.setFractionalTextSize(0.0533f * scale);
        mController.mExoSubtitleView.setBottomPaddingFraction(limit(0.08f + position / 100f, 0f, 0.9f));

        List<Cue> displayCues = new ArrayList<>();
        for (Cue cue : exoCues) {
            if (cue.bitmap == null) {
                displayCues.add(cue);
                continue;
            }
            Cue.Builder builder = cue.buildUpon();
            if (cue.size != Cue.DIMEN_UNSET) {
                builder.setSize(limit(cue.size * scale, 0f, 1f));
            }
            if (cue.bitmapHeight != Cue.DIMEN_UNSET) {
                builder.setBitmapHeight(limit(cue.bitmapHeight * scale, 0f, 1f));
            }
            if (cue.line != Cue.DIMEN_UNSET) {
                builder.setLine(limit(cue.line - position / 100f, 0f, 1f), cue.lineType);
            }
            displayCues.add(builder.build());
        }
        mController.mExoSubtitleView.setCues(displayCues);
    }

    private float limit(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    void setTip(String msg, boolean loading, boolean err) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(new Runnable() { //影魔
            @Override
            public void run() {
                mPlayLoadTip.setText(msg);
                mPlayLoadTip.setVisibility(View.VISIBLE);
                mPlayLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
                mPlayLoadErr.setVisibility(err ? View.VISIBLE : View.GONE);
            }
        });
    }

    void hideTip() {
        mPlayLoadTip.setVisibility(View.GONE);
        mPlayLoading.setVisibility(View.GONE);
        mPlayLoadErr.setVisibility(View.GONE);
    }

    void hideTipOnUiThread() {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideTip();
            }
        });
    }

    void errorWithRetry(String err, boolean finish) {
        if (isPlaybackStarted()) {
            cancelPlayTimeout();
            hideTipOnUiThread();
            return;
        }
        if (!autoRetry()) {
            stopMusicSessionForFailedPlayback();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (finish) {
                        setTip(err, false, true);
                        Toast.makeText(mContext, err, Toast.LENGTH_SHORT).show();
                    } else {
                        setTip(err, false, true);
                    }
                }
            });
        }
    }

    private void stopMusicSessionForFailedPlayback() {
        switchingPlayback = false;
        audioPlayback = false;
        MusicPlaybackService.stop(getContext(), this);
    }

    void playUrl(String url, HashMap<String, String> headers) {
        startSwitchLinePlayTimeout();
        url = attachProxySiteKey(url);
        if(!url.startsWith("data:application"))EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, url));//更新播放地址
        if (!Hawk.get(HawkConfig.M3U8_PURIFY, false)) {
            goPlayUrl(url,headers);
            return;
        }
        if (url.startsWith("http://127.0.0.1") || !url.contains(".m3u8")) {
            goPlayUrl(url,headers);
            return;
        }
        if(DefaultConfig.noAd(mVodInfo.playFlag)){
            goPlayUrl(url,headers);
            return;
        }
        LOG.i("echo-playM3u8:" + url);
        mController.playM3u8(url,headers);
    }
    public void goPlayUrl(String url, HashMap<String, String> headers) {
        LOG.i("echo-goPlayUrl:" + url);
        if (TextUtils.isEmpty(url)) {
            handleResolvePlayUrlFailed("获取播放地址为空");
            return;
        }
        if(autoRetryCount==0)webPlayUrl=url;
        if (mActivity == null) return;
        if (!isAdded()) return;
        final String finalUrl = url;
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopParse();
                if (mVideoView != null) {
                    if (finalUrl != null) {
                        String url = finalUrl;
                        try {
                            int playerType = mVodPlayerCfg.getInt("pl");
                            if (playerType >= 10) {
                                mVideoView.release();
                                VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
                                String playTitle = mVodInfo.name + " " + vs.name;
                                setTip("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + "进行播放", true, false);
                                boolean callResult = false;
                                long progress = getSavedProgress(progressKey);
                                callResult = PlayerHelper.runExternalPlayer(playerType, requireActivity(), url, playTitle, playSubtitle, headers, progress);
                                setTip("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + (callResult ? "成功" : "失败"), callResult, !callResult);
                                return;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        playTimeoutBasePosition = getSavedProgress(progressKey);
                        boolean forceExoPlayer = url.startsWith("data:application/dash+xml;base64,")
                                || url.contains(".mpd") || url.contains("type=mpd");
                        if (url.startsWith("data:application/dash+xml;base64,")) {
                            PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg, 2);
                            App.getInstance().setDashData(url.split("base64,")[1]);
                            url = ControlManager.get().getAddress(true) + "dash/proxy.mpd";
                        } else if (url.contains(".mpd") || url.contains("type=mpd")) {
                            PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg, 2);
                        } else {
                            PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg);
                        }
                        mController.hidePauseRoot();
                        boolean reusePlayer = !forceExoPlayer && mVideoView.getMediaPlayer() != null;
                        if (!reusePlayer) hideTip();
                        if (!reusePlayer && mVideoView.getMediaPlayer() != null) {
                            mVideoView.release();
                        }
                        mVideoView.setProgressKey(progressKey);
                        if (headers != null) {
                            mVideoView.setUrl(url, headers);
                        } else {
                            mVideoView.setUrl(url);
                        }
                        startSwitchLinePlayTimeout();
                        if (reusePlayer) {
                            mVideoView.skipPositionWhenPlay((int) playTimeoutBasePosition);
                            mVideoView.replay(false);
                        } else {
                            mVideoView.start();
                        }
                        mController.resetSpeed();
                    }
                }
            }
        });
    }

    private String attachProxySiteKey(String url) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(sourceKey)) return url;
        if (!url.startsWith(ControlManager.get().getAddress(true) + "proxy?")) return url;
        if (url.contains("siteKey=")) return url;
        try {
            return url + (url.contains("?") ? "&" : "?") + "siteKey=" + URLEncoder.encode(sourceKey, "UTF-8");
        } catch (Throwable th) {
            return url + (url.contains("?") ? "&" : "?") + "siteKey=" + sourceKey;
        }
    }

    private void initSubtitleView() {
        TrackInfo trackInfo = null;
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        mController.mLyricView.setTextSize(previewMode ? 16 : 24);
        mController.mLyricView.setVisibility(View.GONE);
        mController.mLyricView.reset();
        mController.mLyricView.bindToMediaPlayer(mediaPlayer);
        mController.mLyricView.setMergeSameTime(true);
        mController.mLyricView.setLyricMode(true);
        mController.mLyricView.setPlaySubtitleCacheKey(lyricCacheKey);
        mController.mSubtitleView.hasInternal = false;
        mController.mSubtitleView.isInternal = false;
        hideExoInternalSubtitle();
        if (mediaPlayer instanceof IjkMediaPlayer) {
            trackInfo = ((IjkMediaPlayer)mediaPlayer).getTrackInfo();
            if (trackInfo != null && trackInfo.getSubtitle().size() > 0) {
                mController.mSubtitleView.hasInternal = true;
            }
            //默认选中第一个音轨 一般第一个音轨是国语 && 加载上一次选中的
            ((IjkMediaPlayer)mediaPlayer).loadDefaultTrack(trackInfo,progressKey);
            ((IjkMediaPlayer)mediaPlayer).setOnTimedTextListener(new IMediaPlayer.OnTimedTextListener() {
                @Override
                public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
                    if(text==null)return;
                    if (mController.mSubtitleView.isInternal) {
                        com.github.tvbox.osc.subtitle.model.Subtitle subtitle = new com.github.tvbox.osc.subtitle.model.Subtitle();
                        subtitle.content = text.getText();
                        mController.mSubtitleView.onSubtitleChanged(subtitle);
                    }
                }
            });
        }
        if (mediaPlayer instanceof ExoPlayer) {
            ExoPlayer exoPlayer = (ExoPlayer) mediaPlayer;
            trackInfo = exoPlayer.getTrackInfo();
            if (trackInfo != null && !trackInfo.getSubtitle().isEmpty()) {
                mController.mSubtitleView.hasInternal = true;
                exoInternalSubtitle = true;
                mController.mExoSubtitleView.setVisibility(View.VISIBLE);
                exoPlayer.setInternalSubtitleDelay(SubtitleHelper.getTimeDelay());
                exoPlayer.setOnCuesListener(new ExoPlayer.OnCuesListener() {
                    @Override
                    public void onCues(List<Cue> cues) {
                        onExoCues(cues);
                    }
                });
                applyExoSubtitleSettings();
            }
            exoPlayer.loadDefaultTrack(progressKey);
        }
        if (!TextUtils.isEmpty(playLyric)) {
            mController.mLyricView.setSubtitlePath(playLyric);
            mController.mLyricView.setVisibility(View.VISIBLE);
        }
        mController.mSubtitleView.bindToMediaPlayer(mVideoView.getMediaPlayer());
        mController.mSubtitleView.setPlaySubtitleCacheKey(subtitleCacheKey);
        String subtitlePathCache = (String)CacheManager.getCache(MD5.string2MD5(subtitleCacheKey));
        if (subtitlePathCache != null && !subtitlePathCache.isEmpty()) {
            hideExoInternalSubtitle();
            mController.mSubtitleView.setSubtitlePath(subtitlePathCache);
        } else {
            if (playSubtitle != null && playSubtitle .length() > 0) {
                hideExoInternalSubtitle();
                mController.mSubtitleView.setSubtitlePath(playSubtitle);
            } else {
                if (mController.mSubtitleView.hasInternal) {
                    if (mediaPlayer instanceof ExoPlayer) {
                        ((ExoPlayer) mediaPlayer).setInternalSubtitleDelay(SubtitleHelper.getTimeDelay());
                        exoInternalSubtitle = true;
                        mController.mExoSubtitleView.setVisibility(View.VISIBLE);
                        applyExoSubtitleSettings();
                    } else if (mediaPlayer instanceof IjkMediaPlayer && trackInfo != null && trackInfo.getSubtitle().size() > 0) {
                        mController.mSubtitleView.isInternal = true;
                        List<TrackInfoBean> subtitleTrackList = trackInfo.getSubtitle();
                        int selectedIndex = trackInfo.getSubtitleSelected(true);
                        boolean hasMandarin = false;
                        for (TrackInfoBean subtitleTrackInfoBean : subtitleTrackList) {
                            if ("国语".equals(subtitleTrackInfoBean.language)) {
                                hasMandarin = true;
                                if (selectedIndex != subtitleTrackInfoBean.trackId) {
                                    ((IjkMediaPlayer) mediaPlayer).setTrack(subtitleTrackInfoBean.trackId);
                                    break;
                                }
                            }
                        }
                        if (!hasMandarin) {
                            ((IjkMediaPlayer) mediaPlayer).setTrack(subtitleTrackList.get(0).trackId);
                        }
                    }
                }
            }
        }
    }

    private String getSubtitleUrl(JSONObject object) {
        if (object == null) return "";
        String url = object.optString("url", "");
        if (!TextUtils.isEmpty(url) && !FileUtils.hasExtension(url)) {
            String format = object.optString("format", "");
            String name = object.optString("name", "字幕");
            String ext = ".srt";
            if ("text/x-ssa".equals(format)) {
                ext = ".ass";
            } else if ("text/vtt".equals(format)) {
                ext = ".vtt";
            } else if ("text/lrc".equals(format)) {
                ext = ".lrc";
            }
            String filename = name + (name.toLowerCase(Locale.ROOT).endsWith(ext) ? "" : ext);
            url += "#" + mController.encodeUrl(filename);
        }
        return url;
    }

    private boolean isLyricSubtitle(String name) {
        if (TextUtils.isEmpty(name)) return false;
        String value = name.toLowerCase(Locale.ROOT);
        return value.contains("lyric") || value.contains("lrc") || name.contains("歌词");
    }

    private void clearLyricView() {
        if (mController == null || mController.mLyricView == null) return;
        mController.mLyricView.setVisibility(View.GONE);
        mController.mLyricView.destroy();
        mController.mLyricView.setText("");
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        playResultObserver = new Observer<JSONObject>() {
            @Override
            public void onChanged(JSONObject info) {
                if (info == null) publishQuality(null);
                if (info != null) {
                    try {
                        if (isStalePlayResult(info)) {
                            LOG.i("echo-ignore stale play result");
                            return;
                        }
                        mHandler.removeMessages(MSG_RESOLVE_PLAY_URL_TIMEOUT);
                        publishQuality(info);
                        webPlayUrl = null;
                        progressKey = info.optString("proKey", null);
                        boolean parse = info.optString("parse", "1").equals("1");
                        boolean jx = info.optString("jx", "0").equals("1");
                        playSubtitle = info.optString("subt", "");
                        playLyric = info.optString("lyric", "");
                        lyricCacheKey = info.optString("lyricKey", null);
                        if (TextUtils.isEmpty(lyricCacheKey) && !TextUtils.isEmpty(progressKey)) {
                            lyricCacheKey = progressKey + "-lyric";
                        }
                        JSONArray lyrics = info.optJSONArray("lyrics");
                        if (lyrics != null && lyrics.length() > 0) {
                            playLyric = getSubtitleUrl(lyrics.optJSONObject(0));
                        }
                        JSONArray subtitles = info.optJSONArray("subs");
                        if (subtitles != null) {
                            for (int i = 0; i < subtitles.length(); i++) {
                                JSONObject obj = subtitles.optJSONObject(i);
                                if (obj == null) continue;
                                String url = getSubtitleUrl(obj);
                                String name = obj.optString("name", "");
                                if (isLyricSubtitle(name)) {
                                    if (TextUtils.isEmpty(playLyric)) playLyric = url;
                                } else if (TextUtils.isEmpty(playSubtitle)) {
                                    playSubtitle = url;
                                }
                            }
                        }
                        subtitleCacheKey = info.optString("subtKey", null);
                        String playUrl = info.optString("playUrl", "");
                        String flag = info.optString("flag");
                        Object rawUrl = info.opt("url");
                        String url = rawUrl instanceof JSONArray ? rawUrl.toString() : String.valueOf(rawUrl);
                        if(url.startsWith("[")){
                            url=mController.firstUrlByArray(url);
                        }
                        String artwork = info.optString("artwork", "");
                        if (TextUtils.isEmpty(artwork) && !TextUtils.isEmpty(playLyric) && mVodInfo != null) {
                            artwork = mVodInfo.pic;
                        }
                        playArtwork = artwork;
                        mVideoView.setArtwork(playArtwork);
                        String msg = info.optString("msg", "");
                        if (!TextUtils.isEmpty(msg)) {
                            handleResolvePlayUrlFailed(msg);
                            return;
                        }
                        String danmaku = info.optString("danmaku", "").trim();
                        final String danmuProgressKey = progressKey;
                        HashMap<String, String> headers = null;
                        webUserAgent = null;
                        webHeaderMap = null;
                        headers = getHeaders(info);
                        if (headers != null) {
                            webHeaderMap = headers;
                            webUserAgent = getHeaderValue(headers, "user-agent");
                            if (webUserAgent != null) webUserAgent = webUserAgent.trim();
                        }
                        if (parse || jx) {
                            boolean userJxList = (playUrl.isEmpty() && ApiConfig.get().getVipParseFlags().contains(flag)) || jx;
                            initParse(flag, userJxList, playUrl, url);
                        } else {
                            mController.showParse(false);
                            playUrl(playUrl + url, headers);
                        }
                        if (TextUtils.isEmpty(danmaku)) {
                            checkDanmu("");
                            searchDanmu("");
                        } else {
                            checkDanmu(danmaku, () -> {
                                if (TextUtils.equals(danmuProgressKey, progressKey)) {
                                    searchDanmu("");
                                }
                            });
                        }
                    } catch (Throwable th) {
                        handleResolvePlayUrlFailed("获取播放信息错误");
                    }
                } else {
//                    获取播放信息错误后只需再重试一次
                    handleResolvePlayUrlFailed("获取播放信息错误");
                }
            }
        };
        sourceViewModel.playResult.observeForever(playResultObserver);
    }

    public boolean selectQuality(int position) {
        if (qualityResult == null) return false;
        try {
            JSONArray urls = new JSONArray(qualityResult.optString("url"));
            String url = urls.optString(position * 2 + 1);
            if (TextUtils.isEmpty(url)) return false;
            String playUrl = qualityResult.optString("playUrl", "");
            String flag = qualityResult.optString("flag");
            boolean parse = qualityResult.optString("parse", "1").equals("1");
            boolean jx = qualityResult.optString("jx", "0").equals("1");
            HashMap<String, String> headers = getHeaders(qualityResult);
            if (parse || jx) {
                boolean userJxList = (playUrl.isEmpty() && ApiConfig.get().getVipParseFlags().contains(flag)) || jx;
                initParse(flag, userJxList, playUrl, url);
            } else {
                mController.showParse(false);
                playUrl(playUrl + url, headers);
            }
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    private void publishQuality(JSONObject info) {
        try {
            JSONArray urls = new JSONArray(info == null ? "" : info.optString("url"));
            if (urls.length() < 4 || urls.length() % 2 != 0) throw new JSONException("invalid quality urls");
            qualityResult = new JSONObject(info.toString());
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PLAY_QUALITY, qualityResult));
        } catch (Throwable th) {
            qualityResult = null;
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PLAY_QUALITY, null));
        }
    }

    private void searchDanmu(String danmaku) {
        if (!TextUtils.isEmpty(danmaku) || !DanmakuApi.canSearch() || mVodInfo == null) return;
        VodInfo.VodSeries series = getCurrentSeries(mVodInfo.playFlag, mVodInfo.playIndex);
        String key = progressKey;
        DanmakuApi.search(mVodInfo.name, series == null ? "" : series.name, new DanmakuApi.SearchCallback() {
            @Override
            public void onFound(String url) {
                if (!TextUtils.equals(key, progressKey)) return;
                checkDanmu(url);
            }

            @Override
            public void onNotFound() {
                if (!TextUtils.equals(key, progressKey)) return;
                checkDanmu("");
            }
        });
    }

    boolean isStalePlayResult(JSONObject info) {
        if (mVodInfo == null || mVodInfo.seriesMap == null || TextUtils.isEmpty(progressKey)) return false;
        String resultKey = info.optString("proKey", "");
        if (!TextUtils.isEmpty(resultKey) && !progressKey.equals(resultKey)) return true;
        String resultFlag = info.optString("flag", "");
        if (!TextUtils.isEmpty(resultFlag) && !resultFlag.equals(mVodInfo.playFlag)) return true;
        String sourceUrl = info.optString("key", "");
        if (!TextUtils.isEmpty(sourceUrl)) {
            VodInfo.VodSeries vs = getCurrentSeries(mVodInfo.playFlag, mVodInfo.playIndex);
            return vs != null && !sourceUrl.equals(vs.url);
        }
        return false;
    }

    public void setData(Bundle bundle) {
//        mVodInfo = (VodInfo) bundle.getSerializable("VodInfo");
        mVodInfo = App.getInstance().getVodInfo();
        sourceKey = bundle.getString("sourceKey");
        sourceBean = ApiConfig.get().getSource(sourceKey);
        ApiConfig.get().setCurrentPlaySourceKey(sourceKey);
        initPlayerCfg();
        triedLineFlags.clear();
        play(false);
    }

    private void initData() {
        /*Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {

        }*/
    }

    void initPlayerCfg() {
        try {
            mVodPlayerCfg = new JSONObject(mVodInfo.playerCfg);
        } catch (Throwable th) {
            mVodPlayerCfg = new JSONObject();
        }
        try {
            if (!mVodPlayerCfg.has("pl")) {
                mVodPlayerCfg.put("pl", (sourceBean.getPlayerType() == -1) ? (int)Hawk.get(HawkConfig.PLAY_TYPE, 2) : sourceBean.getPlayerType());
            }
            if (mVodPlayerCfg.optInt("pl", 2) == 0) {
                mVodPlayerCfg.put("pl", 2);
            }
            mVodPlayerCfg.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            if (!mVodPlayerCfg.has("ijk")) {
                mVodPlayerCfg.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "硬解码"));
            }
            if (!mVodPlayerCfg.has("sc")) {
                mVodPlayerCfg.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            }
            if (!mVodPlayerCfg.has("sp")) {
                mVodPlayerCfg.put("sp", 1.0f);
            }
            if (!mVodPlayerCfg.has("st")) {
                mVodPlayerCfg.put("st", 0);
            }
            if (!mVodPlayerCfg.has("et")) {
                mVodPlayerCfg.put("et", 0);
            }
        } catch (Throwable th) {

        }
        mController.setPlayerConfig(mVodPlayerCfg);
    }

    public boolean onBackPressed() {
        int requestedOrientation = requireActivity().getRequestedOrientation();
        if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
            requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            mController.mLandscapePortraitBtn.setText("竖屏");
        }
        if (mController.onBackPressed()) {
            return true;
        }
        return false;
    }

    public void setExitingPreview(boolean exitingPreview) {
        this.exitingPreview = exitingPreview;
    }

    private boolean hasAudioOnlyPlayback() {
        return Boolean.TRUE.equals(getAudioOnlyPlayback());
    }

    private Boolean getAudioOnlyPlayback() {
        if (mVideoView == null) return null;
        try {
            AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
            TrackInfo trackInfo = null;
            if (mediaPlayer instanceof IjkMediaPlayer) {
                trackInfo = ((IjkMediaPlayer) mediaPlayer).getTrackInfo();
            } else if (mediaPlayer instanceof ExoPlayer) {
                trackInfo = ((ExoPlayer) mediaPlayer).getTrackInfo();
            }
            if (trackInfo == null) return null;
            return !trackInfo.getAudio().isEmpty() && trackInfo.getVideo().isEmpty();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void updateMusicSession() {
        if (!MusicPlaybackService.isSupported(getContext())) return;
        if (switchingPlayback) return;
        Boolean audioOnly = getAudioOnlyPlayback();
        if (audioOnly != null) audioPlayback = audioOnly;
        if (audioPlayback && TextUtils.isEmpty(playArtwork) && mVodInfo != null && !TextUtils.isEmpty(mVodInfo.pic)) {
            playArtwork = mVodInfo.pic;
            mVideoView.setArtwork(playArtwork);
        }
        if (mVodInfo == null || mVideoView == null || !audioPlayback
                || mVideoView.getCurrentPlayState() == VideoView.STATE_ERROR
                || mVideoView.getCurrentPlayState() == VideoView.STATE_PLAYBACK_COMPLETED) {
            MusicPlaybackService.stop(getContext(), this);
            audioPlayback = false;
            return;
        }
        VodInfo.VodSeries currentSeries = getCurrentSeries(mVodInfo.playFlag, mVodInfo.playIndex);
        String episode = currentSeries == null || TextUtils.isEmpty(currentSeries.name) ? "" : currentSeries.name;
        MusicPlaybackService.update(getContext(), this,
                TextUtils.isEmpty(mVodInfo.name) ? "TVBox" : mVodInfo.name,
                episode, mVodInfo.pic, mVideoView.getCurrentPosition(),
                mVideoView.getDuration(), mVideoView.isPlaying());
    }

    public void resumeFromMediaSession() {
        if (mVideoView != null) {
            mVideoView.start();
            updateMusicSession();
        }
    }

    public void pauseFromMediaSession() {
        if (mVideoView != null) {
            mVideoView.pause();
            updateMusicSession();
        }
    }

    public void stopFromMediaSession() {
        if (mVideoView != null) mVideoView.pause();
        MusicPlaybackService.stop(getContext(), this);
    }

    public void seekFromMediaSession(long position) {
        if (mVideoView != null) {
            mVideoView.seekTo(position);
            updateMusicSession();
        }
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            if (mController.onKeyEvent(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event !=null) {
            if (mController.onKeyDown(keyCode,event)) {
                return true;
            }
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event !=null) {
            if (mController.onKeyUp(keyCode,event)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mVideoView != null && !exitingPreview && !hasAudioOnlyPlayback()) {
            mVideoView.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        exitingPreview = false;
        if (mVideoView != null) {
            mVideoView.resume();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) {
            if (mVideoView != null) {
                mVideoView.pause();
            }
        } else {
            if (mVideoView != null) {
                mVideoView.resume();
            }
        }
        super.onHiddenChanged(hidden);
    }

    @Override
    public void onDestroyView() {
        audioPlayback = false;
        switchingPlayback = false;
        MusicPlaybackService.stop(getContext(), this);
        if (sourceViewModel != null && playResultObserver != null) {
            sourceViewModel.playResult.removeObserver(playResultObserver);
            playResultObserver = null;
        }
        super.onDestroyView();
        ApiConfig.get().setCurrentPlaySourceKey("");
        cancelPlayTimeout();
        EventBus.getDefault().unregister(this);
        if (danmuLoadController != null) {
            danmuLoadController.destroy();
            danmuLoadController = null;
        }
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        stopLoadWebView(true);
        stopParse();
        mController.stopOther();
    }

    private VodInfo mVodInfo;
    private JSONObject mVodPlayerCfg;
    private String sourceKey;
    private SourceBean sourceBean;

    public void playNext(boolean isProgress) {
        triedLineFlags.clear();
        boolean hasNext;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasNext = false;
        } else {
            hasNext = mVodInfo.playIndex + 1 < mVodInfo.seriesMap.get(mVodInfo.playFlag).size();
        }
        if (!hasNext) {
            Toast.makeText(requireContext(), "已经是最后一集了!", Toast.LENGTH_SHORT).show();
            return;
        }else {
            mVodInfo.playIndex++;
        }
        reusePlayerOnSwitch = true;
        play(false);
    }

    public void playPrevious() {
        triedLineFlags.clear();
        boolean hasPre = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasPre = false;
        } else {
            hasPre = mVodInfo.playIndex - 1 >= 0;
        }
        if (!hasPre) {
            Toast.makeText(requireContext(), "已经是第一集了!", Toast.LENGTH_SHORT).show();
            return;
        }
        mVodInfo.playIndex--;
        reusePlayerOnSwitch = true;
        play(false);
    }

    private void showEpisodeDialog() {
        if (!isAdded() || mVodInfo == null || mVodInfo.seriesMap == null || TextUtils.isEmpty(mVodInfo.playFlag)) return;
        List<VodInfo.VodSeries> episodes = mVodInfo.seriesMap.get(mVodInfo.playFlag);
        if (episodes == null || episodes.isEmpty()) return;
        final int episodeCount = episodes.size();
        final int currentPosition = Math.max(0, Math.min(mVodInfo.playIndex, episodeCount - 1));
        final int groupStart;
        final List<VodInfo.VodSeries> dialogEpisodes;
        final int selectedPosition;
        if (episodeCount > 200) {
            int groupCount = episodeCount <= 400 ? 60 : 120;
            groupStart = (currentPosition / groupCount) * groupCount;
            int groupEnd = Math.min(groupStart + groupCount, episodeCount);
            dialogEpisodes = new ArrayList<>(episodes.subList(groupStart, groupEnd));
            selectedPosition = currentPosition - groupStart;
        } else {
            groupStart = 0;
            dialogEpisodes = episodes;
            selectedPosition = currentPosition;
        }
        String title = TextUtils.isEmpty(mVodInfo.name) ? "选集" : mVodInfo.name + " 选集";
        EpisodeDialog dialog = new EpisodeDialog(requireContext(), title, dialogEpisodes, selectedPosition, new EpisodeDialog.EpisodeSelectListener() {
            @Override
            public void selectEpisode(int position) {
                int actualPosition = groupStart + position;
                if (position < 0 || position >= dialogEpisodes.size()
                        || actualPosition >= episodes.size() || actualPosition == mVodInfo.playIndex) return;
                triedLineFlags.clear();
                mVodInfo.playIndex = actualPosition;
                reusePlayerOnSwitch = true;
                play(false);
            }
        });
        dialog.show();
    }

    private int autoRetryCount = 0;
    private long lastRetryTime = 0;  // 记录上次调用时间（毫秒）

    private boolean allowSwitchPlayer = true;
    private boolean hasAutoSwitchedPlayer = false;
    private int autoSwitchedPlayerType = -1;
    private boolean allowAutoSwitchLine = true;
    private boolean playbackStarted = false;
    private long playTimeoutBasePosition = 0;
    private java.util.Set<String> triedLineFlags = new java.util.HashSet<>();  // 记录已尝试过的线路

    private void restoreAutoSwitchedPlayer() {
        if (autoSwitchedPlayerType < 0) return;
        releasePlayerOnSwitch = true;
        try {
            LOG.i("echo-autoRetry restore player: " + mVodPlayerCfg.optInt("pl", -1) + " -> " + autoSwitchedPlayerType);
            mVodPlayerCfg.put("pl", autoSwitchedPlayerType);
            mVodInfo.playerCfg = mVodPlayerCfg.toString();
            mController.setPlayerConfig(mVodPlayerCfg);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodPlayerCfg));
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            autoSwitchedPlayerType = -1;
        }
    }

    boolean autoRetry() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRetryTime > 60_000){
            LOG.i("echo-reset-autoRetryCount");
            autoRetryCount = 0;
            allowSwitchPlayer = true;
            hasAutoSwitchedPlayer = false;
            triedLineFlags.clear();
        }

        lastRetryTime = currentTime;  // 更新上次调用时间
        if (loadFoundVideoUrls != null && loadFoundVideoUrls.size() > 0) {
            autoRetryFromLoadFoundVideoUrls();
            return true;
        }
        if (webPlayUrl != null) {
            if (allowSwitchPlayer && !hasAutoSwitchedPlayer) {
                LOG.i("echo-autoRetry switch player and replay current url");
                int playerType = mVodPlayerCfg.optInt("pl", -1);
                boolean switchSkipped = mController.switchPlayer();
                hasAutoSwitchedPlayer = true;
                allowSwitchPlayer = false;
                if (!switchSkipped) {
                    autoSwitchedPlayerType = playerType;
                    stopParse();
                    initParseLoadFound();
                    if(mVideoView!=null) mVideoView.release();
                    playUrl(webPlayUrl, webHeaderMap);
                    return true;
                }
            }
            LOG.i("echo-autoRetry current url failed after player switch, try next line");
            return tryNextLineIfEnabled();
        }
        return tryNextLineIfEnabled();
    }

    boolean tryNextLineIfEnabled() {
        restoreAutoSwitchedPlayer();
        if (allowAutoSwitchLine && Hawk.get(HawkConfig.AUTO_SWITCH_LINE, true)) return tryNextLine();
        LOG.i("echo-autoRetry line switching disabled");
        autoRetryCount = 0;
        allowSwitchPlayer = true;
        hasAutoSwitchedPlayer = false;
        triedLineFlags.clear();
        return false;
    }

    boolean tryNextLine() {
        if (mVodInfo == null || mVodInfo.seriesMap == null || mVodInfo.seriesMap.isEmpty()) {
            autoRetryCount = 0;
            triedLineFlags.clear();
            return false;
        }
        // 将当前线路标记为已尝试
        String currentFlag = mVodInfo.playFlag;
        int currentIndex = Math.max(mVodInfo.playIndex, 0);
        VodInfo.VodSeries currentSeries = getCurrentSeries(currentFlag, currentIndex);
        if (!TextUtils.isEmpty(currentFlag)) {
            triedLineFlags.add(currentFlag);
        }
        List<String> lineFlags = getLineFlagsInDisplayOrder();
        int currentLineIndex = findLineFlagIndex(lineFlags, currentFlag);
        int startLineIndex = currentLineIndex >= 0 ? currentLineIndex + 1 : 0;
        // 查找下一条未尝试过的线路
        String nextFlag = null;
        int nextIndex = 0;
        for (int i = startLineIndex; i < lineFlags.size(); i++) {
            String flag = lineFlags.get(i);
            List<VodInfo.VodSeries> seriesList = mVodInfo.seriesMap.get(flag);
            if (!triedLineFlags.contains(flag) && seriesList != null && !seriesList.isEmpty()) {
                nextFlag = flag;
                nextIndex = findSameEpisodeIndex(currentSeries, seriesList, currentIndex);
                break;
            }
        }
        if (nextFlag == null) {
            // 所有线路都已尝试过
            LOG.i("echo-autoRetry all lines exhausted");
            triedLineFlags.clear();
            autoRetryCount = 0;
            return requestDetailFallbackAfterLinesExhausted();
        }
        final String flagToSwitch = nextFlag;
        final String preProgressKey = progressKey;
        final long savedProgress = TextUtils.isEmpty(preProgressKey) ? 0 : getSavedProgress(preProgressKey);
        final long preProgress = Math.max(savedProgress, mVideoView == null ? 0 : mVideoView.getCurrentPosition());
        LOG.i("echo-autoRetry switch line: " + mVodInfo.playFlag + " -> " + flagToSwitch);
        // 显示切换线路提示
        if (isAdded()) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, "线路切换至" + flagToSwitch, Toast.LENGTH_SHORT).show();
                }
            });
        }
        // 切换到新线路
        mVodInfo.playFlag = flagToSwitch;
        mVodInfo.playIndex = nextIndex;
        autoRetryCount = 0;
        allowSwitchPlayer = true;
        hasAutoSwitchedPlayer = false;
        inheritProgressKey = preProgressKey;
        inheritProgress = preProgress;
        reusePlayerOnSwitch = true;
        play(false);
        return true;
    }

    private boolean requestDetailFallbackAfterLinesExhausted() {
        Activity activity = getActivity();
        if (!(activity instanceof DetailActivity)) {
            return false;
        }
        return ((DetailActivity) activity).startDetailFallbackAfterLinesExhausted();
    }

    private List<String> getLineFlagsInDisplayOrder() {
        List<String> lineFlags = new java.util.ArrayList<>();
        if (mVodInfo == null || mVodInfo.seriesMap == null) {
            return lineFlags;
        }
        if (mVodInfo.seriesFlags != null) {
            for (VodInfo.VodSeriesFlag flag : mVodInfo.seriesFlags) {
                if (flag != null && !TextUtils.isEmpty(flag.name) && mVodInfo.seriesMap.containsKey(flag.name) && !lineFlags.contains(flag.name)) {
                    lineFlags.add(flag.name);
                }
            }
        }
        for (String flag : mVodInfo.seriesMap.keySet()) {
            if (!TextUtils.isEmpty(flag) && !lineFlags.contains(flag)) {
                lineFlags.add(flag);
            }
        }
        return lineFlags;
    }

    private int findLineFlagIndex(List<String> lineFlags, String currentFlag) {
        if (lineFlags == null || TextUtils.isEmpty(currentFlag)) {
            return -1;
        }
        for (int i = 0; i < lineFlags.size(); i++) {
            if (currentFlag.equals(lineFlags.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private VodInfo.VodSeries getCurrentSeries(String flag, int index) {
        if (flag == null || mVodInfo == null || mVodInfo.seriesMap == null) {
            return null;
        }
        List<VodInfo.VodSeries> currentList = mVodInfo.seriesMap.get(flag);
        if (currentList == null || currentList.isEmpty()) {
            return null;
        }
        int safeIndex = Math.max(0, Math.min(index, currentList.size() - 1));
        return currentList.get(safeIndex);
    }

    private int findSameEpisodeIndex(VodInfo.VodSeries currentSeries, List<VodInfo.VodSeries> targetList, int fallbackIndex) {
        if (targetList == null || targetList.isEmpty()) {
            return 0;
        }
        if (targetList.size() == 1) {
            return 0;
        }
        if (currentSeries == null || TextUtils.isEmpty(currentSeries.name)) {
            return Math.max(0, Math.min(fallbackIndex, targetList.size() - 1));
        }
        int currentEpisode = extractEpisodeNumber(currentSeries.name);
        int matchedIndex = -1;
        int bestScore = 0;
        for (int i = 0; i < targetList.size(); i++) {
            VodInfo.VodSeries targetSeries = targetList.get(i);
            int score = getEpisodeMatchScore(currentSeries.name, currentEpisode, targetSeries == null ? null : targetSeries.name);
            if (score > bestScore) {
                bestScore = score;
                matchedIndex = i;
            }
        }
        if (matchedIndex >= 0) {
            return matchedIndex;
        }
        return Math.max(0, Math.min(fallbackIndex, targetList.size() - 1));
    }

    private int getEpisodeMatchScore(String currentName, int currentEpisode, String targetName) {
        if (TextUtils.isEmpty(currentName) || TextUtils.isEmpty(targetName)) {
            return 0;
        }
        if (targetName.equalsIgnoreCase(currentName)) {
            return 100;
        }
        if (currentEpisode >= 0 && extractEpisodeNumber(targetName) == currentEpisode) {
            return 80;
        }
        String currentLower = currentName.toLowerCase(Locale.ROOT);
        String targetLower = targetName.toLowerCase(Locale.ROOT);
        if (currentEpisode < 0 && currentName.length() >= 2 && targetLower.contains(currentLower)) {
            return 70;
        }
        if (currentEpisode < 0 && targetName.length() >= 2 && currentLower.contains(targetLower)) {
            return 60;
        }
        return 0;
    }

    private int extractEpisodeNumber(String name) {
        if (TextUtils.isEmpty(name)) {
            return -1;
        }
        try {
            String text = name.replaceAll("\\[.*?\\]|\\(.*?\\)", "");
            text = text.replaceAll("\\b(19|20)\\d{2}\\b", "");
            text = text.toLowerCase(Locale.ROOT).replaceAll("2160p|1080p|720p|480p|4k|h26[45]|x26[45]|mp4", "");
            Matcher matcher = Pattern.compile("(?i)(?:ep|\\u7b2c|e|[\\-\\.\\s])\\s?(\\d{1,4})").matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            String number = text.replaceAll("\\D+", "");
            if (!TextUtils.isEmpty(number)) {
                return Integer.parseInt(number);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    void autoRetryFromLoadFoundVideoUrls() {
        String videoUrl = loadFoundVideoUrls.poll();
        HashMap<String,String> header = loadFoundVideoUrlsHeader.get(videoUrl);
        playUrl(videoUrl, header);
    }

    void initParseLoadFound() {
        loadFoundCount.set(0);
        loadFoundVideoUrls = new LinkedList<String>();
        loadFoundVideoUrlsHeader = new HashMap<String, HashMap<String, String>>();
    }

    public void setPlayTitle(boolean show)
    {
        if(show){
            String playTitleInfo= "";
            if(mVodInfo!=null){
                playTitleInfo = mVodInfo.name + " " + mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex).name;
            }
            mController.setTitle(playTitleInfo);
        }else {
            mController.setTitle("");
        }
    }

    public void play(boolean reset) {
        if(mVodInfo==null)return;
        boolean reusePlayer = reusePlayerOnSwitch && !releasePlayerOnSwitch;
        reusePlayerOnSwitch = false;
        releasePlayerOnSwitch = false;
        switchingPlayback = true;
        audioPlayback = false;
        playArtwork = "";
        exitingPreview = false;
        VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodInfo));
        if (reusePlayer) {
            mPlayLoadTip.setVisibility(View.GONE);
            mPlayLoadErr.setVisibility(View.GONE);
            mPlayLoading.setVisibility(View.VISIBLE);
        } else {
            setTip("正在获取播放信息", true, false);
        }
        String playTitleInfo = mVodInfo.name + " " + vs.name;
        mController.setTitle(playTitleInfo);

        stopParse();
        playbackStarted = false;
        playTimeoutBasePosition = 0;
        webPlayUrl = null;
        webHeaderMap = null;
        initParseLoadFound();
        allowSwitchPlayer=true;
        hasAutoSwitchedPlayer=false;
        mController.stopOther();
        resetDanmuState();
        clearLyricView();
        mVideoView.clearArtwork();
        if(mVideoView!=null) {
            if (reusePlayer) {
                long previousPosition = mVideoView.getCurrentPosition();
                if (previousPosition > 0 && !TextUtils.isEmpty(progressKey)) {
                    CacheManager.save(MD5.string2MD5(progressKey), previousPosition);
                }
                AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                }
            } else {
                mVideoView.release();
            }
        }
        ImgUtil.clearMemoryCache();
        subtitleCacheKey = mVodInfo.sourceKey + "-" + mVodInfo.id + "-" + mVodInfo.playFlag + "-" + mVodInfo.playIndex+ "-" + vs.name + "-subt";
        progressKey = mVodInfo.sourceKey + mVodInfo.id + mVodInfo.playFlag + mVodInfo.playIndex + vs.name;
        startResolvePlayUrlTimeout();
        //重新播放清除现有进度
        if (reset) {
            CacheManager.delete(MD5.string2MD5(progressKey), 0);
            CacheManager.delete(MD5.string2MD5(subtitleCacheKey), 0);
        }else{
            inheritProgressIfNeeded();
            try{
                int playerType = mVodPlayerCfg.getInt("pl");
                if(playerType==1){
                    mController.mSubtitleView.setVisibility(View.VISIBLE);
                }else {
                    mController.mSubtitleView.setVisibility(View.GONE);
                }
            }catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if(Jianpian.isJpUrl(vs.url)){//荐片地址特殊判断
            String jp_url= vs.url;
            mController.showParse(false);
            if(vs.url.startsWith("tvbox-xg:")){
                playUrl(Jianpian.JPUrlDec(jp_url.substring(9)), null);
            }else {
                playUrl(Jianpian.JPUrlDec(jp_url), null);
            }
            return;
        }
        if (Thunder.play(vs.url, new Thunder.ThunderCallback() {
            @Override
            public void status(int code, String info) {
                if (code < 0) {
                    setTip(info, false, true);
                } else {
                    setTip(info, true, false);
                }
            }

            @Override
            public void list(Map<Integer, String> urlMap) {
            }

            @Override
            public void play(String url) {
                playUrl(url, null);
            }
        })) {
            mController.showParse(false);
            return;
        }
        sourceViewModel.getPlay(sourceKey, mVodInfo.playFlag, progressKey, vs.url, subtitleCacheKey);
    }

    private void inheritProgressIfNeeded() {
        try {
            if (TextUtils.isEmpty(inheritProgressKey) || TextUtils.isEmpty(progressKey)) return;
            if (TextUtils.equals(inheritProgressKey, progressKey)) return;
            if (inheritProgress <= 0) return;
            Object targetCache = CacheManager.getCache(MD5.string2MD5(progressKey));
            if (targetCache == null) {
                CacheManager.save(MD5.string2MD5(progressKey), inheritProgress);
            }
        } finally {
            inheritProgressKey = null;
            inheritProgress = 0;
        }
    }

    private String playSubtitle;
    private String subtitleCacheKey;
    private String progressKey;
    private String inheritProgressKey;
    private long inheritProgress;
    private String parseFlag;
    private String webUrl;
    private String webUserAgent;
    private HashMap<String, String > webHeaderMap;
    private String webPlayUrl;
    private String m3u8ProxyUrl;
    private String m3u8SourceUrl;

    private void initParse(String flag, boolean useParse, String playUrl, final String url) {
        parseFlag = flag;
        webUrl = url;
        ParseBean parseBean = null;
        mController.showParse(useParse);
        if (useParse) {
            parseBean = ApiConfig.get().getDefaultParse();
        } else {
            if (playUrl.startsWith("json:")) {
                parseBean = new ParseBean();
                parseBean.setType(1);
                parseBean.setUrl(playUrl.substring(5));
            } else if (playUrl.startsWith("parse:")) {
                String parseRedirect = playUrl.substring(6);
                for (ParseBean pb : ApiConfig.get().getParseBeanList()) {
                    if (pb.getName().equals(parseRedirect)) {
                        parseBean = pb;
                        break;
                    }
                }
            }
            if (parseBean == null) {
                parseBean = new ParseBean();
                parseBean.setType(0);
                parseBean.setUrl(playUrl);
            }
        }
        doParse(parseBean);
    }

    JSONObject jsonParse(String input, String json) throws JSONException {
        JSONObject jsonPlayData = new JSONObject(json);
        JSONObject playData = jsonPlayData.optJSONObject("data");
        if (playData == null) {
            playData = jsonPlayData;
        }
        String url = playData.optString("url", jsonPlayData.optString("url", ""));
        if (url.startsWith("//")) {
            url = "http:" + url;
        }
        boolean parse = false;
        if (url.startsWith("video://")) {
            url = url.substring(8);
            parse = true;
        }
        url = DefaultConfig.checkReplaceProxy(url);
        if (!url.startsWith("http") && !url.startsWith("data:application")) {
            return null;
        }
        parse = parse || playData.optInt("parse", jsonPlayData.optInt("parse", 0)) == 1;
        JSONObject headers = new JSONObject();
        HashMap<String, String> headerMap = getHeaders(jsonPlayData);
        HashMap<String, String> dataHeaderMap = getHeaders(playData);
        if (headerMap != null) putHeaders(headers, headerMap);
        if (dataHeaderMap != null) putHeaders(headers, dataHeaderMap);
        String ua = playData.optString("user-agent", jsonPlayData.optString("user-agent", ""));
        if (ua.trim().length() > 0) {
            headers.put("User-Agent", " " + ua);
        }
        String referer = playData.optString("referer", jsonPlayData.optString("referer", ""));
        if (referer.trim().length() > 0) {
            headers.put("Referer", " " + referer);
        }
        JSONObject taskResult = new JSONObject();
        taskResult.put("header", headers);
        taskResult.put("url", url);
        taskResult.put("parse", parse ? 1 : 0);
        return taskResult;
    }

    private HashMap<String, String> getHeaders(JSONObject object) {
        if (object == null) return null;
        HashMap<String, String> headers = new HashMap<>();
        appendHeaders(headers, object.opt("header"));
        appendHeaders(headers, object.opt("headers"));
        return headers.isEmpty() ? null : headers;
    }

    private void appendHeaders(HashMap<String, String> headers, Object rawHeaders) {
        if (rawHeaders == null || rawHeaders == JSONObject.NULL) return;
        try {
            JSONObject json = null;
            if (rawHeaders instanceof JSONObject) {
                json = (JSONObject) rawHeaders;
            } else if (rawHeaders instanceof String) {
                String text = ((String) rawHeaders).trim();
                if (!TextUtils.isEmpty(text)) {
                    json = new JSONObject(text);
                }
            }
            if (json == null) return;
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!TextUtils.isEmpty(key)) {
                    headers.put(key, json.optString(key, ""));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void putHeaders(JSONObject target, HashMap<String, String> headers) throws JSONException {
        if (target == null || headers == null) return;
        for (String key : headers.keySet()) {
            target.put(key, headers.get(key));
        }
    }

    private String getHeaderValue(HashMap<String, String> headers, String name) {
        if (headers == null || name == null) return null;
        for (String key : headers.keySet()) {
            if (name.equalsIgnoreCase(key)) {
                return headers.get(key);
            }
        }
        return null;
    }

    void startResolvePlayUrlTimeout() {
        cancelPlayTimeout();
        mHandler.sendEmptyMessageDelayed(MSG_RESOLVE_PLAY_URL_TIMEOUT, getResolvePlayUrlTimeoutMs());
    }

    private long getResolvePlayUrlTimeoutMs() {
        if (sourceBean == null) return RESOLVE_PLAY_URL_TIMEOUT_MS;
        return Math.max(RESOLVE_PLAY_URL_TIMEOUT_MS, (sourceBean.getPlayTimeoutSeconds() + 1L) * 1000L);
    }

    void startSwitchLinePlayTimeout() {
        if (!allowAutoSwitchLine) {
            cancelPlayTimeout();
            return;
        }
        cancelPlayTimeout();
        LOG.i("echo-switchLinePlay start timeout");
        mHandler.sendEmptyMessageDelayed(MSG_SWITCH_LINE_PLAY_TIMEOUT, SWITCH_LINE_PLAY_TIMEOUT_MS);
    }

    void cancelSwitchLinePlayTimeout() {
        cancelPlayTimeout();
    }

    void cancelPlayTimeout() {
        mHandler.removeMessages(MSG_RESOLVE_PLAY_URL_TIMEOUT);
        mHandler.removeMessages(MSG_SWITCH_LINE_PLAY_TIMEOUT);
    }

    public void setAutoSwitchLineEnabled(boolean enabled) {
        allowAutoSwitchLine = enabled;
        if (!enabled) {
            cancelPlayTimeout();
            triedLineFlags.clear();
        }
    }

    public void setPreviewMode(boolean previewMode) {
        this.previewMode = previewMode;
        if (mController != null) {
            mController.setPreviewMode(previewMode);
            mController.mLyricView.setTextSize(previewMode ? 16 : 24);
        }
    }

    public void pauseForHidden() {
        cancelPlayTimeout();
        stopParse();
        playbackStarted = false;
        if (mVideoView != null) {
            mVideoView.pause();
            mVideoView.release();
        }
        mController.stopOther();
        resetDanmuState();
        webPlayUrl = null;
        webHeaderMap = null;
        initParseLoadFound();
    }

    void markPlaybackStarted() {
        playbackStarted = true;
        cancelPlayTimeout();
    }

    boolean isPlaybackStarted() {
        if (playbackStarted) return true;
        if (mVideoView == null) return false;
        int state = mVideoView.getCurrentPlayState();
        return isStartedPlayState(state) || hasPlaybackProgress(mVideoView.getCurrentPosition()) || mVideoView.isPlaying();
    }

    boolean isStartedPlayState(int state) {
        return state == VideoView.STATE_PREPARED || state == VideoView.STATE_BUFFERED || state == VideoView.STATE_PLAYING;
    }

    boolean hasPlaybackProgress(long progress) {
        return progress > Math.max(playTimeoutBasePosition, 0) + 1000;
    }

    void handleResolvePlayUrlTimeout() {
        LOG.i("echo-resolvePlayUrl timeout, try next line");
        if (sourceViewModel != null) sourceViewModel.cancelPlayRequest();
        stopParse();
        if (!tryNextLineIfEnabled()) {
            stopMusicSessionForFailedPlayback();
            setTip("获取播放地址超时", false, true);
        }
    }

    void handleResolvePlayUrlFailed(String err) {
        LOG.i("echo-resolvePlayUrl failed, try next line: " + err);
        if (sourceViewModel != null) sourceViewModel.cancelPlayRequest();
        stopParse();
        if (tryNextLineIfEnabled()) return;
        cancelPlayTimeout();
        stopMusicSessionForFailedPlayback();
        setTip(err, false, true);
    }

    void handleSwitchLinePlayTimeout() {
        int state = mVideoView == null ? -1 : mVideoView.getCurrentPlayState();
        LOG.i("echo-switchLinePlay timeout state: " + state + ", started: " + playbackStarted);
        if (isPlaybackStarted()) {
            cancelPlayTimeout();
            hideTipOnUiThread();
            return;
        }
        LOG.i("echo-switchLinePlay timeout, try next line");
        stopParse();
        if (hasAutoSwitchedPlayer) {
            if (!tryNextLineIfEnabled()) {
                stopMusicSessionForFailedPlayback();
                setTip("播放超时", false, true);
            }
            return;
        }
        if (!autoRetry()) {
            stopMusicSessionForFailedPlayback();
            setTip("播放超时", false, true);
        }
    }

    void stopParse() {
        mHandler.removeMessages(MSG_PARSE_TIMEOUT);
        stopLoadWebView(false);
        OkGo.getInstance().cancelTag("play");
        OkGo.getInstance().cancelTag("json_jx");
        if (parseThreadPool != null) {
            try {
                parseThreadPool.shutdown();
                parseThreadPool = null;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    ExecutorService parseThreadPool;

    private void doParse(ParseBean pb) {
        stopParse();
        initParseLoadFound();
        if (pb.getType() == 4) {
            parseMix(pb,true);
        }
        else if (pb.getType() == 0) {
            setTip("正在嗅探播放地址", true, false);
            mHandler.removeMessages(MSG_PARSE_TIMEOUT);
            mHandler.sendEmptyMessageDelayed(MSG_PARSE_TIMEOUT, 20 * 1000);
            if(pb.getExt()!=null){
                // 解析ext
                try {
                    HashMap<String, String> reqHeaders = new HashMap<>();
                    JSONObject jsonObject = new JSONObject(pb.getExt());
                    HashMap<String, String> headerMap = getHeaders(jsonObject);
                    if (headerMap != null) {
                        for (String key : headerMap.keySet()) {
                            if (key.equalsIgnoreCase("user-agent")) {
                                webUserAgent = headerMap.get(key).trim();
                            } else {
                                reqHeaders.put(key, headerMap.get(key));
                            }
                        }
                        if(reqHeaders.size()>0)webHeaderMap = reqHeaders;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            loadWebView(pb.getUrl() + webUrl);

        } else if (pb.getType() == 1) { // json 解析
            setTip("正在解析播放地址", true, false);
            // 解析ext
            HttpHeaders reqHeaders = new HttpHeaders();
            try {
                JSONObject jsonObject = new JSONObject(pb.getExt());
                HashMap<String, String> headerMap = getHeaders(jsonObject);
                if (headerMap != null) {
                    for (String key : headerMap.keySet()) {
                        reqHeaders.put(key, headerMap.get(key));
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            OkGo.<String>get(pb.getUrl() + mController.encodeUrl(webUrl))
                    .tag("json_jx")
                    .headers(reqHeaders)
                    .execute(new AbsCallback<String>() {
                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            if (response.body() != null) {
                                return response.body().string();
                            } else {
                                throw new IllegalStateException("网络请求错误");
                            }
                        }

                        @Override
                        public void onSuccess(Response<String> response) {
                            String json = response.body();
                            try {
                                JSONObject rs = jsonParse(webUrl, json);
                                HashMap<String, String> headers = getHeaders(rs);
                                if (rs.optInt("parse", 0) == 1) {
                                    webHeaderMap = headers;
                                    if (headers != null) {
                                        webUserAgent = getHeaderValue(headers, "user-agent");
                                        if (webUserAgent != null) webUserAgent = webUserAgent.trim();
                                    }
                                    loadWebView(DefaultConfig.checkReplaceProxy(rs.getString("url")));
                                } else {
                                    playUrl(rs.getString("url"), headers);
                                }
                            } catch (Throwable e) {
                                e.printStackTrace();
                                errorWithRetry("解析错误", false);
//                                setTip("解析错误", false, true);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            errorWithRetry("解析错误", false);
//                            setTip("解析错误", false, true);
                        }
                    });
        } else if (pb.getType() == 2) { // json 扩展
            setTip("正在解析播放地址", true, false);
            parseThreadPool = Executors.newSingleThreadExecutor();
            LinkedHashMap<String, String> jxs = new LinkedHashMap<>();
            for (ParseBean p : ApiConfig.get().getParseBeanList()) {
                if (p.getType() == 1) {
                    jxs.put(p.getName(), p.mixUrl());
                }
            }
            parseThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    JSONObject rs = ApiConfig.get().jsonExt(pb.getUrl(), jxs, webUrl);
                    if (rs == null || !rs.has("url") || rs.optString("url").isEmpty()) {
//                        errorWithRetry("解析错误", false);
                        setTip("解析错误", false, true);
                    } else {
                        HashMap<String, String> headers = getHeaders(rs);
                        if (rs.has("jxFrom")) {
                            if(!isAdded())return;
                            requireActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext, "解析来自:" + rs.optString("jxFrom"), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        boolean parseWV = rs.optInt("parse", 0) == 1;
                        if (parseWV) {
                            String wvUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                            loadUrl(wvUrl);
                        } else {
                            playUrl(rs.optString("url", ""), headers);
                        }
                    }
                }
            });
        } else if (pb.getType() == 3) { // json 聚合
             parseMix(pb,false);
        }
    }

    private void parseMix(ParseBean pb,boolean isSuper)
    {
        setTip("正在解析播放地址", true, false);
        parseThreadPool = Executors.newSingleThreadExecutor();
        LinkedHashMap<String, HashMap<String, String>> jxs = new LinkedHashMap<>();
        LinkedHashMap<String, String> json_jxs = new LinkedHashMap<>();
        String extendName = "";
        for (ParseBean p : ApiConfig.get().getParseBeanList()) {
            HashMap<String, String> data = new HashMap<String, String>();
            data.put("url", p.getUrl());
            if (p.getUrl().equals(pb.getUrl())) {
                extendName = p.getName();
            }
            data.put("type", p.getType() + "");
            data.put("ext", p.getExt());
            jxs.put(p.getName(), data);

            if (p.getType() == 1) {
                json_jxs.put(p.getName(), p.mixUrl());
            }
        }
        String finalExtendName = extendName;
        parseThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                if(isSuper){
                    //并发执行 嗅探和json
                    JSONObject rs = SuperParse.parse(jxs, parseFlag+"123", webUrl);
                    if (!rs.has("url") || rs.optString("url").isEmpty()) {
                        setTip("解析错误", false, true);
                    } else {
                        if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                            if (rs.has("ua")) {
                                webUserAgent = rs.optString("ua").trim();
                            }
                            setTip("超级解析中", true, false);

                            if(!isAdded())return;
                            requireActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                                    stopParse();
                                    mHandler.removeMessages(MSG_PARSE_TIMEOUT);
                                    mHandler.sendEmptyMessageDelayed(MSG_PARSE_TIMEOUT, 20 * 1000);
                                    loadWebView(mixParseUrl);
                                }
                            });
                            parseThreadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    JSONObject res = SuperParse.doJsonJx(webUrl);
                                    rsJsonJX(res, true);
                                }
                            });
                        } else {
                            rsJsonJX(rs,false);
                        }
                    }
                }else {
                    JSONObject rs = ApiConfig.get().jsonExtMix(parseFlag + "111", pb.getUrl(), finalExtendName, jxs, webUrl);
                    if (rs == null || !rs.has("url") || rs.optString("url").isEmpty()) {
//                        errorWithRetry("解析错误", false);
                        setTip("解析错误", false, true);
                    } else {
                        if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                            if (rs.has("ua")) {
                                webUserAgent = rs.optString("ua").trim();
                            }
                            if(!isAdded())return;
                            requireActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                                    stopParse();
                                    setTip("正在嗅探播放地址", true, false);
                                    mHandler.removeMessages(MSG_PARSE_TIMEOUT);
                                    mHandler.sendEmptyMessageDelayed(MSG_PARSE_TIMEOUT, 20 * 1000);
                                    loadWebView(mixParseUrl);
                                }
                            });
                        } else {
                            rsJsonJX(rs,false);
                        }
                    }
                }
            }
        });
    }

    private void rsJsonJX(JSONObject rs,boolean isSuper){
        if(isSuper){
            if(rs==null || !rs.has("url"))return;
            stopLoadWebView(false);
        }
        HashMap<String, String> headers = getHeaders(rs);
        if (rs.has("jxFrom")) {
            if(!isAdded())return;
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, "解析来自:" + rs.optString("jxFrom"), Toast.LENGTH_SHORT).show();
                }
            });
        }
        playUrl(rs.optString("url", ""), headers);
    }
    public MyVideoView getPlayer() {
        return mVideoView;
    }

    public JsonObject getMediaInfo() {
        JsonObject info = new JsonObject();
        if (mVideoView == null) return info;
        try {
            int playState = mVideoView.getCurrentPlayState();
            int state;
            if (mVideoView.isPlaying()) state = 3;
            else if (playState == VideoView.STATE_BUFFERING) state = 1;
            else if (playState == VideoView.STATE_PAUSED || playState == VideoView.STATE_PREPARED
                    || playState == VideoView.STATE_BUFFERED) state = 2;
            else state = -1;
            info.addProperty("state", state);
            info.addProperty("speed", mVideoView.getSpeed());
            info.addProperty("duration", mVideoView.getDuration());
            info.addProperty("position", mVideoView.getCurrentPosition());
            info.addProperty("url", webPlayUrl == null ? "" : webPlayUrl);
            String title = "";
            String artist = "";
            String artwork = "";
            if (mVodInfo != null) {
                title = TextUtils.isEmpty(mVodInfo.name) ? "" : mVodInfo.name;
                VodInfo.VodSeries series = getCurrentSeries(mVodInfo.playFlag, mVodInfo.playIndex);
                if (series != null && !TextUtils.isEmpty(series.name)) artist = series.name;
                artwork = TextUtils.isEmpty(mVodInfo.pic) ? "" : mVodInfo.pic;
            }
            if (!TextUtils.isEmpty(playArtwork)) artwork = playArtwork;
            info.addProperty("title", title);
            info.addProperty("artist", artist);
            info.addProperty("artwork", artwork);
        } catch (Throwable th) {
            LOG.e("echo-media getMediaInfo error: " + th.getMessage());
        }
        return info;
    }

    // webview
    private XWalkView mXwalkWebView;
    private WebView mSysWebView;
    private final Map<String, Boolean> loadedUrls = new HashMap<>();
    private LinkedList<String> loadFoundVideoUrls = new LinkedList<>();
    private HashMap<String, HashMap<String, String>> loadFoundVideoUrlsHeader = new HashMap<>();
    private final AtomicInteger loadFoundCount = new AtomicInteger(0);

    void loadWebView(String url) {
        if (mSysWebView == null && mXwalkWebView == null) {
            boolean useSystemWebView = Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
            if (!useSystemWebView) {
                XWalkUtils.tryUseXWalk(mContext, new XWalkUtils.XWalkState() {
                    @Override
                    public void success() {
                        initWebView(false);
                        loadUrl(url);
                    }

                    @Override
                    public void fail() {
                        Toast.makeText(mContext, "XWalkView不兼容，已替换为系统自带WebView", Toast.LENGTH_SHORT).show();
                        initWebView(true);
                        loadUrl(url);
                    }

                    @Override
                    public void ignore() {
                        Toast.makeText(mContext, "XWalkView运行组件未下载，已替换为系统自带WebView", Toast.LENGTH_SHORT).show();
                        initWebView(true);
                        loadUrl(url);
                    }
                });
            } else {
                initWebView(true);
                loadUrl(url);
            }
        } else {
            loadUrl(url);
        }
    }

    void initWebView(boolean useSystemWebView) {
        if (useSystemWebView) {
            mSysWebView = new MyWebView(mContext);
            configWebViewSys(mSysWebView);
        } else {
            mXwalkWebView = new MyXWalkView(mContext);
            configWebViewX5(mXwalkWebView);
        }
    }

    void loadUrl(String url) {
        if(!isAdded())return;
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mXwalkWebView != null) {
                    mXwalkWebView.stopLoading();
                    if(webUserAgent != null) {
                        mXwalkWebView.getSettings().setUserAgentString(webUserAgent);
                    }
                    //mXwalkWebView.clearCache(true);
                    if(webHeaderMap != null){
                        mXwalkWebView.loadUrl(url,webHeaderMap);
                    }else {
                        mXwalkWebView.loadUrl(url);
                    }
                }
                if (mSysWebView != null) {
                    mSysWebView.stopLoading();
                    if(webUserAgent != null) {
                        mSysWebView.getSettings().setUserAgentString(webUserAgent);
                    }
                    //mSysWebView.clearCache(true);
                    if(webHeaderMap != null){
                        mSysWebView.loadUrl(url,webHeaderMap);
                    }else {
                        mSysWebView.loadUrl(url);
                    }
                }
            }
        });
    }

    void stopLoadWebView(boolean destroy) {
        if (mActivity == null) return;
        if(!isAdded())return;
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (mXwalkWebView != null) {
                    mXwalkWebView.stopLoading();
                    mXwalkWebView.loadUrl("about:blank");
                    if (destroy) {
                        mXwalkWebView.clearCache(true);
                        mXwalkWebView.removeAllViews();
                        mXwalkWebView.onDestroy();
                        mXwalkWebView = null;
                    }
                }
                if (mSysWebView != null) {
                    mSysWebView.stopLoading();
                    mSysWebView.loadUrl("about:blank");
                    if (destroy) {
                        mSysWebView.clearCache(true);
                        mSysWebView.removeAllViews();
                        mSysWebView.destroy();
                        mSysWebView = null;
                    }
                }
            }
        });
    }

    boolean checkVideoFormat(String url) {
        try{
            if (url.contains("url=http") || url.contains(".html")) {
                return false;
            }
            if (sourceBean.getType() == 3) {
                Spider sp = ApiConfig.get().getCSP(sourceBean);
                if (sp != null && sp.manualVideoCheck()){
                    return sp.isVideoFormat(url);
                }
            }
            return VideoParseRuler.checkIsVideoForParse(webUrl, url);
        }catch (Exception e){
            return false;
        }
    }

    class MyWebView extends WebView {
        public MyWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity)
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayFragment.this);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    class MyXWalkView extends XWalkView {
        public MyXWalkView(Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity)
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayFragment.this);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebViewSys(WebView webView) {
        if (webView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        if(!isAdded())return;
        requireActivity().addContentView(webView, layoutParams);
        /* 添加webView配置 */
        final WebSettings settings = webView.getSettings();
        settings.setNeedInitialFocus(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            settings.setBlockNetworkImage(false);
        } else {
            settings.setBlockNetworkImage(true);
        }
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
//        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        /* 添加webView配置 */
        //设置编码
        settings.setDefaultTextEncodingName("utf-8");
        settings.setUserAgentString(webView.getSettings().getUserAgentString());
//         settings.setUserAgentString(ANDROID_UA);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                return true;
            }
        });
        SysWebClient mSysWebClient = new SysWebClient();
        webView.setWebViewClient(mSysWebClient);
        webView.setBackgroundColor(Color.BLACK);
    }

    private class SysWebClient extends WebViewClient {

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override
        public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
            sslErrorHandler.proceed();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted( view,  url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            LOG.i("echo-onPageFinished url:" + url);
            if(!url.equals("about:blank")){
                mController.evaluateScript(sourceBean,url,view,null);
            }
        }

        WebResourceResponse checkIsVideo(String url, HashMap<String, String> headers) {
            if (url.endsWith("/favicon.ico")) {
                if (url.startsWith("http://127.0.0.1")) {
                    return new WebResourceResponse("image/x-icon", "UTF-8", null);
                }
                return null;
            }

            boolean isFilter = VideoParseRuler.isFilter(webUrl, url);
            if (isFilter) {
                LOG.i( "shouldInterceptLoadRequest filter:" + url);
                return null;
            }

            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrls.put(url, ad);
            } else {
                ad = Boolean.TRUE.equals(loadedUrls.get(url));
            }

            if (!ad) {
                if (checkVideoFormat(url)) {
                    loadFoundVideoUrls.add(url);
                    loadFoundVideoUrlsHeader.put(url, headers);
                    LOG.i("echo-loadFoundVideoUrl:" + url );
                    if (loadFoundCount.incrementAndGet() == 1) {
                        stopLoadWebView(false);
                        SuperParse.stopJsonJx();
                        url = loadFoundVideoUrls.poll();
                        mHandler.removeMessages(MSG_PARSE_TIMEOUT);
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if(!TextUtils.isEmpty(cookie))headers.put("Cookie", " " + cookie);//携带cookie
                        playUrl(url, headers);
                    }
                }
            }

            return ad || loadFoundCount.get() > 0 ?
                    AdBlocker.createEmptyResource() :
                    null;
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
//            WebResourceResponse response = checkIsVideo(url, new HashMap<>());
            return null;
        }

        @Nullable
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            LOG.i("echo-shouldInterceptRequest url:" + url);
            HashMap<String, String> webHeaders = new HashMap<>();
            Map<String, String> hds = request.getRequestHeaders();
            if (hds != null && hds.keySet().size() > 0) {
                for (String k : hds.keySet()) {
                    if (k.equalsIgnoreCase("user-agent")
                            || k.equalsIgnoreCase("referer")
                            || k.equalsIgnoreCase("origin")) {
                        webHeaders.put(k," " + hds.get(k));
                    }
                }
            }
            return checkIsVideo(url, webHeaders);
        }

        @Override
        public void onLoadResource(WebView webView, String url) {
            super.onLoadResource(webView, url);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebViewX5(XWalkView webView) {
        if (webView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        if(!isAdded())return;
        requireActivity().addContentView(webView, layoutParams);
        /* 添加webView配置 */
        final XWalkSettings settings = webView.getSettings();
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            settings.setBlockNetworkImage(false);
        } else {
            settings.setBlockNetworkImage(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
//        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // settings.setUserAgentString(ANDROID_UA);

        webView.setBackgroundColor(Color.BLACK);
        webView.setUIClient(new XWalkUIClient(webView) {
            @Override
            public boolean onConsoleMessage(XWalkView view, String message, int lineNumber, String sourceId, ConsoleMessageType messageType) {
                return false;
            }

            @Override
            public boolean onJsAlert(XWalkView view, String url, String message, XWalkJavascriptResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(XWalkView view, String url, String message, XWalkJavascriptResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(XWalkView view, String url, String message, String defaultValue, XWalkJavascriptResult result) {
                return true;
            }
        });
        XWalkWebClient mX5WebClient = new XWalkWebClient(webView);
        webView.setResourceClient(mX5WebClient);
    }

    private class XWalkWebClient extends XWalkResourceClient {
        public XWalkWebClient(XWalkView view) {
            super(view);
        }

        @Override
        public void onDocumentLoadedInFrame(XWalkView view, long frameId) {
            super.onDocumentLoadedInFrame(view, frameId);
        }

        @Override
        public void onLoadStarted(XWalkView view, String url) {
            super.onLoadStarted(view, url);
        }

        @Override
        public void onLoadFinished(XWalkView view, String url) {
            super.onLoadFinished(view, url);
            LOG.i("echo-onLoadFinished url:" + url);
            if(!url.equals("about:blank")){
                mController.evaluateScript(sourceBean,url,null,view);
            }
        }

        @Override
        public void onProgressChanged(XWalkView view, int progressInPercent) {
            super.onProgressChanged(view, progressInPercent);
        }

        @Override
        public XWalkWebResourceResponse shouldInterceptLoadRequest(XWalkView view, XWalkWebResourceRequest request) {
            String url = request.getUrl().toString();
            LOG.i("echo-shouldInterceptLoadRequest url:" + url);
            // suppress favicon requests as we don't display them anywhere
            if (url.endsWith("/favicon.ico")) {
                if (url.startsWith("http://127.0.0.1")) {
                    return createXWalkWebResourceResponse("image/x-icon", "UTF-8", null);
                }
                return null;
            }

            boolean isFilter = VideoParseRuler.isFilter(webUrl, url);
            if (isFilter) {
                LOG.i( "shouldInterceptLoadRequest filter:" + url);
                return null;
            }

            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrls.put(url, ad);
            } else {
                ad = Boolean.TRUE.equals(loadedUrls.get(url));
            }
            if (!ad ) {
                if (checkVideoFormat(url)) {
                    HashMap<String, String> webHeaders = new HashMap<>();
                    Map<String, String> hds = request.getRequestHeaders();
                    if (hds != null && hds.keySet().size() > 0) {
                        for (String k : hds.keySet()) {
                            if (k.equalsIgnoreCase("user-agent")
                                    || k.equalsIgnoreCase("referer")
                                    || k.equalsIgnoreCase("origin")) {
                                webHeaders.put(k," " + hds.get(k));
                            }
                        }
                    }
                    loadFoundVideoUrls.add(url);
                    loadFoundVideoUrlsHeader.put(url, webHeaders);
                    LOG.i("echo-loadFoundVideoUrl:" + url );
                    if (loadFoundCount.incrementAndGet() == 1) {
                        stopLoadWebView(false);
                        SuperParse.stopJsonJx();
                        mHandler.removeMessages(MSG_PARSE_TIMEOUT);
                        url = loadFoundVideoUrls.poll();
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if(!TextUtils.isEmpty(cookie))webHeaders.put("Cookie", " " + cookie);//携带cookie
                        playUrl(url, webHeaders);
                    }
                }
            }
            return ad || loadFoundCount.get() > 0 ?
                    createXWalkWebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes())) :
                    null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(XWalkView view, String s) {
            return false;
        }

        @Override
        public void onReceivedSslError(XWalkView view, ValueCallback<Boolean> callback, SslError error) {
            callback.onReceiveValue(true);
        }
    }

}
