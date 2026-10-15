package pl.esp.balloon;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class BalloonEsp implements ClientModInitializer {
    private static boolean enabled = true;
    private static KeyBinding toggleKey;

    // kolor RGBA (0-1)
    private static final float R = 1f, G = 0.2f, B = 0.2f, A = 1f;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.balloonesp.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.balloonesp"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Balloon ESP: " + (enabled ? "ON" : "OFF")), true);
                }
            }
        });

        WorldRenderEvents.LAST.register(BalloonEsp::render);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (!enabled || world == null || mc.player == null) return;

        List<Box> boxes = new ArrayList<>();

        // 1) Postawione glowy (bloki)
        int r = mc.options.getClampedViewDistance();
        ChunkPos cp = mc.player.getChunkPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cp.x + dx, cp.z + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof SkullBlockEntity)) continue;
                    BlockState st = world.getBlockState(be.getPos());
                    if (st.isOf(Blocks.PLAYER_HEAD) || st.isOf(Blocks.PLAYER_WALL_HEAD)) {
                        boxes.add(new Box(be.getPos()));
                    }
                }
            }
        }

        // 2) Armor standy z glowa gracza i wyrzucone glowy
        for (Entity e : world.getEntities()) {
            if (e instanceof ArmorStandEntity as
                    && as.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.PLAYER_HEAD)) {
                boxes.add(as.getBoundingBox());
            } else if (e instanceof ItemEntity ie && ie.getStack().isOf(Items.PLAYER_HEAD)) {
                boxes.add(ie.getBoundingBox());
            }
        }
        if (boxes.isEmpty()) return;

        Vec3d cam = ctx.camera().getPos();
        MatrixStack ms = ctx.matrixStack();
        Matrix4f m = ms.peek().getPositionMatrix();

        RenderSystem.disableDepthTest();   // <- widocznosc przez sciany
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (Box b : boxes) {
            drawBox(buf, m, b.offset(-cam.x, -cam.y, -cam.z));
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawBox(BufferBuilder buf, Matrix4f m, Box b) {
        float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
        float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;
        // dol
        line(buf, m, x1, y1, z1, x2, y1, z1); line(buf, m, x2, y1, z1, x2, y1, z2);
        line(buf, m, x2, y1, z2, x1, y1, z2); line(buf, m, x1, y1, z2, x1, y1, z1);
        // gora
        line(buf, m, x1, y2, z1, x2, y2, z1); line(buf, m, x2, y2, z1, x2, y2, z2);
        line(buf, m, x2, y2, z2, x1, y2, z2); line(buf, m, x1, y2, z2, x1, y2, z1);
        // pionowe
        line(buf, m, x1, y1, z1, x1, y2, z1); line(buf, m, x2, y1, z1, x2, y2, z1);
        line(buf, m, x2, y1, z2, x2, y2, z2); line(buf, m, x1, y1, z2, x1, y2, z2);
    }

    private static void line(BufferBuilder buf, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2) {
        buf.vertex(m, x1, y1, z1).color(R, G, B, A);
        buf.vertex(m, x2, y2, z2).color(R, G, B, A);
    }
}
