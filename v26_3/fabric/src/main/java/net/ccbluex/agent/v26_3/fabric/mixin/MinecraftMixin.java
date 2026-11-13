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

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.ccbluex.agent.v26_3.fabric.ClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {

    /**
     * Joining, changing dimension and leaving all set the level here.
     */
    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("HEAD"))
    private void agent$levelChanged(ClientLevel level, boolean stopSound, CallbackInfo info) {
        ClientHooks.levelChanged(level);
    }

    @Inject(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;sendChanges()V", shift = At.Shift.AFTER)
    )
    private void agent$movementSent(CallbackInfo info) {
        ClientHooks.afterMovementSent();
    }

    /**
     * With the attack key up, vanilla stops any dig every tick, also the one a navigation runs.
     */
    @WrapWithCondition(
        method = "continueAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;stopDestroyBlock()V")
    )
    private boolean agent$keepDestroying(MultiPlayerGameMode gameMode) {
        return !ClientHooks.keepsDestroying();
    }

}
