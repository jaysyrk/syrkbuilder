package dev.syrkbuilder.core.session;

import dev.syrkbuilder.core.grid.BlockGrid;
import dev.syrkbuilder.core.grid.Schematics;
import java.io.IOException;
import java.util.List;

public interface TemplateStore {
    void save(String name, BlockGrid grid) throws IOException;

    String export(String name, BlockGrid grid, Schematics.Format format) throws IOException;

    BlockGrid load(String name) throws IOException;

    List<String> list();

    boolean delete(String name);
}
