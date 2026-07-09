package me.voxy.dynamic.gui;

import me.voxy.dynamic.DynamicVoxyManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RenameAreaScreen extends Screen {
    private final Screen parent;
    private final String oldName;
    private final Runnable onRenamed;
    private EditBox nameField;

    public RenameAreaScreen(Screen parent, String oldName, Runnable onRenamed) {
        super(Component.literal("Rename Area"));
        this.parent = parent;
        this.oldName = oldName;
        this.onRenamed = onRenamed;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.nameField = new EditBox(this.font, centerX - 110, centerY - 30, 220, 20, Component.literal("New name"));
        this.nameField.setMaxLength(64);
        this.nameField.setValue(this.oldName);
        this.addRenderableWidget(this.nameField);
        this.setInitialFocus(this.nameField);

        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), button -> confirm())
                .bounds(centerX - 105, centerY, 100, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.minecraft.setScreen(this.parent))
                .bounds(centerX + 5, centerY, 100, 20)
                .build());
    }

    private void confirm() {
        String newName = this.nameField.getValue().trim();
        if (!newName.isEmpty()) {
            DynamicVoxyManager.renameNamedArea(this.oldName, newName);
        }
        this.onRenamed.run();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
