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
import net.ccbluex.agent.simulation.SimulatedPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * From LiquidBounce: [MixinClientLevel.java](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinClientLevel.java#L55-L128)
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {

    /**
     * Block and section updates from the server both end here, on the client thread.
     */
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void agent$blockConfirmed(BlockPos pos, BlockState state, int flags, CallbackInfo info) {
        ClientHooks.blockConfirmed((ClientLevel) (Object) this, pos, state);
    }

    // A simulated player walks through the real level; what blocks show or play for it stays unseen and unheard.
    // Sounds with a source entity other than the local player are already silent on a client, and game events do
    // nothing there.

    @Inject(method = "doAddParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void agent$hideSimulationParticles(CallbackInfo info) {
        cancelWhileSimulating(info);
    }

    @Inject(method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V", at = @At("HEAD"), cancellable = true)
    private void agent$muteSimulationSounds(CallbackInfo info) {
        cancelWhileSimulating(info);
    }

    @Inject(method = "playLocalSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V", at = @At("HEAD"), cancellable = true)
    private void agent$muteSimulationEntitySounds(CallbackInfo info) {
        cancelWhileSimulating(info);
    }

    @Inject(method = "playPlayerSound", at = @At("HEAD"), cancellable = true)
    private void agent$muteSimulationPlayerSounds(CallbackInfo info) {
        cancelWhileSimulating(info);
    }

    @Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
    private void agent$hideSimulationLevelEvents(CallbackInfo info) {
        cancelWhileSimulating(info);
    }

    private static void cancelWhileSimulating(CallbackInfo info) {
        if (SimulatedPlayer.isTicking()) {
            info.cancel();
        }
    }

}
