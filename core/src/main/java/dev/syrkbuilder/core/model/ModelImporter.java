package dev.syrkbuilder.core.model;

import dev.syrkbuilder.core.grid.BlockGrid;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class ModelImporter {
    private ModelImporter() {
    }

    public record Options(int size, boolean solid, String palette) {
    }

    public static BlockGrid importFile(Path file, Options options) throws IOException {
        return importFile(file, options, new java.util.ArrayList<>());
    }

    public static BlockGrid importFile(Path file, Options options, java.util.List<String> warnings) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        VoxelModel voxels;
        if (name.endsWith(".vox")) {
            voxels = VoxLoader.load(file);
            if (options.solid()) {
                Voxelizer.fillInside(voxels);
            }
        } else {
            Mesh mesh;
            if (name.endsWith(".obj")) {
                mesh = ObjLoader.load(file, warnings);
            } else if (name.endsWith(".glb") || name.endsWith(".gltf")) {
                mesh = GltfLoader.load(file);
            } else {
                throw new IOException("Unsupported model type - use .obj, .glb, .gltf or .vox");
            }
            voxels = Voxelizer.voxelize(mesh, options.size(), options.solid());
        }
        return BlockPalette.named(options.palette()).toGrid(voxels);
    }
}
