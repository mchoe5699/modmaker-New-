package com.qwerty.homenet.client.dashboard;

import com.mojang.blaze3d.platform.NativeImage;
import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.data.ZoneData;
import com.qwerty.homenet.network.DashboardDataPacket;
import com.qwerty.homenet.network.ModNetwork;
import com.qwerty.homenet.network.ZoneEditPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 홈네트워크 대시보드 (MTR 대시보드 방식).
 * - 지도: 왼쪽/오른쪽 드래그로 이동, 휠로 확대/축소
 * - "단지 추가" → 지도에서 드래그해서 구역을 그림 → 이름/색상 정하고 저장
 * - 목록에서 단지를 누르면 그 위치로 이동, 수정/삭제
 * - 기기 위치가 점으로 표시되고, 마우스를 올리면 종류와 세대 번호가 보임
 */
public class DashboardScreen extends Screen {
    private static final int PANEL_W = 150;
    private static final int ROW_H = 22;
    private static final int[] PALETTE = {0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x00ACC1, 0x1E88E5,
            0x5E35B1, 0xD81B60, 0x6D4C41, 0x546E7A, 0x00897B, 0x7CB342};

    private enum Mode { VIEW, DRAW, EDIT, CONFIRM_DELETE }

    private List<ZoneData.Zone> zones;
    private List<DeviceRegistry.Entry> devices;

    private double centerX, centerZ;
    private float zoom = 2f;
    private Mode mode = Mode.VIEW;
    private int listScroll;

    // 그리기 / 이동
    private boolean drawing, panning;
    private int dragX1, dragZ1, dragX2, dragZ2;

    // 편집
    private int editId = -1;
    private String editName = "";
    private int editColor = PALETTE[0];
    private int ex1, ez1, ex2, ez2;
    private EditBox nameBox;
    private int deleteId;

    // 지도 텍스처
    private DynamicTexture tex;
    private ResourceLocation texLoc;
    private int texW, texH;
    private boolean dirty = true;
    private int refreshTimer;
    private final Map<Long, int[]> chunkCache = new HashMap<>();

    public DashboardScreen(DashboardDataPacket data) {
        super(Component.translatable("item." + HomeNet.MODID + ".dashboard"));
        this.zones = data.zones();
        this.devices = data.devices();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
    }

    public void update(DashboardDataPacket data) {
        this.zones = data.zones();
        this.devices = data.devices();
        if (mode == Mode.VIEW) rebuildWidgets();
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui." + HomeNet.MODID + ".dashboard." + key, args);
    }

    // ================================================================== 위젯

    @Override
    protected void init() {
        nameBox = null;
        int x = 8, w = PANEL_W - 16;
        switch (mode) {
            case VIEW -> {
                addRenderableWidget(Button.builder(tr("add_zone"), b -> {
                    editId = -1;
                    setMode(Mode.DRAW);
                }).bounds(x, 24, w, 20).build());
                int y0 = 52;
                int rows = Math.max(1, (height - y0 - 8) / ROW_H);
                listScroll = Mth.clamp(listScroll, 0, Math.max(0, zones.size() - rows));
                for (int i = 0; i < rows && i + listScroll < zones.size(); i++) {
                    ZoneData.Zone z = zones.get(i + listScroll);
                    int y = y0 + i * ROW_H;
                    int count = devicesIn(z);
                    addRenderableWidget(Button.builder(Component.literal(z.name() + " (" + count + ")"), b -> focus(z))
                            .bounds(x + 8, y, w - 56, 20).build());
                    addRenderableWidget(Button.builder(Component.literal("✎"), b -> startEdit(z))
                            .bounds(x + w - 46, y, 22, 20).build());
                    addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                        deleteId = z.id();
                        setMode(Mode.CONFIRM_DELETE);
                    }).bounds(x + w - 22, y, 22, 20).build());
                }
            }
            case DRAW -> addRenderableWidget(Button.builder(tr("cancel"), b -> setMode(editId < 0 ? Mode.VIEW : Mode.EDIT))
                    .bounds(x, 24, w, 20).build());
            case EDIT -> {
                nameBox = new EditBox(font, x, 62, w, 18, tr("name"));
                nameBox.setMaxLength(32);
                nameBox.setValue(editName);
                nameBox.setResponder(s -> editName = s);
                addRenderableWidget(nameBox);
                addRenderableWidget(Button.builder(tr("color"), b -> {
                    int idx = 0;
                    for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == editColor) idx = i;
                    editColor = PALETTE[(idx + 1) % PALETTE.length];
                }).bounds(x + 20, 100, w - 20, 20).build());
                addRenderableWidget(Button.builder(tr("redraw"), b -> setMode(Mode.DRAW)).bounds(x, 142, w, 20).build());
                addRenderableWidget(Button.builder(tr("save"), b -> save()).bounds(x, 168, w, 20).build());
                addRenderableWidget(Button.builder(tr("cancel"), b -> setMode(Mode.VIEW)).bounds(x, 192, w, 20).build());
                setFocused(nameBox);
            }
            case CONFIRM_DELETE -> {
                addRenderableWidget(Button.builder(tr("delete"), b -> {
                    ModNetwork.sendToServer(new ZoneEditPacket(ZoneEditPacket.DELETE, deleteId, "", 0, 0, 0, 0, 0));
                    setMode(Mode.VIEW);
                }).bounds(x, 90, w, 20).build());
                addRenderableWidget(Button.builder(tr("cancel"), b -> setMode(Mode.VIEW)).bounds(x, 114, w, 20).build());
            }
        }
        if (texW != width - PANEL_W || texH != height) dirty = true;
    }

    private void setMode(Mode m) {
        mode = m;
        drawing = false;
        rebuildWidgets();
    }

    private int devicesIn(ZoneData.Zone z) {
        int n = 0;
        for (DeviceRegistry.Entry e : devices) {
            if (z.contains(e.pos().getX(), e.pos().getZ()) && zoneAt(e.pos()) == z.id()) n++;
        }
        return n;
    }

    private int zoneAt(BlockPos p) {
        ZoneData.Zone best = null;
        for (ZoneData.Zone z : zones) {
            if (z.contains(p.getX(), p.getZ()) && (best == null || z.area() < best.area())) best = z;
        }
        return best == null ? 0 : best.id();
    }

    private void focus(ZoneData.Zone z) {
        centerX = (z.x1() + z.x2() + 1) / 2.0;
        centerZ = (z.z1() + z.z2() + 1) / 2.0;
        int mapW = width - PANEL_W;
        float fit = Math.min(mapW / (float) (z.x2() - z.x1() + 8), height / (float) (z.z2() - z.z1() + 8));
        zoom = Mth.clamp(fit, 0.25f, 16f);
        dirty = true;
    }

    private void startEdit(ZoneData.Zone z) {
        editId = z.id();
        editName = z.name();
        editColor = z.color();
        ex1 = z.x1(); ez1 = z.z1(); ex2 = z.x2(); ez2 = z.z2();
        focus(z);
        setMode(Mode.EDIT);
    }

    private void save() {
        String name = editName.trim().isEmpty() ? tr("default_name", zones.size() + 1).getString() : editName.trim();
        int action = editId < 0 ? ZoneEditPacket.ADD : ZoneEditPacket.UPDATE;
        ModNetwork.sendToServer(new ZoneEditPacket(action, Math.max(0, editId), name, editColor, ex1, ez1, ex2, ez2));
        setMode(Mode.VIEW);
    }

    // ================================================================== 좌표

    private int mapX0() { return PANEL_W; }
    private int mapW() { return width - PANEL_W; }

    private double worldX(double sx) { return centerX + (sx - mapX0() - mapW() / 2.0) / zoom; }
    private double worldZ(double sy) { return centerZ + (sy - height / 2.0) / zoom; }
    private float screenX(double wx) { return (float) (mapX0() + mapW() / 2.0 + (wx - centerX) * zoom); }
    private float screenY(double wz) { return (float) (height / 2.0 + (wz - centerZ) * zoom); }

    private boolean overMap(double mx) { return mx >= PANEL_W; }

    // ================================================================== 입력

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (!overMap(mx)) return false;
        if (button == 0 && mode == Mode.DRAW) {
            drawing = true;
            dragX1 = dragX2 = Mth.floor(worldX(mx));
            dragZ1 = dragZ2 = Mth.floor(worldZ(my));
            return true;
        }
        if (button == 0 || button == 1) {
            panning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drawing) {
            dragX2 = Mth.floor(worldX(mx));
            dragZ2 = Mth.floor(worldZ(my));
            return true;
        }
        if (panning) {
            centerX -= dx / zoom;
            centerZ -= dy / zoom;
            dirty = true;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drawing) {
            drawing = false;
            ex1 = Math.min(dragX1, dragX2);
            ex2 = Math.max(dragX1, dragX2);
            ez1 = Math.min(dragZ1, dragZ2);
            ez2 = Math.max(dragZ1, dragZ2);
            if (editId < 0) {
                editName = "";
                editColor = PALETTE[zones.size() % PALETTE.length];
            }
            setMode(Mode.EDIT);
            return true;
        }
        panning = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (overMap(mx)) {
            double wx = worldX(mx), wz = worldZ(my);
            zoom = Mth.clamp(zoom * (delta > 0 ? 1.25f : 0.8f), 0.25f, 16f);
            // 마우스 아래 지점이 그대로 있도록
            centerX = wx - (mx - mapX0() - mapW() / 2.0) / zoom;
            centerZ = wz - (my - height / 2.0) / zoom;
            dirty = true;
            return true;
        }
        if (mode == Mode.VIEW) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(delta));
            rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && mode != Mode.VIEW) {
            setMode(Mode.VIEW);
            return true;
        }
        if (mode == Mode.EDIT && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        super.tick();
        if (nameBox != null) nameBox.tick();
        // 지도는 주기적으로 다시 읽음 (청크가 새로 로드될 수 있으므로)
        if (++refreshTimer >= 60) {
            refreshTimer = 0;
            chunkCache.clear();
            dirty = true;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (tex != null) {
            Minecraft.getInstance().getTextureManager().release(texLoc);
            tex = null;
        }
    }

    // ================================================================== 지도 텍스처

    private void rebuildMap() {
        int w = Math.max(1, mapW()), h = Math.max(1, height);
        if (tex == null || texW != w || texH != h) {
            if (tex != null) Minecraft.getInstance().getTextureManager().release(texLoc);
            tex = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, w, h, false));
            texLoc = Minecraft.getInstance().getTextureManager().register("qwertys_homenet_dashboard", tex);
            texW = w;
            texH = h;
        }
        NativeImage img = tex.getPixels();
        if (img == null) return;
        for (int i = 0; i < w; i++) {
            int wx = Mth.floor(centerX + (i + 0.5 - w / 2.0) / zoom);
            for (int j = 0; j < h; j++) {
                int wz = Mth.floor(centerZ + (j + 0.5 - h / 2.0) / zoom);
                img.setPixelRGBA(i, j, colorAt(wx, wz));
            }
        }
        tex.upload();
        dirty = false;
    }

    /** ABGR (NativeImage 형식) */
    private int colorAt(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        long key = ChunkPos.asLong(cx, cz);
        int[] colors = chunkCache.get(key);
        if (colors == null) {
            colors = computeChunk(cx, cz);
            if (colors == null) return ((x ^ z) & 8) == 0 ? 0xFF1C1C1C : 0xFF222222;
            chunkCache.put(key, colors);
        }
        return colors[(z & 15) * 16 + (x & 15)];
    }

    private int[] computeChunk(int cx, int cz) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunk(cx, cz)) return null;
        LevelChunk chunk = level.getChunk(cx, cz);
        int[] out = new int[256];
        int[] heights = new int[256];
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                MapColor color = MapColor.NONE;
                int tries = 0;
                while (y > level.getMinBuildHeight() && tries++ < 12) {
                    mp.set(x, y, z);
                    BlockState s = chunk.getBlockState(mp);
                    color = s.getMapColor(level, mp);
                    if (color != MapColor.NONE) break;
                    y--;
                }
                heights[lz * 16 + lx] = y;
                MapColor.Brightness b = MapColor.Brightness.NORMAL;
                if (lz > 0) {
                    int north = heights[(lz - 1) * 16 + lx];
                    if (y > north) b = MapColor.Brightness.HIGH;
                    else if (y < north) b = MapColor.Brightness.LOW;
                }
                out[lz * 16 + lx] = color == MapColor.NONE ? 0xFF101010 : color.calculateRGBColor(b);
            }
        }
        return out;
    }

    // ================================================================== 그리기

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF0C0E12);
        if (dirty || tex == null) rebuildMap();
        if (texLoc != null) g.blit(texLoc, mapX0(), 0, 0f, 0f, texW, texH, texW, texH);

        g.enableScissor(mapX0(), 0, width, height);
        // 단지
        for (ZoneData.Zone z : zones) {
            if (mode == Mode.EDIT && z.id() == editId) continue;
            drawZoneRect(g, z.x1(), z.z1(), z.x2(), z.z2(), z.color(), z.name());
        }
        if (mode == Mode.EDIT) drawZoneRect(g, ex1, ez1, ex2, ez2, editColor, editName.isEmpty() ? "?" : editName);
        if (drawing) {
            drawZoneRect(g, Math.min(dragX1, dragX2), Math.min(dragZ1, dragZ2), Math.max(dragX1, dragX2), Math.max(dragZ1, dragZ2),
                    0xFFFFFF, (Math.abs(dragX2 - dragX1) + 1) + " x " + (Math.abs(dragZ2 - dragZ1) + 1));
        }
        // 기기
        DeviceRegistry.Entry hover = null;
        for (DeviceRegistry.Entry e : devices) {
            float sx = screenX(e.pos().getX() + 0.5), sy = screenY(e.pos().getZ() + 0.5);
            int r = zoom >= 2 ? 3 : 2;
            g.fill(Math.round(sx) - r - 1, Math.round(sy) - r - 1, Math.round(sx) + r + 1, Math.round(sy) + r + 1, 0xFF000000);
            g.fill(Math.round(sx) - r, Math.round(sy) - r, Math.round(sx) + r, Math.round(sy) + r, 0xFF000000 | kindColor(e.kind()));
            if (Math.abs(mouseX - sx) <= r + 2 && Math.abs(mouseY - sy) <= r + 2) hover = e;
        }
        // 플레이어
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int px = Math.round(screenX(player.getX())), py = Math.round(screenY(player.getZ()));
            g.fill(px - 3, py - 3, px + 3, py + 3, 0xFF000000);
            g.fill(px - 2, py - 2, px + 2, py + 2, 0xFFFFFFFF);
        }
        g.disableScissor();

        // 안내 / 좌표
        if (mode == Mode.DRAW) g.drawCenteredString(font, tr("draw_hint"), mapX0() + mapW() / 2, 8, 0xFFFFFF55);
        if (overMap(mouseX)) {
            String c = "X " + Mth.floor(worldX(mouseX)) + "  Z " + Mth.floor(worldZ(mouseY)) + "   x" + String.format("%.2f", zoom);
            g.drawString(font, c, width - font.width(c) - 6, height - 12, 0xFFDDDDDD);
        }

        // 왼쪽 패널
        g.fill(0, 0, PANEL_W, height, 0xF0141A24);
        g.fill(PANEL_W - 1, 0, PANEL_W, height, 0xFF3A4660);
        g.drawString(font, title, 8, 8, 0xFFFFFFFF);
        switch (mode) {
            case VIEW -> {
                if (zones.isEmpty()) {
                    g.drawWordWrap(font, tr("empty"), 8, 56, PANEL_W - 16, 0xFF9AA6B6);
                }
                int y0 = 52;
                int rows = Math.max(1, (height - y0 - 8) / ROW_H);
                for (int i = 0; i < rows && i + listScroll < zones.size(); i++) {
                    ZoneData.Zone z = zones.get(i + listScroll);
                    int y = y0 + i * ROW_H;
                    g.fill(8, y + 3, 14, y + 17, 0xFF000000 | z.color());
                }
                g.drawWordWrap(font, tr("help"), 8, height - 46, PANEL_W - 16, 0xFF6E7A8E);
            }
            case DRAW -> g.drawWordWrap(font, tr("draw_hint"), 8, 52, PANEL_W - 16, 0xFFFFFF55);
            case EDIT -> {
                g.drawString(font, tr(editId < 0 ? "new_zone" : "edit_zone"), 8, 32, 0xFFFFFFFF);
                g.drawString(font, tr("name"), 8, 50, 0xFF9AA6B6);
                g.drawString(font, tr("color_label"), 8, 88, 0xFF9AA6B6);
                g.fill(8, 101, 26, 119, 0xFF000000 | editColor);
                g.drawString(font, tr("size", ex2 - ex1 + 1, ez2 - ez1 + 1), 8, 126, 0xFF9AA6B6);
            }
            case CONFIRM_DELETE -> {
                ZoneData.Zone z = null;
                for (ZoneData.Zone zz : zones) if (zz.id() == deleteId) z = zz;
                g.drawWordWrap(font, tr("delete_confirm", z == null ? "?" : z.name()), 8, 40, PANEL_W - 16, 0xFFFF8080);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);

        if (hover != null && overMap(mouseX)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("block." + HomeNet.MODID + "." + blockKey(hover.kind())));
            if (!hover.unit().isEmpty()) lines.add(tr("unit", hover.unit()));
            lines.add(Component.literal(hover.pos().toShortString()).withStyle(s -> s.withColor(0x9AA6B6)));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void drawZoneRect(GuiGraphics g, int x1, int z1, int x2, int z2, int color, String name) {
        int sx1 = Math.round(screenX(x1)), sy1 = Math.round(screenY(z1));
        int sx2 = Math.round(screenX(x2 + 1)), sy2 = Math.round(screenY(z2 + 1));
        if (sx2 - sx1 < 2) sx2 = sx1 + 2;
        if (sy2 - sy1 < 2) sy2 = sy1 + 2;
        g.fill(sx1, sy1, sx2, sy2, 0x50000000 | color);
        int c = 0xFF000000 | color;
        g.fill(sx1, sy1, sx2, sy1 + 1, c);
        g.fill(sx1, sy2 - 1, sx2, sy2, c);
        g.fill(sx1, sy1, sx1 + 1, sy2, c);
        g.fill(sx2 - 1, sy1, sx2, sy2, c);
        if (sx2 - sx1 > font.width(name) + 4 && sy2 - sy1 > 12) {
            g.drawCenteredString(font, name, (sx1 + sx2) / 2, (sy1 + sy2) / 2 - 4, 0xFFFFFFFF);
        }
    }

    private static String blockKey(DeviceRegistry.Kind k) {
        return switch (k) {
            case DOOR_CAMERA -> "door_station";
            default -> k.key();
        };
    }

    private static int kindColor(DeviceRegistry.Kind k) {
        return switch (k) {
            case WALLPAD -> 0x4FC3F7;
            case VIDEO_PHONE -> 0x80DEEA;
            case INTERPHONE -> 0xAED581;
            case GUARD_CONSOLE -> 0xFFB74D;
            case LOBBY_PHONE -> 0xBA68C8;
            case DOOR_CAMERA -> 0xF06292;
            case DOOR_PHONE -> 0xE57373;
        };
    }
}
