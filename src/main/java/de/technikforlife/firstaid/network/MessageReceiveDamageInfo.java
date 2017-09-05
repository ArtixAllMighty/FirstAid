package de.technikforlife.firstaid.network;

import de.technikforlife.firstaid.client.GuiApplyHealthItem;
import de.technikforlife.firstaid.damagesystem.PlayerDamageModel;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class MessageReceiveDamageInfo implements IMessage {
    private NBTTagCompound playerDamageModel;

    public MessageReceiveDamageInfo() {}

    public MessageReceiveDamageInfo(PlayerDamageModel model) {
        this.playerDamageModel = model.serializeNBT();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerDamageModel = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, playerDamageModel);
    }

    public static class Handler implements IMessageHandler<MessageReceiveDamageInfo, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(MessageReceiveDamageInfo message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                PlayerDamageModel damageModel = new PlayerDamageModel();
                damageModel.deserializeNBT(message.playerDamageModel);
                if (GuiApplyHealthItem.INSTANCE != null) //GUI already closed
                    GuiApplyHealthItem.INSTANCE.onReceiveData(damageModel);
            });
            return null;
        }
    }
}
