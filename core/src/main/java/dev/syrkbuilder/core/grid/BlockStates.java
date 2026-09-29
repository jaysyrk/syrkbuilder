package dev.syrkbuilder.core.grid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockStates {
    private static final List<String> HORIZONTAL = List.of("north", "east", "south", "west");
    private static final Set<String> RAIL_SHAPES = Set.of("north_south", "east_west", "ascending_east", "ascending_west",
        "ascending_north", "ascending_south", "south_east", "south_west", "north_west", "north_east");

    private BlockStates() {
    }

    public static String transform(String block, int quarterTurns, boolean mirrorX) {
        int open = block.indexOf('[');
        if (open < 0 || !block.endsWith("]")) {
            return block;
        }
        String id = block.substring(0, open);
        Map<String, String> props = new LinkedHashMap<>();
        for (String kv : block.substring(open + 1, block.length() - 1).split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
                props.put(kv.substring(0, eq), kv.substring(eq + 1));
            }
        }
        if (mirrorX) {
            props = mirror(props);
        }
        for (int i = 0; i < Math.floorMod(quarterTurns, 4); i++) {
            props = rotateOnce(props);
        }
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }

    private static String turn(String dir) {
        int i = HORIZONTAL.indexOf(dir);
        return i < 0 ? dir : HORIZONTAL.get((i + 1) % 4);
    }

    private static String flipX(String dir) {
        return switch (dir) {
            case "east" -> "west";
            case "west" -> "east";
            default -> dir;
        };
    }

    private static Map<String, String> rotateOnce(Map<String, String> in) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (HORIZONTAL.contains(key)) {
                out.put(turn(key), value);
                continue;
            }
            switch (key) {
                case "facing", "horizontal_facing" -> out.put(key, turn(value));
                case "axis" -> out.put(key, value.equals("x") ? "z" : value.equals("z") ? "x" : value);
                case "rotation" -> out.put(key, String.valueOf((parse(value) + 4) % 16));
                case "shape" -> out.put(key, RAIL_SHAPES.contains(value) ? rail(value, BlockStates::turn) : value);
                default -> out.put(key, value);
            }
        }
        return reorder(in, out);
    }

    private static Map<String, String> mirror(Map<String, String> in) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (HORIZONTAL.contains(key)) {
                out.put(flipX(key), value);
                continue;
            }
            switch (key) {
                case "facing", "horizontal_facing" -> out.put(key, flipX(value));
                case "rotation" -> out.put(key, String.valueOf((16 - parse(value)) % 16));
                case "hinge", "type" -> out.put(key, value.equals("left") ? "right" : value.equals("right") ? "left" : value);
                case "shape" -> {
                    if (RAIL_SHAPES.contains(value)) {
                        out.put(key, rail(value, BlockStates::flipX));
                    } else {
                        out.put(key, value.replace("left", "#").replace("right", "left").replace("#", "right"));
                    }
                }
                default -> out.put(key, value);
            }
        }
        return reorder(in, out);
    }

    private static String rail(String shape, java.util.function.UnaryOperator<String> dirMap) {
        String[] parts = shape.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('_');
            }
            sb.append(parts[i].equals("ascending") ? parts[i] : dirMap.apply(parts[i]));
        }
        String s = sb.toString();
        if (!RAIL_SHAPES.contains(s) && parts.length == 2 && !parts[0].equals("ascending")) {
            String swapped = s.substring(s.indexOf('_') + 1) + "_" + s.substring(0, s.indexOf('_'));
            if (RAIL_SHAPES.contains(swapped)) {
                return swapped;
            }
        }
        return s;
    }

    private static Map<String, String> reorder(Map<String, String> original, Map<String, String> changed) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : original.keySet()) {
            if (changed.containsKey(key)) {
                out.put(key, changed.get(key));
            }
        }
        for (Map.Entry<String, String> e : changed.entrySet()) {
            out.putIfAbsent(e.getKey(), e.getValue());
        }
        return out;
    }

    private static int parse(String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
