package dev.syrkbuilder.fabric;

import dev.syrkbuilder.core.protocol.Protocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyrkPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<SyrkPayload> TYPE = new Type<>(Identifier.parse(Protocol.CHANNEL));

    public static final StreamCodec<FriendlyByteBuf, SyrkPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBytes(payload.data()),
        buf -> {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new SyrkPayload(bytes);
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
