package dev.syrkbuilder.core.command;

import dev.syrkbuilder.core.edit.Box;
import dev.syrkbuilder.core.edit.EditStream;
import java.util.List;

public record Result(Kind kind, String label, EditStream stream, int count, List<String> lines, Box box, List<String> warnings, Box select) {
    public enum Kind { EDIT, UNDO, REDO, HISTORY, GOTO, CHECKPOINT, RESTORE, MESSAGE, ERROR }

    public static Result edit(String label, EditStream stream) {
        return new Result(Kind.EDIT, label, stream, 0, List.of(), null, List.of(), null);
    }

    public static Result edit(String label, EditStream stream, List<String> doneLines) {
        return new Result(Kind.EDIT, label, stream, 0, doneLines, null, List.of(), null);
    }

    public static Result undo(int count) {
        return new Result(Kind.UNDO, null, null, count, List.of(), null, List.of(), null);
    }

    public static Result redo(int count) {
        return new Result(Kind.REDO, null, null, count, List.of(), null, List.of(), null);
    }

    public static Result history(int count) {
        return new Result(Kind.HISTORY, null, null, count, List.of(), null, List.of(), null);
    }

    public static Result jump(String target) {
        return new Result(Kind.GOTO, target, null, 0, List.of(), null, List.of(), null);
    }

    public static Result checkpoint(String name) {
        return new Result(Kind.CHECKPOINT, name, null, 0, List.of(), null, List.of(), null);
    }

    public static Result restore(String target, Box box) {
        return new Result(Kind.RESTORE, target, null, 0, List.of(), box, List.of(), null);
    }

    public static Result message(List<String> lines) {
        return new Result(Kind.MESSAGE, null, null, 0, lines, null, List.of(), null);
    }

    public static Result message(String line) {
        return message(List.of(line));
    }

    public Result withWarnings(List<String> extra) {
        if (extra.isEmpty()) {
            return this;
        }
        List<String> all = new java.util.ArrayList<>(warnings);
        all.addAll(extra);
        return new Result(kind, label, stream, count, lines, box, List.copyOf(all), select);
    }

    public Result selecting(Box area) {
        return new Result(kind, label, stream, count, lines, box, warnings, area);
    }

    public static Result error(String message) {
        return new Result(Kind.ERROR, null, null, 0, List.of(message), null, List.of(), null);
    }
}
