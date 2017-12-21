package ichttt.mods.firstaid.common.util;

import com.creativemd.playerrevive.api.IRevival;
import com.creativemd.playerrevive.api.capability.CapaRevive;
import com.google.common.collect.ImmutableMap;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommonUtils {
    @Nonnull
    public static final EntityEquipmentSlot[] ARMOR_SLOTS;
    @Nonnull
    public static final ImmutableMap<EntityEquipmentSlot, List<EnumPlayerPart>> slotToParts;

    static {
        ARMOR_SLOTS = new EntityEquipmentSlot[4];
        ARMOR_SLOTS[3] = EntityEquipmentSlot.HEAD;
        ARMOR_SLOTS[2] = EntityEquipmentSlot.CHEST;
        ARMOR_SLOTS[1] = EntityEquipmentSlot.LEGS;
        ARMOR_SLOTS[0] = EntityEquipmentSlot.FEET;
        slotToParts = ImmutableMap.<EntityEquipmentSlot, List<EnumPlayerPart>>builder().
        put(EntityEquipmentSlot.HEAD, Collections.singletonList(EnumPlayerPart.HEAD)).
        put(EntityEquipmentSlot.CHEST, Arrays.asList(EnumPlayerPart.LEFT_ARM, EnumPlayerPart.RIGHT_ARM, EnumPlayerPart.BODY)).
        put(EntityEquipmentSlot.LEGS, Arrays.asList(EnumPlayerPart.LEFT_LEG, EnumPlayerPart.RIGHT_LEG)).
        put(EntityEquipmentSlot.FEET, Arrays.asList(EnumPlayerPart.LEFT_FOOT, EnumPlayerPart.RIGHT_FOOT)).build();
    }

    public static void killPlayer(@Nonnull EntityPlayer player, @Nullable DamageSource source) {
        if (source != null && FirstAid.activeHealingConfig.allowOtherHealingItems && player.checkTotemDeathProtection(source))
            return;

        IRevival revival = getRevivalIfPossible(player);
        if (revival != null) {
            revival.startBleeding();
        } else
            ((DataManagerWrapper) player.dataManager).set_impl(EntityPlayer.HEALTH, 0F);
    }

    /**
     * Gets the cap, or null if not applicable
     * @param player The player to check
     * @return The cap or null if the player cannot be revived
     */
    @Nullable
    public static IRevival getRevivalIfPossible(@Nullable EntityPlayer player) {
        if (player == null || CapaRevive.reviveCapa == null)
            return null;
        MinecraftServer server = player.getServer();
        if (server == null)
            return null;
        IRevival revival = player.getCapability(CapaRevive.reviveCapa, null);
        if (revival != null && server.getPlayerList().getCurrentPlayerCount() > 1)
            return revival;
        else
            return null;
    }

    public static boolean isValidArmorSlot(EntityEquipmentSlot slot) {
        return slot.getSlotType() == EntityEquipmentSlot.Type.ARMOR;
    }

    public static String getActiveModidSave() {
        ModContainer activeModContainer = Loader.instance().activeModContainer();
        return activeModContainer == null ? "UNKNOWN-NULL" : activeModContainer.getModId();
    }
}