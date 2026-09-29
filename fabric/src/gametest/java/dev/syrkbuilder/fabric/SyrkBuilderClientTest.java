package dev.syrkbuilder.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

public class SyrkBuilderClientTest implements FabricClientGameTest {
    private final List<String> report = new ArrayList<>();
    private boolean failed;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> {
            c.options.renderDistance().set(2);
            c.options.simulationDistance().set(5);
            c.options.framerateLimit().set(30);
        });
        try (TestSingleplayerContext world = context.worldBuilder()
            .adjustSettings(creator -> {
                creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                creator.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_CHUNK_RADIUS, 0, null);
            })
            .create()) {
            world.getClientWorld().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");

            BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
            int fx = feet.getX();
            int fy = feet.getY();
            int fz = feet.getZ();
            server.runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(fx - 14, fy, fz - 14, fx + 14, fy + 24, fz + 14));
            server.runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(fx - 14, fy - 1, fz - 14, fx + 14, fy - 1, fz + 14));
            server.runCommand("fill %d %d %d %d %d %d minecraft:dirt".formatted(fx - 14, fy - 4, fz - 14, fx + 14, fy - 2, fz + 14));
            BlockPos target = new BlockPos(fx, fy - 1, fz + 6);
            context.waitFor(client -> client.level.getBlockState(target).is(Blocks.GRASS_BLOCK), 200);
            lookAt(context, target);
            context.waitTicks(5);
            context.takeScreenshot("00_world");

            chat(context, world, "/sb sphere stone 2");
            check("/sb sphere places stone at the aimed block", waitServer(context, server, s -> s.overworld().getBlockState(target.above()).is(Blocks.STONE)));
            context.waitTicks(5);
            context.takeScreenshot("01_sphere_command");
            check("sphere is a live preview", context.computeOnClient(c -> ClientData.pending() != null));

            context.getInput().pressKey(GLFW.GLFW_KEY_F7);
            check("F7 opens the editor", waitClient(context, () -> context.computeOnClient(c -> c.screen instanceof EditorScreen)));
            context.waitTicks(5);
            context.takeScreenshot("02_editor_shapes");

            int minXBefore = context.computeOnClient(c -> ClientData.pending() == null ? Integer.MIN_VALUE : ClientData.pending().minX());
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
            check("arrow key nudges the preview", waitClient(context, () -> context.computeOnClient(c ->
                ClientData.pending() != null && ClientData.pending().minX() != minXBefore)));
            context.takeScreenshot("03_editor_nudged");

            String[] tabs = {"brushes", "fill", "trees", "paths", "selection", "clipboard", "import"};
            int[] keys = {GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9};
            for (int i = 0; i < tabs.length; i++) {
                context.getInput().pressKey(keys[i]);
                context.waitTicks(3);
                context.takeScreenshot("04_tab_" + (i + 3) + "_" + tabs[i]);
            }
            context.getInput().pressKey(GLFW.GLFW_KEY_0);
            context.waitTicks(3);
            context.takeScreenshot("05_tab_history");

            context.getInput().pressKey(GLFW.GLFW_KEY_1);
            context.waitTicks(2);
            context.runOnClient(c -> ((EditorScreen) c.screen).openPicker(false));
            context.waitTicks(3);
            context.takeScreenshot("06_colour_picker");
            context.runOnClient(c -> ((EditorScreen) c.screen).openPicker(true));
            context.waitTicks(3);
            context.takeScreenshot("07_gradient_picker");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(2);
            check("Esc closes the picker, not the editor", context.computeOnClient(c -> c.screen instanceof EditorScreen));

            context.runOnClient(c -> c.setScreen(EditorScreen.at(EditorScreen.SETTINGS)));
            context.waitTicks(3);
            context.takeScreenshot("08_settings");

            context.getInput().holdControl();
            context.getInput().pressKey(GLFW.GLFW_KEY_Z);
            context.getInput().releaseControl();
            check("Ctrl+Z undoes the sphere", waitServer(context, server, s -> !s.overworld().getBlockState(target.above()).is(Blocks.STONE)
                && !s.overworld().getBlockState(target.above().east()).is(Blocks.STONE)
                && !s.overworld().getBlockState(target.above().west()).is(Blocks.STONE)));

            context.runOnClient(c -> c.setScreen(EditorScreen.at(0)));
            context.waitTicks(2);
            context.getInput().pressKey(GLFW.GLFW_KEY_F7);
            check("F7 closes the editor", waitClient(context, () -> context.computeOnClient(c -> c.screen == null)));

            context.getInput().pressKey(GLFW.GLFW_KEY_N);
            check("N turns noclip on", waitClient(context, () -> context.computeOnClient(c -> NoClip.active(c.player.getUUID()))));
            context.waitTicks(3);
            check("noclip sets noPhysics on the client player", context.computeOnClient(c -> c.player.noPhysics));
            double startY = context.computeOnClient(c -> c.player.getY());
            context.getInput().holdKeyFor(options -> options.keyShift, 50);
            double endY = context.computeOnClient(c -> c.player.getY());
            report.add("      noclip flight: y " + String.format("%.2f", startY) + " -> " + String.format("%.2f", endY) + " (ground top at " + fy + ")");
            check("noclip flies down through the ground", endY < fy - 1.5);
            context.takeScreenshot("09_noclip_in_ground");
            context.getInput().pressKey(GLFW.GLFW_KEY_N);
            check("N turns noclip off", waitClient(context, () -> context.computeOnClient(c -> !NoClip.active(c.player.getUUID()))));

            server.runCommand("tp @a %d %d %d".formatted(fx, fy + 1, fz));
            context.waitTicks(10);
            lookAt(context, target);
            context.waitTicks(2);
            BlockPos aimedBefore = context.computeOnClient(c -> SyrkBuilderClient.lookedAt(c.player));
            report.add("      aiming at " + aimedBefore + " before /sb tree");
            chat(context, world, "/sb tree oak");
            boolean trunk = waitServer(context, server, s -> {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (s.overworld().getBlockState(target.offset(dx, 1, dz)).is(Blocks.OAK_LOG)) {
                            return true;
                        }
                    }
                }
                return false;
            });
            BlockPos aimed = context.computeOnClient(c -> SyrkBuilderClient.lookedAt(c.player));
            report.add("      aimed at " + aimed + ", target " + target);
            check("/sb tree grows a trunk where you aim", trunk);
            lookAt(context, new BlockPos(fx, fy + 8, fz + 12));
            context.waitTicks(5);
            context.takeScreenshot("10_tree");
        } catch (Throwable t) {
            failed = true;
            report.add("CRASH " + t);
            for (StackTraceElement e : t.getStackTrace()) {
                report.add("      at " + e);
            }
        } finally {
            writeReport();
        }
        if (failed) {
            throw new AssertionError("SyrkBuilder client test failed:\n" + String.join("\n", report));
        }
    }

    private void chat(ClientGameTestContext context, TestSingleplayerContext world, String text) {
        context.getInput().pressKey(options -> options.keyChat);
        context.waitForScreen(ChatScreen.class);
        context.getInput().typeChars(text);
        context.getInput().holdKeyFor(InputConstants.KEY_RETURN, 0);
        context.waitTicks(5);
    }

    private boolean waitServer(ClientGameTestContext context, TestServerContext server, java.util.function.Predicate<net.minecraft.server.MinecraftServer> condition) {
        for (int i = 0; i < 100; i++) {
            if (server.computeOnServer(condition::test)) {
                return true;
            }
            context.waitTick();
        }
        return false;
    }

    private void lookAt(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(c -> {
            var eye = c.player.getEyePosition();
            double dx = pos.getX() + 0.5 - eye.x;
            double dy = pos.getY() + 0.5 - eye.y;
            double dz = pos.getZ() + 0.5 - eye.z;
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            c.player.setYRot(yaw);
            c.player.setXRot(pitch);
            c.player.yRotO = yaw;
            c.player.xRotO = pitch;
        });
    }

    private boolean waitClient(ClientGameTestContext context, BooleanSupplier condition) {
        for (int i = 0; i < 60; i++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            context.waitTick();
        }
        return condition.getAsBoolean();
    }

    private void check(String name, boolean ok) {
        report.add((ok ? "PASS  " : "FAIL  ") + name);
        if (!ok) {
            failed = true;
        }
    }

    private void writeReport() {
        try {
            Files.write(Path.of("syrkbuilder-test-report.txt"), report);
        } catch (IOException ignored) {
        }
        System.out.println("=== SyrkBuilder test report ===");
        report.forEach(System.out::println);
    }
}
