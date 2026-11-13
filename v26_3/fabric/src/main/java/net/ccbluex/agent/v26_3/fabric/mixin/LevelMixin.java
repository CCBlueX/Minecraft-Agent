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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * From LiquidBounce: [MixinLevel.java](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinLevel.java#L37-L81)
 */
@Mixin(Level.class)
abstract class LevelMixin {

    /**
     * A simulated player walks through the real level: pressure plates, farmland and the like stay as they are.
     */
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void agent$keepLevelFromSimulation(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> info) {
        if (SimulatedPlayer.isTicking()) {
            info.setReturnValue(false);
        }
    }

    /**
     * Every block change of the client level, predicted or from the server. ClientLevel's override passes through
     * here too, and so do server updates, which call the Level method directly.
     */
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void agent$blockChanged(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValueZ() && (Object) this instanceof ClientLevel level) {
            ClientHooks.blockChanged(level, pos);
        }
    }

}
