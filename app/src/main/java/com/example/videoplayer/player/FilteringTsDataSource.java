package com.example.videoplayer.player;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;

/**
 * A simplified TS filter that selects a PID based on URL parameters.
 */
@OptIn(markerClass = UnstableApi.class)
public class FilteringTsDataSource implements DataSource {

    private static final int PKT = 188;

    private final DataSource upstream;
    private final int[] ccMap;

    private int targetPid = -1;
    private int basePid = -1;
    private java.util.Set<Integer> audioPids = java.util.Collections.emptySet();

    private byte[] remainder = new byte[PKT];
    private int remainderLen = 0;

    public FilteringTsDataSource(DataSource upstream, int[] ccMap) {
        this.upstream = upstream;
        this.ccMap = ccMap;
    }

    public static class Factory implements DataSource.Factory {
        private final DataSource.Factory upstreamFactory;
        private final int[] sharedCcMap = new int[8192];

        public Factory(DataSource.Factory upstreamFactory) {
            this.upstreamFactory = upstreamFactory;
            // Initialize ccMap with -1
            java.util.Arrays.fill(sharedCcMap, -1);
        }

        @Override
        public DataSource createDataSource() {
            // Reset ccMap for new instances
            java.util.Arrays.fill(sharedCcMap, -1);
            return new FilteringTsDataSource(upstreamFactory.createDataSource(), sharedCcMap);
        }
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        String targetPidStr = dataSpec.uri.getQueryParameter(PlaylistProxy.PARAM_TARGET_PID);
        targetPid = targetPidStr != null ? Integer.parseInt(targetPidStr) : -1;

        String basePidStr = dataSpec.uri.getQueryParameter(PlaylistProxy.PARAM_BASE_PID);
        basePid = basePidStr != null ? Integer.parseInt(basePidStr) : targetPid;

        String audioPidsStr = dataSpec.uri.getQueryParameter(PlaylistProxy.PARAM_AUDIO_PIDS);
        if (audioPidsStr != null && !audioPidsStr.isEmpty()) {
            String[] parts = audioPidsStr.split(",");
            java.util.Set<Integer> pids = new java.util.HashSet<>();
            for (String p : parts) {
                try {
                    pids.add(Integer.parseInt(p));
                } catch (NumberFormatException ignored) {}
            }
            audioPids = pids;
        } else {
            audioPids = new java.util.HashSet<>();
            if (basePid != -1) audioPids.add(basePid);
            if (targetPid != -1) audioPids.add(targetPid);
        }

        Log.d("FilteringTs",
                "Opening segment with targetPid=" + targetPid + ", basePid=" + basePid +
                        ", audioPids=" + audioPids + " (URI: " + dataSpec.uri + ")");

        return upstream.open(dataSpec);
    }

    private void processPacket(byte[] outBuf, int i) {
        int pid = ((outBuf[i + 1] & 0x1F) << 8) | (outBuf[i + 2] & 0xFF);
        int afc = (outBuf[i + 3] & 0x30) >> 4;
        boolean hasPayload = (afc == 1 || afc == 3);

        // PCR/Sync Preservation
        boolean hasPcr = false;
        if (afc >= 2 && i + 5 < outBuf.length) {
            int afLen = outBuf[i + 4] & 0xFF;
            int afFlags = outBuf[i + 5] & 0xFF;
            if (afLen > 0 && (afFlags & 0x10) != 0) hasPcr = true;
        }

        if (pid == targetPid && targetPid != basePid && basePid != -1) {
            // Remap selected track to base PID
            outBuf[i + 1] = (byte) ((outBuf[i + 1] & 0xE0) | ((basePid >> 8) & 0x1F));
            outBuf[i + 2] = (byte) (basePid & 0xFF);
            updateCc(outBuf, i, basePid, hasPayload);
        } else if (pid == basePid && targetPid != basePid) {
            // Mute original primary unless it has PCR
            if (!hasPcr) {
                outBuf[i + 1] = (byte) (outBuf[i + 1] | 0x1F);
                outBuf[i + 2] = (byte) 0xFF;
            } else {
                if (hasPayload && afc == 3) {
                    // Strip audio payload so it doesn't mix with targetPid audio
                    int oldAfLen = outBuf[i + 4] & 0xFF;
                    outBuf[i + 3] = (byte) ((outBuf[i + 3] & 0xCF) | 0x20);
                    outBuf[i + 4] = (byte) 183;
                    for (int p = i + 5 + oldAfLen; p < i + PKT; p++) {
                        outBuf[p] = (byte) 0xFF;
                    }
                }
                updateCc(outBuf, i, basePid, false);
            }
        } else if (audioPids.contains(pid) && pid != targetPid) {
            // Mute all other audio tracks
            if (!hasPcr) {
                outBuf[i + 1] = (byte) (outBuf[i + 1] | 0x1F);
                outBuf[i + 2] = (byte) 0xFF;
            }
        }
        // Video and other PIDs pass through naturally
    }

    @Override
    public int read(byte[] outBuf, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (targetPid == -1) return upstream.read(outBuf, offset, length);

        if (remainderLen > 0 && length <= remainderLen) {
            System.arraycopy(remainder, 0, outBuf, offset, length);
            remainderLen -= length;
            System.arraycopy(remainder, length, remainder, 0, remainderLen);
            return length;
        }

        System.arraycopy(remainder, 0, outBuf, offset, remainderLen);
        int currentOffset = offset + remainderLen;
        int remainingLength = length - remainderLen;

        int bytesRead = upstream.read(outBuf, currentOffset, remainingLength);

        int totalBytes = (bytesRead > 0) ? remainderLen + bytesRead : remainderLen;
        if (totalBytes == 0) return bytesRead;

        int i = offset;
        int end = offset + totalBytes;
        int lastValidPacketEnd = offset;

        while (i + PKT <= end) {
            if (outBuf[i] == 0x47) {
                processPacket(outBuf, i);
                i += PKT;
                lastValidPacketEnd = i;
            } else {
                i++;
                lastValidPacketEnd = i;
            }
        }

        int leftover = end - lastValidPacketEnd;
        if (leftover > 0) {
            if (remainder.length < leftover) {
                remainder = new byte[leftover + PKT];
            }
            System.arraycopy(outBuf, lastValidPacketEnd, remainder, 0, leftover);
            remainderLen = leftover;
        } else {
            remainderLen = 0;
        }

        int validBytesToReturn = lastValidPacketEnd - offset;
        if (validBytesToReturn > 0) {
            return validBytesToReturn;
        } else if (bytesRead == androidx.media3.common.C.RESULT_END_OF_INPUT) {
            remainderLen = 0;
            return leftover;
        } else {
            return read(outBuf, offset, length);
        }
    }

    private void updateCc(byte[] buf, int i, int pid, boolean hasPayload) {
        int cc = ccMap[pid];
        if (hasPayload) {
            cc = (cc == -1) ? 0 : (cc + 1) & 0x0F;
            ccMap[pid] = cc;
        } else {
            if (cc == -1) cc = 0;
        }
        buf[i + 3] = (byte) ((buf[i + 3] & 0xF0) | cc);
    }

    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public java.util.Map<String, java.util.List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        upstream.close();
    }
}
