package dev.syrkbuilder.core.history;

import dev.syrkbuilder.core.edit.Box;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HistoryTree<B> {
    public static final class Node<B> {
        final int id;
        Node<B> parent;
        final List<Node<B>> children = new ArrayList<>();
        final ChangeSet<B> change;
        String checkpoint;
        Node<B> preferred;

        Node(int id, Node<B> parent, ChangeSet<B> change) {
            this.id = id;
            this.parent = parent;
            this.change = change;
        }

        public int id() {
            return id;
        }

        public Node<B> parent() {
            return parent;
        }

        public List<Node<B>> children() {
            return children;
        }

        public ChangeSet<B> change() {
            return change;
        }

        public String checkpoint() {
            return checkpoint;
        }
    }

    public record Step<B>(ChangeSet<B> change, boolean undo) {
    }

    private final long maxBlocks;
    private final Map<Integer, Node<B>> byId = new HashMap<>();
    private Node<B> root;
    private Node<B> current;
    private int nextId = 1;
    private long totalBlocks;

    public HistoryTree(long maxBlocks) {
        this.maxBlocks = maxBlocks;
        this.root = new Node<>(0, null, null);
        this.root.checkpoint = "start";
        this.current = root;
        byId.put(0, root);
    }

    public Node<B> root() {
        return root;
    }

    public Node<B> current() {
        return current;
    }

    public Node<B> node(int id) {
        return byId.get(id);
    }

    public int size() {
        return byId.size() - 1;
    }

    public long totalBlocks() {
        return totalBlocks;
    }

    public List<Integer> push(ChangeSet<B> change) {
        if (change.size() == 0) {
            return List.of();
        }
        Node<B> node = new Node<>(nextId++, current, change);
        current.children.add(node);
        current.preferred = node;
        current = node;
        byId.put(node.id, node);
        totalBlocks += change.size();
        return prune();
    }

    public boolean drop(Node<B> node) {
        if (node == null || node == root || node == current || !node.children.isEmpty() || node.parent == null) {
            return false;
        }
        node.parent.children.remove(node);
        if (node.parent.preferred == node) {
            node.parent.preferred = null;
        }
        byId.remove(node.id);
        totalBlocks -= node.change.size();
        return true;
    }

    public void grew(long blocks) {
        totalBlocks += blocks;
    }

    public ChangeSet<B> undo() {
        if (current == root) {
            return null;
        }
        Node<B> from = current;
        current = current.parent;
        current.preferred = from;
        return from.change;
    }

    public ChangeSet<B> redo() {
        Node<B> next = current.preferred;
        if (next == null && !current.children.isEmpty()) {
            next = current.children.get(current.children.size() - 1);
        }
        if (next == null) {
            return null;
        }
        current = next;
        return next.change;
    }

    public Node<B> find(String key) {
        String k = key.startsWith("#") ? key.substring(1) : key;
        try {
            Node<B> n = byId.get(Integer.parseInt(k));
            if (n != null) {
                return n;
            }
        } catch (NumberFormatException ignored) {
        }
        Node<B> best = null;
        for (Node<B> n : byId.values()) {
            if (key.equalsIgnoreCase(n.checkpoint) && (best == null || n.id > best.id)) {
                best = n;
            }
        }
        return best;
    }

    public void checkpoint(String name) {
        current.checkpoint = name;
    }

    public List<Step<B>> pathTo(Node<B> target) {
        List<Node<B>> up = new ArrayList<>();
        List<Node<B>> down = new ArrayList<>();
        Map<Node<B>, Integer> depthOfTarget = new HashMap<>();
        int depth = 0;
        for (Node<B> n = target; n != null; n = n.parent) {
            depthOfTarget.put(n, depth++);
        }
        Node<B> lca = current;
        while (!depthOfTarget.containsKey(lca)) {
            up.add(lca);
            lca = lca.parent;
        }
        for (Node<B> n = target; n != lca; n = n.parent) {
            down.add(0, n);
        }
        List<Step<B>> steps = new ArrayList<>();
        for (Node<B> n : up) {
            steps.add(new Step<>(n.change, true));
            n.parent.preferred = n;
        }
        for (Node<B> n : down) {
            steps.add(new Step<>(n.change, false));
            n.parent.preferred = n;
        }
        current = target;
        return steps;
    }

    public Map<Long, B> regionAt(Node<B> target, Box box) {
        Node<B> saved = current;
        List<Step<B>> steps = pathTo(target);
        current = saved;
        restorePreferences(saved);
        Map<Long, B> result = new LinkedHashMap<>();
        for (Step<B> step : steps) {
            ChangeSet<B> c = step.change();
            if (step.undo()) {
                for (int i = c.size() - 1; i >= 0; i--) {
                    put(result, box, c.packedPosition(i), c.palette().get(c.beforeIndex(i)));
                }
            } else {
                for (int i = 0; i < c.size(); i++) {
                    put(result, box, c.packedPosition(i), c.palette().get(c.afterIndex(i)));
                }
            }
        }
        return result;
    }

    private void restorePreferences(Node<B> node) {
        for (Node<B> n = node; n.parent != null; n = n.parent) {
            n.parent.preferred = n;
        }
    }

    private static <B> void put(Map<Long, B> map, Box box, long packed, B state) {
        if (box.contains(ChangeSet.unpackX(packed), ChangeSet.unpackY(packed), ChangeSet.unpackZ(packed))) {
            map.put(packed, state);
        }
    }

    public List<Node<B>> nodes() {
        List<Node<B>> list = new ArrayList<>(byId.values());
        list.sort((a, b) -> Integer.compare(a.id, b.id));
        return list;
    }

    public boolean onCurrentPath(Node<B> node) {
        for (Node<B> n = current; n != null; n = n.parent) {
            if (n == node) {
                return true;
            }
        }
        return false;
    }

    private List<Integer> prune() {
        List<Integer> removed = new ArrayList<>();
        while (totalBlocks > maxBlocks && byId.size() > 2) {
            Node<B> victim = null;
            for (Node<B> n : byId.values()) {
                if (n != root && n.children.isEmpty() && !onCurrentPath(n) && (victim == null || n.id < victim.id)) {
                    victim = n;
                }
            }
            if (victim != null) {
                victim.parent.children.remove(victim);
                if (victim.parent.preferred == victim) {
                    victim.parent.preferred = null;
                }
                forget(victim, removed);
                continue;
            }
            Node<B> next = null;
            for (Node<B> n = current; n != root; n = n.parent) {
                next = n;
            }
            if (next == null) {
                break;
            }
            for (Node<B> sibling : new ArrayList<>(root.children)) {
                if (sibling != next) {
                    forgetSubtree(sibling, removed);
                }
            }
            forget(root, removed);
            Node<B> newRoot = new Node<>(next.id, null, null);
            newRoot.checkpoint = next.checkpoint == null ? "start" : next.checkpoint;
            newRoot.children.addAll(next.children);
            newRoot.preferred = next.preferred;
            for (Node<B> child : newRoot.children) {
                child.parent = newRoot;
            }
            totalBlocks -= next.change.size();
            byId.put(newRoot.id, newRoot);
            removed.add(next.id);
            if (current == next) {
                current = newRoot;
            }
            root = newRoot;
        }
        return removed;
    }

    private void forget(Node<B> n, List<Integer> removed) {
        byId.remove(n.id);
        if (n.change != null) {
            totalBlocks -= n.change.size();
            removed.add(n.id);
        }
    }

    private void forgetSubtree(Node<B> n, List<Integer> removed) {
        for (Node<B> c : n.children) {
            forgetSubtree(c, removed);
        }
        forget(n, removed);
    }

    public record NodeInfo(int id, int parent, String checkpoint, int preferred) {
    }

    public List<NodeInfo> structure() {
        List<NodeInfo> list = new ArrayList<>();
        for (Node<B> n : nodes()) {
            list.add(new NodeInfo(n.id, n.parent == null ? -1 : n.parent.id, n.checkpoint, n.preferred == null ? -1 : n.preferred.id));
        }
        return list;
    }

    public static <B> HistoryTree<B> rebuild(long maxBlocks, List<NodeInfo> infos, Map<Integer, ChangeSet<B>> changes, int currentId) {
        HistoryTree<B> tree = new HistoryTree<>(maxBlocks);
        tree.byId.clear();
        Map<Integer, NodeInfo> infoById = new HashMap<>();
        for (NodeInfo info : infos) {
            infoById.put(info.id(), info);
        }
        for (NodeInfo info : infos) {
            if (info.parent() == -1) {
                Node<B> r = new Node<>(info.id(), null, null);
                r.checkpoint = info.checkpoint();
                tree.root = r;
                tree.byId.put(r.id, r);
            }
        }
        List<NodeInfo> ordered = new ArrayList<>(infos);
        ordered.sort((a, b) -> Integer.compare(a.id(), b.id()));
        for (NodeInfo info : ordered) {
            if (info.parent() == -1) {
                continue;
            }
            Node<B> parent = tree.byId.get(info.parent());
            ChangeSet<B> change = changes.get(info.id());
            if (parent == null || change == null) {
                continue;
            }
            Node<B> n = new Node<>(info.id(), parent, change);
            n.checkpoint = info.checkpoint();
            parent.children.add(n);
            tree.byId.put(n.id, n);
            tree.totalBlocks += change.size();
            tree.nextId = Math.max(tree.nextId, n.id + 1);
        }
        for (NodeInfo info : infos) {
            Node<B> n = tree.byId.get(info.id());
            if (n != null && info.preferred() != -1) {
                n.preferred = tree.byId.get(info.preferred());
            }
        }
        tree.nextId = Math.max(tree.nextId, tree.root.id + 1);
        Node<B> cur = tree.byId.get(currentId);
        tree.current = cur == null ? tree.root : cur;
        return tree;
    }
}
