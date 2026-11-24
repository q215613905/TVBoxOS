package com.github.tvbox.osc.player;

import android.content.Context;
import android.os.Looper;
import android.util.Pair;

import com.github.tvbox.osc.util.AudioTrackMemory;
import com.github.tvbox.osc.util.LOG;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.ArrayList;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xyz.doikki.videoplayer.exo.ExoMediaPlayer;

public class ExoPlayer extends ExoMediaPlayer {

    private static AudioTrackMemory memory;
    private volatile long internalSubtitleDelayUs;
    private OnCuesListener onCuesListener;
    private boolean defaultSubtitleTrackSelected;
    private boolean defaultSubtitleTrackSelectionClosed;

    public ExoPlayer(Context context) {
        super(context);
        setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .build());
        setRenderersFactory(buildRenderersFactory(context));
        LOG.i("echo-exo-low-memory-load-control");
        memory = AudioTrackMemory.getInstance(context);
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        mInternalPlayer.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) {
                loadDefaultSubtitleTrackBeforeReady();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    defaultSubtitleTrackSelectionClosed = true;
                }
            }

            @Override
            public void onCues(CueGroup cueGroup) {
                OnCuesListener listener = onCuesListener;
                if (listener != null) {
                    listener.onCues(cueGroup.cues);
                }
            }
        });
        LOG.i("echo-exo-cues-listener-ready");
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        defaultSubtitleTrackSelected = false;
        defaultSubtitleTrackSelectionClosed = false;
        super.setDataSource(path, headers);
    }

    private RenderersFactory buildRenderersFactory(Context context) {
        DefaultRenderersFactory factory = new SubtitleOffsetRenderersFactory(context, new SubtitleDelayProvider() {
            @Override
            public long getDelayUs() {
                return internalSubtitleDelayUs;
            }
        })
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        try {
            Method method = DefaultRenderersFactory.class.getMethod("forceDisableMediaCodecAsynchronousQueueing");
            method.invoke(factory);
            LOG.i("echo-exo-disable-async-codec-queue");
        } catch (Throwable th) {
            LOG.i("echo-exo-disable-async-codec-queue-skip:" + th.getClass().getSimpleName());
        }
        return factory;
    }

    public TrackInfo getTrackInfo() {
        TrackInfo data = new TrackInfo();
        MappingTrackSelector.MappedTrackInfo mappedInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedInfo == null) return data;

        for (int rendererIndex = 0; rendererIndex < mappedInfo.getRendererCount(); rendererIndex++) {
            int type = mappedInfo.getRendererType(rendererIndex);
            if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_VIDEO && type != C.TRACK_TYPE_TEXT) continue;

            TrackGroupArray groups = mappedInfo.getTrackGroups(rendererIndex);
            for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
                TrackGroup group = groups.get(groupIndex);
                for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                    Format fmt = group.getFormat(trackIndex);
                    if (type == C.TRACK_TYPE_TEXT && isUndeclaredClosedCaptionTrack(fmt)) continue;
                    String language = getLanguage(fmt);
                    String detail = type == C.TRACK_TYPE_VIDEO ? getVideoName(fmt) : getName(fmt);
                    TrackInfoBean bean = new TrackInfoBean();
                    bean.language = language;
                    bean.name = buildDisplayName(type == C.TRACK_TYPE_AUDIO ? "\u97f3\u8f68" : type == C.TRACK_TYPE_VIDEO ? "\u89c6\u8f68" : "\u5b57\u5e55",
                            type == C.TRACK_TYPE_AUDIO ? data.getAudio().size() + 1 : type == C.TRACK_TYPE_VIDEO ? data.getVideo().size() + 1 : data.getSubtitle().size() + 1,
                            language, detail);
                    bean.renderId = rendererIndex;
                    bean.trackGroupId = groupIndex;
                    bean.trackId = trackIndex;
                    bean.groupIndex = groupIndex;
                    bean.index = trackIndex;
                    bean.selected = isCurrentTrackSelected(fmt, type);
                    bean.bitmapSubtitle = type == C.TRACK_TYPE_TEXT && isBitmapSubtitle(fmt);

                    if (type == C.TRACK_TYPE_AUDIO) {
                        data.addAudio(bean);
                    } else if (type == C.TRACK_TYPE_VIDEO) {
                        data.addVideo(bean);
                    } else {
                        data.addSubtitle(bean);
                    }
                }
            }
        }
        return data;
    }

    public void setTrack(int groupIndex, int trackIndex, String playKey) {
        MappingTrackSelector.MappedTrackInfo mappedInfo = trackSelector.getCurrentMappedTrackInfo();
        setTrack(findAudioRendererIndex(mappedInfo), groupIndex, trackIndex, playKey);
    }

    public void setTrack(TrackInfoBean track, String playKey) {
        if (track == null) return;
        setTrack(track.renderId, track.trackGroupId, track.trackId, playKey);
    }

    private void setTrack(int rendererIndex, int groupIndex, int trackIndex, String playKey) {
        try {
            MappingTrackSelector.MappedTrackInfo mappedInfo = trackSelector.getCurrentMappedTrackInfo();
            if (mappedInfo == null) {
                LOG.i("echo-setTrack: MappedTrackInfo is null");
                return;
            }
            if (rendererIndex == C.INDEX_UNSET || rendererIndex < 0 || rendererIndex >= mappedInfo.getRendererCount()) {
                LOG.i("echo-setTrack: No renderer found");
                return;
            }

            TrackGroupArray groups = mappedInfo.getTrackGroups(rendererIndex);
            if (!isTrackIndexValid(groups, groupIndex, trackIndex)) {
                LOG.i("echo-setTrack: Invalid track index - group:" + groupIndex + ", track:" + trackIndex);
                return;
            }
            DefaultTrackSelector.SelectionOverride override =
                    new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);
            DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
            builder.setRendererDisabled(rendererIndex, false);
            builder.clearSelectionOverrides(rendererIndex);
            builder.setSelectionOverride(rendererIndex, groups, override);
            trackSelector.setParameters(builder.build());

            if (mappedInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_AUDIO && !playKey.isEmpty()) {
                memory.save(playKey, groupIndex, trackIndex);
            }
        } catch (Exception e) {
            LOG.i("echo-setTrack error: " + e.getMessage());
        }
    }

    public void loadDefaultTrack(String playKey) {
        Pair<Integer, Integer> pair = memory.exoLoad(playKey);
        if (pair == null) return;

        MappingTrackSelector.MappedTrackInfo mappedInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedInfo == null) return;

        int audioRendererIndex = findAudioRendererIndex(mappedInfo);
        if (audioRendererIndex == C.INDEX_UNSET) return;

        setTrack(audioRendererIndex, pair.first, pair.second, "");
    }

    public void loadDefaultSubtitleTrack() {
        if (defaultSubtitleTrackSelected) return;
        TrackInfo trackInfo = getTrackInfo();
        List<TrackInfoBean> subtitles = trackInfo.getSubtitle();
        if (subtitles.isEmpty()) return;

        defaultSubtitleTrackSelected = true;
        TrackInfoBean target = subtitles.get(0);
        for (TrackInfoBean subtitle : subtitles) {
            if ("国语".equals(subtitle.language)) {
                target = subtitle;
                break;
            }
        }
        setTrack(target, "");
    }

    private void loadDefaultSubtitleTrackBeforeReady() {
        if (defaultSubtitleTrackSelectionClosed) return;
        loadDefaultSubtitleTrack();
    }

    public void setOnCuesListener(OnCuesListener listener) {
        onCuesListener = listener;
    }

    public void setInternalSubtitleDelay(int milliseconds) {
        internalSubtitleDelayUs = milliseconds * 1000L;
    }

    private int findAudioRendererIndex(MappingTrackSelector.MappedTrackInfo mappedInfo) {
        if (mappedInfo == null) return C.INDEX_UNSET;
        for (int i = 0; i < mappedInfo.getRendererCount(); i++) {
            if (mappedInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                return i;
            }
        }
        return C.INDEX_UNSET;
    }

    private boolean isTrackIndexValid(TrackGroupArray groups, int groupIndex, int trackIndex) {
        if (groupIndex < 0 || groupIndex >= groups.length) return false;
        TrackGroup group = groups.get(groupIndex);
        return trackIndex >= 0 && trackIndex < group.length;
    }

    private boolean isBitmapSubtitle(Format format) {
        if (format == null || format.sampleMimeType == null) return false;
        String mimeType = format.sampleMimeType.toLowerCase();
        return mimeType.contains("pgs") || mimeType.contains("dvb") || mimeType.contains("vobsub");
    }

    private boolean isUndeclaredClosedCaptionTrack(Format format) {
        if (format == null || format.accessibilityChannel != Format.NO_VALUE) return false;
        return MimeTypes.APPLICATION_CEA608.equals(format.sampleMimeType)
                || MimeTypes.APPLICATION_CEA708.equals(format.sampleMimeType);
    }

    private boolean isCurrentTrackSelected(Format format, int trackType) {
        if (mInternalPlayer == null) return false;
        Tracks tracks = mInternalPlayer.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != trackType || !group.isSelected()) continue;
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i) && isSameFormat(format, group.getTrackFormat(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSameFormat(Format a, Format b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.id != null && b.id != null && a.id.equals(b.id)) return true;
        return a.equals(b);
    }

    private static final Map<String, String> LANG_MAP = new HashMap<>();

    static {
        LANG_MAP.put("zh", "\u56fd\u8bed");
        LANG_MAP.put("zh-cn", "\u56fd\u8bed");
        LANG_MAP.put("cmn", "\u56fd\u8bed");
        LANG_MAP.put("chi", "\u56fd\u8bed");
        LANG_MAP.put("zho", "\u56fd\u8bed");
        LANG_MAP.put("chs", "\u56fd\u8bed");
        LANG_MAP.put("yue", "\u7ca4\u8bed");
        LANG_MAP.put("zh-hk", "\u7ca4\u8bed");
        LANG_MAP.put("zh-yue", "\u7ca4\u8bed");
        LANG_MAP.put("en", "\u82f1\u8bed");
        LANG_MAP.put("en-us", "\u82f1\u8bed");
        LANG_MAP.put("eng", "\u82f1\u8bed");
        LANG_MAP.put("ja", "\u65e5\u8bed");
        LANG_MAP.put("jpn", "\u65e5\u8bed");
        LANG_MAP.put("ko", "\u97e9\u8bed");
        LANG_MAP.put("kor", "\u97e9\u8bed");
        LANG_MAP.put("th", "\u6cf0\u8bed");
        LANG_MAP.put("tha", "\u6cf0\u8bed");
    }

    private String getLanguage(Format fmt) {
        String language = matchLanguage(fmt.language);
        if (!language.isEmpty()) {
            return language;
        }
        return matchLanguage((fmt.label == null ? "" : fmt.label) + " "
                + (fmt.id == null ? "" : fmt.id) + " "
                + (fmt.codecs == null ? "" : fmt.codecs));
    }

    private String matchLanguage(String text) {
        if (text == null) return "";
        String value = text.toLowerCase();
        String mapped = LANG_MAP.get(value);
        if (mapped != null) return mapped;
        if (value.contains("yue") || value.contains("cantonese") || value.contains("\u7ca4") || value.contains("\u5e7f\u4e1c")) {
            return "\u7ca4\u8bed";
        }
        if (value.contains("zh") || value.contains("chi") || value.contains("zho") || value.contains("chs")
                || value.contains("cht") || value.contains("cmn") || value.contains("\u4e2d")
                || value.contains("\u56fd\u8bed") || value.contains("\u666e\u901a\u8bdd")) {
            return "\u56fd\u8bed";
        }
        if (value.contains("en") || value.contains("eng") || value.contains("english") || value.contains("\u82f1")) {
            return "\u82f1\u8bed";
        }
        if (value.contains("ja") || value.contains("jpn") || value.contains("japanese") || value.contains("\u65e5")) {
            return "\u65e5\u8bed";
        }
        if (value.contains("ko") || value.contains("kor") || value.contains("korean") || value.contains("\u97e9")) {
            return "\u97e9\u8bed";
        }
        if (value.contains("tha") || value.contains("thai") || value.contains("th")) {
            return "\u6cf0\u8bed";
        }
        return "";
    }

    private String getName(Format fmt) {
        String channelLabel;
        if (fmt.channelCount <= 0) {
            channelLabel = "";
        } else if (fmt.channelCount == 1) {
            channelLabel = "\u5355\u58f0\u9053";
        } else if (fmt.channelCount == 2) {
            channelLabel = "\u7acb\u4f53\u58f0";
        } else {
            channelLabel = fmt.channelCount + " \u58f0\u9053";
        }

        String codec = "";
        if (fmt.codecs != null && !fmt.codecs.isEmpty()) {
            codec = fmt.codecs.toUpperCase();
        }
        if (fmt.sampleMimeType != null && fmt.sampleMimeType.contains("/")) {
            String mime = fmt.sampleMimeType.substring(fmt.sampleMimeType.indexOf('/') + 1);
            if (codec.isEmpty()) {
                codec = mime.toUpperCase();
            }
        }
        StringBuilder builder = new StringBuilder();
        appendPart(builder, fmt.label);
        appendPart(builder, codec);
        appendPart(builder, channelLabel);
        return builder.toString();
    }

    private String getVideoName(Format fmt) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, fmt.label);
        if (fmt.width > 0 && fmt.height > 0) {
            appendPart(builder, fmt.width + "x" + fmt.height);
        }
        if (fmt.codecs != null && !fmt.codecs.isEmpty()) {
            appendPart(builder, fmt.codecs.toUpperCase());
        } else if (fmt.sampleMimeType != null && fmt.sampleMimeType.contains("/")) {
            appendPart(builder, fmt.sampleMimeType.substring(fmt.sampleMimeType.indexOf('/') + 1).toUpperCase());
        }
        return builder.toString();
    }

    private String buildDisplayName(String prefix, int number, String language, String detail) {
        StringBuilder builder = new StringBuilder(prefix).append(number);
        if (language != null && !language.isEmpty()) {
            builder.append(" - ").append(language);
        }
        if (detail != null && !detail.isEmpty()) {
            builder.append(" ").append(detail);
        }
        return builder.toString();
    }

    private void appendPart(StringBuilder builder, String value) {
        if (value == null) return;
        String part = value.trim();
        if (part.isEmpty() || "und".equalsIgnoreCase(part) || "\u672a\u77e5".equals(part)) return;
        if (builder.length() > 0) {
            builder.append(" / ");
        }
        builder.append(part);
    }

    public interface OnCuesListener {
        void onCues(List<Cue> cues);
    }

    private interface SubtitleDelayProvider {
        long getDelayUs();
    }

    private static final class SubtitleOffsetRenderersFactory extends DefaultRenderersFactory {
        private final SubtitleDelayProvider subtitleDelayProvider;

        SubtitleOffsetRenderersFactory(Context context, SubtitleDelayProvider subtitleDelayProvider) {
            super(context);
            this.subtitleDelayProvider = subtitleDelayProvider;
        }

        @Override
        protected void buildTextRenderers(Context context, TextOutput output, Looper outputLooper,
                                          int extensionRendererMode, ArrayList<Renderer> out) {
            int firstRendererIndex = out.size();
            super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out);
            for (int i = firstRendererIndex; i < out.size(); i++) {
                Renderer renderer = out.get(i);
                out.set(i, (Renderer) Proxy.newProxyInstance(Renderer.class.getClassLoader(),
                        new Class<?>[]{Renderer.class},
                        new SubtitleOffsetRendererHandler(renderer, subtitleDelayProvider)));
            }
        }
    }

    private static final class SubtitleOffsetRendererHandler implements InvocationHandler {
        private final Renderer renderer;
        private final SubtitleDelayProvider subtitleDelayProvider;

        SubtitleOffsetRendererHandler(Renderer renderer, SubtitleDelayProvider subtitleDelayProvider) {
            this.renderer = renderer;
            this.subtitleDelayProvider = subtitleDelayProvider;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object[] invokeArgs = args;
            if ("render".equals(method.getName()) && args != null && args.length > 0
                    && args[0] instanceof Long) {
                invokeArgs = args.clone();
                long positionUs = (Long) invokeArgs[0];
                invokeArgs[0] = Math.max(0, positionUs - subtitleDelayProvider.getDelayUs());
            }
            try {
                return method.invoke(renderer, invokeArgs);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
