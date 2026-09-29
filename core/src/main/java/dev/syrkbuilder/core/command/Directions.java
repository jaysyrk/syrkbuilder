package dev.syrkbuilder.core.command;

public final class Directions {
    private Directions() {
    }

    public static int[] unit(String word, float yaw, float pitch) {
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        int[] flat = Math.abs(fx) > Math.abs(fz) ? new int[]{(int) Math.signum(fx), 0, 0} : new int[]{0, 0, (int) Math.signum(fz)};
        int[] look = pitch < -60 ? new int[]{0, 1, 0} : pitch > 60 ? new int[]{0, -1, 0} : flat;
        int[] right = {-flat[2], 0, flat[0]};
        return switch (word == null ? "" : word) {
            case "", "me", "look" -> look;
            case "up", "u" -> new int[]{0, 1, 0};
            case "down", "d" -> new int[]{0, -1, 0};
            case "forward", "f", "ahead" -> flat;
            case "back", "backward", "b" -> new int[]{-flat[0], 0, -flat[2]};
            case "right", "r" -> right;
            case "left", "l" -> new int[]{-right[0], 0, -right[2]};
            case "north", "n" -> new int[]{0, 0, -1};
            case "south", "s" -> new int[]{0, 0, 1};
            case "east", "e" -> new int[]{1, 0, 0};
            case "west", "w" -> new int[]{-1, 0, 0};
            default -> null;
        };
    }

    public static boolean isWord(String word) {
        return word != null && unit(word, 0, 0) != null;
    }
}
