package dev.syrkbuilder.fabric;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

public final class NoClip {
    private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();

    private NoClip() {
    }

    public static boolean active(UUID player) {
        return ON.contains(player);
    }

    static boolean toggle(UUID player) {
        if (!ON.remove(player)) {
            ON.add(player);
            return true;
        }
        return false;
    }

    static void set(UUID player, boolean on) {
        if (on) {
            ON.add(player);
        } else {
            ON.remove(player);
        }
    }

    static void clear() {
        ON.clear();
    }

    public static void apply(Player player) {
        if (ON.contains(player.getUUID()) && player.getAbilities().mayfly) {
            player.noPhysics = true;
            player.getAbilities().flying = true;
        }
    }
}
