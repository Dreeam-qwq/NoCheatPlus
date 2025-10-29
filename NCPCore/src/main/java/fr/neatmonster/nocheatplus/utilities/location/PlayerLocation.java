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
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.utilities.location;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.compat.bukkit.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.components.registry.event.IHandle;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.collision.CollisionUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;

/**
 * Lots of content for a location a player is supposed to be at. Constructors
 * for convenient use.
 */
public class PlayerLocation extends RichEntityLocation {

    // "Heavy" object members that need to be set to null on cleanup. //

    /** The player. */
    private Player player = null;


    /**
     * Instantiates a new player location.
     *
     * @param mcAccess
     *            the mc access
     * @param blockCache
     *            BlockCache instance, may be null.
     */
    public PlayerLocation(final IHandle<MCAccess> mcAccess, final BlockCache blockCache) {
        super(mcAccess, blockCache);
    }
    
    /**
     * Gets the player.
     *
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Straw-man method to account for this specific bug: <a href="https://bugs.mojang.com/browse/MC-2404">...</a>
     * Should not be used outside its intended context (sneaking on edges), or if vanilla uses it.
     */
    public boolean isAboveGround() {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        // TODO / NOTE: getFallDistance() or noFallFallDistance here? We'll have to look out for potential abuses (if there's room for any).
        double yBelow = player.getFallDistance() - cc.sfStepHeight; // Technically, the operands where inverted in 1.20.5 (subsequently, the addition to minY was inverted to a subtraction)
        double extraExpansion = pData.getClientVersion().isHigherThan(ClientVersion.V_1_20_3) ? 9.999999747378752E-6D : 0.0; // Introduced in 1.20.6 with the function revision.
        double[] AABB = new double[]{minX, minY + yBelow - extraExpansion, minZ, maxX, minY, maxZ}; // Skip using maxY as we do not care of the top of the box here.
        return  isOnGround() 
                // This fix was introduced in 1.16.2
                || pData.getClientVersion().isAtLeast(ClientVersion.V_1_16_2)
                && (
                    player.getFallDistance() < cc.sfStepHeight && !CollisionUtil.isEmpty(blockCache, player, AABB)
                )
            ;
    }
    
    /**
     * From {@code TridentItem.java}.<br>
     * Gets the riptiding velocity. Not context-aware.
     *
     * @return A Vector containing the riptiding velocity's components (x,y,z).
     */
    public Vector getRiptideVelocity(boolean onGround) {
        // Only players are allowed to riptide (hence why this is in PlayerLocation and not RichEntity).
        final IPlayerData pData = DataManager.getPlayerData(player);
        if (pData.getClientVersion().isLowerThan(ClientVersion.V_1_13)) {
            // Just to be sure.
            return new Vector();
        }
        final double RiptideLevel = BridgeEnchant.getRiptideLevel(player);
        if (RiptideLevel > 0.0) {
            float x = -TrigUtil.sin(getYaw() * TrigUtil.toRadians) * TrigUtil.cos(getPitch() * TrigUtil.toRadians);
            float y = -TrigUtil.sin(getPitch() * TrigUtil.toRadians);
            float z = TrigUtil.cos(getYaw() * TrigUtil.toRadians) * TrigUtil.cos(getPitch() * TrigUtil.toRadians);
            float distance = MathUtil.sqrt(x*x + y*y + z*z);
            float force = 3.0f * ((1.0f + (float) RiptideLevel) / 4.0f);
            x *= force / distance;
            y *= force / distance;
            z *= force / distance;
            if (onGround) {
                // If on ground, the game calls both move() and push().
                // The player is moved by 1.2 blocks from the ground the instant they release the trident
                // This is NOT velocity, but a direct move of the player’s position upward, ignoring physics (the travel function isn't called), almost like a teleport offset.
                // Because the move function is called, this offset is collision-aware.
                double offset = 1.2;
                Vector offsetVector = collide(new Vector(0.0, offset, 0.0), true, getBoundingBox());
                double clampedOffset = offsetVector.getY();
                return new Vector(x, y + clampedOffset, z);
            }
            return new Vector(x, y, z);
        }
        return new Vector();
    }
    
    /**
     * From {@code EntityHuman.java}. <br>
     * Up to 1.14, this method was contained in the move() function in {@code Entity.java}. In 1.14, it was decoupled from it and put in its own method but still in the Entity class. 
     * In 1.15 it was finally moved to the EntityHuman class without any change to its logic.<br>
     * In 1.20.5, its logic was slightly revised.
     * <hr><br>
     * <p>This function modifies the player's speed along the X and Z axes to keep them from moving over 
     * the edge of a block. It assumes the player is shifting (The game uses the isShiftKeyDown() method here!), on the ground, not flying, and has a downward or
     * no vertical movement.</p>
     *
     * <p>The function checks if the passed speed Vector would place the player over empty space (indicating an 
     * edge). If so, speed gets reduced in small steps of 0.05 units until the speed is considered to be safe or reaches zero, to prevent falling off.
     * </p>
     * 
     *<hr><br>
     * Note that -in vanilla- this check uses a copy of the current speed, not the original one, resulting in speed being hidden in certain cases.
     *
     * @param vector The movement vector that may be modified to prevent falling off edges.
     * 
     * @return The adjusted movement vector.
     */
    public Vector maybeBackOffFromEdge(Vector vector) {
        // Only players are capable of crouching (hence why this is in PlayerLocation and not RichEntity).
        final IPlayerData pData = DataManager.getPlayerData(player);
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        double xDistance = vector.getX();
        double zDistance = vector.getZ();
        /** Parameter for searching for collisions below */
        double yBelow = pData.getClientVersion().isAtLeast(ClientVersion.V_1_11) ? -cc.sfStepHeight : -1 + CollisionUtil.COLLISION_EPSILON;
        
        // LEGACY UP TO 1.15
        if (pData.getClientVersion().isLowerThan(ClientVersion.V_1_20_6)) { // 1.20.6 and .5 have the same protocol version, so we cannot distinguish.
            // Move AABB alongside the X axis.
            double[] offsetAABB_X = new double[]{minX + xDistance, minY + yBelow, minZ, maxX + xDistance, minY, maxZ}; // Skip using maxY, as we do not care of the top of the box in this case.
            while (xDistance != 0.0 && CollisionUtil.isEmpty(blockCache, player, offsetAABB_X)) {
                if (xDistance < 0.05 && xDistance >= -0.05) {
                    xDistance = 0.0;
                } 
                else if (xDistance > 0.0) {
                    xDistance -= 0.05;
                } 
                else xDistance += 0.05;
                // Update the AABB with each iteration.
                offsetAABB_X = new double[]{minX + xDistance, minY + yBelow, minZ, maxX + xDistance, minY, maxZ};
            }
            
            // Move AABB alongside the Z axis.
            double[] offsetAABB_Z = new double[]{minX, minY + yBelow, minZ + zDistance, maxX, minY, maxZ + zDistance};
            while (zDistance != 0.0 && CollisionUtil.isEmpty(blockCache, player, offsetAABB_Z)) {
                if (zDistance < 0.05 && zDistance >= -0.05) {
                    zDistance = 0.0;
                } 
                else if (zDistance > 0.0) {
                    zDistance -= 0.05;
                } 
                else zDistance += 0.05;
                offsetAABB_Z = new double[]{minX, minY + yBelow, minZ + zDistance, maxX, minY, maxZ + zDistance};
            }
            
            // Move AABB alongside both (diagonally)
            double[] offsetAABB_XZ = new double[]{minX + xDistance, minY + yBelow, minZ + zDistance, maxX + xDistance, minY, maxZ + zDistance};
            while (xDistance != 0.0 && zDistance != 0.0 && CollisionUtil.isEmpty(blockCache, player, offsetAABB_XZ)) {
                if (xDistance < 0.05 && xDistance >= -0.05) {
                    xDistance = 0.0;
                } 
                else if (xDistance > 0.0) {
                    xDistance -= 0.05;
                } 
                else xDistance += 0.05;
                
                if (zDistance < 0.05 && zDistance >= -0.05) {
                    zDistance = 0.0;
                }
                 else if (zDistance > 0.0) {
                    zDistance -= 0.05;
                } 
                else zDistance += 0.05;
                offsetAABB_XZ = new double[]{minX + xDistance, minY + yBelow, minZ + zDistance, maxX + xDistance, minY, maxZ + zDistance};
            }
        }
        else {
            double signumX = Math.signum(xDistance) * 0.05D;
            double signumZ;
            double[] offsetAABB_X = new double[]{minX + xDistance, minY + yBelow - 9.999999747378752E-6D, minZ, maxX + xDistance, minY, maxZ}; // Skip using maxY, as we do not care of the top of the box in this case.
            for (signumZ = Math.signum(zDistance) * 0.05D; xDistance != 0.0D && CollisionUtil.isEmpty(blockCache, player, offsetAABB_X); xDistance -= signumX) {
                if (Math.abs(xDistance) <= 0.05D) {
                    xDistance = 0.0D;
                    break;
                }
                offsetAABB_X = new double[]{minX + xDistance, minY + yBelow - 9.999999747378752E-6D, minZ, maxX + xDistance, minY, maxZ};
            }
            
            double[] offsetAABB_Z = new double[]{minX, minY + yBelow - 9.999999747378752E-6D, minZ + zDistance, maxX, minY, maxZ + zDistance};
            while (zDistance != 0.0D && CollisionUtil.isEmpty(blockCache, player, offsetAABB_Z)) {
                if (Math.abs(zDistance) <= 0.05D) {
                    zDistance = 0.0D;
                    break;
                }
                zDistance -= signumZ;
                offsetAABB_Z = new double[]{minX, minY + yBelow - 9.999999747378752E-6D, minZ + zDistance, maxX, minY, maxZ + zDistance};
            }
            
            double[] offsetAABB_XZ = new double[]{minX + xDistance, minY + yBelow - 9.999999747378752E-6D, minZ + zDistance, maxX + xDistance, minY, maxZ + zDistance};
            while (xDistance != 0.0D && zDistance != 0.0D && CollisionUtil.isEmpty(blockCache, player, offsetAABB_XZ)) {
                if (Math.abs(xDistance) <= 0.05D) {
                    xDistance = 0.0D;
                } 
                else xDistance -= signumX;
                
                if (Math.abs(zDistance) <= 0.05D) {
                    zDistance = 0.0D;
                } 
                else zDistance -= signumZ;
                offsetAABB_XZ = new double[]{minX + xDistance, minY + yBelow - 9.999999747378752E-6D, minZ + zDistance, maxX + xDistance, minY, maxZ + zDistance};
            }
        }
        vector = new Vector(xDistance, 0.0, zDistance);
        return vector;
    }

    /**
     * Sets the player location object. See
     * {@link #set(Location, Player, double)}.
     *
     * @param location
     *            the location
     * @param player
     *            the player
     */
    public void set(final Location location, final Player player) {
        set(location, player, 0.001);
    }

    /**
     * Sets the player location object. Does not account for special conditions like
     * gliding with elytra with special casing, instead the maximum of accessible heights is used (eyeHeight, nms height/length). Does not set or reset blockCache.
     *
     * @param location
     *            the location
     * @param player
     *            the player
     * @param yOnGround
     *            the y on ground
     */
    public void set(final Location location, final Player player, final double yOnGround) {
        super.set(location, player, yOnGround);
        // Entity reference.
        this.player = player;
    }

    /**
     * Set with specific height/length/eyeHeight properties.
     * @param location
     * @param player
     * @param width
     * @param eyeHeight
     * @param height
     * @param fullHeight
     * @param yOnGround
     */
    public void set(final Location location, final Player player, final double width, final double eyeHeight, 
                    final double height, final double fullHeight, final double yOnGround) {
        super.doSetExactHeight(location, player, true, width, eyeHeight, height, fullHeight, yOnGround);
        // Entity reference.
        this.player = player;
    }

    /**
     * Not supported.
     *
     * @param location
     *            the location
     * @param entity
     *            the entity
     * @param yOnGround
     *            the y on ground
     */
    @Override
    public void set(Location location, Entity entity, double yOnGround) {
        throw new UnsupportedOperationException("Set must specify an instance of Player.");
    }

    /**
     * Not supported.
     *
     * @param location
     *            the location
     * @param entity
     *            the entity
     * @param fullHeight
     *            the full height
     * @param yOnGround
     *            the y on ground
     */
    @Override
    public void set(Location location, Entity entity, double fullHeight, double yOnGround) {
        throw new UnsupportedOperationException("Set must specify an instance of Player.");
    }

    /**
     * Not supported.
     *
     * @param location
     *            the location
     * @param entity
     *            the entity
     * @param fullWidth
     *            the full width
     * @param fullHeight
     *            the full height
     * @param yOnGround
     *            the y on ground
     */
    @Override
    public void set(Location location, Entity entity, double fullWidth, double fullHeight, double yOnGround) {
        throw new UnsupportedOperationException("Set must specify an instance of Player.");
    }

    /**
     * Set cached info according to other.<br>
     * Minimal optimizations: take block flags directly, on-ground max/min
     * bounds, only set stairs if not on ground and not reset-condition.
     *
     * @param other
     *            the other
     */
    public void prepare(final PlayerLocation other) {
        super.prepare(other);
    }

    /**
     * Set some references to null.
     */
    public void cleanup() {
        super.cleanup();
        player = null; // Still reset, to be sure.
    }

    /**
     * Check for bounding box properties that might crash the server (if
     * available, not the absolute coordinates).
     *
     * @return true, if successful
     */
    public boolean hasIllegalStance() {
        // TODO: This doesn't check this location, but the player.
        return getMCAccess().isIllegalBounds(player).decide(); // MAYBE = NO
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.utilities.RichEntityLocation#toString()
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(128);
        builder.append("PlayerLocation(");
        builder.append(world == null ? "null" : world.getName());
        builder.append('/');
        builder.append(Double.toString(x));
        builder.append(", ");
        builder.append(Double.toString(y));
        builder.append(", ");
        builder.append(Double.toString(z));
        builder.append(')');
        return builder.toString();
    }

}
