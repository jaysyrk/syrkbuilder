package dev.syrkbuilder.fabric;

import dev.syrkbuilder.core.protocol.Protocol;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ClientData {
    record Row(int id, int parent, boolean current, boolean onPath, boolean root, int blocks, long time, String checkpoint, String label,
               int lane, int[] passing, int[] merges, boolean above, boolean below) {
    }

    record Pending(String label, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean movable) {
    }

    private static List<Row> history = List.of();
    private static int lanes = 1;
    private static Pending pending;
    private static List<String> templates = List.of();
    private static List<int[]> path = List.of();
    private static int version;

    private ClientData() {
    }

    static List<Row> history() {
        return history;
    }

    static int lanes() {
        return lanes;
    }

    static Pending pending() {
        return pending;
    }

    static List<String> templates() {
        return templates;
    }

    static List<int[]> path() {
        return path;
    }

    static int version() {
        return version;
    }

    static void clear() {
        history = List.of();
        pending = null;
        templates = List.of();
        path = List.of();
        version++;
    }

    static void accept(Protocol.Data d) {
        switch (d.kind()) {
            case "history" -> history = layout(d.text());
            case "pending" -> pending = parsePending(d.text());
            case "templates" -> templates = d.text().isBlank() ? List.of() : List.of(d.text().split("\n"));
            case "path" -> {
                List<int[]> points = new ArrayList<>();
                for (String line : d.text().split("\n")) {
                    String[] p = line.trim().split(" ");
                    if (p.length == 3) {
                        try {
                            points.add(new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])});
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                path = points;
            }
            case "selection" -> {
                String[] p = d.text().trim().split(" ");
                if (p.length == 6) {
                    try {
                        int[] v = new int[6];
                        for (int i = 0; i < 6; i++) {
                            v[i] = Integer.parseInt(p[i]);
                        }
                        Selection.setPos1(new net.minecraft.core.BlockPos(v[0], v[1], v[2]));
                        Selection.setPos2(new net.minecraft.core.BlockPos(v[3], v[4], v[5]));
                    } catch (NumberFormatException ignored) {
                    }
                }
                return;
            }
            default -> {
                return;
            }
        }
        version++;
    }

    private static Pending parsePending(String text) {
        String[] f = text.split("\t");
        if (f.length < 8) {
            return null;
        }
        try {
            return new Pending(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]), f[7].equals("1"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Row> layout(String text) {
        record Raw(int id, int parent, int flags, int blocks, long time, String checkpoint, String label) {
        }
        List<Raw> raws = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (String line : text.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 7) {
                continue;
            }
            try {
                Raw r = new Raw(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                    Long.parseLong(f[4]), f[5], f[6]);
                raws.add(r);
                ids.add(r.id());
            } catch (NumberFormatException ignored) {
            }
        }
        raws.sort((a, b) -> Integer.compare(b.id(), a.id()));
        List<Integer> waiting = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        int maxLane = 0;
        for (Raw r : raws) {
            List<Integer> hits = new ArrayList<>();
            for (int i = 0; i < waiting.size(); i++) {
                if (waiting.get(i) == r.id()) {
                    hits.add(i);
                }
            }
            List<Integer> passingList = new ArrayList<>();
            for (int i = 0; i < waiting.size(); i++) {
                if (waiting.get(i) != -1 && waiting.get(i) != r.id()) {
                    passingList.add(i);
                }
            }
            int lane;
            if (hits.isEmpty()) {
                lane = waiting.indexOf(-1);
                if (lane < 0) {
                    lane = waiting.size();
                    waiting.add(-1);
                }
            } else {
                lane = hits.get(0);
            }
            int[] merges = new int[Math.max(0, hits.size() - 1)];
            for (int i = 1; i < hits.size(); i++) {
                merges[i - 1] = hits.get(i);
                waiting.set(hits.get(i), -1);
            }
            boolean below = r.parent() != -1 && ids.contains(r.parent());
            waiting.set(lane, below ? r.parent() : -1);
            maxLane = Math.max(maxLane, lane);
            int[] passing = passingList.stream().mapToInt(Integer::intValue).toArray();
            rows.add(new Row(r.id(), r.parent(), (r.flags() & 1) != 0, (r.flags() & 2) != 0, (r.flags() & 4) != 0, r.blocks(), r.time(),
                r.checkpoint(), r.label(), lane, passing, merges, !hits.isEmpty(), below));
        }
        lanes = maxLane + 1;
        return rows;
    }
}
