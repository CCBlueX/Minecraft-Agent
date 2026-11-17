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

package net.ccbluex.agent.v26_3.fabric

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.copy
import net.ccbluex.agent.math.turned
import net.ccbluex.agent.navigation.BodyTraits
import net.ccbluex.agent.navigation.LedgeSneak
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.Teleporter
import net.ccbluex.agent.navigation.Turning
import net.ccbluex.agent.simulation.PlayerSimulation
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.simulation.SimulatedPlayer
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.v26_3.fabric.mixin.ClientInputAccessor
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.ContainerInput
import net.ccbluex.agent.world.ContainerMenu
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.Player
import net.minecraft.client.Minecraft
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.phys.EntityHitResult
import kotlin.math.abs
import kotlin.math.roundToInt
import net.minecraft.world.inventory.ContainerInput as McContainerInput

/**
 * The local player as an [AgentBody], moved with vanilla means only: the movement keys of the tick, the player's own
 * rotation, the hotbar, and [net.minecraft.client.multiplayer.MultiPlayerGameMode] for digging, using items,
 * attacking and clicking in menus.
 *
 * The rotation the walk faces ([LookPurpose.MOVEMENT]), aims with ([LookPurpose.AIM]) or flicks to
 * ([LookPurpose.STEER]) is turned to as [turning] paces it, never faster than a person turns
 * ([Turning.humanPaced]), in whole mouse steps; STEER while gliding is taken at once. The keys pressed for the old
 * view are turned onto the new rotation. The camera looks there too: the way the
 * walk goes, or at the block it places or breaks.
 *
 * While the user turns the mouse, or something outside the navigation owns the rotation
 * ([isRotationOverridden]), the camera is theirs: the player moves and the movement packet goes out with the
 * rotation, and once the packet is out the player looks through the camera again. Look-ahead
 * ([LookPurpose.VIEW]) turns the camera only while the rotation is owned elsewhere, so it looks where it goes rather
 * than where that rotation points.
 *
 * [ClientNavigation] drives it. A client with its own rotation and input handling overrides [look], [press] and the
 * rotation members, turns through [pace] and calls the navigator from its own input hook instead. When its rotation
 * system paces the turns, it sets [Turning.instant], so they are not paced twice.
 *
 * @agent claude-opus-5-5
 */
public open class ClientBody @JvmOverloads constructor(turning: Turning = Turning.eased()) : AgentBody {

    /**
     * How movement, aim and steering rotations are turned to; the default eases in as a hand does, and
     * [Turning.instant] leaves the pace to the host's own rotation system.
     */
    public var turning: Turning = turning
        set(value) {
            field = value
            paced = Turning.humanPaced(value)
        }

    private var paced = Turning.humanPaced(turning)

    private val minecraft: Minecraft
        get() = Minecraft.getInstance()

    override val player: Player
        get() = localPlayer.toAgent()

    private val localPlayer: LocalPlayer
        get() = checkNotNull(minecraft.player) { "There is no local player" }

    /**
     * Held at ledges by a sneak in the keys of [press] ([stayOnGroundSurface]).
     */
    override var safeWalk: Boolean = false

    private val ledge = LedgeSneak()

    /**
     * Simulations of the local player are kept from touching the level by this module's mixins.
     */
    override val traits: BodyTraits = BodyTraits(jumpClimbsLadders = true, exactSimulation = true)

    /**
     * None by default: a client teleport also needs the host to rewrite its own movement packets while
     * [net.ccbluex.agent.navigation.teleport.Teleports.rewritesMovement] holds and to report setbacks, so a host that
     * does both overrides this with a [Teleporter] sending its position packets.
     */
    override val teleporter: Teleporter?
        get() = null

    override val latencyMillis: Int
        get() {
            val player = localPlayer
            return player.connection.getPlayerInfo(player.uuid)?.latency ?: 0
        }

    override val sentRotation: ViewRotation
        get() = lastSentRotation ?: player.let { ViewRotation(it.yRot, it.xRot) }

    /**
     * Whether something outside the navigation owns the rotation this tick, such as a client's own aiming feature; the
     * camera then looks the way the navigation goes instead of following it.
     */
    public open val isRotationOverridden: Boolean
        get() = false

    /**
     * The user turned the mouse lately, or the camera follows the navigation's own rotation, so look-ahead keeps out.
     */
    override val isViewTakenOver: Boolean
        get() = isMouseTurning() || !isRotationOverridden

    /**
     * One mouse count at the current sensitivity, as `MouseHandler.turnPlayer` and `Entity.turn` turn.
     */
    override val viewStep: Float
        get() {
            val sensitivity = minecraft.options.sensitivity().get() * SENSITIVITY_SCALE + SENSITIVITY_BASE
            return (sensitivity * sensitivity * sensitivity * MOUSE_FACTOR * DEGREES_PER_COUNT).toFloat()
        }

    private var pressed: Input? = null
    private var pressedExact = false
    private var pressedYRot = 0f
    private var ownSlot = NO_SLOT

    /**
     * The rotation the player moves and is sent with, while it differs from the camera.
     */
    private var rotation: ViewRotation? = null

    /**
     * This tick's [rotation], once a movement or aim rotation was asked for.
     */
    private var nextRotation: ViewRotation? = null
    private var nextPurpose: LookPurpose? = null

    /**
     * The camera, put back once the movement packet of the tick is out.
     */
    private var camera: ViewRotation? = null

    override val movementYRot: Float
        get() = (nextRotation ?: rotation)?.yRot ?: player.yRot

    /**
     * Whether a dig runs that vanilla's own attack handling must not cancel.
     */
    internal var isDestroying: Boolean = false
        private set

    override fun press(input: Input, exact: Boolean) {
        pressed = stayOnGroundSurface(input)
        pressedExact = exact
        pressedYRot = inputYRot
    }

    /**
     * [input] with a sneak at a ledge while [safeWalk] holds the player there; a client with its own [press] puts
     * its keys through this.
     */
    protected fun stayOnGroundSurface(input: Input): Input {
        if (!safeWalk) {
            ledge.reset()
            return input
        }
        return ledge.keys(this, input)
    }

    override fun look(rotation: ViewRotation, purpose: LookPurpose) {
        when (purpose) {
            LookPurpose.VIEW -> {
                val player = localPlayer
                player.yRot = rotation.yRot
                player.xRot = rotation.xRot.coerceIn(MIN_X_ROT, MAX_X_ROT)
            }

            LookPurpose.STEER -> {
                val from = this.rotation ?: player.let { ViewRotation(it.yRot, it.xRot) }
                nextRotation = if (player.isFallFlying) {
                    ViewRotation(
                        from.yRot + Mth.wrapDegrees(rotation.yRot - from.yRot),
                        rotation.xRot.coerceIn(MIN_X_ROT, MAX_X_ROT),
                    )
                } else {
                    pace(from, rotation, purpose)
                }
                nextPurpose = purpose
            }

            LookPurpose.MOVEMENT, LookPurpose.AIM -> if (nextPurpose == null || nextPurpose == LookPurpose.MOVEMENT) {
                nextRotation = pace(this.rotation ?: player.let { ViewRotation(it.yRot, it.xRot) }, rotation, purpose)
                nextPurpose = purpose
            }
        }
    }

    override fun selectHotbarSlot(slot: Int) {
        val inventory = localPlayer.inventory
        if (ownSlot == NO_SLOT) {
            ownSlot = inventory.selectedSlot
        }
        inventory.selectedSlot = slot
    }

    override fun releaseHotbarSlot() {
        if (ownSlot == NO_SLOT) {
            return
        }
        minecraft.player?.inventory?.selectedSlot = ownSlot
        ownSlot = NO_SLOT
    }

    /**
     * Like a click on the block in `Minecraft.startAttack`: starts digging, swings and punches.
     */
    override fun startDestroy(pos: BlockPos, face: Direction): Boolean {
        val gameMode = minecraft.gameMode ?: return false
        val started = gameMode.startDestroyBlock(pos.toMinecraft(), face.toMinecraft())
        isDestroying = gameMode.isDestroying
        swingAttack()
        return started
    }

    /**
     * Like holding the attack key in `Minecraft.continueAttack`.
     */
    override fun continueDestroy(pos: BlockPos, face: Direction): Boolean {
        val gameMode = minecraft.gameMode ?: return false
        isDestroying = true
        val continued = gameMode.continueDestroyBlock(pos.toMinecraft(), face.toMinecraft())
        if (continued) {
            swingAttack()
        }
        return continued
    }

    override fun stopDestroy() {
        isDestroying = false
        minecraft.gameMode?.takeIf { it.isDestroying }?.stopDestroyBlock()
    }

    /**
     * The block branch of `Minecraft.startUseItem`; true if the use went through.
     */
    override fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean {
        val gameMode = minecraft.gameMode ?: return false
        val player = localPlayer
        val hand = hand.toMinecraft()
        val stack = player.getItemInHand(hand)
        val count = stack.count
        // The server places facing the rotation of the last movement packet; the client predicts the same block
        val result = facing(player, sentRotation) { gameMode.useItemOn(player, hand, hit.toMinecraft()) }
        if (result !is InteractionResult.Success) {
            return false
        }

        if (result.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            player.swing(hand, stack.interactAnimation, false)
            if (!stack.isEmpty && (stack.count != count || player.hasInfiniteMaterials())) {
                player.itemUsed(hand)
            }
        }
        return true
    }

    /**
     * The item branch of `Minecraft.startUseItem`; true if the use went through.
     */
    override fun useItem(hand: InteractionHand): Boolean {
        val gameMode = minecraft.gameMode ?: return false
        val player = localPlayer
        val hand = hand.toMinecraft()
        val stack = player.getItemInHand(hand)
        if (stack.isEmpty) {
            return false
        }

        // The use packet carries the rotation, which has to be the one the movement packet of the tick sends
        val result = facing(player, nextRotation ?: rotation) { gameMode.useItem(player, hand) }
        if (result !is InteractionResult.Success) {
            return false
        }

        if (result.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            player.swing(hand, stack.interactAnimation, false)
        }
        player.itemUsed(hand)
        // Vanilla lets go of a use whose key is up the next tick; an eat or a raised shield has to last
        keepUsing(this, player.isUsingItem)
        return true
    }

    override fun attack(target: Entity) {
        val gameMode = minecraft.gameMode ?: return
        gameMode.attack(localPlayer, target.toMinecraft())
        swingAttack()
    }

    override fun stab() {
        val player = localPlayer
        val stack = player.mainHandItem
        val weapon = stack.get(DataComponents.PIERCING_WEAPON) ?: return
        minecraft.gameMode?.piercingAttack(stack.attackAnimation, weapon)
    }

    override fun swing(hand: InteractionHand) {
        val player = localPlayer
        val hand = hand.toMinecraft()
        player.swing(hand, player.getItemInHand(hand).attackAnimation, false)
    }

    override fun startUsingItem(hand: InteractionHand): Boolean = useItem(hand)

    override fun releaseUsingItem() {
        keepUsing(this, false)
        minecraft.gameMode?.releaseUsingItem(localPlayer)
    }

    override val isUsingItem: Boolean
        get() = localPlayer.isUsingItem

    override val attackStrength: Float
        get() = localPlayer.getAttackStrengthScale(0.5f)

    override val minAttackReach: Double
        get() = 0.0

    override val maxAttackReach: Double
        get() = localPlayer.entityInteractionRange()

    override val isCombat1_8: Boolean
        get() = false

    override fun swapWithHotbar(inventorySlot: Int, hotbarSlot: Int) {
        click(menuSlot(inventorySlot), hotbarSlot, McContainerInput.SWAP)
    }

    override fun swapWithOffhand(inventorySlot: Int) {
        click(menuSlot(inventorySlot), Inventory.SLOT_OFFHAND, McContainerInput.SWAP)
    }

    override fun drop(inventorySlot: Int, wholeStack: Boolean) {
        click(menuSlot(inventorySlot), if (wholeStack) 1 else 0, McContainerInput.THROW)
    }

    override val containerMenu: ContainerMenu?
        get() = localPlayer.containerMenu.takeIf { it !is InventoryMenu }?.toAgent()

    override fun takeFromContainer(containerSlot: Int) {
        clickContainer(containerSlot, 0, ContainerInput.QUICK_MOVE)
    }

    override fun clickContainer(containerSlot: Int, button: Int, type: ContainerInput) {
        val player = localPlayer
        val menu = player.containerMenu.takeIf { it !is InventoryMenu } ?: return
        minecraft.gameMode?.handleContainerInput(menu.containerId, containerSlot, button, type.toMinecraft(), player)
    }

    override val inventoryMenu: ContainerMenu
        get() = localPlayer.inventoryMenu.toAgent()

    override fun clickInventory(menuSlot: Int, button: Int, type: ContainerInput) {
        click(menuSlot, button, type.toMinecraft())
    }

    private fun click(menuSlot: Int, button: Int, type: McContainerInput) {
        val player = localPlayer
        minecraft.gameMode?.handleContainerInput(player.inventoryMenu.containerId, menuSlot, button, type, player)
    }

    override fun closeContainer() {
        localPlayer.closeContainer()
    }

    /**
     * The [InventoryMenu] slot of [inventorySlot]: hotbar 36 to 44, armor 8 (feet) to 5 (head), offhand 45.
     */
    private fun menuSlot(inventorySlot: Int): Int = when (inventorySlot) {
        in 0..8 -> InventoryMenu.USE_ROW_SLOT_START + inventorySlot
        in 9..35 -> inventorySlot
        in 36..39 -> InventoryMenu.ARMOR_SLOT_END - 1 - (inventorySlot - 36)
        else -> InventoryMenu.SHIELD_SLOT
    }

    /**
     * The entity branch of `Minecraft.startUseItem`, at the point of [entity] the rotation the server has meets.
     */
    override fun interact(entity: Entity, hand: InteractionHand): Boolean {
        val gameMode = minecraft.gameMode ?: return false
        val player = localPlayer
        val target = entity.toMinecraft()
        val game = hand.toMinecraft()
        val eye = player.eyePosition
        val box = target.boundingBox.inflate(target.pickRadius.toDouble())
        val reach = sentRotation.viewVector.toMinecraft().scale(player.entityInteractionRange())
        val point = box.clip(eye, eye.add(reach)).orElse(box.center)
        val result = gameMode.interact(player, target, EntityHitResult(target, point), game)
        if (result !is InteractionResult.Success) {
            return false
        }
        if (result.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            player.swing(game, player.getItemInHand(game).attackAnimation, false)
        }
        return true
    }

    /**
     * After the navigator ran in the player's tick, before the player moves: puts the player on the rotation of the
     * tick, or turns it back towards the camera, and puts the keys into [input], turned onto that rotation. The camera
     * keeps that rotation unless it is the user's or owned elsewhere.
     */
    internal fun applyTick(player: LocalPlayer, input: ClientInput) {
        val view = ViewRotation(player.yRot, player.xRot)
        val last = this.rotation
        val rotation = nextRotation
            ?: last?.let { pace(it, view, LookPurpose.MOVEMENT) }?.takeUnless { it.reaches(view) }
        val viewFollows = rotation != null && !isMouseTurning() && !isRotationOverridden
        this.rotation = rotation.takeUnless { viewFollows }
        nextRotation = null
        nextPurpose = null
        if (rotation == null && last != null) {
            alignCamera(player, last.yRot)
        }
        if (rotation != null) {
            if (!viewFollows) {
                camera = view
            }
            player.yRot = rotation.yRot
            player.xRot = rotation.xRot
            if (viewFollows) {
                // The camera moves on from where it was by less than a whole turn, as a mouse turn does
                player.yRotO += Math.round((player.yRot - player.yRotO) / FULL_TURN) * FULL_TURN
            }
        }

        val pressed = pressed
        this.pressed = null
        val keys = when {
            // Sprint left unset leaves it to the player's own key, unless the keys are exact
            pressed != null -> pressed.turned(pressedYRot, player.yRot)
                .copy(sprint = pressed.sprint || !pressedExact && input.keyPresses.sprint())

            rotation != null -> input.keyPresses.toAgent().turned(view.yRot, player.yRot)
            else -> return
        }
        input.keyPresses = keys.toMinecraft()
        (input as ClientInputAccessor).agentSetMoveVector(SimulatedInput(keys).moveVector.toMinecraft())
    }

    override fun simulate(input: SimulatedInput, yRot: Float): PlayerSimulation.Steered =
        SimulatedPlayer.fromPlayer(localPlayer, input, traits.navigatesInMovementTick, simulationHooks)
            .also { it.yRot = yRot }

    /**
     * Back from a rotation of its own, the camera's yaw is moved by whole turns next to the yaw sent last, which looks
     * the same; the server would take a jump of a whole turn for a turn no player makes.
     */
    private fun alignCamera(player: LocalPlayer, sentYRot: Float) {
        val turns = Math.round((sentYRot - player.yRot) / FULL_TURN) * FULL_TURN
        player.yRot += turns
        player.yRotO += turns
    }

    /**
     * The server set the rotation along with the position; the next turn starts from there.
     */
    internal fun positionCorrected() {
        rotation = null
        nextRotation = null
        nextPurpose = null
    }

    /**
     * Once the movement packet of the tick is out: the player looks through the camera again.
     */
    internal fun restoreCamera() {
        val camera = camera ?: return
        this.camera = null
        minecraft.player?.let {
            it.yRot = camera.yRot
            it.xRot = camera.xRot
        }
    }

    /**
     * Lets go of everything held, for when navigation stops.
     */
    internal fun release() {
        pressed = null
        restoreCamera()
        val last = rotation
        rotation = null
        nextRotation = null
        nextPurpose = null
        val player = minecraft.player
        if (last != null && player != null) {
            alignCamera(player, last.yRot)
        }
        safeWalk = false
        ledge.reset()
        if (isDestroying) {
            stopDestroy()
        }
        releaseHotbarSlot()
        keepUsing(this, false)
    }

    /**
     * Runs [action] with [player] turned to [rotation] when there is one, and turns it back.
     */
    private inline fun <T> facing(player: LocalPlayer, rotation: ViewRotation?, action: () -> T): T {
        if (rotation == null) {
            return action()
        }
        val yRot = player.yRot
        val xRot = player.xRot
        player.yRot = rotation.yRot
        player.xRot = rotation.xRot
        try {
            return action()
        } finally {
            player.yRot = yRot
            player.xRot = xRot
        }
    }

    /**
     * [from] turned towards [to] as [turning] paces it, never faster than a person turns, in whole [viewStep]s.
     */
    protected fun pace(from: ViewRotation, to: ViewRotation, purpose: LookPurpose): ViewRotation {
        val turned = paced.turn(from, to, purpose)
        val step = viewStep
        val yRot = quantize(Mth.wrapDegrees(turned.yRot - from.yRot), step)
        val xRot = quantize(turned.xRot - from.xRot, step)
        return ViewRotation(from.yRot + yRot, (from.xRot + xRot).coerceIn(MIN_X_ROT, MAX_X_ROT))
    }

    /**
     * Whether this is [other] within half a [viewStep], which turns cannot get any closer.
     */
    private fun ViewRotation.reaches(other: ViewRotation): Boolean {
        val tolerance = viewStep / 2f + EPSILON
        return abs(Mth.wrapDegrees(yRot - other.yRot)) <= tolerance && abs(xRot - other.xRot) <= tolerance
    }

    /**
     * [value] in whole [step]s; any value when [step] is 0.
     */
    private fun quantize(value: Float, step: Float) = if (step > 0f) (value / step).roundToInt() * step else value

    private fun swingAttack() {
        val player = localPlayer
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
        player.connection.send(ServerboundPunchPacket.INSTANCE)
    }

    private companion object {
        const val NO_SLOT = -1
        const val MIN_X_ROT = -90f
        const val MAX_X_ROT = 90f
        const val SENSITIVITY_SCALE = 0.6
        const val SENSITIVITY_BASE = 0.2
        const val MOUSE_FACTOR = 8.0
        const val DEGREES_PER_COUNT = 0.15
        const val EPSILON = 1.0E-4f
        const val FULL_TURN = 360f
    }

}
