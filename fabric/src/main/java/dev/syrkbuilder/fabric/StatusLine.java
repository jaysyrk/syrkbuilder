package dev.syrkbuilder.fabric;

final class StatusLine {
    private static volatile String text = "";
    private static volatile long at;

    private StatusLine() {
    }

    static void set(String message) {
        text = message.replace('&', '§');
        at = System.currentTimeMillis();
    }

    static String get() {
        return System.currentTimeMillis() - at > 60_000 ? "" : text;
    }
}
