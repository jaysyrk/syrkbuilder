package dev.syrkbuilder.core.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Args {
    private final List<String> positional = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final Map<String, String> options = new HashMap<>();
    private final Set<String> flags = new HashSet<>();

    public static Args parse(String line) {
        Args a = new Args();
        for (String token : line.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            a.tokens.add(token);
            int eq = token.indexOf('=');
            if (token.startsWith("-") && token.length() > 1 && !Character.isDigit(token.charAt(1))) {
                a.flags.add(token.substring(1).toLowerCase(Locale.ROOT));
            } else if (eq > 0 && token.indexOf('[') < 0) {
                a.options.put(token.substring(0, eq).toLowerCase(Locale.ROOT), token.substring(eq + 1));
            } else {
                a.positional.add(token);
            }
        }
        return a;
    }

    public String rest(int from) {
        return from >= tokens.size() ? "" : String.join(" ", tokens.subList(from, tokens.size()));
    }

    public int size() {
        return positional.size();
    }

    public String word(int i) {
        return i < positional.size() ? positional.get(i) : null;
    }

    public String lower(int i) {
        String w = word(i);
        return w == null ? "" : w.toLowerCase(Locale.ROOT);
    }

    public boolean flag(String name) {
        return flags.contains(name);
    }

    public boolean has(String key) {
        return options.containsKey(key);
    }

    public String string(String key, String def) {
        return options.getOrDefault(key, def);
    }

    public int intArg(int i, String key, int def, int min, int max) {
        String raw = word(i);
        if (raw == null) {
            raw = options.get(key);
        }
        return raw == null ? def : limit(parseInt(raw, key), min, max, key);
    }

    public double doubleArg(int i, String key, double def, double min, double max) {
        String raw = word(i);
        if (raw == null) {
            raw = options.get(key);
        }
        return raw == null ? def : limit(parseDouble(raw, key), min, max, key);
    }

    public int intValue(String key, int def, int min, int max) {
        String raw = options.get(key);
        return raw == null ? def : limit(parseInt(raw, key), min, max, key);
    }

    public long longValue(String key, long def) {
        String raw = options.get(key);
        if (raw == null) {
            return def;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return raw.hashCode();
        }
    }

    public double doubleValue(String key, double def, double min, double max) {
        String raw = options.get(key);
        return raw == null ? def : limit(parseDouble(raw, key), min, max, key);
    }

    public List<String> notes() {
        return notes;
    }

    private int limit(int v, int min, int max, String key) {
        if (v < min || v > max) {
            int to = Math.max(min, Math.min(max, v));
            notes.add(name(key) + " " + v + " is out of range (" + min + " to " + max + ") - used " + to + ".");
            return to;
        }
        return v;
    }

    private double limit(double v, double min, double max, String key) {
        if (v < min || v > max) {
            double to = Math.max(min, Math.min(max, v));
            notes.add(name(key) + " " + trim(v) + " is out of range (" + trim(min) + " to " + trim(max) + ") - used " + trim(to) + ".");
            return to;
        }
        return v;
    }

    private static String trim(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e9 ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String name(String key) {
        return switch (key == null ? "" : key) {
            case "r" -> "Radius";
            case "rx", "ry", "rz" -> "Radius " + key.substring(1);
            case "n" -> "Count";
            case "" -> "Value";
            default -> Character.toUpperCase(key.charAt(0)) + key.substring(1);
        };
    }

    private static int parseInt(String raw, String key) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a whole number" + (key == null ? "" : " (" + key + ")"));
        }
    }

    private static double parseDouble(String raw, String key) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a number" + (key == null ? "" : " (" + key + ")"));
        }
    }
}
