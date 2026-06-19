/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package fr.neatmonster.nocheatplus.checks.moving.envelope;

import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;


public final class PhysicsGroundResolver {
    /** {@link net.minecraft.world.level.block.SlimeBlock#stepOn} only runs while {@code |y| < 0.1}. */
    public static final double SLIME_STEP_ON_MAX_Y = 0.1;

    private PhysicsGroundResolver() {}

    public static void clearClientPacketGround(final PlayerMoveData move) {
        move.hasClientFromOnGround = false;
        move.hasClientToOnGround = false;
        move.hasClientToHorizontalCollision = false;
    }

    public static void bindClientGround(final PlayerMoveData move,
            final boolean fromOnGround, final boolean hasFrom,
            final boolean toOnGround, final boolean hasTo,
            final boolean horizontalCollision, final boolean hasHorizontalCollision) {
        move.hasClientFromOnGround = hasFrom;
        move.clientFromOnGround = fromOnGround;
        move.hasClientToOnGround = hasTo;
        move.clientToOnGround = toOnGround;
        move.hasClientToHorizontalCollision = hasHorizontalCollision;
        move.clientToHorizontalCollision = horizontalCollision;
    }



    public static boolean resolvePhysicsOnGround(final PlayerLocation from,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove,
            final boolean forceSetOnGround, final boolean forceSetOffGround) {
        if (forceSetOffGround) {
            return false;
        }

        if (forceSetOnGround) {
            return true;
        }

        if (thisMove.hasClientFromOnGround) {
            return thisMove.clientFromOnGround;
        }

        return collisionDerivedOnGround(from, thisMove, lastMove);
    }

    public static boolean resolveEnvelopeFromOnGround(final PlayerMoveData thisMove,
            final boolean collisionFromOnGround) {
        if (thisMove.hasClientFromOnGround) {
            return thisMove.clientFromOnGround;
        }

        return collisionFromOnGround;
    }

    public static boolean resolveEnvelopeToOnGround(final PlayerMoveData thisMove,
            final boolean collisionToOnGround) {
        if (thisMove.hasClientToOnGround) {
            return thisMove.clientToOnGround;
        }

        return collisionToOnGround;
    }

    private static boolean collisionDerivedOnGround(final PlayerLocation from,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        return from.isOnGround()
                || lastMove.toIsValid && lastMove.yDistance <= 0.0 && lastMove.from.onGround
                || lastMove.yDistance < 0.0 && thisMove.fromLostGround;
    }
}