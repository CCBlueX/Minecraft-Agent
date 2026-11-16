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

@file:JvmName("Conversions")

package net.ccbluex.agent.v26_3

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec2
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.ContainerInput
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.entity.EquipmentSlot
import net.ccbluex.agent.world.entity.GameType
import net.ccbluex.agent.world.entity.MoverType
import net.ccbluex.agent.world.entity.Pose
import net.minecraft.core.BlockPos as McBlockPos
import net.minecraft.core.Direction as McDirection
import net.minecraft.world.InteractionHand as McInteractionHand
import net.minecraft.world.entity.EquipmentSlot as McEquipmentSlot
import net.minecraft.world.entity.MoverType as McMoverType
import net.minecraft.world.entity.Pose as McPose
import net.minecraft.world.entity.player.Input as McInput
import net.minecraft.world.inventory.ContainerInput as McContainerInput
import net.minecraft.world.level.GameType as McGameType
import net.minecraft.world.phys.AABB as McAABB
import net.minecraft.world.phys.BlockHitResult as McBlockHitResult
import net.minecraft.world.phys.HitResult as McHitResult
import net.minecraft.world.phys.Vec2 as McVec2
import net.minecraft.world.phys.Vec3 as McVec3

// The library's own types and the game's, both ways. Enums keep the game's order, so they convert by ordinal.

/**
 * @agent claude-opus-5-5
 */
public fun McVec3.toAgent(): Vec3 = Vec3(x, y, z)

/**
 * @agent claude-opus-5-5
 */
public fun Vec3.toMinecraft(): McVec3 = McVec3(x, y, z)

/**
 * @agent claude-opus-5-5
 */
public fun McVec2.toAgent(): Vec2 = Vec2(x, y)

/**
 * @agent claude-opus-5-5
 */
public fun Vec2.toMinecraft(): McVec2 = McVec2(x, y)

/**
 * @agent claude-opus-5-5
 */
public fun McBlockPos.toAgent(): BlockPos = BlockPos(x, y, z)

/**
 * @agent claude-opus-5-5
 */
public fun BlockPos.toMinecraft(): McBlockPos = McBlockPos(x, y, z)

/**
 * @agent claude-opus-5-5
 */
public fun McAABB.toAgent(): AABB = AABB(minX, minY, minZ, maxX, maxY, maxZ)

/**
 * @agent claude-opus-5-5
 */
public fun AABB.toMinecraft(): McAABB = McAABB(minX, minY, minZ, maxX, maxY, maxZ)

/**
 * @agent claude-opus-5-5
 */
public fun McDirection.toAgent(): Direction = Direction.entries[ordinal]

/**
 * @agent claude-opus-5-5
 */
public fun Direction.toMinecraft(): McDirection = McDirection.entries[ordinal]

/**
 * @agent claude-opus-5-5
 */
public fun McDirection.Axis.toAgent(): Direction.Axis = Direction.Axis.entries[ordinal]

/**
 * @agent claude-opus-5-5
 */
public fun Direction.Axis.toMinecraft(): McDirection.Axis = McDirection.Axis.entries[ordinal]

/**
 * @agent claude-opus-5-5
 */
public fun McInteractionHand.toAgent(): InteractionHand = InteractionHand.entries[ordinal]

/**
 * @agent claude-opus-5-5
 */
public fun InteractionHand.toMinecraft(): McInteractionHand = McInteractionHand.entries[ordinal]

/**
 * @agent claude-opus-5-5
 */
public fun McInput.toAgent(): Input = Input(forward(), backward(), left(), right(), jump(), shift(), sprint())

/**
 * @agent claude-opus-5-5
 */
public fun Input.toMinecraft(): McInput = McInput(forward, backward, left, right, jump, shift, sprint)

/**
 * @agent claude-opus-5-5
 */
public fun McEquipmentSlot.toAgent(): EquipmentSlot = EquipmentSlot.valueOf(name)

/**
 * @agent claude-opus-5-5
 */
public fun EquipmentSlot.toMinecraft(): McEquipmentSlot = McEquipmentSlot.valueOf(name)

/**
 * @agent claude-opus-5-5
 */
public fun McGameType.toAgent(): GameType = GameType.valueOf(name)

/**
 * @agent claude-opus-5-5
 */
public fun McMoverType.toAgent(): MoverType = MoverType.valueOf(name)

/**
 * @agent claude-opus-5-5
 */
public fun MoverType.toMinecraft(): McMoverType = McMoverType.valueOf(name)

/**
 * @agent claude-opus-5-5
 */
public fun McPose.toAgent(): Pose = Pose.entries.firstOrNull { it.name == name } ?: Pose.OTHER

/**
 * @agent claude-opus-5-5
 */
public fun ContainerInput.toMinecraft(): McContainerInput = McContainerInput.valueOf(name)

/**
 * @agent claude-opus-5-5
 */
public fun McBlockHitResult.toAgent(): BlockHitResult = if (type == McHitResult.Type.MISS) {
    BlockHitResult.miss(location.toAgent(), direction.toAgent(), blockPos.toAgent())
} else {
    BlockHitResult(location.toAgent(), direction.toAgent(), blockPos.toAgent(), isInside)
}

/**
 * @agent claude-opus-5-5
 */
public fun BlockHitResult.toMinecraft(): McBlockHitResult = if (type == net.ccbluex.agent.world.HitResult.Type.MISS) {
    McBlockHitResult.miss(location.toMinecraft(), direction.toMinecraft(), blockPos.toMinecraft())
} else {
    McBlockHitResult(location.toMinecraft(), direction.toMinecraft(), blockPos.toMinecraft(), isInside)
}
