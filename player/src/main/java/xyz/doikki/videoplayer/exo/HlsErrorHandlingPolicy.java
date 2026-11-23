package xyz.doikki.videoplayer.exo;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;

/**
 * HLS切片快速重试，兼容TS同步异常，并保留默认的错误暴露阈值和备用轨道回退。
 */
public class HlsErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {

    private static final long RETRY_DELAY_MS = 500;  // 重试延迟500毫秒

    public HlsErrorHandlingPolicy() {
        super();
    }

    @Override
    public long getRetryDelayMsFor(@NonNull LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo) {
        boolean isMediaChunk = loadErrorInfo.mediaLoadData.dataType == C.DATA_TYPE_MEDIA;
        String message = loadErrorInfo.exception.getMessage();
        if (isMediaChunk && loadErrorInfo.exception instanceof ParserException
                && message != null && message.startsWith("Cannot find sync byte.")) {
            // TS解析器已丢弃不同步数据，HlsMediaChunk会记录读取位置，允许重试后继续读取。
            Log.w("Tvbox-runtime", "echo-hls-ts-resync-retry count=" + loadErrorInfo.errorCount
                    + ", bytesLoaded=" + loadErrorInfo.loadEventInfo.bytesLoaded
                    + ", segment=" + loadErrorInfo.loadEventInfo.uri);
            return RETRY_DELAY_MS;
        }
        long retryDelayMs = super.getRetryDelayMsFor(loadErrorInfo);
        // 保留默认不可重试异常分类，重试次数由默认错误暴露阈值控制。
        return isMediaChunk && retryDelayMs != C.TIME_UNSET ? RETRY_DELAY_MS : retryDelayMs;
    }
}
