package dev.syrkbuilder.core.session;

import dev.syrkbuilder.core.protocol.UploadChunk;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Uploads {
    private static final class Pending {
        final byte[][] parts;
        int received;
        long bytes;

        Pending(int total) {
            parts = new byte[total][];
        }
    }

    private static final int MAX_PENDING = 4;
    private static final int MAX_COMPLETE = 4;

    private final long maxBytes;
    private final Map<Integer, Pending> pending = new LinkedHashMap<>();
    private final Map<Integer, byte[]> complete = new LinkedHashMap<>();

    public Uploads(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public void accept(UploadChunk chunk) {
        if (chunk.total() <= 0 || chunk.total() > 4096 || chunk.index() < 0 || chunk.index() >= chunk.total()) {
            throw new IllegalArgumentException("Bad upload chunk");
        }
        Pending p = pending.get(chunk.uploadId());
        if (p == null) {
            while (pending.size() >= MAX_PENDING) {
                pending.remove(pending.keySet().iterator().next());
            }
            p = new Pending(chunk.total());
            pending.put(chunk.uploadId(), p);
        }
        if (p.parts.length != chunk.total()) {
            pending.remove(chunk.uploadId());
            throw new IllegalArgumentException("Upload changed size midway");
        }
        if (p.parts[chunk.index()] == null) {
            p.parts[chunk.index()] = chunk.data();
            p.received++;
            p.bytes += chunk.data().length;
        }
        if (p.bytes > maxBytes) {
            pending.remove(chunk.uploadId());
            throw new IllegalArgumentException("Upload is larger than the server allows (" + maxBytes / 1024 / 1024 + " MB)");
        }
        if (p.received == p.parts.length) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) p.bytes);
            for (byte[] part : p.parts) {
                out.writeBytes(part);
            }
            pending.remove(chunk.uploadId());
            complete.put(chunk.uploadId(), out.toByteArray());
            while (complete.size() > MAX_COMPLETE) {
                complete.remove(complete.keySet().iterator().next());
            }
        }
    }

    public byte[] take(int uploadId) {
        return complete.remove(uploadId);
    }
}
