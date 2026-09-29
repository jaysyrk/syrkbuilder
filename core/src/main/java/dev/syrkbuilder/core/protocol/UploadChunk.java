package dev.syrkbuilder.core.protocol;

public record UploadChunk(int uploadId, int index, int total, byte[] data) {
}
