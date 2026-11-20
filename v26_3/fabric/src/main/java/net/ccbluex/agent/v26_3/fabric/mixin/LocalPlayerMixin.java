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
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs navigation between reading the keys and moving, so its keys and rotation move the player this tick, and sends
 * the position of a tick a ledge sneak stopped the player in.
 *
 * @agent claude-opus-5-5
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {

    @Shadow
    public ClientInput input;

    @Inject(
        method = "aiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER)
    )
    private void agent$navigate(CallbackInfo info) {
        ClientHooks.afterInputTick((LocalPlayer) (Object) this, input);
    }

    /**
     * A sneak holding the player at a ledge can take its whole move while the player keeps its speed. Grim takes a
     * tick without a position for one the player hardly moved in and predicts the next move without that speed.
     */
    @ModifyVariable(method = "sendPosition", at = @At("STORE"), name = "move")
    private boolean agent$sendPositionHeldAtLedge(boolean move) {
        var player = (LocalPlayer) (Object) this;
        return move || player.onGround() && player.isShiftKeyDown() && player.getDeltaMovement().horizontalDistanceSqr() > 0.0;
    }

}
