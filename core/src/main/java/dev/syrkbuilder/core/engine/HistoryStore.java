package dev.syrkbuilder.core.engine;

import dev.syrkbuilder.core.history.ChangeSet;
import dev.syrkbuilder.core.history.HistoryCodec;
import dev.syrkbuilder.core.history.HistoryTree;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class HistoryStore {
    private static final HistoryCodec.StateCodec<String> STRINGS = new HistoryCodec.StateCodec<>() {
        @Override
        public String encode(String state) {
            return state;
        }

        @Override
        public String decode(String raw) {
            return raw;
        }
    };

    private final File root;
    private final Logger logger;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SyrkBuilder-history");
        t.setDaemon(true);
        return t;
    });

    HistoryStore(File root, Logger logger) {
        this.root = root;
        this.logger = logger;
    }

    private File dir(String key) {
        return new File(root, key);
    }

    record Loaded(HistoryCodec.Index index, Map<Integer, ChangeSet<String>> changes) {
    }

    void load(String key, java.util.function.Consumer<Loaded> done) {
        io.execute(() -> {
            Loaded result = null;
            File dir = dir(key);
            File index = new File(dir, "index.sbi");
            if (index.isFile()) {
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(index)))) {
                    HistoryCodec.Index idx = HistoryCodec.readIndex(in);
                    Map<Integer, ChangeSet<String>> changes = new HashMap<>();
                    for (HistoryTree.NodeInfo n : idx.nodes()) {
                        File f = new File(dir, n.id() + ".sbc");
                        if (n.parent() == -1 || !f.isFile()) {
                            continue;
                        }
                        try (DataInputStream cin = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f))))) {
                            changes.put(n.id(), HistoryCodec.readChange(cin, STRINGS));
                        }
                    }
                    result = new Loaded(idx, changes);
                } catch (IOException e) {
                    logger.warning("Couldn't load history " + key + ": " + e.getMessage());
                }
            }
            done.accept(result);
        });
    }

    void saveChange(String key, int id, ChangeSet<String> change) {
        io.execute(() -> {
            File dir = dir(key);
            dir.mkdirs();
            File f = new File(dir, id + ".sbc");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(f))))) {
                HistoryCodec.writeChange(out, change, STRINGS);
            } catch (IOException e) {
                logger.warning("Couldn't save history " + key + "/" + id + ": " + e.getMessage());
            }
        });
    }

    void saveIndex(String key, List<HistoryTree.NodeInfo> nodes, int currentId, List<Integer> removed) {
        io.execute(() -> {
            File dir = dir(key);
            dir.mkdirs();
            for (int id : removed) {
                new File(dir, id + ".sbc").delete();
            }
            File tmp = new File(dir, "index.sbi.tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
                HistoryCodec.writeIndex(out, nodes, currentId);
            } catch (IOException e) {
                logger.warning("Couldn't save history index " + key + ": " + e.getMessage());
                return;
            }
            File index = new File(dir, "index.sbi");
            index.delete();
            if (!tmp.renameTo(index)) {
                logger.warning("Couldn't replace history index " + key);
            }
        });
    }

    void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
