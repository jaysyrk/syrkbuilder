package dev.syrkbuilder.core.script;

import dev.syrkbuilder.core.edit.EditBuffer;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.noise.PerlinNoise;
import dev.syrkbuilder.core.shape.ShapeStream;
import dev.syrkbuilder.core.shape.Shapes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

public final class ScriptRunner {
    private static final int MAX_STACK_DEPTH = 2000;

    public static final class ScriptFailure extends Exception {
        public ScriptFailure(String message) {
            super(message);
        }
    }

    public record Output(EditBuffer edits, List<String> messages) {
    }

    private static final class Timeout extends Error {
        Timeout() {
            super(null, null, false, false);
        }
    }

    private static final class SandboxFactory extends ContextFactory {
        private final long deadline;

        SandboxFactory(long deadline) {
            this.deadline = deadline;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            if (System.nanoTime() > deadline) {
                throw new Timeout();
            }
        }
    }

    private interface Body {
        Object call(Object[] args);
    }

    private static final class Fn extends BaseFunction {
        private final Body body;

        Fn(Body body) {
            this.body = body;
        }

        @Override
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            Object r = body.call(args);
            return r == null ? Undefined.instance : r;
        }
    }

    private ScriptRunner() {
    }

    public static Output run(String source, String name, WorldView world, int[] origin, int[] pos1, int[] pos2,
                             String[] args, long maxBlocks, long timeoutMillis) throws ScriptFailure {
        EditBuffer edits = new EditBuffer(maxBlocks);
        List<String> messages = new ArrayList<>();
        Map<String, Pattern> patterns = new HashMap<>();
        PerlinNoise noise = new PerlinNoise(1);
        Random random = new Random(1);

        SandboxFactory factory = new SandboxFactory(System.nanoTime() + timeoutMillis * 1_000_000L);
        Context cx = factory.enterContext();
        try {
            cx.setOptimizationLevel(-1);
            cx.setMaximumInterpreterStackDepth(MAX_STACK_DEPTH);
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setInstructionObserverThreshold(5000);
            cx.setClassShutter(className -> false);
            ScriptableObject scope = cx.initSafeStandardObjects();

            ScriptableObject.putProperty(scope, "origin", point(cx, scope, origin));
            ScriptableObject.putProperty(scope, "pos1", pos1 == null ? null : point(cx, scope, pos1));
            ScriptableObject.putProperty(scope, "pos2", pos2 == null ? null : point(cx, scope, pos2));
            Object[] jsArgs = new Object[args.length];
            System.arraycopy(args, 0, jsArgs, 0, args.length);
            ScriptableObject.putProperty(scope, "args", cx.newArray(scope, jsArgs));

            put(scope, "set", a -> {
                int x = i(a, 0);
                int y = i(a, 1);
                int z = i(a, 2);
                edits.set(x, y, z, pattern(patterns, s(a, 3)).pick(x, y, z));
                return null;
            });
            put(scope, "get", a -> {
                int x = i(a, 0);
                int y = i(a, 1);
                int z = i(a, 2);
                String mine = edits.get(x, y, z);
                return mine != null ? Pattern.baseId(mine) : world.blockId(x, y, z);
            });
            put(scope, "ground", a -> world.groundY(i(a, 0), i(a, 1)));
            put(scope, "fill", a -> {
                Pattern p = pattern(patterns, s(a, 6));
                int x1 = Math.min(i(a, 0), i(a, 3));
                int x2 = Math.max(i(a, 0), i(a, 3));
                int y1 = Math.min(i(a, 1), i(a, 4));
                int y2 = Math.max(i(a, 1), i(a, 4));
                int z1 = Math.min(i(a, 2), i(a, 5));
                int z2 = Math.max(i(a, 2), i(a, 5));
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        for (int x = x1; x <= x2; x++) {
                            edits.set(x, y, z, p.pick(x, y, z));
                        }
                    }
                }
                return null;
            });
            put(scope, "sphere", a -> {
                shape(edits, Shapes.sphere(i(a, 0), i(a, 1), i(a, 2), d(a, 3)), pattern(patterns, s(a, 4)), a.length > 5 && Context.toBoolean(a[5]));
                return null;
            });
            put(scope, "cylinder", a -> {
                shape(edits, Shapes.cylinder(i(a, 0), i(a, 1), i(a, 2), d(a, 3), Math.max(1, i(a, 4))), pattern(patterns, s(a, 5)), a.length > 6 && Context.toBoolean(a[6]));
                return null;
            });
            put(scope, "line", a -> {
                double radius = a.length > 7 ? d(a, 7) : 0;
                shape(edits, Shapes.line(i(a, 0), i(a, 1), i(a, 2), i(a, 3), i(a, 4), i(a, 5), radius), pattern(patterns, s(a, 6)), false);
                return null;
            });
            put(scope, "noise", a -> {
                double scale = a.length > 2 ? Math.max(0.0001, d(a, 2)) : 16;
                return noise.fbm(d(a, 0) / scale, d(a, 1) / scale, 4, 2.0, 0.5);
            });
            put(scope, "rand", a -> random.nextDouble());
            put(scope, "print", a -> {
                StringBuilder sb = new StringBuilder();
                for (Object o : a) {
                    sb.append(sb.length() > 0 ? " " : "").append(Context.toString(o));
                }
                if (messages.size() < 50) {
                    messages.add(sb.toString());
                }
                return null;
            });

            cx.evaluateString(scope, source, name, 1, null);
        } catch (Timeout t) {
            throw new ScriptFailure("Script took longer than " + timeoutMillis + " ms and was stopped.");
        } catch (OutOfMemoryError e) {
            throw new ScriptFailure("Script used too much memory and was stopped.");
        } catch (RhinoException e) {
            throw new ScriptFailure(e.details() + " (" + name + " line " + e.lineNumber() + ")");
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ScriptFailure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            Context.exit();
        }
        return new Output(edits, messages);
    }

    private static Scriptable point(Context cx, Scriptable scope, int[] p) {
        Scriptable obj = cx.newObject(scope);
        ScriptableObject.putProperty(obj, "x", p[0]);
        ScriptableObject.putProperty(obj, "y", p[1]);
        ScriptableObject.putProperty(obj, "z", p[2]);
        return obj;
    }

    private static void put(ScriptableObject scope, String name, Body body) {
        ScriptableObject.putProperty(scope, name, new Fn(body));
    }

    private static void shape(EditBuffer edits, Shapes.Placed placed, Pattern pattern, boolean hollow) {
        if (placed.box().volume() > 50_000_000L) {
            throw new IllegalArgumentException("Shape too large");
        }
        ShapeStream stream = new ShapeStream(placed.box(), placed.shape(), pattern, hollow, null);
        while (stream.drain(edits::set, 1 << 20)) {
        }
    }

    private static Pattern pattern(Map<String, Pattern> cache, String raw) {
        return cache.computeIfAbsent(raw, Pattern::parse);
    }

    private static Object arg(Object[] a, int i) {
        if (i >= a.length || a[i] == Undefined.instance) {
            throw new IllegalArgumentException("Missing argument " + (i + 1));
        }
        return a[i];
    }

    private static int i(Object[] a, int i) {
        return (int) Math.floor(Context.toNumber(arg(a, i)));
    }

    private static double d(Object[] a, int i) {
        return Context.toNumber(arg(a, i));
    }

    private static String s(Object[] a, int i) {
        return Context.toString(arg(a, i));
    }
}
