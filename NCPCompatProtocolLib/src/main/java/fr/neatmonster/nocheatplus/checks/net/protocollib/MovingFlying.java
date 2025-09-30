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
package fr.neatmonster.nocheatplus.checks.net.protocollib;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.PacketType.Protocol;
import com.comphenix.protocol.PacketType.Sender;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.net.FlyingFrequency;
import fr.neatmonster.nocheatplus.checks.net.Moving;
import fr.neatmonster.nocheatplus.checks.net.NetConfig;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.checks.net.WrongTurn;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying.PACKET_CONTENT;
import fr.neatmonster.nocheatplus.checks.net.model.TeleportQueue.AckReference;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.time.monotonic.Monotonic;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;

/**
 * Run checks related to moving (pos/look/flying). Skip packets that shouldn't
 * get processed anyway due to a teleport. Also update lastKeepAliveTime.
 * 
 * @author asofold
 *
 */
public class MovingFlying extends BaseAdapter {

    private static final boolean isServerAtLeast1_21_3 = ServerVersion.isAtLeast("1.21.3");
    // Setup for flying packets.
    public static final int indexOnGround = 0;
    public static final int indexhorizontalCollision = isServerAtLeast1_21_3 ? 1 : 0;
    public static final int indexhasPos = 1 + indexhorizontalCollision;
    public static final int indexhasLook = 2 + indexhorizontalCollision;
    public static final int indexX = 0;
    public static final int indexY = 1;
    public static final int indexZ = 2;
    public static final int indexStance = 3; // 1.7.10
    public static final int indexYaw = 0;
    public static final int indexPitch = 1;

    // Setup for teleport accept packet.
    private static PacketType confirmTeleportType;
    private static boolean acceptConfirmTeleportPackets;

    private final Plugin plugin = Bukkit.getPluginManager().getPlugin("NoCheatPlus");
    private static PacketType[] initPacketTypes() {
        final List<PacketType> types = new LinkedList<PacketType>(Arrays.asList(PacketType.Play.Client.LOOK, PacketType.Play.Client.POSITION, PacketType.Play.Client.POSITION_LOOK));
        if (ServerVersion.isLowerThan("1.17")) {
            types.add(PacketType.Play.Client.FLYING);
            StaticLog.logInfo("Add listener for legacy PlayInFlying packet.");
        } 
        else types.add(PacketType.Play.Client.GROUND);
        // Add confirm teleport.
        // PacketPlayInTeleportAccept
        confirmTeleportType = ProtocolLibComponent.findPacketTypeByName(Protocol.PLAY, Sender.CLIENT, "AcceptTeleportation");
        if (confirmTeleportType == null) { // Fallback check for the old packet name.
            confirmTeleportType = ProtocolLibComponent.findPacketTypeByName(Protocol.PLAY, Sender.CLIENT, "TeleportAccept");
        }
        if (confirmTeleportType != null && ServerVersion.isAtLeast("1.9")) {
            StaticLog.logInfo("Confirm teleport packet available (via name): " + confirmTeleportType);
            types.add(confirmTeleportType);
            acceptConfirmTeleportPackets = true;
        } 
        else acceptConfirmTeleportPackets = false;
        return types.toArray(new PacketType[types.size()]);
    }

    /** Frequency check for flying packets. */
    private final FlyingFrequency flyingFrequency = new FlyingFrequency();
    /** Other checks related to packet content. */
    private final Moving moving = new Moving();
    /** Ilegal pitch check */
    private final WrongTurn wrongTurn = new WrongTurn(); 

    private final int idFlying = counters.registerKey("packet.flying");
    private final int idAsyncFlying = counters.registerKey("packet.flying.asynchronous");

    /** If a packet can't be parsed, this time stamp is set for occasional logging. */
    private long packetMismatch = Long.MIN_VALUE;
    /** Every minute max, good for updating :).*/
    private long packetMismatchLogFrequency = 60000;

    private final HashSet<PACKET_CONTENT> validContent = new LinkedHashSet<PACKET_CONTENT>();
    
    public MovingFlying(Plugin plugin) {
        // PacketPlayInFlying[3, legacy: 10]
        super(plugin, ListenerPriority.LOW, initPacketTypes());
        // Keep the CheckType NET for now.
        // Add feature tags for checks.
        if (NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().isActiveAnywhere(CheckType.NET_FLYINGFREQUENCY)) {
            NCPAPIProvider.getNoCheatPlusAPI().addFeatureTags( "checks", Collections.singletonList(FlyingFrequency.class.getSimpleName()));
        }
        NCPAPIProvider.getNoCheatPlusAPI().addComponent(flyingFrequency);
    }

    @Override
    public void onPacketReceiving(final PacketEvent event) {
        try {
            if (event.isPlayerTemporary()) return;
        } 
        catch (NoSuchMethodError e) {
            if (event.getPlayer() == null) {
                return;
            }
            if (DataManager.getPlayerDataSafe(event.getPlayer()) == null) {
                return;
            }
        }
        if (event.getPacketType().equals(confirmTeleportType)) {
            if (acceptConfirmTeleportPackets) {
                onConfirmTeleportPacket(event);
            }
        }
        else onFlyingPacket(event);
    }

    private void onConfirmTeleportPacket(final PacketEvent event) {
        try {
            processConfirmTeleport(event);
        }
        catch (Throwable t) {
            noConfirmTeleportPacket();
        }
    }

    private void processConfirmTeleport(final PacketEvent event) {
        final PacketContainer packet = event.getPacket();
        final StructureModifier<Integer> integers = packet.getIntegers();
        if (integers.size() != 1) {
            noConfirmTeleportPacket();
            return;
        }
        // TODO: Cross check legacy types (if they even had an integer).
        Integer teleportId = integers.read(0);
        if (teleportId == null) {
            // TODO: Not sure ...
            return;
        }
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerDataSafe(player);
        final NetData data = pData.getGenericInstance(NetData.class);
        final AlmostBoolean matched = data.teleportQueue.processAck(teleportId);
        if (matched.decideOptimistically()) {
            ActionFrequency.subtract(System.currentTimeMillis(), 1, data.flyingFrequencyAll);
        }
        if (pData.isDebugActive(this.checkType)) { 
            debug(player, "Confirm teleport packet" + (matched.decideOptimistically() ? (" (matched=" + matched + ")") : "") + ": " + teleportId);
        }
    }

    private void noConfirmTeleportPacket() {
        acceptConfirmTeleportPackets = false;
        // TODO: Attempt to unregister.
        NCPAPIProvider.getNoCheatPlusAPI().getLogManager().info(Streams.STATUS, "Confirm teleport packet not available.");
    }

    private void onFlyingPacket(final PacketEvent event) {
        final boolean primaryThread = Bukkit.isPrimaryThread(); 
        counters.add(idFlying, 1, primaryThread);
        if (event.isAsync() == primaryThread) {
            counters.add(ProtocolLibComponent.idInconsistentIsAsync, 1, primaryThread);
        }
        if (!primaryThread) {
            // Count all asynchronous events extra.
            counters.addSynchronized(idAsyncFlying, 1);
            // TODO: Detect game phase for the player?
        }

        final long time =  System.currentTimeMillis();
        final Player player = event.getPlayer();
        if (player == null) {
            // TODO: Need config?
            counters.add(ProtocolLibComponent.idNullPlayer, 1, primaryThread);
            event.setCancelled(true);
            return;
        }

        final IPlayerData pData = DataManager.getPlayerDataSafe(player);
        // Always update last received time.
        final NetData data = pData.getGenericInstance(NetData.class);
        data.lastKeepAliveTime = time; // Update without much of a contract.
        // TODO: Leniency options too (packet order inversion). -> current: flyingQueue is fetched.
        final NetConfig cc = pData.getGenericInstance(NetConfig.class);
        boolean cancel = false;
        // Interpret the packet content.
        final DataPacketFlying packetData = interpretPacket(event, time);
        // Early return tests, if the packet can be interpreted.
        boolean skipChecks = false;
        if (packetData != null) {
            // Prevent processing packets with obviously malicious content.
            if (isInvalidContent(packetData)) {
                event.setCancelled(true);
                // Do request and improbable update here for good measure.
                TickTask.requestImprobableUpdate(player.getUniqueId(), 1.0f);
                if (pData.isDebugActive(this.checkType)) {
                    debug(player, "Incoming flying packet, cancel due to malicious content: " + packetData);
                }
                return;
            }

            switch (data.teleportQueue.processAck(packetData)) {
                case WAITING: {
                    if (pData.isDebugActive(this.checkType)) {
                        debug(player, "Incoming flying packet, still waiting for ACK on outgoing position.");
                    }
                    if (confirmTeleportType != null && cc.supersededFlyingCancelWaiting) {
                        // Don't add to the flying queue for now (assumed invalid).
                        final AckReference ackReference = data.teleportQueue.getLastAckReference();
                        if (ackReference.lastOutgoingId != Integer.MIN_VALUE && ackReference.lastOutgoingId != ackReference.maxConfirmedId) {
                            // Still waiting for a 'confirm teleport' packet. More or less safe to cancel this out.
                            /*
                             * TODO: The actual issue with this, apart from
                             * potential freezing, also concerns gameplay experience
                             * in case of minor set backs, which also could be
                             * caused by the server, e.g. with 'moved wrongly' or
                             * setting players outside of blocks. In this case the
                             * moves sent before teleport ack would still be valid
                             * after the teleport, because distances are small. The
                             * actual solution should still be to a) not have false
                             * positives b) somehow get rid all the
                             * position-correction teleporting the server does, for
                             * the cases a plugin can handle.
                             */
                             // See todo below on if (cancel) {...}
                             cancel = true;
                        }
                    }
                    break;
                }
                case ACK: {
                    // Skip processing ACK packets, no cancel.
                    skipChecks = true;
                    if (pData.isDebugActive(this.checkType)) {
                        debug(player, "Incoming flying packet, interpret as ACK for outgoing position. Skip checks.");
                    }
                }
                default: {
                    // Continue.
                    data.addFlyingQueue(packetData); // TODO: Not the optimal position, perhaps.
                }
            }
            // Add as valid packet (exclude invalid coordinates etc. for now).
            validContent.add(packetData.getSimplifiedContentType());
        }

        // Actual packet frequency check.
        // TODO: Consider using the NetStatic check.
        if (!cancel && !skipChecks 
            && flyingFrequency.check(player, packetData, time, data, cc, pData)
            && !pData.hasBypass(CheckType.NET_FLYINGFREQUENCY, player)) {
            cancel = true;
        }
        // More packet checks.
        if (!cancel && !skipChecks && pData.isCheckActive(CheckType.NET_MOVING, player) 
            && moving.check(player, packetData, data, cc, pData, plugin)
            && !pData.hasBypass(CheckType.NET_MOVING, player)) {
            cancel = true;
        }
        // Illegal pitch check
        if (!cancel && pData.isCheckActive(CheckType.NET_WRONGTURN, player) 
            && wrongTurn.check(player, packetData.getPitch(), data, cc)
            && !pData.hasBypass(CheckType.NET_WRONGTURN, player)) {
            cancel = true; // Is it a good idea to cancel or should we just reset the players pitch?
        }

        // Process cancel and debug log.
        if (cancel) {
            /*
             * TODO: Test if this can fix packet fly exploits; it will also cause set backs on flying packets awaiting ACK(s)
             *  Previously, NCP would simply cancel these flying packets out, without really restricting movement.
             */
            data.requestSetBack(player, this, plugin, CheckType.NET);
            event.setCancelled(true);
        }
        
        if (pData.isDebugActive(this.checkType)) {
            debug(player, (packetData == null ? "(Incompatible data)" : packetData.toString()) + (event.isCancelled() ? " CANCEL" : ""));
        }
    }
    
    /**
     * Checks if the given {@link DataPacketFlying} contains invalid position or look data. <br>
     * Delegates to {@link LocUtil#isBadCoordinate(double...)} and {@link LocUtil#isBadCoordinate(float...)}.<br>
     * If this returns true, invalid packets won't be added to the flying queue, and will be cancelled immediately.
     *
     * @param packetData the flying packet data to validate
     * @return true if the packet contains invalid position or look data, false otherwise
     */
    private boolean isInvalidContent(final DataPacketFlying packetData) {
        if (packetData.hasPos && LocUtil.isBadCoordinate(packetData.getX(), packetData.getY(), packetData.getZ())) {
            return true;
        }
        return packetData.hasLook && LocUtil.isBadCoordinate(packetData.getYaw(), packetData.getPitch());
    }

    /**
     * Interpret the packet content and do with it whatever is suitable.
     * @param event
     * @param time
     * @return Packet data if successful, or null on packet mismatch.
     */
    private DataPacketFlying interpretPacket(final PacketEvent event, final long time) {
        final PacketContainer packet = event.getPacket();
        final List<Boolean> booleans = packet.getBooleans().getValues();
        if (booleans.size() != (isServerAtLeast1_21_3 ? 4 : 3)) {
            packetMismatch(event);
            return null;
        }
        final boolean onGround = booleans.get(MovingFlying.indexOnGround);
        final boolean horizontalCollision = isServerAtLeast1_21_3 && booleans.get(MovingFlying.indexhorizontalCollision);
        final boolean hasPos = booleans.get(MovingFlying.indexhasPos);
        final boolean hasLook = booleans.get(MovingFlying.indexhasLook);

        if (!hasPos && !hasLook) {
            return new DataPacketFlying(onGround, horizontalCollision, time);
        }
        final List<Double> doubles;
        final List<Float> floats;

        if (hasPos) {
            doubles = packet.getDoubles().getValues();
            if (doubles.size() != 3 && doubles.size() != 4) {
                // 3: 1.8, 4: 1.7.10 and before (stance).
                packetMismatch(event);
                return null;
            }
            // TODO: before 1.8: stance (should make possible to reject in isInvalidContent).
        }
        else {
            doubles = null;
        }

        if (hasLook) {
            floats = packet.getFloat().getValues();
            if (floats.size() != 2) {
                packetMismatch(event);
                return null;
            }
        }
        else {
            floats = null;
        }
        if (hasPos && hasLook) {
            return new DataPacketFlying(onGround, horizontalCollision, doubles.get(indexX), doubles.get(indexY), doubles.get(indexZ), floats.get(indexYaw), floats.get(indexPitch), time);
        }
        else if (hasLook) {
            return new DataPacketFlying(onGround, horizontalCollision, floats.get(indexYaw), floats.get(indexPitch), time);
        }
        else if (hasPos) {
            return new DataPacketFlying(onGround, horizontalCollision, doubles.get(indexX), doubles.get(indexY), doubles.get(indexZ), time);
        }
        else {
            throw new IllegalStateException("Can't be, it can't be!");
        }
    }

    /**
     * Log warning to console, stop interpreting packet content.
     */
    private void packetMismatch(final PacketEvent packetEvent) {
        final long time = Monotonic.synchMillis();
        if (time - packetMismatchLogFrequency > packetMismatch) {
            packetMismatch = time;
            StringBuilder builder = new StringBuilder(512);
            builder.append(CheckUtils.getLogMessagePrefix(packetEvent.getPlayer(), checkType));
            builder.append("Incoming packet could not be interpreted. Are server and plugins up to date (NCP/ProtocolLib...)? This message is logged every ");
            builder.append(packetMismatchLogFrequency / 1000);
            builder.append(" seconds, disregarding for which player this happens.");
            if (!validContent.isEmpty()) {
                builder.append(" On other occasion, valid content was received for: ");
                StringUtil.join(validContent, ", ", builder);
            }
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS, builder.toString());
        }
    }
}
