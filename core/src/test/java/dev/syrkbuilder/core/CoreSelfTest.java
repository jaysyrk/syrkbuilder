package dev.syrkbuilder.core;

import dev.syrkbuilder.core.command.Commands;
import dev.syrkbuilder.core.command.Result;
import dev.syrkbuilder.core.edit.Box;
import dev.syrkbuilder.core.edit.EditStream;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.history.ChangeSet;
import dev.syrkbuilder.core.history.HistoryCodec;
import dev.syrkbuilder.core.history.HistoryTree;
import dev.syrkbuilder.core.grid.BlockGrid;
import dev.syrkbuilder.core.grid.BlockStates;
import dev.syrkbuilder.core.grid.GridCodec;
import dev.syrkbuilder.core.model.ModelImporter;
import dev.syrkbuilder.core.protocol.UploadChunk;
import dev.syrkbuilder.core.session.Services;
import dev.syrkbuilder.core.session.Session;
import dev.syrkbuilder.core.session.TemplateStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import dev.syrkbuilder.core.noise.PerlinNoise;
import dev.syrkbuilder.core.protocol.Protocol;
import dev.syrkbuilder.core.protocol.Request;
import dev.syrkbuilder.core.shape.ShapeStream;
import dev.syrkbuilder.core.shape.Shapes;
import dev.syrkbuilder.core.terrain.TerrainJob;
import dev.syrkbuilder.core.terrain.TerrainParams;
import dev.syrkbuilder.core.terrain.TerrainType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class CoreSelfTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        noise();
        shapes();
        grids();
        templates();
        scripts();
        models();
        engine();
        completion();
        warnings();
        brushes();
        gradients();
        schematics();
        treesAndPaths();
        fills();
        helixConnected();
        patterns();
        history();
        protocol();
        terrain();
        commands();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok, Object detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + name + ": " + detail);
        }
    }

    static int topOf(BlockGrid g, int x, int z) {
        for (int y = g.sizeY() - 1; y >= 0; y--) {
            if (g.get(x, y, z) != null) {
                return y;
            }
        }
        return -1;
    }

    static final class FlatWorld implements WorldView {
        final Map<Long, String> blocks = new HashMap<>();
        final int groundY;

        FlatWorld(int groundY) {
            this.groundY = groundY;
        }

        static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        }

        @Override
        public int groundY(int x, int z) {
            return groundY;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 320;
        }

        @Override
        public String blockId(int x, int y, int z) {
            String b = blocks.get(key(x, y, z));
            if (b != null) {
                return Pattern.baseId(b);
            }
            return y <= groundY ? "minecraft:stone" : "minecraft:air";
        }

        @Override
        public String blockState(int x, int y, int z) {
            String b = blocks.get(key(x, y, z));
            return b != null ? b : blockId(x, y, z);
        }

        int run(EditStream stream) {
            int[] n = {0};
            while (stream.drain((x, y, z, b) -> {
                blocks.put(key(x, y, z), b);
                n[0]++;
            }, 5000)) {
            }
            return n[0];
        }
    }

    private static void noise() {
        PerlinNoise a = new PerlinNoise(42);
        PerlinNoise b = new PerlinNoise(42);
        double min = 9;
        double max = -9;
        boolean same = true;
        for (int i = 0; i < 20000; i++) {
            double x = i * 0.137;
            double y = i * 0.071;
            double v = a.noise(x, y);
            same &= v == b.noise(x, y);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        check("noise deterministic", same, "");
        check("noise range", min >= -1.5 && max <= 1.5 && max - min > 1, min + ".." + max);
        double r = a.ridged(3.3, 7.1, 6, 2, 0.5);
        check("ridged in 0..1", r >= 0 && r <= 1, r);
    }

    private static int count(Shapes.Placed p, boolean hollow) {
        FlatWorld w = new FlatWorld(-1000);
        return w.run(new ShapeStream(p.box(), p.shape(), Pattern.of("stone"), hollow, null));
    }

    private static void shapes() {
        int r = 10;
        int sphere = count(Shapes.sphere(0, 100, 0, r), false);
        double expected = 4.0 / 3 * Math.PI * Math.pow(r + 0.5, 3);
        check("sphere volume", Math.abs(sphere - expected) / expected < 0.05, sphere + " vs " + expected);
        int shell = count(Shapes.sphere(0, 100, 0, r), true);
        check("hollow sphere smaller", shell < sphere / 3 && shell > 0, shell);
        int ring = count(Shapes.circle(0, 100, 0, 20, 1, Shapes.Axis.Y), false);
        check("circle ring ~ circumference", Math.abs(ring - 2 * Math.PI * 20) < 20, ring);
        int disc = count(Shapes.circle(0, 100, 0, 20, 1e9, Shapes.Axis.Y), false);
        check("disc ~ area", Math.abs(disc - Math.PI * 20.5 * 20.5) / (Math.PI * 400) < 0.05, disc);
        int cyl = count(Shapes.cylinder(0, 100, 0, 5, 10), false);
        check("cylinder = disc x height", cyl == 10 * count(Shapes.circle(0, 100, 0, 5, 1e9, Shapes.Axis.Y), false), cyl);
        int pyr = count(Shapes.pyramid(0, 100, 0, 3), false);
        check("pyramid 7x7+5x5+3x3+1", pyr == 49 + 25 + 9 + 1, pyr);
        int line = count(Shapes.line(0, 100, 0, 30, 100, 0, 0), false);
        check("line length", line == 31, line);
        int torus = count(Shapes.torus(0, 100, 0, 12, 3), false);
        double tv = 2 * Math.PI * Math.PI * 12 * 3.5 * 3.5;
        check("torus volume", Math.abs(torus - tv) / tv < 0.1, torus + " vs " + tv);
        int vertical = count(Shapes.circle(0, 100, 0, 8, 1, Shapes.Axis.X), false);
        int flat = count(Shapes.circle(0, 100, 0, 8, 1, Shapes.Axis.Y), false);
        check("upright circle same size", vertical == flat, vertical + " vs " + flat);
    }

    private static void patterns() {
        Pattern p = Pattern.parse("70%stone,30%andesite");
        int stone = 0;
        for (int i = 0; i < 10000; i++) {
            if (p.pick(i, i * 7, i * 13).equals("minecraft:stone")) {
                stone++;
            }
        }
        check("pattern weights", Math.abs(stone - 7000) < 300, stone);
        Pattern s = Pattern.parse("oak_stairs[facing=east,half=top],Stone");
        check("pattern keeps states", s.blocks().get(0).equals("minecraft:oak_stairs[facing=east,half=top]"), s.blocks());
        check("pattern lowercases ids", s.blocks().get(1).equals("minecraft:stone"), s.blocks());
        check("same position same pick", p.pick(5, 6, 7).equals(p.pick(5, 6, 7)), "");
    }

    private static void history() throws Exception {
        ChangeSet<String> c = new ChangeSet<>("test");
        c.record(-30000000, -64, 29999999, "air", "stone");
        c.record(5, 319, -5, "dirt", "grass");
        c.record(5, 319, -5, "grass", "gold");
        Map<String, String> world = new HashMap<>();
        c.replay(true, 0, 100, (x, y, z, s) -> world.put(x + "," + y + "," + z, s));
        check("undo restores original of double-changed block", "dirt".equals(world.get("5,319,-5")), world);
        check("packing extremes", "air".equals(world.get("-30000000,-64,29999999")), world);
        c.replay(false, 0, 100, (x, y, z, s) -> world.put(x + "," + y + "," + z, s));
        check("redo applies final", "gold".equals(world.get("5,319,-5")), world);
        int next = c.replay(true, 0, 2, (x, y, z, s) -> { });
        check("replay budget", next == 2, next);

        historyTree();
    }

    private static ChangeSet<String> change(String label, int x, String from, String to) {
        ChangeSet<String> c = new ChangeSet<>(label);
        c.record(x, 0, 0, from, to);
        return c;
    }

    private static void historyTree() throws Exception {
        HistoryTree<String> t = new HistoryTree<>(1000);
        ChangeSet<String> a = change("a", 1, "air", "A");
        ChangeSet<String> b = change("b", 2, "air", "B");
        t.push(a);
        t.push(b);
        check("tree undo", t.undo() == b, "");
        ChangeSet<String> c = change("c", 3, "air", "C");
        t.push(c);
        check("new edit after undo keeps old branch", t.size() == 3 && t.node(2) != null, t.size());
        List<HistoryTree.Step<String>> path = t.pathTo(t.node(2));
        check("goto other branch: undo c then redo b", path.size() == 2 && path.get(0).undo() && path.get(0).change() == c
            && !path.get(1).undo() && path.get(1).change() == b, path);
        check("redo after goto follows new branch", t.undo() == b && t.redo() == b, "");
        t.checkpoint("before-c");
        check("checkpoint lookup", t.find("before-c") == t.current() && t.find("#1") == t.node(1) && t.find("start") == t.root(), "");

        HistoryTree<String> r = new HistoryTree<>(1000);
        ChangeSet<String> e1 = new ChangeSet<>("e1");
        e1.record(0, 0, 0, "air", "stone");
        e1.record(50, 0, 0, "air", "stone");
        r.push(e1);
        ChangeSet<String> e2 = new ChangeSet<>("e2");
        e2.record(0, 0, 0, "stone", "gold");
        e2.record(50, 0, 0, "stone", "gold");
        r.push(e2);
        Map<Long, String> region = r.regionAt(r.root(), Box.of(-5, -5, -5, 5, 5, 5));
        check("region restore limited to box", region.size() == 1 && "air".equals(region.get(ChangeSet.pack(0, 0, 0))), region);
        check("region restore leaves position alone", r.current().id() == 2, r.current().id());
        Map<Long, String> mid = r.regionAt(r.node(1), Box.of(-5, -5, -5, 5, 5, 5));
        check("region restore to middle", "stone".equals(mid.get(ChangeSet.pack(0, 0, 0))), mid);

        HistoryTree<String> q = new HistoryTree<>(1000);
        ChangeSet<String> qb = change("qb", 2, "air", "B");
        ChangeSet<String> qc = change("qc", 3, "air", "C");
        q.push(change("qa", 1, "air", "A"));
        q.push(qb);
        q.undo();
        q.push(qc);
        q.undo();
        q.regionAt(q.node(2), Box.of(-5, -5, -5, 5, 5, 5));
        check("region restore keeps the redo branch", q.redo() == qc, q.current().id());

        HistoryTree<String> lin = new HistoryTree<>(1000);
        ChangeSet<String> l1 = change("l1", 1, "air", "A");
        lin.push(l1);
        lin.push(change("l2", 2, "air", "B"));
        lin.push(change("l3", 3, "air", "C"));
        Map<Integer, ChangeSet<String>> partial = new HashMap<>();
        partial.put(1, l1);
        partial.put(3, lin.current().change());
        HistoryTree<String> survived = HistoryTree.rebuild(1000, lin.structure(), partial, lin.current().id());
        check("rebuild with a lost entry stays on its nearest ancestor", survived.current().id() == 1 && survived.undo() == l1,
            survived.current().id());

        HistoryTree<String> p = new HistoryTree<>(3);
        p.push(change("1", 1, "a", "b"));
        p.push(change("2", 2, "a", "b"));
        p.undo();
        p.push(change("3", 3, "a", "b"));
        p.push(change("4", 4, "a", "b"));
        check("prune drops side branch first", p.node(2) == null && p.totalBlocks() == 3, p.totalBlocks());
        p.push(change("5", 5, "a", "b"));
        check("prune moves root forward", p.totalBlocks() <= 3 && p.undo() != null, p.totalBlocks());

        HistoryCodec.StateCodec<String> sc = new HistoryCodec.StateCodec<>() {
            public String encode(String s) {
                return s;
            }

            public String decode(String s) {
                return s;
            }
        };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HistoryCodec.writeChange(new DataOutputStream(bytes), e2, sc);
        ChangeSet<String> back = HistoryCodec.readChange(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), sc);
        check("change file roundtrip", back.size() == 2 && back.label().equals("e2") && "gold".equals(back.palette().get(back.afterIndex(1))), back.size());
        ByteArrayOutputStream idx = new ByteArrayOutputStream();
        HistoryCodec.writeIndex(new DataOutputStream(idx), t.structure(), t.current().id());
        HistoryCodec.Index index = HistoryCodec.readIndex(new DataInputStream(new ByteArrayInputStream(idx.toByteArray())));
        Map<Integer, ChangeSet<String>> changes = new HashMap<>();
        changes.put(1, a);
        changes.put(2, b);
        changes.put(3, c);
        HistoryTree<String> rebuilt = HistoryTree.rebuild(1000, index.nodes(), changes, index.currentId());
        check("tree rebuild", rebuilt.size() == 3 && rebuilt.current().id() == t.current().id()
            && "before-c".equals(rebuilt.find("before-c").checkpoint()) && rebuilt.redo() == t.redo(), rebuilt.size());
    }

    private static void protocol() throws Exception {
        Request r = new Request("terrain mountain radius=60", new int[]{1, -2, 3}, null, new int[]{4, 5, 6}, new int[]{7, 8, 9}, 91.5f, -12f);
        Request back = (Request) Protocol.decode(Protocol.encode(r));
        check("protocol roundtrip", back.command().equals(r.command()) && back.target()[1] == -2 && back.pos1() == null
            && back.pos2()[2] == 6 && back.yaw() == 91.5f, back);
        byte[] big = new byte[75000];
        new java.util.Random(3).nextBytes(big);
        List<byte[]> packets = Protocol.encodeUpload(9, big);
        check("upload split into 3 packets under 32KB", packets.size() == 3 && packets.stream().allMatch(pk -> pk.length < 32767), packets.size());
        Session session = new Session(1 << 20);
        java.util.Collections.reverse(packets);
        for (byte[] pk : packets) {
            session.uploads().accept((UploadChunk) Protocol.decode(pk));
        }
        check("upload reassembles out of order", java.util.Arrays.equals(session.uploads().take(9), big), "");
        Session small = new Session(1000);
        boolean rejected = false;
        try {
            for (byte[] pk : Protocol.encodeUpload(1, big)) {
                small.uploads().accept((UploadChunk) Protocol.decode(pk));
            }
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check("upload size limit", rejected, "");
        Session flood = new Session(1 << 20);
        flood.uploads().accept(new UploadChunk(1, 0, 2, new byte[]{1}));
        for (int id = 100; id < 110; id++) {
            flood.uploads().accept(new UploadChunk(id, 0, 2, new byte[]{2}));
        }
        flood.uploads().accept(new UploadChunk(1, 1, 2, new byte[]{3}));
        flood.uploads().accept(new UploadChunk(109, 1, 2, new byte[]{4}));
        check("unfinished uploads are capped, oldest dropped first", flood.uploads().take(1) == null
            && Arrays.equals(flood.uploads().take(109), new byte[]{2, 4}), "");
    }

    private static void terrain() {
        for (TerrainType type : TerrainType.values()) {
            FlatWorld w = new FlatWorld(64);
            TerrainParams p = new TerrainParams();
            p.type = type;
            p.radius = type == TerrainType.ISLAND ? 16 : 30;
            p.height = type.defaultHeight;
            p.seed = 1234;
            p.style = type.defaultStyle;
            p.erosion = type.defaultErosion;
            p.width = 10;
            TerrainJob job = new TerrainJob(p, 0, 100, 0, w).prepare();
            int n = w.run(job);
            check(type.id() + " produces blocks", n > 100, n);
            check(type.id() + " estimate matches", n == job.estimate(), n + " vs " + job.estimate());
            int highest = Integer.MIN_VALUE;
            int lowest = Integer.MAX_VALUE;
            for (Map.Entry<Long, String> e : w.blocks.entrySet()) {
                long k = e.getKey();
                int y = (int) (k << 52 >> 52);
                if (!e.getValue().endsWith("air")) {
                    highest = Math.max(highest, y);
                }
                lowest = Math.min(lowest, y);
            }
            switch (type) {
                case CRATER, CANYON -> check(type.id() + " digs down", lowest < 64 - p.height / 2, lowest);
                case ISLAND -> check("island floats around y=100", highest > 100 && lowest < 100 && lowest > 64, lowest + ".." + highest);
                default -> check(type.id() + " peak near requested height", highest > 64 + p.height * 0.6 && highest <= 64 + p.height + 2,
                    highest + " for height " + p.height);
            }
            boolean cornerUntouched = !w.blocks.containsKey(FlatWorld.key(-p.radius - 1, 64, -p.radius - 1));
            check(type.id() + " leaves far corner alone", cornerUntouched || type == TerrainType.CANYON, "");
        }
        FlatWorld w = new FlatWorld(64);
        TerrainParams p = new TerrainParams();
        p.type = TerrainType.VOLCANO;
        p.radius = 40;
        p.height = 50;
        p.seed = 7;
        p.style = "volcanic";
        p.erosion = 0;
        w.run(new TerrainJob(p, 0, 64, 0, w).prepare());
        check("volcano has a lava lake", w.blocks.containsValue("minecraft:lava"), "");
        FlatWorld w1 = new FlatWorld(64);
        FlatWorld w2 = new FlatWorld(64);
        p.type = TerrainType.MOUNTAIN;
        p.erosion = 60;
        w1.run(new TerrainJob(p, 0, 64, 0, w1).prepare());
        w2.run(new TerrainJob(p, 0, 64, 0, w2).prepare());
        check("terrain deterministic per seed", w1.blocks.equals(w2.blocks), "");
    }

    private static void commands() {
        Commands cmds = new Commands(2_000_000);
        FlatWorld w = new FlatWorld(64);
        Request look = new Request("sphere 60%stone,40%andesite 6 -h", new int[]{0, 70, 0}, null, null, null, 0, 0);
        Result r = cmds.run(look, w, session(), services());
        check("sphere command", r.kind() == Result.Kind.EDIT && w.run(r.stream()) > 0, r);
        Result noSel = cmds.run(new Request("set stone", null, null, null, null, 0, 0), w, session(), services());
        check("set needs selection", noSel.kind() == Result.Kind.ERROR, noSel);
        Result set = cmds.run(new Request("set glass", null, new int[]{0, 70, 0}, new int[]{2, 71, 3}, null, 0, 0), w, session(), services());
        check("set selection size", w.run(set.stream()) == 3 * 2 * 4, "");
        Result rep = cmds.run(new Request("replace glass gold_block", null, new int[]{0, 70, 0}, new int[]{9, 71, 9}, null, 0, 0), w, session(), services());
        check("replace only matching", w.run(rep.stream()) == 24, "");
        Result tooBig = cmds.run(new Request("set stone", null, new int[]{0, 0, 0}, new int[]{999, 100, 999}, null, 0, 0), w, session(), services());
        check("volume limit", tooBig.kind() == Result.Kind.ERROR, tooBig);
        Result terr = cmds.run(new Request("terrain mountain radius=30 height=40 seed=5", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), services());
        check("terrain command", terr.kind() == Result.Kind.EDIT, terr);
        Result badStyle = cmds.run(new Request("terrain mountain style=cheese", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), services());
        check("bad style rejected", badStyle.kind() == Result.Kind.ERROR, badStyle);
        Result badNum = cmds.run(new Request("sphere stone big", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), services());
        check("bad number is an error, not a crash", badNum.kind() == Result.Kind.ERROR, badNum);
        Box box = Box.of(5, 5, 5, 1, 1, 1);
        check("box normalizes", box.minX() == 1 && box.volume() == 125, box);
    }

    private static Session session() {
        return new Session(1 << 24);
    }

    static final class MemoryTemplates implements TemplateStore {
        public String export(String name, dev.syrkbuilder.core.grid.BlockGrid grid, dev.syrkbuilder.core.grid.Schematics.Format format) {
            return name + "." + format.extension;
        }

        final Map<String, byte[]> files = new HashMap<>();

        public void save(String name, BlockGrid grid) {
            files.put(name, GridCodec.encode(grid));
        }

        public BlockGrid load(String name) throws java.io.IOException {
            byte[] b = files.get(name);
            return b == null ? null : GridCodec.decode(b);
        }

        public List<String> list() {
            return new ArrayList<>(files.keySet());
        }

        public boolean delete(String name) {
            return files.remove(name) != null;
        }
    }

    private static Services services() {
        return services(new MemoryTemplates(), Map.of());
    }

    private static Services services(TemplateStore store, Map<String, String> scripts) {
        return new Services() {
            public TemplateStore templates() {
                return store;
            }

            public String serverScript(String name) {
                return scripts.get(name);
            }

            public long scriptTimeoutMillis() {
                return 1000;
            }
        };
    }

    private static void grids() throws Exception {
        check("stairs rotate", BlockStates.transform("minecraft:oak_stairs[facing=north,half=bottom,shape=inner_left]", 1, false)
            .equals("minecraft:oak_stairs[facing=east,half=bottom,shape=inner_left]"), "");
        check("stairs mirror", BlockStates.transform("minecraft:oak_stairs[facing=east,half=top,shape=outer_left]", 0, true)
            .equals("minecraft:oak_stairs[facing=west,half=top,shape=outer_right]"), BlockStates.transform("minecraft:oak_stairs[facing=east,half=top,shape=outer_left]", 0, true));
        check("fence connections rotate", BlockStates.transform("minecraft:oak_fence[east=false,north=true,south=false,waterlogged=false,west=false]", 1, false)
            .equals("minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=false]"), BlockStates.transform("minecraft:oak_fence[east=false,north=true,south=false,waterlogged=false,west=false]", 1, false));
        check("log axis rotates", BlockStates.transform("minecraft:oak_log[axis=x]", 1, false).equals("minecraft:oak_log[axis=z]"), "");
        check("sign rotation", BlockStates.transform("minecraft:oak_sign[rotation=14,waterlogged=false]", 1, false).equals("minecraft:oak_sign[rotation=2,waterlogged=false]"), "");
        check("rails rotate", BlockStates.transform("minecraft:rail[shape=north_south,waterlogged=false]", 1, false).contains("shape=east_west")
            && BlockStates.transform("minecraft:rail[shape=south_east,waterlogged=false]", 1, false).contains("shape=south_west"),
            BlockStates.transform("minecraft:rail[shape=south_east,waterlogged=false]", 1, false));
        check("four turns = identity", BlockStates.transform("minecraft:rail[shape=ascending_north]", 4, false).equals("minecraft:rail[shape=ascending_north]"), "");
        check("plain block unchanged", BlockStates.transform("minecraft:stone", 1, true).equals("minecraft:stone"), "");

        BlockGrid g = new BlockGrid(3, 1, 2);
        g.set(0, 0, 0, "minecraft:stone");
        g.set(2, 0, 1, "minecraft:gold_block");
        g.markers().put("spawn", new int[]{2, 0, 1});
        BlockGrid r = g.transformed(1, false);
        check("grid rotate dims", r.sizeX() == 2 && r.sizeZ() == 3, r.sizeX() + "x" + r.sizeZ());
        check("grid rotate cells", "minecraft:stone".equals(r.get(1, 0, 0)) && "minecraft:gold_block".equals(r.get(0, 0, 2)), "");
        check("marker follows rotation", Arrays.equals(r.markers().get("spawn"), new int[]{0, 0, 2}), Arrays.toString(r.markers().get("spawn")));
        BlockGrid full = g.transformed(4, false);
        check("grid 4 turns identity", "minecraft:gold_block".equals(full.get(2, 0, 1)), "");
        BlockGrid back = GridCodec.decode(GridCodec.encode(r));
        check("grid codec roundtrip", back.sizeX() == 2 && "minecraft:gold_block".equals(back.get(0, 0, 2)) && back.get(1, 0, 1) == null
            && Arrays.equals(back.markers().get("spawn"), new int[]{0, 0, 2}), "");
    }

    private static void templates() throws Exception {
        Commands cmds = new Commands(2_000_000);
        FlatWorld w = new FlatWorld(64);
        w.blocks.put(FlatWorld.key(0, 65, 0), "minecraft:oak_stairs[facing=north]");
        w.blocks.put(FlatWorld.key(2, 65, 0), "minecraft:gold_block");
        Session s = session();
        MemoryTemplates store = new MemoryTemplates();
        Services sv = services(store, Map.of());
        int[] p1 = {0, 65, 0};
        int[] p2 = {2, 66, 1};
        Result m = cmds.run(new Request("marker spawn1", new int[]{1, 64, 1}, null, null, null, 0, 0), w, s, sv);
        check("marker set on top of block", m.kind() == Result.Kind.MESSAGE && Arrays.equals(s.markers().get("spawn1"), new int[]{1, 65, 1}), m);
        Result saved = cmds.run(new Request("template save arena1", null, p1, p2, null, 0, 0), w, s, sv);
        check("template save", saved.kind() == Result.Kind.MESSAGE && store.files.containsKey("arena1"), saved);
        Result pasted = cmds.run(new Request("template paste arena1 rotate=90", new int[]{100, 70, 100}, null, null, null, 0, 0), w, s, sv);
        check("template paste", pasted.kind() == Result.Kind.EDIT, pasted);
        w.run(pasted.stream());
        boolean stairsTurned = w.blocks.values().stream().anyMatch(b -> b.equals("minecraft:oak_stairs[facing=east]"));
        check("pasted stairs rotated", stairsTurned, w.blocks.values());
        check("paste reports marker position", pasted.lines().size() == 1 && pasted.lines().get(0).contains("spawn1"), pasted.lines());
        Result swap = cmds.run(new Request("template paste arena1 swap=gold_block:diamond_block -a", new int[]{200, 70, 200}, null, null, null, 0, 0), w, s, sv);
        FlatWorld w2 = new FlatWorld(0);
        w2.run(swap.stream());
        check("paste swap + skip air", w2.blocks.containsValue("minecraft:diamond_block") && !w2.blocks.containsValue("minecraft:gold_block")
            && w2.blocks.values().stream().noneMatch(b -> b.endsWith("air")), w2.blocks.values());
        Result list = cmds.run(new Request("template list", null, null, null, null, 0, 0), w, s, sv);
        check("template list", list.lines().get(0).contains("arena1"), list);
        Result bad = cmds.run(new Request("template save ../evil", null, p1, p2, null, 0, 0), w, s, sv);
        check("template names are safe", bad.kind() == Result.Kind.ERROR, bad);
        Result copy = cmds.run(new Request("copy", null, p1, p2, null, 0, 0), w, s, sv);
        Result rot = cmds.run(new Request("rotate 180", null, null, null, null, 0, 0), w, s, sv);
        check("copy + rotate clipboard", copy.kind() == Result.Kind.MESSAGE && rot.kind() == Result.Kind.MESSAGE && s.clipboard().sizeX() == 3, rot);
        Result badRot = cmds.run(new Request("rotate 45", null, null, null, null, 0, 0), w, s, sv);
        check("rotation must be 90s", badRot.kind() == Result.Kind.ERROR, badRot);

        BlockGrid model = new BlockGrid(2, 2, 2);
        model.set(0, 0, 0, "minecraft:red_wool");
        for (byte[] pk : Protocol.encodeUpload(77, GridCodec.encode(model))) {
            try {
                s.uploads().accept((UploadChunk) Protocol.decode(pk));
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        Result up = cmds.run(new Request("upload-paste 77 name=dragon", new int[]{0, 64, 0}, null, null, null, 0, 0), w, s, sv);
        FlatWorld w3 = new FlatWorld(0);
        check("upload paste places only filled voxels", up.kind() == Result.Kind.EDIT && w3.run(up.stream()) == 1 && up.label().contains("dragon"), up);
    }

    private static void scripts() {
        Commands cmds = new Commands(100_000);
        FlatWorld w = new FlatWorld(64);
        String tower = "var h = parseInt(args[0] || '5');\n"
            + "for (var y = 0; y < h; y++) { set(origin.x, origin.y + 1 + y, origin.z, y % 2 ? 'stone' : 'gold_block'); }\n"
            + "print('built', h);";
        Result r = cmds.run(new Request("script tower 4", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), services(new MemoryTemplates(), Map.of("tower", tower)));
        check("script runs", r.kind() == Result.Kind.EDIT && w.run(r.stream()) == 4, r);
        check("script print", r.lines().stream().anyMatch(l -> l.contains("built 4")), r.lines());
        Map<String, String> sc = new HashMap<>();
        sc.put("loop", "while (true) {}");
        sc.put("java", "java.lang.System.exit(1)");
        sc.put("packages", "Packages.java.io.File");
        sc.put("big", "fill(0,0,0, 100,100,100, 'stone')");
        sc.put("syntax", "set(1,2,");
        sc.put("recurse", "function f(n) { return f(n + 1); } f(0);");
        sc.put("shapes", "sphere(0,80,0,4,'glass',true); cylinder(0,64,20,3,5,'stone'); line(0,70,0,10,70,0,'gold_block'); print(get(0,64,0), ground(3,3), noise(1,2) <= 1)");
        Services sv = services(new MemoryTemplates(), sc);
        long start = System.currentTimeMillis();
        Result loop = cmds.run(new Request("script loop", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), sv);
        check("infinite loop is stopped", loop.kind() == Result.Kind.ERROR && loop.lines().get(0).contains("longer") && System.currentTimeMillis() - start < 5000, loop);
        Result java = cmds.run(new Request("script java", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), sv);
        check("no Java access", java.kind() == Result.Kind.ERROR, java);
        Result pk = cmds.run(new Request("script packages", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), sv);
        check("no Packages access", pk.kind() == Result.Kind.ERROR, pk);
        Result big = cmds.run(new Request("script big", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), sv);
        check("script block limit", big.kind() == Result.Kind.ERROR && big.lines().get(0).contains("limit"), big);
        Result syntax = cmds.run(new Request("script syntax", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), sv);
        check("syntax error reported with line", syntax.kind() == Result.Kind.ERROR && syntax.lines().get(0).contains("line"), syntax);
        long deepStart = System.currentTimeMillis();
        Result deep = cmds.run(new Request("script recurse", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), sv);
        check("runaway recursion is stopped quickly", deep.kind() == Result.Kind.ERROR && deep.lines().get(0).contains("stack")
            && System.currentTimeMillis() - deepStart < 1000, deep);
        Result shapes = cmds.run(new Request("script shapes", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), sv);
        check("script shape helpers", shapes.kind() == Result.Kind.EDIT && shapes.lines().get(0).contains("minecraft:stone 64 true"), shapes.lines());
        Result missing = cmds.run(new Request("script nope", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), sv);
        check("missing script", missing.kind() == Result.Kind.ERROR, missing);
    }

    private static void models() throws Exception {
        Path dir = Files.createTempDirectory("sbmodels");
        String obj = "mtllib cube.mtl\nusemtl red\n"
            + "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nv 0 0 1\nv 1 0 1\nv 1 1 1\nv 0 1 1\n"
            + "f 1 2 3 4\nf 5 6 7 8\nf 1 2 6 5\nf 2 3 7 6\nf 3 4 8 7\nf 4 1 5 8\n";
        Files.writeString(dir.resolve("cube.obj"), obj);
        Files.writeString(dir.resolve("cube.mtl"), "newmtl red\nKd 0.62 0.15 0.13\n");
        BlockGrid shell = ModelImporter.importFile(dir.resolve("cube.obj"), new ModelImporter.Options(10, false, "wool"));
        BlockGrid solid = ModelImporter.importFile(dir.resolve("cube.obj"), new ModelImporter.Options(10, true, "wool"));
        check("obj cube size", shell.sizeX() == 10 && shell.sizeY() == 10 && shell.sizeZ() == 10, shell.sizeX());
        check("obj shell hollow", shell.nonEmpty() == 1000 - 512, shell.nonEmpty());
        check("obj solid filled", solid.nonEmpty() == 1000, solid.nonEmpty());
        check("obj colour matched to red wool", shell.palette().size() == 1 && shell.palette().get(0).equals("minecraft:red_wool"), shell.palette());

        ByteArrayOutputStream vox = new ByteArrayOutputStream();
        ByteBuffer b = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
        b.put("VOX ".getBytes()).putInt(150);
        b.put("MAIN".getBytes()).putInt(0).putInt(0);
        b.put("SIZE".getBytes()).putInt(12).putInt(0).putInt(2).putInt(3).putInt(4);
        b.put("XYZI".getBytes()).putInt(8).putInt(0).putInt(1).put((byte) 1).put((byte) 2).put((byte) 3).put((byte) 1);
        b.put("RGBA".getBytes()).putInt(1024).putInt(0);
        for (int i = 0; i < 256; i++) {
            b.put((byte) 20).put((byte) 60).put((byte) 150).put((byte) 255);
        }
        vox.write(b.array(), 0, b.position());
        Files.write(dir.resolve("m.vox"), vox.toByteArray());
        java.awt.image.BufferedImage hm = new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int ix = 0; ix < 40; ix++) {
            for (int iz = 0; iz < 20; iz++) {
                int v = ix * 255 / 39;
                hm.setRGB(ix, iz, v << 16 | v << 8 | v);
            }
        }
        javax.imageio.ImageIO.write(hm, "png", dir.resolve("ramp.png").toFile());
        BlockGrid hg = ModelImporter.importFile(dir.resolve("ramp.png"), new ModelImporter.Options(20, false, "all", 16));
        check("heightmap keeps the aspect ratio", hg.sizeX() == 20 && hg.sizeZ() == 10 && hg.sizeY() == 17, hg.sizeX() + "x" + hg.sizeY() + "x" + hg.sizeZ());
        check("heightmap: dark is low, bright is high", hg.get(0, 1, 5) == null && hg.get(19, 16, 5) != null && hg.get(0, 0, 5) != null, hg.get(19, 16, 5));
        check("heightmap has grass on gentle slopes", "minecraft:grass_block".equals(hg.get(5, topOf(hg, 5, 5), 5)), hg.get(5, topOf(hg, 5, 5), 5));
        BlockGrid vg = ModelImporter.importFile(dir.resolve("m.vox"), new ModelImporter.Options(64, false, "concrete"));
        check("vox axes (z-up -> y-up)", vg.sizeX() == 2 && vg.sizeY() == 4 && vg.sizeZ() == 3, vg.sizeX() + "x" + vg.sizeY() + "x" + vg.sizeZ());
        check("vox voxel placed + coloured", "minecraft:blue_concrete".equals(vg.get(1, 3, 0)) && vg.nonEmpty() == 1, vg.palette());

        String json = "{\"asset\":{\"version\":\"2.0\"},\"scene\":0,\"scenes\":[{\"nodes\":[0]}],"
            + "\"nodes\":[{\"mesh\":0,\"translation\":[5,0,0]}],"
            + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"material\":0}]}],"
            + "\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorFactor\":[0.95,0.7,0.1,1]}}],"
            + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"}],"
            + "\"bufferViews\":[{\"buffer\":0,\"byteLength\":36}],\"buffers\":[{\"byteLength\":36}]}";
        while (json.length() % 4 != 0) {
            json += " ";
        }
        ByteBuffer bin = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        bin.putFloat(0).putFloat(0).putFloat(0).putFloat(4).putFloat(0).putFloat(0).putFloat(0).putFloat(4).putFloat(0);
        byte[] jb = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer glb = ByteBuffer.allocate(12 + 8 + jb.length + 8 + 36).order(ByteOrder.LITTLE_ENDIAN);
        glb.put("glTF".getBytes()).putInt(2).putInt(glb.capacity());
        glb.putInt(jb.length).putInt(0x4E4F534A).put(jb);
        glb.putInt(36).putInt(0x004E4942).put(bin.array());
        Files.write(dir.resolve("tri.glb"), glb.array());
        BlockGrid tg = ModelImporter.importFile(dir.resolve("tri.glb"), new ModelImporter.Options(9, false, "concrete"));
        check("glb triangle size", tg.sizeX() == 9 && tg.sizeY() == 9 && tg.sizeZ() == 1, tg.sizeX() + "x" + tg.sizeY() + "x" + tg.sizeZ());
        check("glb triangle filled half", tg.nonEmpty() >= 40 && tg.nonEmpty() <= 55, tg.nonEmpty());
        check("glb colour matched to yellow", tg.palette().get(0).equals("minecraft:yellow_concrete"), tg.palette());
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFF2C2E8F);
        img.setRGB(1, 0, 0xFFE06100);
        javax.imageio.ImageIO.write(img, "png", dir.resolve("tex.png").toFile());
        Files.writeString(dir.resolve("quad.mtl"), "newmtl t\nKd 1 1 1\nmap_Kd tex.png\n");
        Files.writeString(dir.resolve("quad.obj"), "mtllib quad.mtl\nusemtl t\nv 0 0 0\nv 8 0 0\nv 8 8 0\nv 0 8 0\n"
            + "vt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nf 1/1 2/2 3/3 4/4\n");
        BlockGrid qg = ModelImporter.importFile(dir.resolve("quad.obj"), new ModelImporter.Options(9, false, "concrete"));
        check("texture sampled", "minecraft:blue_concrete".equals(qg.get(1, 4, 0)) && "minecraft:orange_concrete".equals(qg.get(7, 4, 0)),
            qg.get(1, 4, 0) + " / " + qg.get(7, 4, 0));
        Path d2 = Files.createTempDirectory("sbmtl");
        Files.writeString(d2.resolve("Cube.obj"), "\uFEFFmtllib exported_name.mtl\nusemtl red\n" + obj.substring(obj.indexOf("v 0 0 0")), StandardCharsets.UTF_8);
        Files.writeString(d2.resolve("CUBE.MTL"), "newmtl red\r\nKd 0.62 0.15 0.13\r\n");
        List<String> warn = new ArrayList<>();
        BlockGrid found = ModelImporter.importFile(d2.resolve("Cube.obj"), new ModelImporter.Options(10, false, "wool"), warn);
        check("mtl found despite name/case/BOM/CRLF", found.palette().equals(List.of("minecraft:red_wool")) && warn.isEmpty(), found.palette() + " " + warn);
        Files.delete(d2.resolve("CUBE.MTL"));
        warn.clear();
        ModelImporter.importFile(d2.resolve("Cube.obj"), new ModelImporter.Options(10, false, "wool"), warn);
        check("missing mtl is reported", warn.size() == 1 && warn.get(0).contains("colours file"), warn);
        boolean unsupported = false;
        try {
            Files.writeString(dir.resolve("x.fbx"), "");
            ModelImporter.importFile(dir.resolve("x.fbx"), new ModelImporter.Options(9, false, "all"));
        } catch (java.io.IOException e) {
            unsupported = true;
        }
        check("unsupported format message", unsupported, "");
    }

    static final class MemoryPlatform implements dev.syrkbuilder.core.engine.Platform<FlatWorld, String> {
        final java.util.ArrayDeque<Runnable> mainThread = new java.util.ArrayDeque<>();
        String denyScripts;

        @Override
        public String scriptDenied(java.util.UUID player) {
            return denyScripts;
        }

        public WorldView view(FlatWorld w) {
            return w;
        }

        public String worldKey(FlatWorld w) {
            return "test/overworld";
        }

        public String parse(String s) {
            if (s.contains("bogus")) {
                throw new IllegalArgumentException("bad");
            }
            return s;
        }

        public String serialize(String b) {
            return b;
        }

        public String blockId(String b) {
            return dev.syrkbuilder.core.edit.Pattern.baseId(b);
        }

        public String get(FlatWorld w, int x, int y, int z) {
            return w.blockState(x, y, z);
        }

        public void set(FlatWorld w, int x, int y, int z, String b) {
            w.blocks.put(FlatWorld.key(x, y, z), b);
        }

        public synchronized void runOnMainThread(Runnable task) {
            mainThread.add(task);
        }

        synchronized Runnable poll() {
            return mainThread.poll();
        }
    }

    private static void engine() throws Exception {
        java.io.File data = Files.createTempDirectory("sbengine").toFile();
        FlatWorld world = new FlatWorld(64);
        java.util.UUID player = new java.util.UUID(1, 2);
        List<String> msgs = new ArrayList<>();
        MemoryPlatform platform = new MemoryPlatform();
        dev.syrkbuilder.core.engine.EngineConfig cfg = dev.syrkbuilder.core.engine.EngineConfig.defaults();
        dev.syrkbuilder.core.engine.Engine<FlatWorld, String>[] engine = new dev.syrkbuilder.core.engine.Engine[]{
            new dev.syrkbuilder.core.engine.Engine<>(platform, cfg, data, java.util.logging.Logger.getLogger("test"))};
        check("example scripts installed", new java.io.File(data, "scripts/maze.js").isFile(), "");
        java.util.function.BiConsumer<String, int[][]> send = (cmd, pos) -> {
            engine[0].receive(player, world, Protocol.encode(new Request(cmd, pos[0], pos[1], pos[2], null, 0, 0)), msgs::add);
            for (int i = 0; i < 400; i++) {
                Runnable r;
                while ((r = platform.poll()) != null) {
                    r.run();
                }
                engine[0].tick();
                if (!engine[0].busy() && platform.mainThread.isEmpty() && i > 5) {
                    break;
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        int[][] none = {null, null, null};
        java.util.function.Function<int[], int[][]> look = t -> new int[][]{t, null, null};
        java.util.function.BiFunction<int[], int[], int[][]> sel = (a, b) -> new int[][]{null, a, b};
        java.util.function.Supplier<String> at = () -> world.blockState(0, 70, 0);

        send.accept("help", none);
        send.accept("sphere gold_block 3", look.apply(new int[]{0, 70, 0}));
        if (!at.get().equals("minecraft:gold_block")) {
            send.accept("sphere gold_block 3", look.apply(new int[]{0, 70, 0}));
        }
        check("engine edit", at.get().equals("minecraft:gold_block"), msgs);
        send.accept("set diamond_block", sel.apply(new int[]{0, 70, 0}, new int[]{1, 70, 1}));
        send.accept("undo", none);
        check("engine undo", at.get().equals("minecraft:gold_block"), at.get());
        send.accept("checkpoint sphere", none);
        send.accept("set emerald_block", sel.apply(new int[]{0, 70, 0}, new int[]{0, 70, 0}));
        send.accept("goto 2", none);
        check("engine goto branch", at.get().equals("minecraft:diamond_block"), at.get());
        send.accept("goto start", none);
        check("engine goto start", at.get().equals("minecraft:air"), at.get());
        send.accept("goto sphere", none);
        check("engine goto checkpoint", at.get().equals("minecraft:gold_block"), at.get());
        send.accept("goto 2", none);
        send.accept("restore start", sel.apply(new int[]{0, 70, 0}, new int[]{0, 70, 0}));
        check("engine restore region", at.get().equals("minecraft:air") && world.blockState(1, 70, 1).equals("minecraft:diamond_block"), at.get());
        int quietFrom = msgs.size();
        send.accept("sphere coal_block 2 -q", look.apply(new int[]{300, 70, 300}));
        boolean chatty = msgs.subList(quietFrom, msgs.size()).stream().anyMatch(m -> Protocol.dataLine(m) == null);
        check("quiet edit places without chat", world.blockState(300, 70, 300).equals("minecraft:coal_block") && !chatty, msgs.subList(quietFrom, msgs.size()));
        quietFrom = msgs.size();
        send.accept("set bogus_block -q", sel.apply(new int[]{5, 71, 5}, new int[]{5, 71, 5}));
        check("quiet edit still reports errors", msgs.subList(quietFrom, msgs.size()).stream().anyMatch(m -> m.contains("Unknown block")), msgs.subList(quietFrom, msgs.size()));
        java.util.function.Supplier<String> lastSelection = () -> {
            for (int i = msgs.size() - 1; i >= 0; i--) {
                Protocol.Data d = Protocol.dataLine(msgs.get(i));
                if (d != null && d.kind().equals("selection")) {
                    return d.text();
                }
            }
            return null;
        };
        world.blocks.put(FlatWorld.key(500, 70, 500), "minecraft:gold_block");
        world.blocks.put(FlatWorld.key(501, 70, 500), "minecraft:iron_block");
        send.accept("move 3 east", sel.apply(new int[]{500, 70, 500}, new int[]{501, 70, 500}));
        check("move shifts the blocks", world.blockId(500, 70, 500).endsWith("air") && world.blockState(503, 70, 500).equals("minecraft:gold_block")
            && world.blockState(504, 70, 500).equals("minecraft:iron_block"), world.blockId(503, 70, 500));
        check("move takes the selection along", "503 70 500 504 70 500".equals(lastSelection.get()), lastSelection.get());
        send.accept("move 1 0 0", sel.apply(new int[]{503, 70, 500}, new int[]{504, 70, 500}));
        check("move by an overlapping offset", world.blockId(503, 70, 500).endsWith("air") && world.blockState(504, 70, 500).equals("minecraft:gold_block")
            && world.blockState(505, 70, 500).equals("minecraft:iron_block"), world.blockId(504, 70, 500) + " " + world.blockId(505, 70, 500));
        send.accept("stack 2 up", sel.apply(new int[]{504, 70, 500}, new int[]{505, 70, 500}));
        check("stack repeats the selection", world.blockState(504, 71, 500).equals("minecraft:gold_block") && world.blockState(505, 72, 500).equals("minecraft:iron_block")
            && world.blockId(504, 73, 500).endsWith("air"), world.blockId(504, 72, 500));
        send.accept("set stone", sel.apply(new int[]{520, 70, 520}, new int[]{524, 74, 524}));
        send.accept("count stone", sel.apply(new int[]{520, 70, 520}, new int[]{524, 74, 524}));
        check("count reports matching blocks", msgs.get(msgs.size() - 1).contains("125"), msgs.get(msgs.size() - 1));
        send.accept("hollow", sel.apply(new int[]{520, 70, 520}, new int[]{524, 74, 524}));
        check("hollow empties the inside", world.blockId(522, 72, 522).endsWith("air") && world.blockId(521, 71, 521).endsWith("air")
            && world.blockId(520, 72, 522).equals("minecraft:stone") && world.blockId(522, 74, 522).equals("minecraft:stone"), world.blockId(522, 72, 522));
        send.accept("distr", sel.apply(new int[]{520, 70, 520}, new int[]{524, 74, 524}));
        check("distr lists block counts", msgs.stream().anyMatch(m -> m.contains("98") && m.contains("stone")), msgs.subList(Math.max(0, msgs.size() - 4), msgs.size()));
        send.accept("select", look.apply(new int[]{520, 70, 520}));
        check("magic select picks the connected build", "520 70 520 524 74 524".equals(lastSelection.get()), lastSelection.get());
        send.accept("expand 2 up", sel.apply(new int[]{520, 70, 520}, new int[]{524, 74, 524}));
        check("expand grows the selection", "520 70 520 524 76 524".equals(lastSelection.get()), lastSelection.get());
        send.accept("contract 1 all", sel.apply(new int[]{520, 70, 520}, new int[]{524, 74, 524}));
        check("contract all shrinks every side", "521 71 521 523 73 523".equals(lastSelection.get()), lastSelection.get());
        send.accept("overlay snow_block", sel.apply(new int[]{540, 60, 540}, new int[]{541, 80, 541}));
        check("overlay covers the top", world.blockState(540, 65, 540).equals("minecraft:snow_block") && world.blockState(541, 65, 541).equals("minecraft:snow_block")
            && world.blockId(540, 66, 540).endsWith("air"), world.blockId(540, 65, 540));
        send.accept("naturalize", sel.apply(new int[]{550, 55, 550}, new int[]{550, 64, 550}));
        check("naturalize adds grass and dirt", world.blockId(550, 64, 550).equals("minecraft:grass_block") && world.blockId(550, 62, 550).equals("minecraft:dirt")
            && world.blockId(550, 58, 550).equals("minecraft:stone"), world.blockId(550, 64, 550));
        world.blocks.put(FlatWorld.key(600, 70, 600), "minecraft:gold_block");
        send.accept("cut", sel.apply(new int[]{600, 70, 600}, new int[]{600, 70, 600}));
        check("cut clears and fills the clipboard", world.blockId(600, 70, 600).endsWith("air") && msgs.stream().anyMatch(m -> m.contains("Cut")), msgs.get(msgs.size() - 1));
        send.accept("paste", look.apply(new int[]{602, 64, 600}));
        check("cut blocks paste back", world.blockState(602, 65, 600).equals("minecraft:gold_block"), world.blockId(602, 65, 600));
        for (int y = 65; y <= 75; y++) {
            world.blocks.put(FlatWorld.key(610, y, 610), "minecraft:stone");
        }
        send.accept("smooth 2", sel.apply(new int[]{605, 55, 605}, new int[]{615, 80, 615}));
        check("smooth flattens a spike", world.blockId(610, 75, 610).endsWith("air") && world.blockId(610, 64, 610).equals("minecraft:stone"), world.blockId(610, 70, 610));
        for (int x = 620; x <= 623; x++) {
            world.blocks.put(FlatWorld.key(x, 65, 620), "minecraft:water");
        }
        send.accept("drain 6", look.apply(new int[]{621, 64, 620}));
        check("drain removes connected water", world.blockId(620, 65, 620).endsWith("air") && world.blockId(623, 65, 620).endsWith("air"), world.blockId(620, 65, 620));
        send.accept("snow 3", look.apply(new int[]{630, 64, 630}));
        check("snow covers the ground", world.blockId(630, 65, 630).equals("minecraft:snow") && world.blockId(633, 65, 630).equals("minecraft:snow")
            && world.blockId(633, 65, 633).endsWith("air"), world.blockId(630, 65, 630));
        send.accept("thaw 3", look.apply(new int[]{630, 64, 630}));
        check("thaw removes the snow", world.blockId(630, 65, 630).endsWith("air"), world.blockId(630, 65, 630));
        world.blocks.put(FlatWorld.key(640, 64, 640), "minecraft:dirt");
        send.accept("green 2", look.apply(new int[]{640, 64, 640}));
        check("green turns dirt to grass", world.blockId(640, 64, 640).equals("minecraft:grass_block"), world.blockId(640, 64, 640));
        send.accept("text gold_block Hi", look.apply(new int[]{700, 64, 700}));
        check("text draws letters", world.blockState(704, 65, 700).equals("minecraft:gold_block") && world.blockState(704, 71, 700).equals("minecraft:gold_block")
            && world.blockId(703, 65, 700).endsWith("air"), world.blockId(704, 65, 700));
        send.accept("arch stone_bricks 10 5", look.apply(new int[]{720, 64, 720}));
        check("arch has a top and two feet", world.blockState(720, 69, 720).equals("minecraft:stone_bricks") && world.blockState(715, 65, 720).equals("minecraft:stone_bricks")
            && world.blockState(725, 65, 721).equals("minecraft:stone_bricks") && world.blockId(720, 65, 720).endsWith("air"), world.blockId(720, 69, 720));
        send.accept("replacenear 2 stone andesite", look.apply(new int[]{740, 64, 740}));
        check("replacenear swaps blocks in range", world.blockId(740, 64, 740).equals("minecraft:andesite") && world.blockId(742, 64, 740).equals("minecraft:andesite")
            && world.blockId(743, 64, 740).equals("minecraft:stone"), world.blockId(740, 64, 740));
        world.blocks.put(FlatWorld.key(760, 70, 760), "minecraft:emerald_block");
        send.accept("copy", sel.apply(new int[]{760, 70, 760}, new int[]{760, 70, 760}));
        send.accept("brush stamp -r", look.apply(new int[]{770, 64, 770}));
        check("stamp brush paints the clipboard", world.blockState(770, 65, 770).equals("minecraft:emerald_block"), world.blockId(770, 65, 770));
        send.accept("script maze", sel.apply(new int[]{20, 64, 20}, new int[]{34, 64, 34}));
        check("engine runs bundled script", world.blocks.containsValue("minecraft:oak_leaves[persistent=true]"), msgs.subList(Math.max(0, msgs.size() - 3), msgs.size()));
        for (String[] sc : new String[][]{{"house", "800", "minecraft:oak_door"}, {"lighthouse", "850", "minecraft:sea_lantern"}, {"well", "900", "minecraft:water"}}) {
            int spot = Integer.parseInt(sc[1]);
            int before = msgs.size();
            send.accept("script " + sc[0], look.apply(new int[]{spot, 64, spot}));
            boolean errors = msgs.subList(before, msgs.size()).stream().anyMatch(m -> m.startsWith("&c"));
            boolean placed = world.blocks.values().stream().anyMatch(b -> b.startsWith(sc[2]));
            check("example script " + sc[0] + " builds", !errors && placed, msgs.subList(before, msgs.size()));
        }
        msgs.clear();
        send.accept("set bogus_block", sel.apply(new int[]{5, 70, 5}, new int[]{5, 70, 5}));
        check("engine reports bad block", msgs.stream().anyMatch(m -> m.contains("Unknown block")), msgs);
        world.blocks.put(FlatWorld.key(40, 70, 40), "minecraft:dirt");
        world.blocks.put(FlatWorld.key(41, 70, 40), "minecraft:stone");
        send.accept("mask dirt", none);
        send.accept("set gold_block", sel.apply(new int[]{40, 70, 40}, new int[]{42, 70, 40}));
        check("mask only changes listed blocks", world.blockState(40, 70, 40).equals("minecraft:gold_block")
            && world.blockState(41, 70, 40).equals("minecraft:stone") && world.blockState(42, 70, 40).equals("minecraft:air"), world.blockState(41, 70, 40));
        send.accept("mask !stone", none);
        send.accept("set iron_block", sel.apply(new int[]{40, 70, 40}, new int[]{42, 70, 40}));
        check("inverted mask protects blocks", world.blockState(40, 70, 40).equals("minecraft:iron_block")
            && world.blockState(41, 70, 40).equals("minecraft:stone") && world.blockState(42, 70, 40).equals("minecraft:iron_block"), world.blockState(42, 70, 40));
        send.accept("mask off", none);
        send.accept("symmetry xz", look.apply(new int[]{50, 70, 50}));
        send.accept("set oak_stairs[facing=east]", sel.apply(new int[]{53, 70, 52}, new int[]{53, 70, 52}));
        check("symmetry x mirrors and flips facing", world.blockState(47, 70, 52).equals("minecraft:oak_stairs[facing=west]"), world.blockState(47, 70, 52));
        send.accept("set oak_stairs[facing=north]", sel.apply(new int[]{53, 71, 52}, new int[]{53, 71, 52}));
        check("symmetry z mirrors and flips facing", world.blockState(53, 71, 48).equals("minecraft:oak_stairs[facing=south]")
            && world.blockState(47, 71, 48).equals("minecraft:oak_stairs[facing=south]"), world.blockState(53, 71, 48));
        send.accept("undo", none);
        check("symmetry copies undo together", world.blockState(47, 71, 48).equals("minecraft:air") && world.blockState(53, 71, 52).equals("minecraft:air"), world.blockState(47, 71, 48));
        send.accept("symmetry off", none);
        java.util.function.Function<String, String> lastData = kind -> {
            for (int i = msgs.size() - 1; i >= 0; i--) {
                Protocol.Data d = Protocol.dataLine(msgs.get(i));
                if (d != null && d.kind().equals(kind)) {
                    return d.text();
                }
            }
            return null;
        };
        java.util.function.Supplier<Integer> historySize = () -> lastData.apply("history").split("\n").length;
        send.accept("sphere gold_block 1", look.apply(new int[]{60, 70, 60}));
        check("preview reports the pending edit", lastData.apply("pending") != null && lastData.apply("pending").startsWith("sphere"), lastData.apply("pending"));
        int histBefore = historySize.get();
        send.accept("nudge 5 0 0", none);
        check("nudge moves the pending edit", world.blockId(60, 70, 60).endsWith("air") && world.blockState(65, 70, 60).equals("minecraft:gold_block"), world.blockId(60, 70, 60));
        check("nudge replaces it in history instead of adding a branch", historySize.get() == histBefore, historySize.get() + " vs " + histBefore);
        send.accept("nudge up 2", none);
        check("nudge by direction", world.blockState(65, 72, 60).equals("minecraft:gold_block") && world.blockId(65, 70, 60).endsWith("air"), world.blockId(65, 70, 60));
        send.accept("cancel", none);
        check("cancel removes it from the world and history", world.blockId(65, 72, 60).endsWith("air") && historySize.get() == histBefore - 1
            && "".equals(lastData.apply("pending")), historySize.get());
        world.blocks.put(FlatWorld.key(80, 70, 80), "minecraft:gold_block");
        world.blocks.put(FlatWorld.key(81, 70, 80), "minecraft:iron_block");
        send.accept("copy", sel.apply(new int[]{80, 70, 80}, new int[]{81, 70, 80}));
        send.accept("paste", look.apply(new int[]{90, 69, 90}));
        check("paste lands east-west", world.blockState(89, 70, 90).equals("minecraft:gold_block") && world.blockState(90, 70, 90).equals("minecraft:iron_block"), world.blockState(89, 70, 90));
        send.accept("turn", none);
        check("turn re-pastes rotated", world.blockState(90, 70, 89).equals("minecraft:gold_block") && world.blockState(90, 70, 90).equals("minecraft:iron_block")
            && world.blockId(89, 70, 90).endsWith("air"), world.blockState(90, 70, 89) + " " + world.blockId(89, 70, 90));
        send.accept("sphere stone 0", look.apply(new int[]{95, 70, 95}));
        msgs.clear();
        send.accept("nudge 1 0 0", none);
        send.accept("undo", none);
        check("placing something else keeps the turned paste", world.blockState(90, 70, 89).equals("minecraft:gold_block"), world.blockState(90, 70, 89));
        int dragBefore = historySize.get();
        send.accept("sphere gold_block 0", look.apply(new int[]{100, 70, 100}));
        send.accept("sphere gold_block 0 stroke=42", look.apply(new int[]{102, 70, 100}));
        send.accept("sphere gold_block 0 stroke=42", look.apply(new int[]{104, 70, 100}));
        check("drag placements merge with the click", historySize.get() == dragBefore + 1 && world.blockState(104, 70, 100).equals("minecraft:gold_block")
            && "".equals(lastData.apply("pending")), historySize.get() + " vs " + dragBefore);
        send.accept("undo", none);
        check("one undo removes the whole drag", world.blockId(100, 70, 100).endsWith("air") && world.blockId(104, 70, 100).endsWith("air"), "");
        send.accept("preview off", none);
        send.accept("sphere stone 0", look.apply(new int[]{97, 70, 97}));
        msgs.clear();
        send.accept("nudge 1 0 0", none);
        check("preview off: nothing to nudge", msgs.stream().anyMatch(m -> m.contains("Nothing to change")), msgs);
        send.accept("preview on", none);
        msgs.clear();
        send.accept("sync", none);
        check("sync sends history, templates and pending data", lastData.apply("history") != null && lastData.apply("templates") != null
            && lastData.apply("pending") != null && msgs.stream().allMatch(m -> Protocol.dataLine(m) != null), msgs.size());
        check("direction follows where you look", java.util.Arrays.equals(dev.syrkbuilder.core.engine.Engine.direction("forward", 90, 2), new int[]{-2, 0, 0})
            && java.util.Arrays.equals(dev.syrkbuilder.core.engine.Engine.direction("right", 0, 1), new int[]{-1, 0, 0}), "");
        engine[0].shutdown();
        engine[0] = new dev.syrkbuilder.core.engine.Engine<>(platform, cfg, data, java.util.logging.Logger.getLogger("test"));
        send.accept("history", none);
        send.accept("goto 2", none);
        check("engine history survives restart", at.get().equals("minecraft:diamond_block"), at.get() + " " + msgs.subList(Math.max(0, msgs.size() - 4), msgs.size()));
        send.accept("undo", none);
        check("engine undo after restart", at.get().equals("minecraft:gold_block"), at.get());
        send.accept("goto sphere", none);
        check("engine checkpoint survives restart", at.get().equals("minecraft:gold_block"), at.get());
        int before = world.blocks.size();
        send.accept("brush sphere 1 glass stroke=77", look.apply(new int[]{40, 80, 40}));
        send.accept("brush sphere 1 glass stroke=77", look.apply(new int[]{44, 80, 40}));
        send.accept("brush sphere 1 glass stroke=77", look.apply(new int[]{48, 80, 40}));
        check("stroke placed three dabs", world.blockState(44, 80, 40).equals("minecraft:glass") && world.blockState(48, 80, 40).equals("minecraft:glass"), "");
        send.accept("undo", none);
        check("one undo removes the whole stroke", world.blockState(40, 80, 40).equals("minecraft:air") && world.blockState(48, 80, 40).equals("minecraft:air"),
            world.blockState(40, 80, 40) + " " + world.blockState(48, 80, 40));
        engine[0].shutdown();

        Path newest;
        try (var files = Files.walk(data.toPath().resolve("history"))) {
            newest = files.filter(f -> f.getFileName().toString().matches("\\d+\\.sbc"))
                .max(java.util.Comparator.comparingInt(f -> Integer.parseInt(f.getFileName().toString().replace(".sbc", ""))))
                .orElseThrow();
        }
        byte[] whole = Files.readAllBytes(newest);
        Files.write(newest, Arrays.copyOf(whole, whole.length / 2));
        engine[0] = new dev.syrkbuilder.core.engine.Engine<>(platform, cfg, data, java.util.logging.Logger.getLogger("test"));
        send.accept("goto 2", none);
        check("one damaged history file doesn't lose the rest", at.get().equals("minecraft:diamond_block"),
            at.get() + " " + msgs.subList(Math.max(0, msgs.size() - 3), msgs.size()));
        int gotoFrom = msgs.size();
        send.accept("goto sphere", none);
        check("checkpoints survive a damaged history file", at.get().equals("minecraft:gold_block")
            && msgs.subList(gotoFrom, msgs.size()).stream().noneMatch(m -> m.contains("No history entry")), at.get());
        engine[0].shutdown();

        MemoryPlatform lockedPlatform = new MemoryPlatform();
        lockedPlatform.denyScripts = "&cno scripts";
        dev.syrkbuilder.core.engine.Engine<FlatWorld, String> locked = new dev.syrkbuilder.core.engine.Engine<>(lockedPlatform, cfg,
            Files.createTempDirectory("sblocked").toFile(), java.util.logging.Logger.getLogger("test"));
        List<String> lockedMsgs = new ArrayList<>();
        locked.receive(player, world, Protocol.encode(new Request("script maze", null, new int[]{20, 64, 20}, new int[]{34, 64, 34}, null, 0, 0)), lockedMsgs::add);
        for (int i = 0; i < 2500 && lockedMsgs.isEmpty(); i++) {
            Runnable r;
            while ((r = lockedPlatform.poll()) != null) {
                r.run();
            }
            locked.tick();
            Thread.sleep(2);
        }
        check("platform can refuse scripts", lockedMsgs.contains("&cno scripts"), lockedMsgs);
        locked.shutdown();
    }

    private static void completion() {
        dev.syrkbuilder.core.command.Completer c = new dev.syrkbuilder.core.command.Completer(
            () -> List.of("minecraft:stone", "minecraft:stone_bricks", "minecraft:andesite", "minecraft:oak_planks", "minecraft:gold_block"),
            () -> List.of("arena1", "hut"), () -> List.of("tower", "maze"), () -> List.of("tree.obj", "mushroom.vox"));
        check("complete command", c.complete("sp", null).equals(List.of("sphere")), c.complete("sp", null));
        check("complete all commands", c.complete("", null).size() == dev.syrkbuilder.core.command.Completer.COMMANDS.size(), "");
        check("complete block", c.complete("sphere sto", null).equals(List.of("stone", "stone_bricks")), c.complete("sphere sto", null));
        check("complete block in mix", c.complete("set 70%stone,30%and", null).equals(List.of("70%stone,30%andesite")), c.complete("set 70%stone,30%and", null));
        check("complete weighted block", c.complete("set 70%go", null).equals(List.of("70%gold_block")), c.complete("set 70%go", null));
        check("complete number hints", c.complete("sphere stone ", null).contains("10"), c.complete("sphere stone ", null));
        check("complete flags", c.complete("sphere stone 5 -", null).equals(List.of("-h", "-a")), c.complete("sphere stone 5 -", null));
        check("replace second block", c.complete("replace stone oak", null).equals(List.of("oak_planks")), c.complete("replace stone oak", null));
        check("terrain types", c.complete("terrain mo", null).equals(List.of("mountain")), c.complete("terrain mo", null));
        check("terrain options", c.complete("terrain mountain ra", null).equals(List.of("radius=")), c.complete("terrain mountain ra", null));
        check("terrain style values", c.complete("terrain mesa style=de", null).equals(List.of("style=desert")), c.complete("terrain mesa style=de", null));
        check("template sub", c.complete("template p", null).equals(List.of("paste")), c.complete("template p", null));
        check("template names", c.complete("template paste a", null).equals(List.of("arena1")), c.complete("template paste a", null));
        check("template paste options", c.complete("template paste hut ro", null).equals(List.of("rotate=")), c.complete("template paste hut ro", null));
        check("template save offers no paste options", c.complete("template save hut ", null).isEmpty(), c.complete("template save hut ", null));
        check("paste rotate values", c.complete("paste rotate=", null).equals(List.of("rotate=90", "rotate=180", "rotate=270")), c.complete("paste rotate=", null));
        check("swap values", c.complete("paste swap=stone:and", null).equals(List.of("swap=stone:andesite")), c.complete("paste swap=stone:and", null));
        check("import files via client command", c.complete("tr", "import").equals(List.of("tree.obj")), c.complete("tr", "import"));
        check("import palette", c.complete("tree.obj palette=w", "import").equals(List.of("palette=wool", "palette=wood")), c.complete("tree.obj palette=w", "import"));
        check("script files", c.complete("m", "script").equals(List.of("maze")), c.complete("m", "script"));
        check("aliases", c.complete("t mesa style=me", null).equals(List.of("style=mesa")), c.complete("t mesa style=me", null));
        check("word start", dev.syrkbuilder.core.command.Completer.wordStart("sphere sto") == 7, "");
        check("unknown command no crash", c.complete("bogus x", null).isEmpty(), "");
        check("brush types", c.complete("sm", "brush").equals(List.of("smooth")), c.complete("sm", "brush"));
        check("brush bind types", c.complete("bind ra", "brush").equals(List.of("raise")), c.complete("bind ra", "brush"));
        check("terrain brush takes no blocks", c.complete("smooth 5 ", "brush").stream().noneMatch(x -> x.equals("stone")), c.complete("smooth 5 ", "brush"));
        check("block brush takes blocks", c.complete("paint 5 sto", "brush").equals(List.of("stone", "stone_bricks")), c.complete("paint 5 sto", "brush"));
        check("brush options", c.complete("scatter 5 poppy de", "brush").equals(List.of("density=", "depth=")), c.complete("scatter 5 poppy de", "brush"));
        check("mask inverted blocks", c.complete("mask !sto", null).equals(List.of("!stone", "!stone_bricks")), c.complete("mask !sto", null));
        check("mask off", c.complete("mask o", null).equals(List.of("off", "oak_planks")), c.complete("mask o", null));
        check("symmetry axes", c.complete("sym x", null).equals(List.of("x", "xz")), c.complete("sym x", null));
        check("gradient pattern blocks", c.complete("set gradx:stone,and", null).equals(List.of("gradx:stone,andesite")), c.complete("set gradx:stone,and", null));
        check("gradient pattern first block", c.complete("set grad:go", null).equals(List.of("grad:gold_block")), c.complete("set grad:go", null));
        check("brush from= blocks", c.complete("replace 4 sand from=go", "brush").equals(List.of("from=gold_block")), c.complete("replace 4 sand from=go", "brush"));
    }

    private static void warnings() {
        Commands cmds = new Commands(50_000_000);
        FlatWorld w = new FlatWorld(64);
        Result big = cmds.run(new Request("terrain mountain height=500 radius=40 erosion=0 seed=1", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), services());
        check("over-limit value is reported", big.warnings().stream().anyMatch(x -> x.contains("Height 500") && x.contains("320")), big.warnings());
        check("terrain hitting build limit is reported", big.warnings().stream().anyMatch(x -> x.contains("build limit")), big.warnings());
        Result ok = cmds.run(new Request("terrain mountain height=40 radius=40 seed=1", new int[]{0, 64, 0}, null, null, null, 0, 0), w, session(), services());
        check("normal terrain has no warnings", ok.warnings().isEmpty(), ok.warnings());
        Result tall = cmds.run(new Request("cyl stone 3 100", new int[]{0, 300, 0}, null, null, null, 0, 0), w, session(), services());
        check("shape above build limit is reported", tall.warnings().stream().anyMatch(x -> x.contains("cut off")), tall.warnings());
        Result sphere = cmds.run(new Request("sphere stone 5", new int[]{0, 100, 0}, null, null, null, 0, 0), w, session(), services());
        check("shape inside world has no warnings", sphere.warnings().isEmpty(), sphere.warnings());
        Result r = cmds.run(new Request("sphere stone 9999", new int[]{0, 100, 0}, null, null, null, 0, 0), w, session(), services());
        check("huge radius limited and reported", r.warnings().stream().anyMatch(x -> x.startsWith("Radius 9999")), r.warnings());
    }

    private static FlatWorld meadow() {
        FlatWorld w = new FlatWorld(64);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                w.blocks.put(FlatWorld.key(x, 64, z), "minecraft:grass_block[snowy=false]");
                w.blocks.put(FlatWorld.key(x, 63, z), "minecraft:dirt");
            }
        }
        return w;
    }

    private static int topAt(FlatWorld w, int x, int z) {
        for (int y = 120; y > 0; y--) {
            if (!w.blockId(x, y, z).endsWith("air")) {
                return y;
            }
        }
        return -1;
    }

    private static Result brush(FlatWorld w, String cmd, int[] at) {
        return new Commands(1_000_000).run(new Request(cmd, at, null, null, null, 0, 0), w, session(), services());
    }

    private static void brushes() {
        FlatWorld w = meadow();
        w.run(brush(w, "brush raise 5 strength=4", new int[]{0, 64, 0}).stream());
        check("raise lifts the middle", topAt(w, 0, 0) == 68, topAt(w, 0, 0));
        check("raise fades out at the edge", topAt(w, 6, 0) == 64 && topAt(w, 4, 0) > 64 && topAt(w, 4, 0) < 68, topAt(w, 4, 0));
        check("raise keeps grass on top and dirt below", w.blockState(0, 68, 0).startsWith("minecraft:grass_block") && w.blockState(0, 66, 0).equals("minecraft:dirt"),
            w.blockState(0, 68, 0) + " / " + w.blockState(0, 66, 0));
        w.run(brush(w, "brush smooth 6 strength=1", new int[]{0, 64, 0}).stream());
        check("smooth lowers the bump", topAt(w, 0, 0) < 68, topAt(w, 0, 0));
        w.run(brush(w, "brush flatten 8 strength=1", new int[]{0, 64, 0}).stream());
        check("flatten pulls back to the aim height", topAt(w, 0, 0) == 64 && topAt(w, 2, 1) == 64, topAt(w, 0, 0));
        w.run(brush(w, "brush lower 4 strength=3", new int[]{0, 64, 0}).stream());
        check("lower digs down", topAt(w, 0, 0) == 61 && w.blockState(0, 61, 0).startsWith("minecraft:grass_block"), topAt(w, 0, 0) + " " + w.blockState(0, 61, 0));

        FlatWorld m = meadow();
        m.run(brush(m, "brush overlay 3 snow_block", new int[]{0, 64, 0}).stream());
        check("overlay adds a layer on top", m.blockState(0, 65, 0).equals("minecraft:snow_block") && m.blockState(3, 65, 0).equals("minecraft:snow_block")
            && m.blockId(5, 65, 0).endsWith("air"), m.blockState(0, 65, 0));
        m.run(brush(m, "brush overlay 3 gravel -r depth=2", new int[]{0, 65, 0}).stream());
        check("overlay -r replaces the top", m.blockState(0, 65, 0).equals("minecraft:gravel") && m.blockState(0, 64, 0).equals("minecraft:gravel") && m.blockState(0, 63, 0).equals("minecraft:dirt"), m.blockState(0, 64, 0));
        FlatWorld f = meadow();
        f.run(brush(f, "brush scatter 4 poppy density=1", new int[]{0, 64, 0}).stream());
        check("scatter plants on the ground", f.blockState(1, 65, 1).equals("minecraft:poppy") && f.blockId(1, 66, 1).endsWith("air"), f.blockState(1, 65, 1));
        FlatWorld p = meadow();
        int painted = p.run(brush(p, "brush paint 3 moss_block", new int[]{0, 64, 0}).stream());
        check("paint only touches the exposed surface", painted > 0 && p.blockState(0, 64, 0).equals("minecraft:moss_block") && p.blockState(0, 62, 0).equals("minecraft:stone"), painted);
        FlatWorld e = meadow();
        e.run(brush(e, "brush erase 3", new int[]{0, 64, 0}).stream());
        check("erase removes a ball", e.blockId(0, 64, 0).endsWith("air") && e.blockId(0, 61, 0).endsWith("air") && e.blockId(0, 60, 0).equals("minecraft:stone"), "");
        FlatWorld l = meadow();
        l.run(brush(l, "brush erase 4", new int[]{0, 64, 0}).stream());
        l.run(brush(l, "brush fill 4 water", new int[]{0, 63, 0}).stream());
        check("fill fills the hole below the aim", l.blockState(0, 62, 0).equals("minecraft:water") && l.blockId(0, 64, 0).endsWith("air"), l.blockState(0, 62, 0));
        FlatWorld r = meadow();
        r.run(brush(r, "brush replace 4 sand from=grass_block,dirt", new int[]{0, 64, 0}).stream());
        check("replace uses from=", r.blockState(0, 64, 0).equals("minecraft:sand") && r.blockState(0, 63, 0).equals("minecraft:sand")
            && r.blockState(0, 62, 0).equals("minecraft:stone"), r.blockState(0, 62, 0));
        Result list = brush(r, "brush list", null);
        check("brush list", list.kind() == Result.Kind.MESSAGE && list.lines().size() == dev.syrkbuilder.core.brush.BrushType.values().length + 1, list.lines().size());
        Result needsBlocks = brush(r, "brush sphere 3", new int[]{0, 64, 0});
        check("block brushes need blocks", needsBlocks.kind() == Result.Kind.ERROR, needsBlocks);

        FlatWorld pan = new FlatWorld(0);
        pan.run(brush(pan, "brush sphere 4 stone ry=1", new int[]{0, 100, 0}).stream());
        check("ry= flattens a sphere brush", pan.blockId(4, 100, 0).equals("minecraft:stone") && pan.blockId(0, 101, 0).equals("minecraft:stone")
            && pan.blockId(0, 102, 0).endsWith("air"), pan.blockId(0, 102, 0));
        FlatWorld wide = meadow();
        wide.run(brush(wide, "brush erase 3 rx=6", new int[]{0, 64, 0}).stream());
        check("rx= stretches a brush along x only", wide.blockId(6, 64, 0).endsWith("air") && !wide.blockId(0, 64, 5).endsWith("air"), wide.blockId(0, 64, 5));
        FlatWorld ridge = meadow();
        ridge.run(brush(ridge, "brush raise 3 rz=8 strength=4", new int[]{0, 64, 0}).stream());
        check("terrain brushes stretch into a ridge", topAt(ridge, 0, 6) > 64 && topAt(ridge, 6, 0) == 64, topAt(ridge, 0, 6) + " / " + topAt(ridge, 6, 0));
        FlatWorld slab = new FlatWorld(0);
        slab.run(brush(slab, "brush blob 3 stone ry=1 strength=0", new int[]{0, 100, 0}).stream());
        check("voxel brushes stretch too", slab.blockId(3, 100, 0).equals("minecraft:stone") && slab.blockId(0, 101, 0).equals("minecraft:stone")
            && slab.blockId(0, 102, 0).endsWith("air"), slab.blockId(0, 102, 0));

        FlatWorld b = meadow();
        int blob = b.run(brush(b, "brush blob 4 stone", new int[]{0, 80, 0}).stream());
        check("blob makes a lumpy ball", blob > 100 && blob < 900 && b.blockState(0, 80, 0).equals("minecraft:stone"), blob);
        FlatWorld cv = meadow();
        cv.run(brush(cv, "brush carve 3", new int[]{0, 62, 0}).stream());
        check("carve hollows out", cv.blockId(0, 62, 0).endsWith("air") && cv.blockId(0, 50, 0).equals("minecraft:stone"), cv.blockId(0, 62, 0));
        FlatWorld sc = meadow();
        for (int y = 65; y <= 67; y++) {
            sc.blocks.put(FlatWorld.key(0, y, 0), "minecraft:stone");
        }
        sc.blocks.put(FlatWorld.key(2, 64, 2), "minecraft:air");
        sc.run(brush(sc, "brush sculpt 3", new int[]{0, 65, 0}).stream());
        check("sculpt shaves off a spike", sc.blockId(0, 66, 0).endsWith("air") && sc.blockId(0, 67, 0).endsWith("air"), sc.blockId(0, 66, 0));
        check("sculpt fills a dent with the surface block", sc.blockState(2, 64, 2).startsWith("minecraft:grass_block"), sc.blockState(2, 64, 2));
        check("sculpt leaves flat ground alone", sc.blockState(1, 64, -2).startsWith("minecraft:grass_block") && sc.blockId(1, 65, -2).endsWith("air"), "");
        FlatWorld in = meadow();
        in.run(brush(in, "brush inflate 2", new int[]{0, 64, 0}).stream());
        check("inflate grows a layer of the touching block", in.blockState(0, 65, 0).startsWith("minecraft:grass_block") && in.blockId(0, 66, 0).endsWith("air"), in.blockState(0, 65, 0));
        FlatWorld infl = meadow();
        infl.run(brush(infl, "brush inflate 2 moss_block", new int[]{0, 64, 0}).stream());
        check("inflate with blocks", infl.blockState(0, 65, 0).equals("minecraft:moss_block"), infl.blockState(0, 65, 0));
        FlatWorld de = meadow();
        de.blocks.put(FlatWorld.key(0, 65, 0), "minecraft:poppy");
        de.run(brush(de, "brush deflate 2", new int[]{0, 64, 0}).stream());
        check("deflate shaves a layer and its plants", de.blockId(0, 64, 0).endsWith("air") && de.blockId(0, 65, 0).endsWith("air")
            && de.blockId(0, 63, 0).equals("minecraft:dirt"), de.blockId(0, 65, 0));
        FlatWorld ro = meadow();
        int rough = ro.run(brush(ro, "brush roughen 5 strength=1", new int[]{0, 64, 0}).stream());
        check("roughen bumps and pits the surface", rough > 5 && ro.blockId(0, 55, 0).equals("minecraft:stone"), rough);
        FlatWorld dc = meadow();
        dc.run(brush(dc, "brush decay 3 density=1", new int[]{0, 64, 0}).stream());
        check("decay crumbles exposed blocks", dc.blockId(0, 64, 0).endsWith("air") && dc.blockId(0, 63, 0).equals("minecraft:dirt"), dc.blockId(0, 64, 0));
        FlatWorld sp = meadow();
        int splat = sp.run(brush(sp, "brush splatter 4 moss_block density=1", new int[]{0, 64, 0}).stream());
        check("splatter paints surface patches only", splat > 0 && sp.blockState(0, 62, 0).equals("minecraft:stone") && sp.blockId(0, 65, 0).endsWith("air"), splat);
        FlatWorld sk = meadow();
        sk.run(brush(sk, "brush spikes 4 stone density=1 strength=6", new int[]{0, 64, 0}).stream());
        check("spikes rise from the ground", topAt(sk, 0, 0) >= 67 && sk.blockState(0, 65, 0).equals("minecraft:stone"), topAt(sk, 0, 0));
        FlatWorld cr = meadow();
        cr.run(brush(cr, "brush crater 8 strength=4", new int[]{0, 64, 0}).stream());
        check("crater digs a bowl with a rim", topAt(cr, 0, 0) == 60 && topAt(cr, 7, 0) >= 65, topAt(cr, 0, 0) + " / " + topAt(cr, 7, 0));
        FlatWorld te = meadow();
        te.run(brush(te, "brush raise 6 strength=6", new int[]{0, 64, 0}).stream());
        te.run(brush(te, "brush terrace 6 strength=2", new int[]{0, 64, 0}).stream());
        boolean stepped = true;
        for (int x = 0; x <= 4; x++) {
            stepped &= (topAt(te, x, 0) - 64) % 2 == 0;
        }
        check("terrace cuts steps", stepped, topAt(te, 0, 0) + "," + topAt(te, 1, 0) + "," + topAt(te, 2, 0) + "," + topAt(te, 3, 0));
        Result optional = brush(te, "brush sculpt 3 strength=1", new int[]{0, 64, 0});
        check("sculpt needs no blocks", optional.kind() == Result.Kind.EDIT, optional);
    }

    private static void schematics() throws Exception {
        dev.syrkbuilder.core.grid.BlockGrid g = new dev.syrkbuilder.core.grid.BlockGrid(7, 5, 6);
        String[] kinds = new String[20];
        for (int i = 0; i < kinds.length; i++) {
            kinds[i] = "minecraft:block_" + i;
        }
        kinds[3] = "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]";
        kinds[4] = "minecraft:air";
        for (int y = 0; y < 5; y++) {
            for (int z = 0; z < 6; z++) {
                for (int x = 0; x < 7; x++) {
                    g.set(x, y, z, (x + y + z) % 11 == 0 ? null : kinds[(x * 7 + y * 3 + z * 5) % kinds.length]);
                }
            }
        }
        dev.syrkbuilder.core.grid.BlockGrid s = dev.syrkbuilder.core.grid.Schematics.read(dev.syrkbuilder.core.grid.Schematics.writeSponge(g, 4440));
        dev.syrkbuilder.core.grid.BlockGrid l = dev.syrkbuilder.core.grid.Schematics.read(dev.syrkbuilder.core.grid.Schematics.writeLitematic(g, "test", 4440));
        boolean sameS = s.sizeX() == 7 && s.sizeY() == 5 && s.sizeZ() == 6;
        boolean sameL = l.sizeX() == 7 && l.sizeY() == 5 && l.sizeZ() == 6;
        for (int y = 0; y < 5; y++) {
            for (int z = 0; z < 6; z++) {
                for (int x = 0; x < 7; x++) {
                    String want = g.get(x, y, z) == null ? "minecraft:air" : g.get(x, y, z);
                    sameS &= want.equals(s.get(x, y, z));
                    sameL &= want.equals(l.get(x, y, z));
                }
            }
        }
        check(".schem round trip (varints, states, air)", sameS, s.get(3, 0, 0));
        check(".litematic round trip (20-entry palette packs across longs)", sameL, l.get(3, 0, 0));

        java.util.Map<String, Object> palette = new java.util.LinkedHashMap<>();
        palette.put("minecraft:stone", 0);
        palette.put("minecraft:structure_void", 1);
        palette.put("minecraft:dirt", 200);
        java.util.Map<String, Object> blocks = new java.util.LinkedHashMap<>();
        blocks.put("Palette", palette);
        blocks.put("Data", new byte[]{0, 1, (byte) 0xC8, 0x01, 0});
        java.util.Map<String, Object> inner = new java.util.LinkedHashMap<>();
        inner.put("Version", 3);
        inner.put("Width", (short) 2);
        inner.put("Height", (short) 1);
        inner.put("Length", (short) 2);
        inner.put("Blocks", blocks);
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("Schematic", inner);
        dev.syrkbuilder.core.grid.BlockGrid v3 = dev.syrkbuilder.core.grid.Schematics.read(dev.syrkbuilder.core.nbt.Nbt.write("", root));
        check("sponge v3 + structure void", "minecraft:stone".equals(v3.get(0, 0, 0)) && v3.get(1, 0, 0) == null && "minecraft:dirt".equals(v3.get(0, 0, 1)), v3.get(0, 0, 1));

        java.util.Map<String, Object> region = new java.util.LinkedHashMap<>();
        region.put("Position", xyzTag(5, 0, 5));
        region.put("Size", xyzTag(-2, 1, -1));
        dev.syrkbuilder.core.nbt.Nbt.ListTag pal = dev.syrkbuilder.core.nbt.Nbt.ListTag.of(dev.syrkbuilder.core.nbt.Nbt.COMPOUND);
        java.util.Map<String, Object> air = new java.util.LinkedHashMap<>();
        air.put("Name", "minecraft:air");
        java.util.Map<String, Object> log = new java.util.LinkedHashMap<>();
        log.put("Name", "minecraft:oak_log");
        java.util.Map<String, Object> props = new java.util.LinkedHashMap<>();
        props.put("axis", "x");
        log.put("Properties", props);
        pal.items().add(air);
        pal.items().add(log);
        region.put("BlockStatePalette", pal);
        region.put("BlockStates", new long[]{0b0100L});
        java.util.Map<String, Object> regions = new java.util.LinkedHashMap<>();
        regions.put("r", region);
        java.util.Map<String, Object> lroot = new java.util.LinkedHashMap<>();
        lroot.put("Version", 6);
        lroot.put("Regions", regions);
        dev.syrkbuilder.core.grid.BlockGrid neg = dev.syrkbuilder.core.grid.Schematics.read(dev.syrkbuilder.core.nbt.Nbt.write("", lroot));
        check("litematic negative size + properties", neg.sizeX() == 2 && "minecraft:oak_log[axis=x]".equals(neg.get(1, 0, 0)) && "minecraft:air".equals(neg.get(0, 0, 0)), neg.get(1, 0, 0));

        boolean legacy = false;
        java.util.Map<String, Object> old = new java.util.LinkedHashMap<>();
        old.put("Width", (short) 1);
        old.put("Blocks", new byte[1]);
        old.put("Materials", "Alpha");
        try {
            dev.syrkbuilder.core.grid.Schematics.read(dev.syrkbuilder.core.nbt.Nbt.write("Schematic", old));
        } catch (java.io.IOException e) {
            legacy = e.getMessage().contains("MCEdit");
        }
        check("old MCEdit schematic explains itself", legacy, "");

        java.io.File dir = Files.createTempDirectory("sbtpl").toFile();
        java.io.File we = Files.createTempDirectory("sbwe").toFile();
        Files.write(new java.io.File(we, "My House.schem").toPath(), dev.syrkbuilder.core.grid.Schematics.writeSponge(g, 4440));
        dev.syrkbuilder.core.engine.FileTemplateStore store = new dev.syrkbuilder.core.engine.FileTemplateStore(dir);
        store.addFolder(we);
        store.export("tower", g, dev.syrkbuilder.core.grid.Schematics.Format.LITEMATIC);
        check("template list spans folders and formats", store.list().equals(List.of("my_house", "tower")), store.list());
        check("load .schem from another folder", store.load("my_house") != null && store.load("my_house").sizeX() == 7, "");
        check("load exported .litematic", store.load("tower") != null && store.load("tower").sizeZ() == 6, "");
        check("delete never touches other folders", !store.delete("my_house") && new java.io.File(we, "My House.schem").isFile(), "");
    }

    private static java.util.Map<String, Object> xyzTag(int x, int y, int z) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        return m;
    }

    private static void treesAndPaths() {
        FlatWorld w = meadow();
        int placed = w.run(brush(w, "tree oak seed=7", new int[]{0, 64, 0}).stream());
        check("oak tree: trunk on the ground", w.blockState(0, 65, 0).equals("minecraft:oak_log[axis=y]") && w.blockState(0, 64, 0).startsWith("minecraft:grass_block"), w.blockState(0, 65, 0));
        check("oak tree: persistent leaves", w.blocks.containsValue("minecraft:oak_leaves[persistent=true]") && placed > 30, placed);
        boolean all = true;
        for (dev.syrkbuilder.core.tree.Trees.Type t : dev.syrkbuilder.core.tree.Trees.Type.values()) {
            FlatWorld tw = meadow();
            Result r = brush(tw, "tree " + t.id + " seed=3", new int[]{0, 64, 0});
            int n = r.kind() == Result.Kind.EDIT ? tw.run(r.stream()) : 0;
            FlatWorld tw2 = meadow();
            int n2 = tw2.run(brush(tw2, "tree " + t.id + " seed=3", new int[]{0, 64, 0}).stream());
            if (n < 10 || n != n2) {
                all = false;
                System.out.println("  tree " + t.id + ": " + n + " / " + n2);
            }
        }
        check("every tree type grows, same seed = same tree", all, "");
        FlatWorld f = meadow();
        f.run(brush(f, "brush trees 10 type=birch density=1", new int[]{0, 64, 0}).stream());
        List<int[]> trunks = new ArrayList<>();
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                if (f.blockState(x, 65, z).startsWith("minecraft:birch_log")) {
                    trunks.add(new int[]{x, z});
                }
            }
        }
        boolean spaced = trunks.size() >= 3;
        for (int[] a : trunks) {
            for (int[] b : trunks) {
                if (a != b && (a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) < 16) {
                    spaced = false;
                }
            }
        }
        check("trees brush plants spaced trees", spaced, trunks.size());

        Session ps = session();
        Commands c = new Commands(1_000_000);
        java.util.function.BiFunction<FlatWorld, String, Result> run = (world, cmd) -> c.run(new Request(cmd, null, null, null, null, 0, 0), world, ps, services());
        java.util.function.BiConsumer<String, int[]> at = (cmd, pos) -> c.run(new Request(cmd, pos, null, null, null, 0, 0), meadow(), ps, services());
        at.accept("path add", new int[]{0, 64, 0});
        FlatWorld one = meadow();
        check("path needs two points", run.apply(one, "path road").kind() == Result.Kind.ERROR, "");
        at.accept("path add", new int[]{10, 64, 0});
        at.accept("path add", new int[]{20, 64, 6});
        FlatWorld road = meadow();
        road.blocks.put(FlatWorld.key(5, 65, 0), "minecraft:stone");
        road.run(run.apply(road, "path road 3").stream());
        check("road: surface + cut", road.blockState(5, 64, 0).equals("minecraft:dirt_path") && road.blockId(5, 65, 0).endsWith("air")
            && road.blockState(5, 64, 1).equals("minecraft:dirt_path"), road.blockState(5, 64, 0));
        FlatWorld wall = meadow();
        wall.run(run.apply(wall, "path wall 1 height=3").stream());
        check("wall stands on the curve", wall.blockState(5, 67, 0).equals("minecraft:stone_bricks") && wall.blockId(5, 68, 0).endsWith("air"), wall.blockState(5, 67, 0));
        FlatWorld river = meadow();
        river.run(run.apply(river, "path river 7").stream());
        check("river: water, deeper in the middle, sand bed", river.blockState(5, 64, 0).equals("minecraft:water")
            && river.blockState(5, 62, 0).equals("minecraft:water") && river.blocks.containsValue("minecraft:sand"), river.blockState(5, 62, 0));
        FlatWorld tunnel = meadow();
        tunnel.run(run.apply(tunnel, "path tunnel 5").stream());
        check("tunnel carves through", tunnel.blockId(5, 65, 0).endsWith("air") && tunnel.blockId(5, 67, 0).endsWith("air"), "");
        at.accept("path clear", new int[]{0, 0, 0});
        at.accept("path add", new int[]{0, 72, 0});
        at.accept("path add", new int[]{16, 72, 0});
        FlatWorld bridge = meadow();
        bridge.run(run.apply(bridge, "path bridge 5").stream());
        check("bridge: deck, rails, pillars to the ground", bridge.blockState(8, 72, 0).equals("minecraft:spruce_planks")
            && bridge.blocks.containsValue("minecraft:spruce_fence") && bridge.blocks.containsValue("minecraft:stone_bricks"), bridge.blockState(8, 72, 0));
        check("path points kept in the session", ps.path().size() == 2, ps.path().size());
    }

    private static void helixConnected() {
        FlatWorld w = new FlatWorld(0);
        int n = w.run(brush(w, "helix stone 6 20 turns=3", new int[]{0, 100, 0}).stream());
        java.util.Set<Long> cells = new java.util.HashSet<>();
        for (java.util.Map.Entry<Long, String> e : w.blocks.entrySet()) {
            if (e.getValue().equals("minecraft:stone")) {
                cells.add(e.getKey());
            }
        }
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        Long start = cells.iterator().next();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            long k = queue.poll();
            int x = dev.syrkbuilder.core.history.ChangeSet.unpackX(k);
            int y = dev.syrkbuilder.core.history.ChangeSet.unpackY(k);
            int z = dev.syrkbuilder.core.history.ChangeSet.unpackZ(k);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long q = dev.syrkbuilder.core.history.ChangeSet.pack(x + dx, y + dy, z + dz);
                        if (cells.contains(q) && seen.add(q)) {
                            queue.add(q);
                        }
                    }
                }
            }
        }
        check("helix is one continuous spiral (no gaps)", n > 100 && seen.size() == cells.size(), seen.size() + "/" + cells.size());
    }

    private static void fills() {
        FlatWorld w = meadow();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = 62; y <= 64; y++) {
                    w.blocks.put(FlatWorld.key(x, y, z), "minecraft:air");
                }
            }
        }
        Result hole = brush(w, "fill water 10", new int[]{2, 61, 2});
        int n = w.run(hole.stream());
        check("hole fill fills the pit and stops at ground level", n == 75 && w.blockState(0, 64, 4).equals("minecraft:water")
            && w.blockId(2, 65, 2).endsWith("air"), n);
        FlatWorld open = meadow();
        Result leak = brush(open, "fill stone 4", new int[]{0, 64, 0});
        check("open fill stays in the radius and warns", leak.warnings().stream().anyMatch(x -> x.contains("radius")) && open.run(leak.stream()) > 0, leak.warnings());
        FlatWorld c = meadow();
        int recolored = c.run(brush(c, "fill moss_block 3 mode=connected", new int[]{0, 64, 0}).stream());
        check("connected fill recolours matching blocks only", recolored > 20 && c.blockState(0, 64, 0).equals("minecraft:moss_block")
            && c.blockState(0, 63, 0).equals("minecraft:dirt"), recolored);
        check("nearest colours", dev.syrkbuilder.core.model.BlockPalette.named("concrete").nearest(0xE06100, 3).get(0).equals("minecraft:orange_concrete"), "");
    }

    private static void gradients() {
        Commands cmds = new Commands(1_000_000);
        FlatWorld w = new FlatWorld(0);
        Result r = cmds.run(new Request("cyl grad:white_wool,gray_wool,black_wool 4 11", new int[]{0, 99, 0}, null, null, null, 0, 0), w, session(), services());
        w.run(r.stream());
        java.util.function.IntFunction<Map<String, Integer>> layer = y -> {
            Map<String, Integer> m = new HashMap<>();
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    String b = w.blocks.get(FlatWorld.key(x, y, z));
                    if (b != null) {
                        m.merge(b, 1, Integer::sum);
                    }
                }
            }
            return m;
        };
        check("gradient bottom is first block", layer.apply(100).keySet().equals(Set.of("minecraft:white_wool")), layer.apply(100));
        check("gradient top is last block", layer.apply(110).keySet().equals(Set.of("minecraft:black_wool")), layer.apply(110));
        Map<String, Integer> mid = layer.apply(102);
        check("gradient blends between steps", mid.size() == 2 && mid.containsKey("minecraft:white_wool") && mid.containsKey("minecraft:gray_wool"), mid);
        FlatWorld s2 = new FlatWorld(0);
        s2.run(cmds.run(new Request("sphere gradr:gold_block,stone 6", new int[]{0, 100, 0}, null, null, null, 0, 0), s2, session(), services()).stream());
        check("radial gradient: centre first, edge last", "minecraft:gold_block".equals(s2.blocks.get(FlatWorld.key(0, 100, 0)))
            && "minecraft:stone".equals(s2.blocks.get(FlatWorld.key(6, 100, 0))), "");
        FlatWorld s3 = new FlatWorld(0);
        s3.run(cmds.run(new Request("set gradx:red_wool,blue_wool", null, new int[]{0, 50, 0}, new int[]{10, 50, 0}, null, 0, 0), s3, session(), services()).stream());
        check("sideways gradient over a selection", "minecraft:red_wool".equals(s3.blocks.get(FlatWorld.key(0, 50, 0)))
            && "minecraft:blue_wool".equals(s3.blocks.get(FlatWorld.key(10, 50, 0))), "");
        Result g = cmds.run(new Request("gradient white_concrete black_concrete 5", null, null, null, null, 0, 0), w, session(), services());
        check("colour gradient finds in-between blocks", g.kind() == Result.Kind.MESSAGE && g.lines().get(1).startsWith("&7Use it as blocks anywhere: &fgrad:white_concrete,")
            && g.lines().get(1).endsWith(",black_concrete") && g.lines().get(1).split(",").length >= 3, g.lines());
        Result hex = cmds.run(new Request("gradient #ff0000 #0000ff 4 palette=wool", null, null, null, null, 0, 0), w, session(), services());
        check("hex colour gradient", hex.kind() == Result.Kind.MESSAGE && hex.lines().get(1).contains("red_wool"), hex.lines());
        Result bad = cmds.run(new Request("gradient mystery_block stone", null, null, null, null, 0, 0), w, session(), services());
        check("unknown colour is an error", bad.kind() == Result.Kind.ERROR, bad);

        FlatWorld down = new FlatWorld(0);
        down.run(cmds.run(new Request("set grad(down):white_wool,black_wool", null, new int[]{0, 50, 0}, new int[]{0, 60, 0}, null, 0, 0), down, session(), services()).stream());
        check("grad(down) starts at the top", "minecraft:white_wool".equals(down.blocks.get(FlatWorld.key(0, 60, 0)))
            && "minecraft:black_wool".equals(down.blocks.get(FlatWorld.key(0, 50, 0))), "");
        FlatWorld west = new FlatWorld(0);
        west.run(cmds.run(new Request("set grad(west):red_wool,blue_wool", null, new int[]{0, 50, 0}, new int[]{10, 50, 0}, null, 0, 0), west, session(), services()).stream());
        check("grad(west) runs from east to west", "minecraft:red_wool".equals(west.blocks.get(FlatWorld.key(10, 50, 0)))
            && "minecraft:blue_wool".equals(west.blocks.get(FlatWorld.key(0, 50, 0))), "");
        FlatWorld diag = new FlatWorld(0);
        diag.run(cmds.run(new Request("set grad(1/0/1):red_wool,blue_wool", null, new int[]{0, 50, 0}, new int[]{10, 50, 10}, null, 0, 0), diag, session(), services()).stream());
        check("diagonal gradient corner to corner", "minecraft:red_wool".equals(diag.blocks.get(FlatWorld.key(0, 50, 0)))
            && "minecraft:blue_wool".equals(diag.blocks.get(FlatWorld.key(10, 50, 10))), "");
        FlatWorld look = new FlatWorld(0);
        look.run(cmds.run(new Request("set grad(look):red_wool,blue_wool", null, new int[]{0, 50, 0}, new int[]{10, 50, 0}, null, -90f, 0), look, session(), services()).stream());
        check("grad(look) follows the way you face", "minecraft:red_wool".equals(look.blocks.get(FlatWorld.key(0, 50, 0)))
            && "minecraft:blue_wool".equals(look.blocks.get(FlatWorld.key(10, 50, 0))), "");
        FlatWorld in = new FlatWorld(0);
        in.run(cmds.run(new Request("sphere grad(in):gold_block,stone 6", new int[]{0, 100, 0}, null, null, null, 0, 0), in, session(), services()).stream());
        check("grad(in): edge first, centre last", "minecraft:stone".equals(in.blocks.get(FlatWorld.key(0, 100, 0)))
            && "minecraft:gold_block".equals(in.blocks.get(FlatWorld.key(6, 100, 0))), "");

        FlatWorld dabs = new FlatWorld(0);
        for (int y : new int[]{100, 110}) {
            dabs.run(cmds.run(new Request("brush sphere 2 grad(up,100..110):white_wool,black_wool", new int[]{0, y, 0}, null, null, null, 0, 0), dabs, session(), services()).stream());
        }
        check("a gradient range spans separate brush dabs", "minecraft:white_wool".equals(dabs.blocks.get(FlatWorld.key(0, 100, 0)))
            && "minecraft:black_wool".equals(dabs.blocks.get(FlatWorld.key(0, 110, 0))), "");
        FlatWorld flipped = new FlatWorld(0);
        flipped.run(cmds.run(new Request("set grad(y,60..50):white_wool,black_wool", null, new int[]{0, 50, 0}, new int[]{0, 60, 0}, null, 0, 0), flipped, session(), services()).stream());
        check("the range's order sets the direction", "minecraft:white_wool".equals(flipped.blocks.get(FlatWorld.key(0, 60, 0)))
            && "minecraft:black_wool".equals(flipped.blocks.get(FlatWorld.key(0, 50, 0))), "");
        Result nonsense = cmds.run(new Request("set grad(sideways):stone,dirt", null, new int[]{0, 50, 0}, new int[]{1, 50, 0}, null, 0, 0), w, session(), services());
        check("unknown gradient direction is an error", nonsense.kind() == Result.Kind.ERROR && nonsense.lines().get(0).contains("up, down"), nonsense);
        Result noColon = cmds.run(new Request("set grad(down)stone,dirt", null, new int[]{0, 50, 0}, new int[]{1, 50, 0}, null, 0, 0), w, session(), services());
        check("gradient options need a colon", noColon.kind() == Result.Kind.ERROR, noColon);
    }
}
