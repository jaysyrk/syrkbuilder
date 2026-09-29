package dev.syrkbuilder.core.grid;

import dev.syrkbuilder.core.nbt.Nbt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Schematics {
    public static final int DEFAULT_DATA_VERSION = 4440;

    public enum Format {
        SPONGE("schem"), LITEMATIC("litematic");

        public final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public static Format byName(String name) {
            return switch (name.toLowerCase(java.util.Locale.ROOT)) {
                case "schem", "sponge", "worldedit", "we", "fawe", "axiom" -> SPONGE;
                case "litematic", "litematica", "lite" -> LITEMATIC;
                default -> null;
            };
        }
    }

    private Schematics() {
    }

    public static BlockGrid read(byte[] data) throws IOException {
        Nbt.Root root = Nbt.read(data);
        Map<String, Object> tag = root.tag();
        if (tag.containsKey("Regions")) {
            return readLitematic(tag);
        }
        Map<String, Object> inner = Nbt.compound(tag, "Schematic");
        if (inner != null) {
            tag = inner;
        }
        if (tag.containsKey("Width") && tag.containsKey("Length")) {
            return readSponge(tag);
        }
        if (tag.containsKey("Blocks") && tag.containsKey("Materials")) {
            throw new IOException("This is an old MCEdit .schematic (pre-1.13 numeric ids). Open it in WorldEdit and save it as .schem first.");
        }
        throw new IOException("Not a .schem or .litematic file");
    }

    private static BlockGrid readSponge(Map<String, Object> tag) throws IOException {
        int version = Nbt.intValue(tag, "Version", 2);
        int w = Nbt.intValue(tag, "Width", 0);
        int h = Nbt.intValue(tag, "Height", 0);
        int l = Nbt.intValue(tag, "Length", 0);
        if (w <= 0 || h <= 0 || l <= 0) {
            throw new IOException("Schematic has no size");
        }
        Map<String, Object> palette;
        byte[] data;
        if (version >= 3) {
            Map<String, Object> blocks = Nbt.compound(tag, "Blocks");
            if (blocks == null) {
                throw new IOException("Schematic has no Blocks");
            }
            palette = Nbt.compound(blocks, "Palette");
            data = blocks.get("Data") instanceof byte[] b ? b : null;
        } else {
            palette = Nbt.compound(tag, "Palette");
            data = tag.get("BlockData") instanceof byte[] b ? b : null;
        }
        if (palette == null || data == null) {
            throw new IOException("Schematic has no block palette or data");
        }
        String[] byIndex = new String[palette.size()];
        for (Map.Entry<String, Object> e : palette.entrySet()) {
            int idx = ((Number) e.getValue()).intValue();
            if (idx >= byIndex.length) {
                byIndex = java.util.Arrays.copyOf(byIndex, idx + 1);
            }
            byIndex[idx] = e.getKey();
        }
        BlockGrid grid = new BlockGrid(w, h, l);
        int[] local = new int[byIndex.length];
        for (int i = 0; i < byIndex.length; i++) {
            String b = byIndex[i];
            local[i] = b == null || b.equals("minecraft:structure_void") ? BlockGrid.EMPTY : grid.addPalette(b);
        }
        int pos = 0;
        long total = (long) w * h * l;
        for (int i = 0; i < total; i++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (pos >= data.length) {
                    throw new IOException("Schematic block data is truncated");
                }
                int b = data[pos++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 28) {
                    throw new IOException("Bad varint in schematic");
                }
            }
            grid.setRawCell(i, value >= 0 && value < local.length ? local[value] : BlockGrid.EMPTY);
        }
        return grid;
    }

    public static byte[] writeSponge(BlockGrid grid, int dataVersion) throws IOException {
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int n = grid.sizeX() * grid.sizeY() * grid.sizeZ();
        for (int i = 0; i < n; i++) {
            int raw = grid.rawCell(i);
            String block = raw == BlockGrid.EMPTY ? "minecraft:air" : grid.palette().get(raw);
            Integer idx = palette.get(block);
            if (idx == null) {
                idx = palette.size();
                palette.put(block, idx);
            }
            int v = idx;
            while ((v & ~0x7F) != 0) {
                data.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            data.write(v);
        }
        Map<String, Object> paletteTag = new LinkedHashMap<>();
        palette.forEach((k, v) -> paletteTag.put(k, v));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("WEOffsetX", -(grid.sizeX() / 2));
        meta.put("WEOffsetY", 0);
        meta.put("WEOffsetZ", -(grid.sizeZ() / 2));
        Map<String, Object> tag = new LinkedHashMap<>();
        tag.put("Version", 2);
        tag.put("DataVersion", dataVersion);
        tag.put("Metadata", meta);
        tag.put("Width", (short) grid.sizeX());
        tag.put("Height", (short) grid.sizeY());
        tag.put("Length", (short) grid.sizeZ());
        tag.put("Offset", new int[]{0, 0, 0});
        tag.put("PaletteMax", palette.size());
        tag.put("Palette", paletteTag);
        tag.put("BlockData", data.toByteArray());
        tag.put("BlockEntities", Nbt.ListTag.of(Nbt.COMPOUND));
        return Nbt.write("Schematic", tag);
    }

    private static BlockGrid readLitematic(Map<String, Object> tag) throws IOException {
        Map<String, Object> regions = Nbt.compound(tag, "Regions");
        if (regions == null || regions.isEmpty()) {
            throw new IOException("Litematic has no regions");
        }
        record Region(int minX, int minY, int minZ, int sx, int sy, int sz, List<String> palette, long[] states) {
        }
        List<Region> list = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Object value : regions.values()) {
            if (!(value instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) value;
            Map<String, Object> pos = Nbt.compound(r, "Position");
            Map<String, Object> size = Nbt.compound(r, "Size");
            Nbt.ListTag paletteTag = Nbt.list(r, "BlockStatePalette");
            if (pos == null || size == null || paletteTag == null || !(r.get("BlockStates") instanceof long[] states)) {
                continue;
            }
            int[] p = {Nbt.intValue(pos, "x", 0), Nbt.intValue(pos, "y", 0), Nbt.intValue(pos, "z", 0)};
            int[] s = {Nbt.intValue(size, "x", 0), Nbt.intValue(size, "y", 0), Nbt.intValue(size, "z", 0)};
            if (s[0] == 0 || s[1] == 0 || s[2] == 0) {
                continue;
            }
            int[] min = new int[3];
            for (int i = 0; i < 3; i++) {
                min[i] = s[i] < 0 ? p[i] + s[i] + 1 : p[i];
                s[i] = Math.abs(s[i]);
            }
            List<String> palette = new ArrayList<>();
            for (Object entry : paletteTag.items()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> e = (Map<String, Object>) entry;
                palette.add(stateString(e));
            }
            list.add(new Region(min[0], min[1], min[2], s[0], s[1], s[2], palette, states));
            minX = Math.min(minX, min[0]);
            minY = Math.min(minY, min[1]);
            minZ = Math.min(minZ, min[2]);
            maxX = Math.max(maxX, min[0] + s[0] - 1);
            maxY = Math.max(maxY, min[1] + s[1] - 1);
            maxZ = Math.max(maxZ, min[2] + s[2] - 1);
        }
        if (list.isEmpty()) {
            throw new IOException("Litematic has no readable regions");
        }
        BlockGrid grid = new BlockGrid(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        for (Region r : list) {
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, r.palette().size() - 1)));
            int[] local = new int[r.palette().size()];
            for (int i = 0; i < local.length; i++) {
                String b = r.palette().get(i);
                local[i] = b.equals("minecraft:structure_void") ? BlockGrid.EMPTY : grid.addPalette(b);
            }
            long volume = (long) r.sx() * r.sy() * r.sz();
            if ((volume * bits + 63) / 64 > r.states().length) {
                throw new IOException("Litematic block data is truncated");
            }
            for (int y = 0; y < r.sy(); y++) {
                for (int z = 0; z < r.sz(); z++) {
                    for (int x = 0; x < r.sx(); x++) {
                        long i = ((long) y * r.sz() + z) * r.sx() + x;
                        int idx = (int) unpack(r.states(), i, bits);
                        if (idx < local.length && local[idx] != BlockGrid.EMPTY) {
                            int gx = r.minX() - minX + x;
                            int gy = r.minY() - minY + y;
                            int gz = r.minZ() - minZ + z;
                            grid.setRawCell((gy * grid.sizeZ() + gz) * grid.sizeX() + gx, local[idx]);
                        }
                    }
                }
            }
        }
        return grid;
    }

    private static long unpack(long[] data, long index, int bits) {
        long mask = (1L << bits) - 1;
        long start = index * bits;
        int word = (int) (start >> 6);
        int offset = (int) (start & 63);
        int endWord = (int) ((start + bits - 1) >> 6);
        if (word == endWord) {
            return (data[word] >>> offset) & mask;
        }
        return ((data[word] >>> offset) | (data[endWord] << (64 - offset))) & mask;
    }

    private static void pack(long[] data, long index, int bits, long value) {
        long start = index * bits;
        int word = (int) (start >> 6);
        int offset = (int) (start & 63);
        int endWord = (int) ((start + bits - 1) >> 6);
        data[word] |= value << offset;
        if (endWord != word) {
            data[endWord] |= value >>> (64 - offset);
        }
    }

    public static byte[] writeLitematic(BlockGrid grid, String name, int dataVersion) throws IOException {
        List<String> palette = new ArrayList<>();
        Map<String, Integer> index = new LinkedHashMap<>();
        palette.add("minecraft:air");
        index.put("minecraft:air", 0);
        int n = grid.sizeX() * grid.sizeY() * grid.sizeZ();
        int[] cells = new int[n];
        long blocks = 0;
        for (int i = 0; i < n; i++) {
            int raw = grid.rawCell(i);
            String block = raw == BlockGrid.EMPTY ? "minecraft:air" : grid.palette().get(raw);
            Integer idx = index.get(block);
            if (idx == null) {
                idx = palette.size();
                palette.add(block);
                index.put(block, idx);
            }
            cells[i] = idx;
            if (idx != 0) {
                blocks++;
            }
        }
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
        long[] states = new long[(int) (((long) n * bits + 63) / 64)];
        for (int i = 0; i < n; i++) {
            pack(states, i, bits, cells[i]);
        }
        Nbt.ListTag paletteTag = Nbt.ListTag.of(Nbt.COMPOUND);
        for (String b : palette) {
            paletteTag.items().add(stateTag(b));
        }
        Map<String, Object> size = xyz(grid.sizeX(), grid.sizeY(), grid.sizeZ());
        Map<String, Object> region = new LinkedHashMap<>();
        region.put("Position", xyz(0, 0, 0));
        region.put("Size", size);
        region.put("BlockStatePalette", paletteTag);
        region.put("BlockStates", states);
        region.put("TileEntities", Nbt.ListTag.of(Nbt.COMPOUND));
        region.put("Entities", Nbt.ListTag.of(Nbt.COMPOUND));
        region.put("PendingBlockTicks", Nbt.ListTag.of(Nbt.COMPOUND));
        region.put("PendingFluidTicks", Nbt.ListTag.of(Nbt.COMPOUND));
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put(name, region);
        long now = System.currentTimeMillis();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("Name", name);
        meta.put("Author", "SyrkBuilder");
        meta.put("Description", "");
        meta.put("RegionCount", 1);
        meta.put("TotalVolume", n);
        meta.put("TotalBlocks", (int) blocks);
        meta.put("TimeCreated", now);
        meta.put("TimeModified", now);
        meta.put("EnclosingSize", xyz(grid.sizeX(), grid.sizeY(), grid.sizeZ()));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 6);
        root.put("MinecraftDataVersion", dataVersion);
        root.put("Metadata", meta);
        root.put("Regions", regions);
        return Nbt.write("", root);
    }

    private static Map<String, Object> xyz(int x, int y, int z) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        return m;
    }

    private static String stateString(Map<String, Object> e) {
        String name = Nbt.string(e, "Name");
        if (name == null) {
            return "minecraft:air";
        }
        Map<String, Object> props = Nbt.compound(e, "Properties");
        if (props == null || props.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, Object> p : props.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(p.getKey()).append('=').append(p.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }

    private static Map<String, Object> stateTag(String block) {
        Map<String, Object> tag = new LinkedHashMap<>();
        int open = block.indexOf('[');
        tag.put("Name", open < 0 ? block : block.substring(0, open));
        if (open >= 0 && block.endsWith("]")) {
            Map<String, Object> props = new LinkedHashMap<>();
            for (String kv : block.substring(open + 1, block.length() - 1).split(",")) {
                int eq = kv.indexOf('=');
                if (eq > 0) {
                    props.put(kv.substring(0, eq), kv.substring(eq + 1));
                }
            }
            if (!props.isEmpty()) {
                tag.put("Properties", props);
            }
        }
        return tag;
    }
}
