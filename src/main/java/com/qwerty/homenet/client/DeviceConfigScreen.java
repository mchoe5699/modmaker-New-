package com.qwerty.homenet.client;

import com.qwerty.homenet.block.DeviceType;
import com.qwerty.homenet.blockentity.DeviceBlockEntity;
import com.qwerty.homenet.network.DeviceConfigOpenPacket;
import com.qwerty.homenet.network.DeviceConfigPacket;
import com.qwerty.homenet.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** 제어 블록 / 스마트 조명 설정: 이름, 기기 종류, 켜기/끄기 테스트 */
public class DeviceConfigScreen extends HomeNetScreen {
    private final BlockPos pos;
    private String name;
    private DeviceType type;
    private boolean on;
    private EditBox nameBox;

    public DeviceConfigScreen(DeviceConfigOpenPacket p) {
        super(Component.translatable("gui.qwertys_homenet.device_config"), 220, 140);
        this.pos = p.pos();
        this.name = p.name();
        this.type = DeviceType.byId(p.type());
        this.on = p.on();
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, left + 12, top + 40, panelW - 24, 18, tr("device_name"));
        nameBox.setMaxLength(DeviceBlockEntity.MAX_NAME);
        nameBox.setValue(name);
        nameBox.setResponder(s -> name = s);
        nameBox.setHint(type.displayName());
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(tr("device_type", type.displayName()), b -> {
            type = type.next();
            rebuildWidgets();
        }).bounds(left + 12, top + 66, panelW - 24, 20).build());

        addRenderableWidget(Button.builder(tr("test_toggle", type.stateName(on)), b -> {
            ModNetwork.sendToServer(new DeviceConfigPacket(pos, name, type.ordinal(), true));
            on = !on;
            rebuildWidgets();
        }).bounds(left + 12, top + 92, 94, 20).build());

        addRenderableWidget(Button.builder(tr("save"), b -> save())
                .bounds(left + panelW - 106, top + 92, 94, 20).build());
    }

    private void save() {
        ModNetwork.sendToServer(new DeviceConfigPacket(pos, name, type.ordinal(), false));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawDevice(g, title, null);
        g.drawString(font, tr("device_name"), left + 12, top + 28, TEXT_DIM, false);
        g.drawString(font, tr("device_link_hint"), left + 12, top + 120, TEXT_DIM, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        super.tick();
        if (nameBox != null) nameBox.tick();
    }
}
