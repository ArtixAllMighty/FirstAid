/*
 * FirstAid
 * Copyright (C) 2017-2019
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.common.damagesystem.distribution;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.IDamageDistribution;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.event.FirstAidLivingDamageEvent;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageReceiveDamage;
import ichttt.mods.firstaid.common.util.ArmorUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.stats.Stats;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.network.PacketDistributor;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class DamageDistribution implements IDamageDistribution {

    public static float handleDamageTaken(IDamageDistribution damageDistribution, AbstractPlayerDamageModel damageModel, float damage, @Nonnull PlayerEntity player, @Nonnull DamageSource source, boolean addStat, boolean redistributeIfLeft) {
        if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info("Damaging {} using {} for dmg source {}, redistribute {}, addStat {}", damage, damageDistribution.toString(), source.damageType, redistributeIfLeft, addStat);
        }
        CompoundNBT beforeCache = damageModel.serializeNBT();
        damage = ArmorUtils.applyGlobalPotionModifiers(player, source, damage);
        //VANILLA COPY - combat tracker and exhaustion
        if (damage != 0.0F) {
            player.addExhaustion(source.getHungerDamage());
            float currentHealth = player.getHealth();
            player.getCombatTracker().trackDamage(source, currentHealth, damage);
        }

        float left = damageDistribution.distributeDamage(damage, player, source, addStat);
        if (left > 0 && redistributeIfLeft) {
            damageDistribution = RandomDamageDistribution.NEAREST_KILL;
            left = damageDistribution.distributeDamage(left, player, source, addStat);
        }
        PlayerDamageModel before = PlayerDamageModel.create();
        before.deserializeNBT(beforeCache);
        if (MinecraftForge.EVENT_BUS.post(new FirstAidLivingDamageEvent(player, damageModel, before, source, left))) {
            damageModel.deserializeNBT(beforeCache); //restore prev state
            return 0F;
        }

        if (damageModel.isDead(player))
            CommonUtils.killPlayer(player, source);
        return left;
    }

    protected float minHealth(@Nonnull PlayerEntity player, @Nonnull AbstractDamageablePart part) {
        return 0F;
    }

    protected float distributeDamageOnParts(float damage, @Nonnull AbstractPlayerDamageModel damageModel, @Nonnull EnumPlayerPart[] enumParts, @Nonnull PlayerEntity player, boolean addStat) {
        ArrayList<AbstractDamageablePart> damageableParts = new ArrayList<>(enumParts.length);
        for (EnumPlayerPart part : enumParts) {
            damageableParts.add(damageModel.getFromEnum(part));
        }
        Collections.shuffle(damageableParts);
        for (AbstractDamageablePart part : damageableParts) {
            float minHealth = minHealth(player, part);
            FirstAid.NETWORKING.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player), new MessageReceiveDamage(part.part, damage, minHealth));
            float dmgDone = damage - part.damage(damage, player, damageModel.getMorphineTicks() == 0, minHealth);
            if (addStat)
                player.addStat(Stats.DAMAGE_TAKEN, Math.round(dmgDone * 10.0F));
            damage -= dmgDone;
            if (damage == 0)
                break;
            else if (damage < 0) {
                FirstAid.LOGGER.error("Got negative damage {} left? Logic error? ", damage);
                break;
            }
        }
        return damage;
    }

    @Nonnull
    protected abstract List<Pair<EquipmentSlotType, EnumPlayerPart[]>> getPartList();

    @Override
    public float distributeDamage(float damage, @Nonnull PlayerEntity player, @Nonnull DamageSource source, boolean addStat) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        for (Pair<EquipmentSlotType, EnumPlayerPart[]> pair : getPartList()) {
            EquipmentSlotType slot = pair.getLeft();
            damage = ArmorUtils.applyArmor(player, player.getItemStackFromSlot(slot), source, damage, slot);
            if (damage <= 0F)
                return 0F;
            damage = ArmorUtils.applyEnchantmentModifiers(player.getItemStackFromSlot(slot), source, damage);
            if (damage <= 0F)
                return 0F;
            damage = ForgeHooks.onLivingDamage(player, source, damage); //we post every time we damage a part, make it so other mods can modify
            if (damage <= 0F) return 0F;

            damage = distributeDamageOnParts(damage, damageModel, pair.getRight(), player, addStat);
            if (damage == 0F)
                break;
        }
        return damage;
    }
}
