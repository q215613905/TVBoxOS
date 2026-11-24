package xyz.doikki.videoplayer.exo;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;

import java.io.IOException;

/**
 * 自定义HLS错误处理策略，跳过坏的切片继续播放
 */
public class HlsErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {

    private static final int MAX_RETRIES = 3;  // 最多重试3次
    private static final long RETRY_DELAY_MS = 500;  // 重试延迟500毫秒

    public HlsErrorHandlingPolicy() {
        super();
    }

    @Override
    public long getRetryDelayMsFor(@NonNull LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo) {
        // 如果是HLS切片错误，使用较短的重试延迟
        if (isChunkError(loadErrorInfo)) {
            return RETRY_DELAY_MS;
        }
        return super.getRetryDelayMsFor(loadErrorInfo);
    }

    @Override
    public int getMinimumLoadableRetryCount(int dataType) {
        // 对于HLS切片，限制重试次数，避免无限重试
        if (dataType == C.DATA_TYPE_MEDIA) {
            return MAX_RETRIES;
        }
        return super.getMinimumLoadableRetryCount(dataType);
    }

    @Override
    public LoadErrorHandlingPolicy.FallbackSelection getFallbackSelectionFor(
            @NonNull FallbackOptions fallbackOptions,
            @NonNull LoadErrorInfo loadErrorInfo) {

        // 对于HLS切片加载错误，不使用fallback，而是直接跳过
        if (isChunkError(loadErrorInfo)) {
            // 返回null表示不使用fallback，ExoPlayer会跳过这个切片继续播放
            return null;
        }

        return super.getFallbackSelectionFor(fallbackOptions, loadErrorInfo);
    }

    /**
     * 判断是否是切片错误
     */
    private boolean isChunkError(@NonNull LoadErrorInfo loadErrorInfo) {
        // 检查是否是IO异常（网络错误、404等）
        Throwable error = loadErrorInfo.exception;
        return error instanceof IOException;
    }
}
