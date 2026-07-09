package me.voxy.dynamic.gui;

import me.voxy.dynamic.DynamicVoxyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.Consumer;

public class VoxyDynamicAreasScreen extends Screen {
    private final String serverId;
    private EditBox nameField;
    private AreaList areaList;

    public VoxyDynamicAreasScreen(String serverId) {
        super(Component.literal("Voxy Dynamic Areas - " + serverId));
        this.serverId = serverId;
    }

    @Override
    protected void init() {
        int fieldWidth = 220;
        int centerX = this.width / 2;

        this.nameField = new EditBox(this.font, centerX - fieldWidth / 2, 30, fieldWidth - 110, 20, Component.literal("Area name"));
        this.nameField.setMaxLength(64);
        this.addRenderableWidget(this.nameField);

        this.addRenderableWidget(Button.builder(Component.literal("Save Current As"), button -> saveCurrent())
                .bounds(centerX - fieldWidth / 2 + fieldWidth - 105, 30, 105, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Use Default Storage"), button -> {
                    DynamicVoxyManager.loadDefaultArea();
                    refreshList();
                })
                .bounds(centerX - 90, 54, 180, 20)
                .build());

        this.areaList = new AreaList(this.minecraft, this.width, this.height - 108, 78, 22);
        this.addRenderableWidget(this.areaList);
        refreshList();

        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(centerX - 50, this.height - 28, 100, 20)
                .build());
    }

    private void saveCurrent() {
        String name = this.nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        DynamicVoxyManager.saveCurrentAreaAs(name);
        this.nameField.setValue("");
        refreshList();
    }

    private void refreshList() {
        Map<String, String> areas = DynamicVoxyManager.getNamedAreas(this.serverId);
        String pinned = DynamicVoxyManager.getPinnedName(this.serverId);
        this.areaList.refresh(areas, pinned);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    private class AreaList extends ObjectSelectionList<AreaList.Entry> {
        AreaList(Minecraft client, int width, int height, int y0, int itemHeight) {
            super(client, width, height, y0, itemHeight);
        }

        void refresh(Map<String, String> areas, String pinned) {
            this.clearEntries();
            if (areas.isEmpty()) {
                this.addEntry(new Entry(null, false));
                return;
            }
            for (String name : areas.keySet()) {
                this.addEntry(new Entry(name, name.equals(pinned)));
            }
        }

        class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String name;
            private final StringWidget label;
            private final Button renameButton;
            private final Button loadButton;
            private final Button deleteButton;

            private static final int DELETE_WIDTH = 50;
            private static final int LOAD_WIDTH = 42;
            private static final int RENAME_WIDTH = 52;
            private static final int BUTTON_GAP = 4;
            private static final int LABEL_GAP = 8;
            private static final int LABEL_PADDING = 4;

            Entry(String name, boolean pinned) {
                this.name = name;
                String text = name == null ? "No saved areas for this server yet" : (pinned ? "● " : "") + name;
                this.label = new StringWidget(0, 0, 20, 20, Component.literal(text), VoxyDynamicAreasScreen.this.font);
                if (name != null) {
                    this.renameButton = Button.builder(Component.literal("Rename"), button ->
                            VoxyDynamicAreasScreen.this.minecraft.setScreen(new RenameAreaScreen(
                                    VoxyDynamicAreasScreen.this, name, VoxyDynamicAreasScreen.this::refreshList))
                    ).bounds(0, 0, RENAME_WIDTH, 20).build();
                    this.loadButton = Button.builder(Component.literal("Load"), button -> {
                        DynamicVoxyManager.applyNamedArea(name);
                        refreshList();
                    }).bounds(0, 0, LOAD_WIDTH, 20).build();
                    this.deleteButton = Button.builder(Component.literal("Delete"), button -> {
                        DynamicVoxyManager.deleteNamedArea(name);
                        refreshList();
                    }).bounds(0, 0, DELETE_WIDTH, 20).build();
                } else {
                    this.renameButton = null;
                    this.loadButton = null;
                    this.deleteButton = null;
                }
            }

            @Override
            public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
                int labelX = getContentX() + LABEL_PADDING;
                int labelAvailableWidth;

                if (this.loadButton != null) {
                    int buttonY = getContentY() + (getContentHeight() - 20) / 2;
                    int deleteX = getContentRight() - DELETE_WIDTH;
                    int loadX = deleteX - BUTTON_GAP - LOAD_WIDTH;
                    int renameX = loadX - BUTTON_GAP - RENAME_WIDTH;

                    this.deleteButton.setX(deleteX);
                    this.deleteButton.setY(buttonY);
                    this.loadButton.setX(loadX);
                    this.loadButton.setY(buttonY);
                    this.renameButton.setX(renameX);
                    this.renameButton.setY(buttonY);

                    labelAvailableWidth = Math.max(20, renameX - LABEL_GAP - labelX);
                } else {
                    labelAvailableWidth = Math.max(20, getContentRight() - LABEL_PADDING - labelX);
                }

                this.label.setX(labelX);
                this.label.setY(getContentY() + (getContentHeight() - 8) / 2);
                this.label.setWidth(labelAvailableWidth);
                this.label.setMaxWidth(labelAvailableWidth, StringWidget.TextOverflow.CLAMPED);
                this.label.render(graphics, mouseX, mouseY, partialTick);

                if (this.loadButton != null) {
                    this.renameButton.render(graphics, mouseX, mouseY, partialTick);
                    this.loadButton.render(graphics, mouseX, mouseY, partialTick);
                    this.deleteButton.render(graphics, mouseX, mouseY, partialTick);
                }
            }

            @Override
            public void visitWidgets(Consumer<AbstractWidget> consumer) {
                consumer.accept(this.label);
                if (this.loadButton != null) {
                    consumer.accept(this.renameButton);
                    consumer.accept(this.loadButton);
                    consumer.accept(this.deleteButton);
                }
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (this.renameButton != null && this.renameButton.mouseClicked(event, doubleClick)) {
                    return true;
                }
                if (this.loadButton != null && this.loadButton.mouseClicked(event, doubleClick)) {
                    return true;
                }
                if (this.deleteButton != null && this.deleteButton.mouseClicked(event, doubleClick)) {
                    return true;
                }
                return super.mouseClicked(event, doubleClick);
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.name != null ? this.name : "No saved areas");
            }
        }
    }
}
