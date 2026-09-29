package dev.syrkbuilder.core.protocol;

public record Request(String command, int[] target, int[] pos1, int[] pos2, int[] feet, float yaw, float pitch) {
}
