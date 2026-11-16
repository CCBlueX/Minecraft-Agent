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

import net.ccbluex.agent.navigation.Listener
import net.ccbluex.agent.navigation.Navigator
import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.v26_3.defineGameTypes

/**
 * Runs a [Navigator] for the local player: while it holds a walk ([Navigator.moveTo], [Navigator.follow],
 * [net.ccbluex.agent.navigation.walkTo]), every player tick right after the input read the keys and before the player
 * moves, it goes on with the walk; once the walk ended, the body lets go of the player. Call it on the client thread.
 *
 * It walks the player of the account the game plays as when it is made. In a game that runs several accounts at
 * once, make it with the account's game loaded; a walk never moves to another account. An idle navigation whose
 * account stopped playing takes up the account that plays now, as after an account switch.
 *
 * Block updates, setbacks and level changes of its account reach the navigator for as long as this navigation is kept.
 *
 * @agent claude-opus-5-5
 */
public class ClientNavigation @JvmOverloads constructor(
    public val body: ClientBody = ClientBody(),
    options: Options = Options(),
    listener: Listener = Listener.NONE,
) {

    init {
        defineGameTypes()
    }

    public val navigator: Navigator = Navigator(body, options, listener)

    /**
     * Read every tick; a new copy takes effect at once.
     */
    public var options: Options by navigator::options

    private var walked = false

    /**
     * The name of the account whose player this navigation walks.
     */
    internal var account: String = currentAccount()

    /**
     * Neither walking nor about to let go of the player after a walk.
     */
    internal val isIdle: Boolean
        get() = !walked && !navigator.isInProgress

    init {
        register(this)
    }

    /**
     * Goes on with the held walk; true if it ticked, so the body has a tick to apply.
     */
    internal fun tick(): Boolean {
        if (navigator.isInProgress) {
            walked = true
            navigator.tick()
            return true
        }
        if (walked) {
            walked = false
            navigator.stop()
            body.release()
        }
        return false
    }
}
