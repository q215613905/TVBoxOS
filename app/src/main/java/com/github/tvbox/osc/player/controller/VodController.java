package com.github.tvbox.osc.player.controller;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.server.RemoteServer;
import com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.github.tvbox.osc.ui.adapter.ParseAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.DanmuHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.M3u8;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.ScreenUtils;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.github.tvbox.osc.util.thunder.Jianpian;
import com.github.tvbox.osc.util.thunder.Thunder;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xwalk.core.XWalkView;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.util.Date;
import java.util.Map;
import java.util.Locale;

import xyz.doikki.videoplayer.player.VideoView;

import static xyz.doikki.videoplayer.util.PlayerUtils.stringForTime;
import static xyz.doikki.videoplayer.util.PlayerUtils.safeTimeMs;

public class VodController extends BaseController {
    private static final float PORTRAIT_EPISODE_SWIPE_DP = 80f;
    private static final long PORTRAIT_EPISODE_TITLE_SHOW_MS = 3000L;
    private boolean portraitEpisodeSwipeTriggered;
    private final Runnable portraitEpisodeTitleRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isBottomVisible()) mTopRoot1.setVisibility(GONE);
        }
    };

    public VodController(@NonNull @NotNull Context context) {
        super(context);
        mHandlerCallback = new HandlerCallback() {
            @Override
            public void callback(Message msg) {
                switch (msg.what) {
                    case 1000: { // seek 刷新
                        mProgressRoot.setVisibility(VISIBLE);
                        break;
                    }
                    case 1001: { // seek 关闭
                        mProgressRoot.setVisibility(GONE);
                        break;
                    }
                    case 1002: { // 显示底部菜单
                        updateDanmuSearchUiBtn();
                        hidePauseRoot();
                        mPlayTitle.setVisibility(GONE);
                        mBottomRoot.setVisibility(VISIBLE);
                        mTopRoot1.setVisibility(VISIBLE);
                        mTopRoot2.setVisibility(VISIBLE);
                        mPlayLoadNetSpeedRightTop.setVisibility(VISIBLE);
                        if(Hawk.get(HawkConfig.SCREEN_DISPLAY,GONE)==GONE){
                            mPlayPauseTime.setVisibility(VISIBLE);
                        }else {
                            net_play_speed.setVisibility(GONE);
                        }
                        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
                        backBtn.setVisibility(ScreenUtils.isTv(context) || isPortrait ? INVISIBLE : VISIBLE);
                        showLockView();
                        break;
                    }
                    case 1003: { // 隐藏底部菜单
                        mBottomRoot.setVisibility(GONE);
                        mTopRoot1.setVisibility(GONE);
                        mPlayLoadNetSpeedRightTop.setVisibility(GONE);
                        if (videoPlayState == VideoView.STATE_PAUSED) {
                            showPauseRoot();
                            mPlayTitle.setVisibility(VISIBLE);
                        } else {
                            hidePauseRoot();
                            mPlayTitle.setVisibility(GONE);
                        }
                        if(Hawk.get(HawkConfig.SCREEN_DISPLAY,GONE)==GONE){
                            mPlayPauseTime.setVisibility(GONE);
                        }else {
                            net_play_speed.setVisibility(VISIBLE);
                        }
                        backBtn.setVisibility(INVISIBLE);
                        mHandler.removeCallbacks(lockRunnable);
                        if (mLockView != null) {
                            mLockView.setVisibility(INVISIBLE);
                        }
                        break;
                    }
                    case 1004: { // 设置速度
                        if (isInPlaybackState()) {
                            try {
                                float speed = (float) mPlayerConfig.getDouble("sp");
                                mControlWrapper.setSpeed(speed);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } else
                            mHandler.sendEmptyMessageDelayed(1004, 100);
                        break;
                    }
                }
            }
        };
    }

    @Override
    public boolean onDown(MotionEvent e) {
        portraitEpisodeSwipeTriggered = false;
        return super.onDown(e);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (isPortraitEpisodeSwipe(e1, e2)) {
            if (!portraitEpisodeSwipeTriggered && Math.abs(e2.getY() - e1.getY()) >= portraitEpisodeSwipeThreshold()) {
                portraitEpisodeSwipeTriggered = true;
                if (listener != null) {
                    if (e2.getY() < e1.getY()) listener.playNext(false);
                    else listener.playPre();
                    showPortraitEpisodeTitle();
                }
            }
            return true;
        }
        return super.onScroll(e1, e2, distanceX, distanceY);
    }

    private boolean isPortraitEpisodeSwipe(MotionEvent e1, MotionEvent e2) {
        if (e1 == null || e2 == null || !canHandleGesture(e1)) return false;
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) return false;
        return Math.abs(e2.getY() - e1.getY()) > Math.abs(e2.getX() - e1.getX());
    }

    private float portraitEpisodeSwipeThreshold() {
        return getResources().getDisplayMetrics().density * PORTRAIT_EPISODE_SWIPE_DP;
    }

    private void showPortraitEpisodeTitle() {
        if (isBottomVisible()) return;
        mHandler.removeCallbacks(portraitEpisodeTitleRunnable);
        mTopRoot1.setVisibility(VISIBLE);
        mHandler.postDelayed(portraitEpisodeTitleRunnable, PORTRAIT_EPISODE_TITLE_SHOW_MS);
    }

    SeekBar mSeekBar;
    TextView mCurrentTime;
    TextView mTotalTime;
    boolean mIsDragging;
    private boolean mSeekBarKeyTracking;
    LinearLayout mProgressRoot;
    TextView mProgressText;
    ImageView mProgressIcon;
    ImageView mLockView;
    LinearLayout mBottomRoot;
    LinearLayout mPlayBtnGroup;
    LinearLayout mTopRoot1;
    LinearLayout mTopRoot2;
    LinearLayout mParseRoot;
    TvRecyclerView mGridParseView;
    TextView mPlayTitle;
    TextView mPlayTitle1;
    TextView mPlayLabel;
    TextView mPlayLoadNetSpeedRightTop;
    TextView mNextBtn;
    TextView mPreBtn;
    TextView mPlayerScaleBtn;
    public TextView mPlayerSpeedBtn;
    TextView mPlayerBtn;
    TextView mPlayerIJKBtn;
    TextView mPlayerRetry;
    TextView mPlayrefresh;
    public TextView mPlayerTimeStartEndText;
    public TextView mPlayerTimeStartBtn;
    public TextView mPlayerTimeSkipBtn;
    public TextView mPlayerTimeResetBtn;
    TextView mPlayPauseTime;
    TextView mPlayLoadNetSpeed;
    TextView mVideoSize;
    public SimpleSubtitleView mSubtitleView;
    public SimpleSubtitleView mLyricView;
    public SubtitleView mExoSubtitleView;
    TextView mZimuBtn;
    TextView mAudioTrackBtn;
    TextView mVideoTrackBtn;
    TextView mDanmuSettingBtn;
    TextView mDanmuSearchUiBtn;
    public TextView mLandscapePortraitBtn;
    private View backBtn;//返回键
    private boolean isClickBackBtn;
    TextView seekTime; //右上角进度时间显示
    TextView mScreenDisplay; //增加屏显开关
    LinearLayout tv_screen_display; //增加屏显布局
    TextView mCastBtn;
    TextView net_play_speed;
    private boolean hasDanmu = false;
    private boolean showParse;

    LockRunnable lockRunnable = new LockRunnable();
    private boolean isLock = false;
    private boolean previewMode = false;
    Handler myHandle;
    Runnable myRunnable;
    int myHandleSeconds = 10000;//闲置多少毫秒秒关闭底栏  默认6秒

    int videoPlayState = 0;

    private final Runnable myRunnable2 = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            Date date = new Date();
            @SuppressLint("SimpleDateFormat") SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
            mPlayPauseTime.setText(timeFormat.format(date));
            long mSpeed = mControlWrapper.getTcpSpeed();
            String speed = PlayerHelper.getDisplaySpeed(mSpeed,false);
            String speedBps = PlayerHelper.getDisplaySpeedBps(mSpeed,true);
            mPlayLoadNetSpeedRightTop.setText(speedBps);
            mPlayLoadNetSpeed.setText(speed);
            net_play_speed.setText(speedBps);
            int[] mVideoSizes = mControlWrapper.getVideoSize();
            String width = Integer.toString(mVideoSizes[0]);
            String height = Integer.toString(mVideoSizes[1]);
            mVideoSize.setText("[ " + width + " X " + height +" ]");

            mHandler.postDelayed(this, 1000);
        }
    };
    
    private void showLockView() {
        if (previewMode || getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) isLock = false;
            mHandler.removeCallbacks(lockRunnable);
            if (mLockView != null) {
                mLockView.setVisibility(INVISIBLE);
            }
            return;
        }
        mLockView.setVisibility(ScreenUtils.isTv(getContext()) ? INVISIBLE : VISIBLE);
        mHandler.removeCallbacks(lockRunnable);
        if (isLock) {
            mHandler.postDelayed(lockRunnable, 3000);
        }
    }

    public void setPreviewMode(boolean previewMode) {
        this.previewMode = previewMode;
        mHandler.removeCallbacks(lockRunnable);
        if (mLockView != null) {
            mLockView.setVisibility(INVISIBLE);
        }
    }

    @Override
    protected void initView() {
        super.initView();
        mCurrentTime = findViewById(R.id.curr_time);
        mTotalTime = findViewById(R.id.total_time);
        mPlayTitle = findViewById(R.id.tv_info_name);
        mPlayTitle1 = findViewById(R.id.tv_info_name1);
        mPlayLabel = findViewById(R.id.play_label);
        mPlayLoadNetSpeedRightTop = findViewById(R.id.tv_play_load_net_speed_right_top);
        mSeekBar = findViewById(R.id.seekBar);
        CircleThumbDrawable seekThumb = new CircleThumbDrawable(getContext());
        mSeekBar.setThumb(seekThumb);
        mSeekBar.setThumbOffset(seekThumb.getIntrinsicWidth() / 2);
        mProgressRoot = findViewById(R.id.tv_progress_container);
        mProgressIcon = findViewById(R.id.tv_progress_icon);
        mProgressText = findViewById(R.id.tv_progress_text);
        mBottomRoot = findViewById(R.id.bottom_container);
        mTopRoot1 = findViewById(R.id.tv_top_l_container);
        mTopRoot2 = findViewById(R.id.tv_top_r_container);
        mPlayBtnGroup = findViewById(R.id.play_btn_group);
        tv_screen_display = findViewById(R.id.tv_screen_display);
        net_play_speed = findViewById(R.id.net_play_speed);
        mParseRoot = findViewById(R.id.parse_root);
        mGridParseView = findViewById(R.id.mGridParseView);
        mPlayerRetry = findViewById(R.id.play_retry);
        mPlayrefresh = findViewById(R.id.play_refresh);
        mNextBtn = findViewById(R.id.play_next);
        mPreBtn = findViewById(R.id.play_pre);
        mPlayerScaleBtn = findViewById(R.id.play_scale);
        mPlayerSpeedBtn = findViewById(R.id.play_speed);
        mPlayerBtn = findViewById(R.id.play_player);
        mPlayerIJKBtn = findViewById(R.id.play_ijk);
        mPlayerTimeStartEndText = findViewById(R.id.play_time_start_end_text);
        mPlayerTimeStartBtn = findViewById(R.id.play_time_start);
        mPlayerTimeSkipBtn = findViewById(R.id.play_time_end);
        mPlayerTimeResetBtn = findViewById(R.id.play_time_reset);
        mPlayPauseTime = findViewById(R.id.tv_sys_time);
        mPlayLoadNetSpeed = findViewById(R.id.tv_play_load_net_speed);
        mVideoSize = findViewById(R.id.tv_videosize);
        mSubtitleView = findViewById(R.id.subtitle_view);
        mLyricView = findViewById(R.id.lyric_view);
        mExoSubtitleView = findViewById(R.id.exo_subtitle_view);
        mZimuBtn = findViewById(R.id.zimu_select);
        mAudioTrackBtn = findViewById(R.id.audio_track_select);
        mVideoTrackBtn = findViewById(R.id.video_track_select);
        mDanmuSettingBtn = findViewById(R.id.danmu_setting);
        mDanmuSearchUiBtn = findViewById(R.id.danmu_search_ui);
        mLandscapePortraitBtn = findViewById(R.id.landscape_portrait);
        backBtn = findViewById(R.id.tv_back);
        seekTime = findViewById(R.id.tv_seek_time);
        mScreenDisplay = findViewById(R.id.screen_display);
        mCastBtn = findViewById(R.id.play_cast);
        updateDanmuBtn();
        updateDanmuSearchUiBtn();
        backBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    setLandscapePortrait();
                    hideBottom();
                    return;
                }
                if (getContext() instanceof Activity) {
                    isClickBackBtn = true;
                    ((Activity) getContext()).onBackPressed();
                }
            }
        });
        mLockView = findViewById(R.id.tv_lock);
        mLockView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                isLock = !isLock;
                mLockView.setImageResource(isLock ? R.drawable.icon_lock : R.drawable.icon_unlock);
                if (isLock) {
                    Message obtain = Message.obtain();
                    obtain.what = 1003;//隐藏底部菜单
                    mHandler.sendMessage(obtain);
                }
                showLockView();
            }
        });
        View rootView = findViewById(R.id.rootView);
        rootView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (previewMode) {
                    return false;
                }
                if (isLock) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        showLockView();
                    }
                }
                return isLock;
            }
        });

        initSubtitleInfo();

        myHandle = new Handler();
        myRunnable = new Runnable() {
            @Override
            public void run() {
                hideBottom();
            }
        };

        mPlayPauseTime.post(new Runnable() {
            @Override
            public void run() {
                mHandler.post(myRunnable2);
            }
        });

        mGridParseView.setLayoutManager(new V7LinearLayoutManager(getContext(), 0, false));
        ParseAdapter parseAdapter = new ParseAdapter();
        parseAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                ParseBean parseBean = parseAdapter.getItem(position);
                // 当前默认解析需要刷新
                int currentDefault = parseAdapter.getData().indexOf(ApiConfig.get().getDefaultParse());
                parseAdapter.notifyItemChanged(currentDefault);
                ApiConfig.get().setDefaultParse(parseBean);
                parseAdapter.notifyItemChanged(position);
                listener.changeParse(parseBean);
                hideBottom();
            }
        });
        mGridParseView.setAdapter(parseAdapter);
        parseAdapter.setNewData(ApiConfig.get().getParseBeanList());

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }

                updateSeekBarTime(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                beginSeekBarTracking();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                finishSeekBarTracking();
            }
        });
        mSeekBar.setOnHoverListener(new OnHoverListener() {
            @Override
            public boolean onHover(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        keepSeekBarActive();
                        v.setSelected(true);
                        v.refreshDrawableState();
                        v.jumpDrawablesToCurrentState();
                        v.invalidate();
                        break;
                    case MotionEvent.ACTION_HOVER_MOVE:
                        keepSeekBarActive();
                        break;
                    case MotionEvent.ACTION_HOVER_EXIT:
                        v.setSelected(false);
                        v.refreshDrawableState();
                        v.jumpDrawablesToCurrentState();
                        v.invalidate();
                        break;
                }
                return false;
            }
        });
        mSeekBar.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    boolean hasParse = mParseRoot.getVisibility() == VISIBLE && mGridParseView.getAdapter() != null && mGridParseView.getAdapter().getItemCount() > 0;
                    mSeekBar.setNextFocusUpId(hasParse ? R.id.mGridParseView : R.id.play_next);
                    keepSeekBarActive();
                    v.setSelected(true);
                    v.refreshDrawableState();
                    v.jumpDrawablesToCurrentState();
                    v.invalidate();
                } else {
                    v.setSelected(false);
                    v.refreshDrawableState();
                    v.jumpDrawablesToCurrentState();
                    v.invalidate();
                    if (mSeekBarKeyTracking) {
                        finishSeekBarTracking();
                    }
                }
            }
        });
        mSeekBar.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return false;
                }
                if (!isInPlaybackState()) {
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    beginSeekBarTracking();
                    mSeekBarKeyTracking = true;
                    v.setSelected(true);
                    v.jumpDrawablesToCurrentState();
                    v.invalidate();
                    moveSeekBarByKey(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    v.setSelected(v.hasFocus());
                    v.jumpDrawablesToCurrentState();
                    v.invalidate();
                    if (mSeekBarKeyTracking) {
                        finishSeekBarTracking();
                    }
                    return true;
                }
                return true;
            }
        });
        mSeekBar.setOnGenericMotionListener(new OnGenericMotionListener() {
            @Override
            public boolean onGenericMotion(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_SCROLL || !isInPlaybackState()) {
                    return false;
                }
                float scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (scroll == 0) {
                    scroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                }
                if (scroll == 0) {
                    return false;
                }
                beginSeekBarTracking();
                moveSeekBarByKey(scroll > 0 ? 1 : -1);
                finishSeekBarTracking();
                return true;
            }
        });
        mPlayerRetry.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.replay(true);
                hideBottom();
            }
        });
        mPlayrefresh.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.replay(false);
                hideBottom();
            }
        });
        mNextBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.playNext(false);
                hideBottom();
            }
        });
        setOnTvLongClickListener(mNextBtn, new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                FastClickCheckUtil.check(view);
                listener.showEpisodeDialog();
                hideBottom();
                return true;
            }
        });
        mPreBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.playPre();
                hideBottom();
            }
        });
        setOnTvLongClickListener(mPreBtn, new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                FastClickCheckUtil.check(view);
                listener.showEpisodeDialog();
                hideBottom();
                return true;
            }
        });
        mPlayerScaleBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                showScaleDialog();
            }
        });
        setOnTvLongClickListener(mPlayerScaleBtn, new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                FastClickCheckUtil.check(view);
                try {
                    mPlayerConfig.put("sc", 0);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    mControlWrapper.setScreenScaleType(0);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mPlayerSpeedBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                showSpeedDialog();
            }
        });

        setOnTvLongClickListener(mPlayerSpeedBtn, new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                FastClickCheckUtil.check(view);
                try {
                    mPlayerConfig.put("sp", 1.0f);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    speed_old = 1.0f;
                    mControlWrapper.setSpeed(1.0f);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mPlayerBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int playerType = mPlayerConfig.getInt("pl");
                    ArrayList<Integer> exsitPlayerTypes = PlayerHelper.getExistPlayerTypes();
                    int playerTypeIdx = 0;
                    int playerTypeSize = exsitPlayerTypes.size();
                    for(int i = 0; i<playerTypeSize; i++) {
                        if (playerType == exsitPlayerTypes.get(i)) {
                            if (i == playerTypeSize - 1) {
                                playerTypeIdx = 0;
                            } else {
                                playerTypeIdx = i + 1;
                            }
                        }
                    }
                    playerType = exsitPlayerTypes.get(playerTypeIdx);
                    mPlayerConfig.put("pl", playerType);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    listener.replay(false);
                    listener.setAllowSwitchPlayer(false);
                    hideBottom();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        setOnTvLongClickListener(mPlayerBtn, new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                FastClickCheckUtil.check(view);
                try {
                    int playerType = mPlayerConfig.getInt("pl");
                    int defaultPos = 0;
                    ArrayList<Integer> players = PlayerHelper.getExistPlayerTypes();
                    ArrayList<Integer> renders = new ArrayList<>();
                    for(int p = 0; p<players.size(); p++) {
                        renders.add(p);
                        if (players.get(p) == playerType) {
                            defaultPos = p;
                        }
                    }
                    SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                    dialog.setTip("请选择播放器");
                    dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                        @Override
                        public void click(Integer value, int pos) {
                            try {
                                dialog.cancel();
                                int thisPlayType = players.get(pos);
                                if (thisPlayType != playerType) {
                                    mPlayerConfig.put("pl", thisPlayType);
                                    updatePlayerCfgView();
                                    listener.updatePlayerCfg();
                                    listener.replay(false);
                                    listener.setAllowSwitchPlayer(false);
                                    hideBottom();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public String getDisplay(Integer val) {
                            Integer playerType = players.get(val);
                            return PlayerHelper.getPlayerName(playerType);
                        }
                    }, new DiffUtil.ItemCallback<Integer>() {
                        @Override
                        public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                            return oldItem.intValue() == newItem.intValue();
                        }

                        @Override
                        public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                            return oldItem.intValue() == newItem.intValue();
                        }
                    }, renders, defaultPos);
                    dialog.show();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mPlayerIJKBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    String ijk = mPlayerConfig.getString("ijk");
                    List<IJKCode> codecs = ApiConfig.get().getIjkCodes();
                    for (int i = 0; i < codecs.size(); i++) {
                        if (ijk.equals(codecs.get(i).getName())) {
                            if (i >= codecs.size() - 1)
                                ijk = codecs.get(0).getName();
                            else {
                                ijk = codecs.get(i + 1).getName();
                            }
                            break;
                        }
                    }
                    mPlayerConfig.put("ijk", ijk);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    listener.replay(false);
                    hideBottom();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
//        增加播放页面片头片尾时间重置
        mPlayerTimeResetBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    mPlayerConfig.put("et", 0);
                    mPlayerConfig.put("st", 0);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayerTimeStartBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int current = safeTimeMs(mControlWrapper.getCurrentPosition());
                    int duration = safeTimeMs(mControlWrapper.getDuration());
                    if (current > duration / 2) return;
                    mPlayerConfig.put("st",current/1000);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        setOnTvLongClickListener(mPlayerTimeStartBtn, new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                try {
                    mPlayerConfig.put("st", 0);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mPlayerTimeSkipBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int current = safeTimeMs(mControlWrapper.getCurrentPosition());
                    int duration = safeTimeMs(mControlWrapper.getDuration());
                    if (current < duration / 2) return;
                    mPlayerConfig.put("et", (duration - current)/1000);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        setOnTvLongClickListener(mPlayerTimeSkipBtn, new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                try {
                    mPlayerConfig.put("et", 0);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mZimuBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                listener.selectSubtitle();
                hideBottom();
            }
        });
        setOnTvLongClickListener(mZimuBtn, new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                mSubtitleView.setVisibility(View.GONE);
                mSubtitleView.destroy();
                mSubtitleView.clearSubtitleCache();
                mSubtitleView.isInternal = false;
                mLyricView.setVisibility(View.GONE);
                mLyricView.destroy();
                mLyricView.clearSubtitleCache();
                mExoSubtitleView.setVisibility(View.GONE);
                hideBottom();
                Toast.makeText(getContext(), "字幕已关闭", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        mAudioTrackBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                listener.selectAudioTrack();
                hideBottom();
            }
        });
        mVideoTrackBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                listener.selectVideoTrack();
                hideBottom();
            }
        });
        mDanmuSettingBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                listener.showDanmuSetting();
            }
        });
        setOnTvLongClickListener(mDanmuSettingBtn, new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                FastClickCheckUtil.check(view);
                boolean opened = listener.toggleDanmu();
                hideBottom();
                Toast.makeText(getContext(), opened ? "弹幕已开启" : "弹幕已临时关闭", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        mDanmuSearchUiBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.searchDanmuUi(false);
                hideBottom();
            }
        });
        setOnTvLongClickListener(mDanmuSearchUiBtn, new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                listener.searchDanmuUi(true);
                hideBottom();
                return true;
            }
        });
        mLandscapePortraitBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                setLandscapePortrait();
                hideBottom();
            }
        });
        //屏显
        int disPlay = Hawk.get(HawkConfig.SCREEN_DISPLAY, GONE);
        mTopRoot2.setVisibility(disPlay);
        seekTime.setVisibility(disPlay);
        net_play_speed.setVisibility(disPlay);
        mPlayPauseTime.setVisibility(disPlay);
        mScreenDisplay.setTextColor(disPlay==VISIBLE?getResources().getColor(R.color.color_02F8E1): Color.WHITE);
        mScreenDisplay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                int disPlay =(Hawk.get(HawkConfig.SCREEN_DISPLAY, GONE) == VISIBLE) ? GONE : VISIBLE;
                seekTime.setVisibility(disPlay);
                net_play_speed.setVisibility(disPlay);
                if(disPlay==VISIBLE)mPlayPauseTime.setVisibility(disPlay);
                Hawk.put(HawkConfig.SCREEN_DISPLAY, disPlay);
                mScreenDisplay.setTextColor(disPlay==VISIBLE?getResources().getColor(R.color.color_02F8E1): Color.WHITE);
                hideBottom();
            }
        });
        mCastBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null) listener.clickCast();
            }
        });
        if (Build.VERSION.SDK_INT < 30) {
            mCastBtn.setVisibility(GONE);
        } else {
            mCastBtn.setVisibility(VISIBLE);
        }
        mScreenDisplay.setNextFocusRightId(R.id.play_next);
        mNextBtn.setNextFocusLeftId(R.id.screen_display);
    }

    private void showScaleDialog() {
        try {
            int scaleType = mPlayerConfig.getInt("sc");
            ArrayList<Integer> scales = new ArrayList<>();
            for (int i = 0; i <= 5; i++) scales.add(i);
            SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
            dialog.setTip("请选择画面尺寸");
            dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                @Override
                public void click(Integer value, int pos) {
                    try {
                        dialog.cancel();
                        mPlayerConfig.put("sc", value);
                        updatePlayerCfgView();
                        listener.updatePlayerCfg();
                        mControlWrapper.setScreenScaleType(value);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public String getDisplay(Integer value) {
                    return PlayerHelper.getScaleName(value);
                }
            }, new DiffUtil.ItemCallback<Integer>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                    return oldItem.intValue() == newItem.intValue();
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                    return oldItem.intValue() == newItem.intValue();
                }
            }, scales, scaleType);
            dialog.show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showSpeedDialog() {
        try {
            float speed = (float) mPlayerConfig.getDouble("sp");
            ArrayList<Float> speeds = new ArrayList<>();
            float[] speedOptions = {0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f};
            for (float value : speedOptions) speeds.add(value);
            int defaultPos = speeds.indexOf(speed);
            SelectDialog<Float> dialog = new SelectDialog<>(mActivity);
            dialog.setTip("请选择播放倍速");
            dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Float>() {
                @Override
                public void click(Float value, int pos) {
                    try {
                        dialog.cancel();
                        mPlayerConfig.put("sp", value);
                        updatePlayerCfgView();
                        listener.updatePlayerCfg();
                        speed_old = value;
                        mControlWrapper.setSpeed(value);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public String getDisplay(Float value) {
                    return value + "x";
                }
            }, new DiffUtil.ItemCallback<Float>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull Float oldItem, @NonNull @NotNull Float newItem) {
                    return oldItem.equals(newItem);
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull Float oldItem, @NonNull @NotNull Float newItem) {
                    return oldItem.equals(newItem);
                }
            }, speeds, defaultPos < 0 ? 1 : defaultPos);
            dialog.show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void hideLiveAboutBtn() {
        if (mControlWrapper != null && mControlWrapper.getDuration() == 0) {
            mPlayerSpeedBtn.setVisibility(GONE);
            mPlayerTimeStartEndText.setVisibility(GONE);
            mPlayerTimeStartBtn.setVisibility(GONE);
            mPlayerTimeSkipBtn.setVisibility(GONE);
            mPlayerTimeResetBtn.setVisibility(GONE);
        } else {
            mPlayerSpeedBtn.setVisibility(View.VISIBLE);
            mPlayerTimeStartEndText.setVisibility(GONE);
            mPlayerTimeStartBtn.setVisibility(View.VISIBLE);
            mPlayerTimeSkipBtn.setVisibility(View.VISIBLE);
            mPlayerTimeResetBtn.setVisibility(View.VISIBLE);
        }
    }

    public void initLandscapePortraitBtnInfo() {
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (mCurrentTime != null) mCurrentTime.setVisibility(isPortrait ? GONE : VISIBLE);
        if (mTotalTime != null) mTotalTime.setVisibility(isPortrait ? GONE : VISIBLE);
        if (isPortrait) {
            if (backBtn != null) backBtn.setVisibility(INVISIBLE);
            if (mLockView != null) mLockView.setVisibility(INVISIBLE);
        }
        updatePortraitMenu(isPortrait);

        if (mLandscapePortraitBtn == null) return;
        boolean showButton = false;
        if (mControlWrapper != null && mActivity != null && !ScreenUtils.isTv(mActivity)) {
            int[] videoSize = mControlWrapper.getVideoSize();
            int width = videoSize[0];
            int height = videoSize[1];
            long duration = safeTimeMs(mControlWrapper.getDuration());
            boolean shortVideo = duration > 0 && duration < 12 * 60 * 1000L;
            showButton = width > 0 && height > 0 && (width <= height || shortVideo) && ScreenUtils.getSqrt(mActivity) < 10.0;
        }
        mLandscapePortraitBtn.setVisibility(showButton ? VISIBLE : GONE);
        if (showButton) mLandscapePortraitBtn.setText(isPortrait ? "横屏" : "竖屏");
    }

    private void updatePortraitMenu(boolean isPortrait) {
        if (isPortrait) {
            mParseRoot.setVisibility(GONE);
            mPlayLabel.setVisibility(GONE);
            mPlayrefresh.setVisibility(GONE);
            mPlayerScaleBtn.setVisibility(GONE);
            mPlayerIJKBtn.setVisibility(GONE);
            mPlayerTimeStartEndText.setVisibility(GONE);
            mPlayerTimeStartBtn.setVisibility(GONE);
            mPlayerTimeSkipBtn.setVisibility(GONE);
            mPlayerTimeResetBtn.setVisibility(GONE);
            mCastBtn.setVisibility(GONE);
            mZimuBtn.setVisibility(GONE);
            mAudioTrackBtn.setVisibility(GONE);
            mVideoTrackBtn.setVisibility(GONE);
            mDanmuSettingBtn.setVisibility(GONE);
            mDanmuSearchUiBtn.setVisibility(GONE);
            mScreenDisplay.setVisibility(GONE);
            return;
        }

        mParseRoot.setVisibility(showParse ? VISIBLE : GONE);
        mPlayrefresh.setVisibility(VISIBLE);
        mPlayerScaleBtn.setVisibility(VISIBLE);
        mPlayerTimeStartEndText.setVisibility(GONE);
        mPlayerTimeStartBtn.setVisibility(VISIBLE);
        mPlayerTimeSkipBtn.setVisibility(VISIBLE);
        mPlayerTimeResetBtn.setVisibility(VISIBLE);
        mZimuBtn.setVisibility(VISIBLE);
        mScreenDisplay.setVisibility(VISIBLE);
        mCastBtn.setVisibility(Build.VERSION.SDK_INT < 30 ? GONE : VISIBLE);
        if (mPlayerConfig != null) updatePlayerCfgView();
        updateDanmuBtn();
        updateDanmuSearchUiBtn();
    }

    void setLandscapePortrait() {
        if (mActivity == null) return;
        if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mLandscapePortraitBtn.setText("竖屏");
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            mLandscapePortraitBtn.setText("横屏");
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initLandscapePortraitBtnInfo();
    }

    void initSubtitleInfo() {
        int subtitleTextSize = SubtitleHelper.getTextSize(mActivity);
        mSubtitleView.setTextSize(subtitleTextSize);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.player_vod_control_view;
    }

    public void showParse(boolean userJxList) {
        showParse = userJxList;
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        mParseRoot.setVisibility(showParse && !isPortrait ? VISIBLE : GONE);
    }

    private JSONObject mPlayerConfig = null;

    public void setPlayerConfig(JSONObject playerCfg) {
        this.mPlayerConfig = playerCfg;
        updatePlayerCfgView();
    }

    void updatePlayerCfgView() {
        try {
            int playerType = mPlayerConfig.getInt("pl");
            mPlayerBtn.setText(getPlayerShortName(playerType));
            mPlayerScaleBtn.setText(PlayerHelper.getScaleName(mPlayerConfig.getInt("sc")));
            mPlayerIJKBtn.setText(getCodecShortName(mPlayerConfig.getString("ijk")));
            mPlayerIJKBtn.setVisibility(playerType == 1 ? VISIBLE : GONE);
            mPlayerScaleBtn.setText(PlayerHelper.getScaleName(mPlayerConfig.getInt("sc")));
            mPlayerSpeedBtn.setText(mPlayerConfig.getDouble("sp") + "x");
            int start = mPlayerConfig.getInt("st");
            int end = mPlayerConfig.getInt("et");
            mPlayerTimeStartBtn.setText(start == 0 ? "片头" : stringForTime(start * 1000));
            mPlayerTimeSkipBtn.setText(end == 0 ? "片尾" : stringForTime(end * 1000));
            mAudioTrackBtn.setVisibility((playerType == 1 || playerType == 2) ? VISIBLE : GONE);
            mVideoTrackBtn.setVisibility((playerType == 1 || playerType == 2) ? VISIBLE : GONE);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String getPlayerShortName(int playerType) {
        String playerName = PlayerHelper.getPlayerName(playerType);
        return playerName;
    }

    private String getCodecShortName(String codecName) {
        if ("硬解码".equals(codecName)) {
            return "硬解";
        }
        if ("软解码".equals(codecName)) {
            return "软解";
        }
        return codecName;
    }

    public void setTitle(String playTitleInfo) {
        mPlayTitle.setText(playTitleInfo);
        mPlayTitle1.setText(playTitleInfo);
    }

    public void setUrlTitle(String playTitleInfo) {
        mPlayTitle.setText(playTitleInfo);
    }

    public void resetSpeed() {
        skipEnd = true;
        mHandler.removeMessages(1004);
        mHandler.sendEmptyMessageDelayed(1004, 100);
    }

    public void setHasDanmu(boolean hasDanmu) {
        this.hasDanmu = hasDanmu;
        updateDanmuBtn();
    }

    public void updateDanmuBtn() {
        if (mDanmuSettingBtn == null) return;
        mDanmuSettingBtn.setVisibility(DanmuHelper.isOpen() ? VISIBLE : GONE);
        updatePlayLabelVisibility();
    }

    public void updateDanmuSearchUiBtn() {
        if (mDanmuSearchUiBtn == null) return;
        boolean hasDanmuSearchUi = ApiConfig.get().hasDanmuSearchUi();
        mDanmuSearchUiBtn.setVisibility(hasDanmuSearchUi ? VISIBLE : GONE);
        updatePlayLabelVisibility();
    }

    private void updatePlayLabelVisibility() {
        if (mPlayLabel == null || mPlayBtnGroup == null) return;
        boolean isDanmuMenuVisible = false;
        boolean isScreenDisplayNext = false;
        for (int i = 0; i < mPlayBtnGroup.getChildCount(); i++) {
            View child = mPlayBtnGroup.getChildAt(i);
            if (child.getVisibility() != VISIBLE) continue;
            if (child == mDanmuSettingBtn) {
                isDanmuMenuVisible = true;
                continue;
            }
            if (isDanmuMenuVisible) {
                isScreenDisplayNext = child == mScreenDisplay;
                break;
            }
        }
        mPlayLabel.setVisibility(isDanmuMenuVisible && !isScreenDisplayNext ? GONE : VISIBLE);
    }

    public interface VodControlListener {
        void playNext(boolean rmProgress);

        void playPre();

        void showEpisodeDialog();

        void prepared();

        void changeParse(ParseBean pb);

        void updatePlayerCfg();

        void replay(boolean replay);

        void errReplay();

        void selectSubtitle();

        void selectAudioTrack();

        void selectVideoTrack();

        void showDanmuSetting();

        boolean toggleDanmu();

        void searchDanmuUi(boolean longClick);

        void startPlayUrl(String url, HashMap<String, String> headers);

        void onM3u8ProxyUrl(String proxyUrl, String sourceUrl);

        void clickCast();

        void setAllowSwitchPlayer(boolean isAllow);
    }

    public void setListener(VodControlListener listener) {
        this.listener = listener;
    }

    private VodControlListener listener;

    private boolean skipEnd = true;

    private void keepSeekBarActive() {
        if (myHandle != null && myRunnable != null) {
            myHandle.removeCallbacks(myRunnable);
            myHandle.postDelayed(myRunnable, myHandleSeconds);
        }
        mHandler.removeMessages(1002);
        mHandler.removeMessages(1003);
        if (mBottomRoot != null && mBottomRoot.getVisibility() != VISIBLE) {
            mHandler.sendEmptyMessage(1002);
        }
    }

    private void beginSeekBarTracking() {
        keepSeekBarActive();
        if (mIsDragging) {
            return;
        }
        mIsDragging = true;
        mControlWrapper.stopProgress();
        mControlWrapper.stopFadeOut();
    }

    private void finishSeekBarTracking() {
        keepSeekBarActive();
        long newPosition = getSeekBarPosition(mSeekBar.getProgress());
        mControlWrapper.seekTo(newPosition);
        mIsDragging = false;
        mSeekBarKeyTracking = false;
        mControlWrapper.startProgress();
        mControlWrapper.startFadeOut();
    }

    private long getSeekBarPosition(int progress) {
        long duration = safeTimeMs(mControlWrapper.getDuration());
        int max = mSeekBar == null ? 0 : mSeekBar.getMax();
        if (duration <= 0 || max <= 0) {
            return 0;
        }
        return (duration * progress) / max;
    }

    private void updateSeekBarTime(int progress) {
        long newPosition = getSeekBarPosition(progress);
        if (mCurrentTime != null) {
            mCurrentTime.setText(stringForTime(safeTimeMs(newPosition)));
        }
    }

    private void moveSeekBarByKey(int dir) {
        int duration = safeTimeMs(mControlWrapper.getDuration());
        if (duration <= 0 || mSeekBar.getMax() <= 0) {
            return;
        }
        int progress = mSeekBar.getProgress() + getSeekBarKeyProgress(duration) * dir;
        if (progress < 0) {
            progress = 0;
        } else if (progress > mSeekBar.getMax()) {
            progress = mSeekBar.getMax();
        }
        mSeekBar.setProgress(progress);
        updateSeekBarTime(progress);
        updateSeekUI(safeTimeMs(mControlWrapper.getCurrentPosition()), safeTimeMs(getSeekBarPosition(progress)), duration);
    }

    private int getSeekBarKeyProgress(int duration) {
        long increment;
        if (duration > 3 * 60 * 60 * 1000) {
            increment = 5 * 60 * 1000;
        } else if (duration > 30 * 60 * 1000) {
            increment = 60 * 1000;
        } else if (duration > 15 * 60 * 1000) {
            increment = 30 * 1000;
        } else if (duration > 10 * 60 * 1000) {
            increment = 15 * 1000;
        } else {
            increment = 10 * 1000;
        }
        return Math.max(1, (int) (increment * mSeekBar.getMax() / duration));
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void setProgress(int duration, int position) {

        if (mIsDragging) {
            return;
        }
        super.setProgress(duration, position);
        if (skipEnd && position != 0 && duration != 0) {
            int et = 0;
            try {
                et = mPlayerConfig.getInt("et");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (et > 0 && position + (et * 1000) >= duration) {
                skipEnd = false;
                listener.playNext(true);
            }
        }
        mCurrentTime.setText(stringForTime(position));
        mTotalTime.setText(stringForTime(duration));
        seekTime.setText(formatSeekTime(position) + " | " + formatSeekTime(duration)); //右上角进度条时间显示
        if (duration > 0) {
            mSeekBar.setEnabled(true);
            int pos = (int) (position * 1.0 / duration * mSeekBar.getMax());
            mSeekBar.setProgress(pos);
        } else {
            mSeekBar.setEnabled(false);
        }
        int percent = mControlWrapper.getBufferedPercentage();
        if (percent >= 95) {
            mSeekBar.setSecondaryProgress(mSeekBar.getMax());
        } else {
            mSeekBar.setSecondaryProgress(percent * 10);
        }
    }

    private static String formatSeekTime(int timeMs) {
        int totalSeconds = Math.max(0, timeMs) / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        return hours > 0
                ? String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private boolean simSlideStart = false;
    private int simSeekPosition = 0;
    private long simSlideOffset = 0;
    private long lastSlideTime = 0;

    public void tvSlideStop() {
        if (!simSlideStart)
            return;
        mControlWrapper.seekTo(simSeekPosition);
        if (!mControlWrapper.isPlaying())
            mControlWrapper.start();
        simSlideStart = false;
        simSeekPosition = 0;
        simSlideOffset = 0;
    }
    public void tvSlideStart(int dir) {
        int duration = safeTimeMs(mControlWrapper.getDuration());
        if (duration <= 0)
            return;

        long currentTime = System.currentTimeMillis();
        final int baseSkip = 10000; // 基础跳转10秒
        final float accelerationFactor = 2.0f; // 连续操作时的加速因子
        final long threshold = 800; // 操作间隔阈值500ms

        if (!simSlideStart) {
            simSlideStart = true;
            simSlideOffset = (long) baseSkip * dir;
        } else {
            if (currentTime - lastSlideTime <= threshold) {
                simSlideOffset += (baseSkip * accelerationFactor * dir);
            } else {
                simSlideOffset = (long) baseSkip * dir;
            }
        }
        lastSlideTime = currentTime;
        int currentPosition = safeTimeMs(mControlWrapper.getCurrentPosition());
        int position = (int) (currentPosition + simSlideOffset);
        if (position > duration) position = duration;
        if (position < 0) position = 0;
        updateSeekUI(currentPosition, position, duration);
        simSeekPosition = position;
    }

    @Override
    protected void updateSeekUI(int curr, int seekTo, int duration) {
        super.updateSeekUI(curr, seekTo, duration);
        if (seekTo > curr) {
            mProgressIcon.setImageResource(R.drawable.exo_icon_fastforward);
        } else {
            mProgressIcon.setImageResource(R.drawable.exo_icon_rewind);
        }
        mProgressText.setText(stringForTime(seekTo) + " / " + stringForTime(duration));
        mHandler.sendEmptyMessage(1000);
        mHandler.removeMessages(1001);
        mHandler.sendEmptyMessageDelayed(1001, 1000);
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        videoPlayState = playState;
        switch (playState) {
            case VideoView.STATE_IDLE:
                break;
            case VideoView.STATE_PLAYING:
                initLandscapePortraitBtnInfo();
                startProgress();
                break;
            case VideoView.STATE_PAUSED:
                mTopRoot1.setVisibility(GONE);
//                mTopRoot2.setVisibility(GONE);
                mPlayLoadNetSpeedRightTop.setVisibility(GONE);
                if (isBottomVisible()) {
                    hideBottom();
                }
                showPauseRoot();
                mPlayTitle.setVisibility(VISIBLE);
                break;
            case VideoView.STATE_ERROR:
                listener.errReplay();
                break;
            case VideoView.STATE_PREPARED:
                mPlayLoadNetSpeed.setVisibility(GONE);
                hideLiveAboutBtn();
                listener.prepared();
                break;
            case VideoView.STATE_BUFFERED:
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_BUFFERING:
                mPlayLoadNetSpeed.setVisibility(GONE);
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                listener.playNext(true);
                break;
        }
    }

    boolean isBottomVisible() {
        return mBottomRoot.getVisibility() == VISIBLE;
    }

    void showBottom() {
        mHandler.removeMessages(1003);
        mHandler.sendEmptyMessage(1002);
        mNextBtn.requestFocus();
    }

    void showUpBottom() {
        mHandler.removeMessages(1003);
        mHandler.sendEmptyMessage(1002);
        mPlayerTimeStartBtn.requestFocus();
    }

    void hideBottom() {
        mHandler.removeMessages(1002);
        mHandler.sendEmptyMessage(1003);
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        myHandle.removeCallbacks(myRunnable);
        if (super.onKeyEvent(event)) {
            return true;
        }
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && keyCode == KeyEvent.KEYCODE_MENU) {
            listener.showEpisodeDialog();
            hideBottom();
            return true;
        }
        if (isBottomVisible()) {
            mHandler.removeMessages(1002);
            mHandler.removeMessages(1003);
            myHandle.postDelayed(myRunnable, myHandleSeconds);
            return super.dispatchKeyEvent(event);
        }
        boolean isInPlayback = isInPlaybackState();
        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isInPlayback) {
                    tvSlideStart(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (isInPlayback) {
                    if (event.getRepeatCount() == 0) togglePlay();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!isBottomVisible()) {
                    showBottom();
                    myHandle.postDelayed(myRunnable, myHandleSeconds);
                    return true;
                }
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isInPlayback) {
                    tvSlideStop();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }


    private boolean fromLongPress;
    private float speed_old = 1.0f;

    private void speedPlayStart(){
        fromLongPress = true;
        try {
            speed_old = (float) mPlayerConfig.getDouble("sp");
            float speed = 3.0f;
            mPlayerConfig.put("sp", speed);
            updatePlayerCfgView();
            listener.updatePlayerCfg();
            mControlWrapper.setSpeed(speed);
            findViewById(R.id.play_speed_3_container).setVisibility(View.VISIBLE);
        } catch (JSONException f) {
            f.printStackTrace();
        }
    }
    private void speedPlayEnd(){
        if (fromLongPress) {
            fromLongPress =false;
            try {
                float speed = speed_old;
                mPlayerConfig.put("sp", speed);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                mControlWrapper.setSpeed(speed);
            } catch (JSONException f) {
                f.printStackTrace();
            }
            findViewById(R.id.play_speed_3_container).setVisibility(View.GONE);
        }
    }
    @Override
    public void onLongPress(MotionEvent e) {
        if (videoPlayState!=VideoView.STATE_PAUSED) {
            speedPlayStart();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            speedPlayEnd();
        }
        return super.onTouchEvent(e);
    }

    /**
     * 为播放菜单按钮补充遥控器确认键的长按处理。
     * 部分电视盒子不会把确认键长按分发为 View.OnLongClickListener。
     */
    private void setOnTvLongClickListener(final View view, final OnLongClickListener listener) {
        view.setOnLongClickListener(listener);
        view.setOnKeyListener(new OnKeyListener() {
            private boolean longPressTriggered;
            private final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (view.hasFocus() && view.isShown()) {
                        longPressTriggered = true;
                        view.performLongClick();
                    }
                }
            };

            private boolean isConfirmKey(int keyCode) {
                return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                        || keyCode == KeyEvent.KEYCODE_BUTTON_A;
            }

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (!isConfirmKey(keyCode)) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        longPressTriggered = false;
                        view.removeCallbacks(longPressRunnable);
                        view.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                    }
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    view.removeCallbacks(longPressRunnable);
                    if (!longPressTriggered) {
                        view.performClick();
                    }
                    return true;
                }
                return true;
            }
        });
    }


    private final Handler mmHandler = new Handler();
    private Runnable mLongPressRunnable;
    private static final long LONG_PRESS_DELAY = 800;
    private boolean isLongPressTriggered = false;

    private boolean setMinPlayTimeChange(String typeEt,boolean increase){
        myHandle.removeCallbacks(myRunnable);
        myHandle.postDelayed(myRunnable, myHandleSeconds);
        try {
            int currentValue = mPlayerConfig.optInt(typeEt, 0);
            if(currentValue!=0){
                int newValue = increase ? currentValue + 1 : currentValue - 1;
                if(newValue < 0) {
                    newValue = 0;
                }
                mPlayerConfig.put(typeEt,newValue);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isBottomVisible()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP ) {
                if(mPlayerTimeStartBtn.hasFocus()){
                    if(setMinPlayTimeChange("st",true)){
                        return true;
                    }
                }
                View focusedView = mPlayBtnGroup.findFocus();
                if (focusedView instanceof TextView) {
                    return true;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ) {
                if(mPlayerTimeStartBtn.hasFocus()){
                    if(setMinPlayTimeChange("st",false))return true;
                }
            }
            return super.onKeyDown(keyCode, event);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getRepeatCount() == 0) {
            isLongPressTriggered = false;
            mLongPressRunnable = new Runnable() {
                @Override
                public void run() {
                    speedPlayStart();
                    isLongPressTriggered = true;
                }
            };
            mmHandler.postDelayed(mLongPressRunnable, LONG_PRESS_DELAY);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            // 移除长按回调
            if (mLongPressRunnable != null) {
                mmHandler.removeCallbacks(mLongPressRunnable);
                mLongPressRunnable = null;
            }
            if (isLongPressTriggered) {
                speedPlayEnd();
            } else {
                if (!isBottomVisible()) {
                    showUpBottom();
                    myHandle.postDelayed(myRunnable, myHandleSeconds);
                }else {
                    return super.onKeyUp(keyCode, event);
                }
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        myHandle.removeCallbacks(myRunnable);
        if (!isBottomVisible()) {
            showBottom();
            // 闲置计时关闭
            myHandle.postDelayed(myRunnable, myHandleSeconds);
        } else {
            hideBottom();
        }
        return true;
    }
    
    private class LockRunnable implements Runnable {
        @Override
        public void run() {
            mLockView.setVisibility(INVISIBLE);
        }
    }
    
    @Override
    public boolean onBackPressed() {
        if (isClickBackBtn) {
            isClickBackBtn = false;
            if (isBottomVisible()) {
                hideBottom();
            }
            return false;
        }
        if (super.onBackPressed()) {
            return true;
        }
        if (isBottomVisible()) {
            hideBottom();
            return true;
        }
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(myRunnable2);
    }


    //尝试去bom
    public String getWebPlayUrlIfNeeded(String webPlayUrl) {
        if (webPlayUrl != null && !webPlayUrl.contains("127.0.0.1:9978") &&  webPlayUrl.contains(".m3u8")) {
            try {
                String urlEncode = URLEncoder.encode(webPlayUrl, "UTF-8");
                LOG.i("echo-BOM-------");
                return ControlManager.get().getAddress(true) + "proxy?go=bom&url=" + urlEncode;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return webPlayUrl;
    }

    public String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }

    public boolean switchPlayer(){
        try {
            int playerType= mPlayerConfig.getInt("pl");
            int p_type = (playerType == 1) ? playerType + 1 : (playerType == 2) ? playerType - 1 : playerType;
            if (p_type != playerType) {
                LOG.i("echo-switchPlayer: " + playerType + " -> " + p_type);
//                Toast.makeText(getContext(), "切换到"+(p_type==1?"IJK":"EXO"), Toast.LENGTH_SHORT).show();
                mPlayerConfig.put("pl", p_type);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
            }else {
                LOG.i("echo-switchPlayer: skip unsupported playerType=" + playerType);
                return true;
            }
        }catch (Exception e){
            LOG.i("echo-switchPlayer error: " + e.getMessage());
            return true;
        }
        return false;
    }

    public void playM3u8(final String url, final HashMap<String, String> headers) {
        if(url.contains("url=")){
            listener.startPlayUrl(url, headers);
            return;
        }
        OkGo.getInstance().cancelTag("m3u8-1");
        OkGo.getInstance().cancelTag("m3u8-2");
        final HttpHeaders okGoHeaders = new HttpHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                okGoHeaders.put(entry.getKey(), entry.getValue());
            }
        }
        OkGo.<String>get(url)
                .tag("m3u8-1")
                .headers(okGoHeaders)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        String content = response.body();
                        if (!content.startsWith("#EXTM3U")) {
                            listener.startPlayUrl(url, headers);
                            return;
                        }
                        String forwardUrl = extractForwardUrl(url, content);
                        if (forwardUrl.isEmpty()) {
                            LOG.i("echo-m3u81-to-play");
                            processM3u8Content(url, content, headers);
                        } else {
                            fetchAndProcessForwardUrl(forwardUrl, headers, okGoHeaders, url);
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        LOG.e("echo-m3u8请求错误1: " + response.getException());
                        listener.startPlayUrl(url, headers);
                    }
                });
    }

    private String extractForwardUrl(String baseUrl, String content) {
        String[] lines = content.split("\\r?\\n",50);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                // 只需要找接下来的几行
                for (int j = i + 1; j < lines.length; j++) {
                    String targetLine = lines[j].trim();
                    if (targetLine.isEmpty()) continue;
                    if (isValidM3u8Line(targetLine)) {
                        return resolveForwardUrl(baseUrl, targetLine);
                    }
                }
            }
        }
        return "";
    }

    private boolean isValidM3u8Line(String line) {
        return !line.startsWith("#") && (line.endsWith(".m3u8") || line.contains(".m3u8?"));
    }

    private void processM3u8Content(String url, String content, HashMap<String, String> headers) {
        String basePath = getBasePath(url);
        RemoteServer.m3u8Content = M3u8.purify(basePath, content);
        if (RemoteServer.m3u8Content == null || M3u8.currentAdCount==0) {
            LOG.i("echo-m3u8内容解析：未检测到广告");
            listener.startPlayUrl(url, headers);
        } else {
            String proxyUrl = ControlManager.get().getAddress(true) + "proxyM3u8";
            listener.onM3u8ProxyUrl(proxyUrl, url);
            listener.startPlayUrl(proxyUrl, headers);
            Toast.makeText(getContext(), "已移除视频广告 "+M3u8.currentAdCount+" 条", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchAndProcessForwardUrl(final String forwardUrl, final HashMap<String, String> headers,
                                           HttpHeaders okGoHeaders, final String fallbackUrl) {
        OkGo.<String>get(forwardUrl)
                .tag("m3u8-2")
                .headers(okGoHeaders)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        String content = response.body();
                        LOG.i("echo-m3u82-to-play");
                        processM3u8Content(forwardUrl, content, headers);
                    }
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        LOG.e("echo-重定向 m3u8 请求错误: " + response.getException());
                        listener.startPlayUrl(fallbackUrl, headers);
                    }
                });
    }

    private String getBasePath(String url) {
        int ilast = url.lastIndexOf('/');
        return url.substring(0, ilast + 1);
    }

    private String resolveForwardUrl(String baseUrl, String line) {
        try {
            // 使用 URL 构造器自动解析相对路径
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, line);
            return resolved.toString();
        } catch (MalformedURLException e) {
            // 出现异常时可以记录日志，并返回原始 line
            LOG.e("echo-resolveForwardUrl异常: " + e.getMessage());
            return line;
        }
    }

    public String firstUrlByArray(String url)
    {
        try {
            JSONArray urlArray = new JSONArray(url);
            for (int i = 0; i < urlArray.length(); i++) {
                String item = urlArray.getString(i);
                if (item.contains("http")) {
                    url = item;
                    break; // 找到第一个立即终止循环
                }
            }
        } catch (JSONException e) {
        }
        return url;
    }

    public void evaluateScript(SourceBean sourceBean,String url, WebView web_view, XWalkView xWalk_view){
        String clickSelector = sourceBean.getClickSelector().trim();
        clickSelector=clickSelector.isEmpty()?VideoParseRuler.getHostScript(url):clickSelector;
        if (!clickSelector.isEmpty()) {
            String selector;
            if (clickSelector.contains(";") && !clickSelector.endsWith(";")) {
                String[] parts = clickSelector.split(";", 2);
                if (!url.contains(parts[0])) {
                    return;
                }
                selector = parts[1].trim();
            } else {
                selector = clickSelector.trim();
            }
            // 构造点击的 JS 代码
            String js = selector;
//            if(!selector.contains("click()"))js+=".click();";
            LOG.i("echo-javascript:" + js);
            if(web_view!=null){
                //4.4以上才支持这种写法
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    web_view.evaluateJavascript(js, null);
                } else {
                    web_view.loadUrl("javascript:" + js);
                }
            }
            if(xWalk_view!=null){
                //4.0+开始全部支持这种写法
                xWalk_view.evaluateJavascript(js, null);
            }
        }
    }

    public void stopOther()
    {
        Thunder.stop(false);//停止磁力下载
        Jianpian.finish();//停止p2p下载
        App.getInstance().setDashData(null);
    }

    private static class CircleThumbDrawable extends Drawable {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int normalSize;
        private final int activeSize;
        private final float strokeWidth;
        private boolean active;

        CircleThumbDrawable(Context context) {
            normalSize = dp(context, 12);
            activeSize = dp(context, 16);
            strokeWidth = dp(context, 2);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.WHITE);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokeWidth);
            strokePaint.setColor(Color.parseColor("#E50914"));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();
            int size = active ? activeSize : normalSize;
            float radius = Math.max(0, size / 2f - strokeWidth / 2f);
            canvas.drawCircle(cx, cy, radius, fillPaint);
            canvas.drawCircle(cx, cy, radius, strokePaint);
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] stateSet) {
            boolean newActive = false;
            if (stateSet != null) {
                for (int state : stateSet) {
                    if (state == android.R.attr.state_pressed
                            || state == android.R.attr.state_focused
                            || state == android.R.attr.state_selected) {
                        newActive = true;
                        break;
                    }
                }
            }
            if (active == newActive) {
                return false;
            }
            active = newActive;
            invalidateSelf();
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return activeSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return activeSize;
        }

        private static int dp(Context context, float value) {
            return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
