package xyz.doikki.videoplayer.exo;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.source.hls.HlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaChunkExtractor;
import com.google.android.exoplayer2.util.TimestampAdjuster;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 在当前连接内继续读取TS不同步数据，避免每次同步异常都重新请求切片。 */
public final class ResyncHlsExtractorFactory implements HlsExtractorFactory {

    private static final int MAX_CONSECUTIVE_RESYNC_ERRORS = 16;

    @Override
    public HlsMediaChunkExtractor createExtractor(Uri uri, Format format,
            @Nullable List<Format> muxedCaptionFormats, TimestampAdjuster timestampAdjuster,
            Map<String, List<String>> responseHeaders, ExtractorInput sniffingExtractorInput,
            PlayerId playerId) throws IOException {
        return new ResyncExtractor(HlsExtractorFactory.DEFAULT.createExtractor(uri, format,
                muxedCaptionFormats, timestampAdjuster, responseHeaders, sniffingExtractorInput, playerId));
    }

    private static final class ResyncExtractor implements HlsMediaChunkExtractor {
        private final HlsMediaChunkExtractor delegate;
        private ExtractorInput lastInput;
        private long lastResyncPosition = -1;
        private int consecutiveResyncErrors;

        ResyncExtractor(HlsMediaChunkExtractor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void init(ExtractorOutput extractorOutput) {
            delegate.init(extractorOutput);
        }

        @Override
        public boolean read(ExtractorInput input) throws IOException {
            if (lastInput != input) {
                // TS解析器会跨切片复用，每个新输入独立计算容错次数和读取位置。
                lastInput = input;
                resetResyncProgress();
            }
            try {
                boolean hasMoreData = delegate.read(input);
                resetResyncProgress();
                return hasMoreData;
            } catch (ParserException error) {
                String message = error.getMessage();
                if (message == null || !message.startsWith("Cannot find sync byte.")) {
                    throw error;
                }
                long position = input.getPosition();
                if (consecutiveResyncErrors >= MAX_CONSECUTIVE_RESYNC_ERRORS
                        || position <= lastResyncPosition) {
                    Log.w("Tvbox-runtime", "echo-hls-ts-resync-fallback count=" + consecutiveResyncErrors
                            + ", position=" + position);
                    throw error;
                }
                // 异常前TS解析器已丢弃内部缓冲中的不同步字节。
                // 首次异常可能只消耗内部缓冲，后续连续异常必须有新的读取进展。
                lastResyncPosition = position;
                consecutiveResyncErrors++;
                Log.w("Tvbox-runtime", "echo-hls-ts-resync-inplace count=" + consecutiveResyncErrors
                        + ", position=" + position);
                // 返回外层读取循环，保留HlsMediaChunk原有的取消检查和输入结束处理。
                return true;
            }
        }

        private void resetResyncProgress() {
            lastResyncPosition = -1;
            consecutiveResyncErrors = 0;
        }

        @Override
        public boolean isPackedAudioExtractor() {
            return delegate.isPackedAudioExtractor();
        }

        @Override
        public boolean isReusable() {
            return delegate.isReusable();
        }

        @Override
        public HlsMediaChunkExtractor recreate() {
            return new ResyncExtractor(delegate.recreate());
        }

        @Override
        public void onTruncatedSegmentParsed() {
            delegate.onTruncatedSegmentParsed();
            resetResyncProgress();
        }
    }
}
