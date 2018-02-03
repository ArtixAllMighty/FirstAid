package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.util.GuiUtils;
import ichttt.mods.firstaid.common.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.damagesystem.capability.PlayerDataManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@SideOnly(Side.CLIENT)
public class HUDHandler {
    private static final Map<EnumPlayerPart, String> TRANSLATION_MAP = new HashMap<>();
    private static int maxLength;
    private static final DecimalFormat TEXT_FORMAT = new DecimalFormat("#.#");

    public static void rebuildTranslationTable() {
        FirstAid.logger.debug("Building GUI translation table");
        TRANSLATION_MAP.clear();
        maxLength = 0;
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            String translated = I18n.format("gui." + part.toString().toLowerCase(Locale.ENGLISH));
            maxLength = Math.max(maxLength, translated.length());
            TRANSLATION_MAP.put(part, translated);
        }
    }

    public static void renderOverlay(ScaledResolution scaledResolution) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!FirstAidConfig.overlay.showOverlay || mc.player == null || GuiHealthScreen.isOpen || mc.player.isCreative() || mc.player.isSpectator())
            return;
        AbstractPlayerDamageModel damageModel = PlayerDataManager.getDamageModel(mc.player);
        Objects.requireNonNull(damageModel);
        if (damageModel.isTemp) //Wait until we receive the remote model
            return;
        mc.getTextureManager().bindTexture(Gui.ICONS);
        Gui gui = mc.ingameGUI;
        int xOffset = FirstAidConfig.overlay.xOffset;
        int yOffset = FirstAidConfig.overlay.yOffset;
        switch (FirstAidConfig.overlay.position) {
            case 0:
                break;
            case 1:
                xOffset = scaledResolution.getScaledWidth() - xOffset - damageModel.getMaxRenderSize() - (maxLength * 5 + 6);
                break;
            case 2:
                yOffset = scaledResolution.getScaledHeight() - yOffset - 80;
                break;
            case 3:
                xOffset = scaledResolution.getScaledWidth() - xOffset - damageModel.getMaxRenderSize() - (maxLength * 5 + 6);
                yOffset = scaledResolution.getScaledHeight() - yOffset - 80;
                break;
            default:
                throw new RuntimeException("Invalid config option for position: " + FirstAidConfig.overlay.position);
        }
        if (mc.currentScreen instanceof GuiChat && FirstAidConfig.overlay.position == 2)
            return;
        if (mc.gameSettings.showDebugInfo && FirstAidConfig.overlay.position == 0)
            return;
        GlStateManager.pushMatrix();
        GlStateManager.scale(FirstAidConfig.overlay.hudScale, FirstAidConfig.overlay.hudScale, 1);
        GlStateManager.translate(xOffset, yOffset, 0F);
        boolean playerDead = damageModel.isDead(mc.player);
        for (AbstractDamageablePart part : damageModel) {
            mc.fontRenderer.drawStringWithShadow(TRANSLATION_MAP.get(part.part), 0, 0, 0xFFFFFF);
            if (FirstAidConfig.overlay.displayHealthAsNumber) {
                mc.fontRenderer.drawStringWithShadow(TEXT_FORMAT.format(part.currentHealth) + "/" + part.getMaxHealth(), maxLength * 5 + 6, 0, 0xFFFFFF);
            } else {
                mc.getTextureManager().bindTexture(Gui.ICONS);
                GuiUtils.drawHealth(part, maxLength * 5 + 6, 0, gui, false, playerDead);
            }
            GlStateManager.translate(0, 10F, 0F);

        }
        GlStateManager.popMatrix();
    }
}
