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

package ichttt.mods.firstaid.common.apiimpl;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.FirstAidRegistry;
import ichttt.mods.firstaid.api.IDamageDistribution;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.debuff.builder.IDebuffBuilder;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.damagesystem.debuff.ConstantDebuff;
import ichttt.mods.firstaid.common.damagesystem.debuff.OnHitDebuff;
import ichttt.mods.firstaid.common.damagesystem.debuff.SharedDebuff;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.StandardDamageDistribution;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public class FirstAidRegistryImpl extends FirstAidRegistry {
    public static final FirstAidRegistryImpl INSTANCE = new FirstAidRegistryImpl();
    private final Map<String, IDamageDistribution> DISTRIBUTION_MAP = new ConcurrentHashMap<>();
    private final Map<Item, Pair<Function<ItemStack, AbstractPartHealer>, Function<ItemStack, Integer>>> HEALER_MAP = new ConcurrentHashMap<>();
    private final Multimap<EnumDebuffSlot, Supplier<IDebuff>> DEBUFFS = HashMultimap.create();
    private boolean registrationAllowed = true;

    public static void finish() {
        FirstAidRegistry registryImpl = FirstAidRegistry.getImpl();
        if (registryImpl == null)
            throw new IllegalStateException("The apiimpl has not been set! Something went seriously wrong!");
        if (registryImpl != INSTANCE)
            throw new IllegalStateException("A mod has registered a custom apiimpl for the registry. THIS IS NOT ALLOWED!" +
            "It should be " + INSTANCE.getClass().getName() + " but it actually is " + registryImpl.getClass().getName());
        INSTANCE.registrationAllowed = false;
        if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info("REG READOUT:");
            for (Map.Entry<String, IDamageDistribution> entry : INSTANCE.DISTRIBUTION_MAP.entrySet()) {
                FirstAid.LOGGER.info("{} bound to {}", entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void bindDamageSourceStandard(@Nonnull DamageSource damageType, @Nonnull List<Pair<EquipmentSlotType, EnumPlayerPart[]>> priorityTable, boolean shufflePriorityTable) {
        bindDamageSourceCustom(damageType, new StandardDamageDistribution(priorityTable, shufflePriorityTable));
    }

    @Override
    public void bindDamageSourceRandom(@Nonnull DamageSource damageType, boolean nearestFirst, boolean tryNoKill) {
        if (nearestFirst) {
            if (!tryNoKill)
                bindDamageSourceCustom(damageType, RandomDamageDistribution.NEAREST_KILL);
            else
                bindDamageSourceCustom(damageType, RandomDamageDistribution.NEAREST_NOKILL);
        } else {
            bindDamageSourceCustom(damageType, tryNoKill ? RandomDamageDistribution.ANY_NOKILL : RandomDamageDistribution.ANY_KILL);
        }
    }

    @Override
    public synchronized void bindDamageSourceCustom(@Nonnull DamageSource damageType, @Nonnull IDamageDistribution distributionTable) {
        String type = damageType.damageType;
        if (DISTRIBUTION_MAP.containsKey(type))
            FirstAid.LOGGER.info("Damage Distribution override detected for source " + damageType);
        if (distributionTable == RandomDamageDistribution.NEAREST_KILL) //This is the default
            DISTRIBUTION_MAP.remove(type);
        else
            DISTRIBUTION_MAP.put(type, distributionTable);
    }

    @Override
    public synchronized void registerHealingType(@Nonnull Item item, @Nonnull Function<ItemStack, AbstractPartHealer> factory, Function<ItemStack, Integer> applyTime) {
        if (this.HEALER_MAP.containsKey(item))
            FirstAid.LOGGER.warn("Healing type override detected for item " + item);
        this.HEALER_MAP.put(item, Pair.of(factory, applyTime));
    }

    @Nullable
    @Override
    public AbstractPartHealer getPartHealer(@Nonnull ItemStack type) {
        Pair<Function<ItemStack, AbstractPartHealer>, Function<ItemStack, Integer>> pair = this.HEALER_MAP.get(type.getItem());
        if (pair != null)
            return pair.getLeft().apply(type);
        return null;
    }

    @Override
    public Integer getPartHealingTime(@Nonnull ItemStack stack) {
        Pair<Function<ItemStack, AbstractPartHealer>, Function<ItemStack, Integer>> pair = this.HEALER_MAP.get(stack.getItem());
        if (pair != null)
            return pair.getRight().apply(stack);
        return null;
    }

    @Override
    public void registerDebuff(@Nonnull EnumDebuffSlot slot, @Nonnull IDebuffBuilder abstractBuilder) {
        DebuffBuilder builder;
        try {
            builder = (DebuffBuilder) abstractBuilder;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Builder must an instance of the default builder received via DebuffBuilderFactory!", e);
        }
        //Build the finished debuff
        FirstAid.LOGGER.debug("Building debuff from mod {} for slot {} with potion effect {}, type = {}", CommonUtils.getActiveModidSafe(), slot, builder.potionName, builder.isOnHit ? "OnHit" : "Constant");
        BooleanSupplier isEnabled;
        if (builder.isEnabledSupplier == null)
            isEnabled = () -> true;
        else
            isEnabled = builder.isEnabledSupplier;

        Preconditions.checkArgument(!builder.map.isEmpty(), "Failed to register debuff with condition has set");
        Supplier<IDebuff> debuff;
        if (builder.isOnHit) {
            debuff = () -> new OnHitDebuff(builder.potionName, builder.map, isEnabled, builder.sound);
        } else {
            Preconditions.checkArgument(builder.sound == null, "Tried to register constant debuff with sound effect.");
            debuff = () -> new ConstantDebuff(builder.potionName, builder.map, isEnabled);
        }
        registerDebuff(slot, debuff);
    }

    @Override
    public synchronized void registerDebuff(@Nonnull EnumDebuffSlot slot, @Nonnull Supplier<IDebuff> debuff) {
        if (!registrationAllowed)
            throw new IllegalStateException("Registration must take place before FMLLoadCompleteEvent");

        if (slot.playerParts.length > 1 && !(debuff instanceof SharedDebuff)) {
            this.DEBUFFS.put(slot, () -> new SharedDebuff(debuff.get(), slot));
            return;
        }

        this.DEBUFFS.put(slot, debuff);
    }

    @Nonnull
    @Override
    public IDamageDistribution getDamageDistribution(@Nonnull DamageSource source) {
        IDamageDistribution distribution = DISTRIBUTION_MAP.get(source.damageType);
        if (distribution == null)
            distribution = RandomDamageDistribution.NEAREST_KILL;
        return distribution;
    }

    @Nonnull
    @Override
    public IDebuff[] getDebuffs(@Nonnull EnumDebuffSlot slot) {
        return DEBUFFS.get(slot).stream().map(Supplier::get).toArray(IDebuff[]::new);
    }
}
