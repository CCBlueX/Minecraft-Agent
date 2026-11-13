/*
 * This file is part of Minecraft Agent (https://github.com/CCBlueX/Minecraft-Agent)
 *
 * Copyright (c) 2026 CCBlueX
 *
 * Minecraft Agent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Minecraft Agent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Minecraft Agent. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.agent.v26_3.fabric.mixin;

import net.ccbluex.agent.v26_3.fabric.ClientHooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Each handler is first called on the network thread, which hands the packet over and throws, so only the call on
// the client thread gets to the end.
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void agent$positionCorrected(ClientboundPlayerPositionPacket packet, CallbackInfo info) {
        ClientHooks.positionCorrected();
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void agent$chunkLoaded(ClientboundLevelChunkWithLightPacket packet, CallbackInfo info) {
        ClientHooks.columnChanged(packet.x(), packet.z());
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("TAIL"))
    private void agent$chunkForgotten(ClientboundForgetLevelChunkPacket packet, CallbackInfo info) {
        ClientHooks.columnChanged(packet.pos().x(), packet.pos().z());
    }

}
