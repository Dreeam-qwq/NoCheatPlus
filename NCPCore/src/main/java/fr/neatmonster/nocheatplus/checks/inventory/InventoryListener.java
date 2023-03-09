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
package fr.neatmonster.nocheatplus.checks.inventory;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckListener;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.combined.Combined;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.components.registry.factory.IFactoryOne;
import fr.neatmonster.nocheatplus.components.registry.feature.JoinLeaveListener;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.players.PlayerFactoryArgument;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.InventoryUtil;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;
import fr.neatmonster.nocheatplus.utilities.moving.MovingUtil;
import fr.neatmonster.nocheatplus.worlds.WorldFactoryArgument;

/**
 * Central location to listen to events that are relevant for the inventory checks.
 * 
 * @see InventoryEvent
 */
public class InventoryListener  extends CheckListener implements JoinLeaveListener {
    
    /** More Inventory check */
    private final MoreInventory moreInv = addCheck(new MoreInventory());

    /** The fast click check. */
    private final FastClick  fastClick  = addCheck(new FastClick());

    /** The instant bow check. */
    private final InstantBow instantBow = addCheck(new InstantBow());
    
    /** The open check */
    private final Open open = addCheck(new Open());
    
    private boolean keepCancel = false;

    private final boolean hasInventoryAction;

    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    private final Location useLoc = new Location(null, 0, 0, 0);

    private final Counters counters = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class);

    private final int idCancelDead = counters.registerKey("cancel.dead");

    private final IGenericInstanceHandle<IEntityAccessVehicle> handleVehicles = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IEntityAccessVehicle.class);

    @SuppressWarnings("unchecked")
    public InventoryListener() {
        super(CheckType.INVENTORY);
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        api.register(api.newRegistrationContext()
                // InventoryConfig
                .registerConfigWorld(InventoryConfig.class)
                .factory(new IFactoryOne<WorldFactoryArgument, InventoryConfig>() {
                    @Override
                    public InventoryConfig getNewInstance(
                            WorldFactoryArgument arg) {
                        return new InventoryConfig(arg.worldData);
                    }
                })
                .registerConfigTypesPlayer()
                .context() //
                // InventoryData
                .registerDataPlayer(InventoryData.class)
                .factory(new IFactoryOne<PlayerFactoryArgument, InventoryData>() {
                    @Override
                    public InventoryData getNewInstance(
                            PlayerFactoryArgument arg) {
                        return new InventoryData();
                    }
                })
                .addToGroups(CheckType.INVENTORY, true, IData.class, ICheckData.class)
                .context() //
                );
        // Move to BridgeMisc?
        hasInventoryAction = ReflectionUtil.getClass("org.bukkit.event.inventory.InventoryAction") != null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityShootBow(final EntityShootBowEvent event) {
        // Only if a player shot the arrow.
        if (event.getEntity() instanceof Player) {

            final Player player = (Player) event.getEntity();
            final IPlayerData pData = DataManager.getPlayerData(player);
            if (instantBow.isEnabled(player, pData)) {
                final long now = System.currentTimeMillis();
                final Location loc = player.getLocation(useLoc);
                if (Combined.checkYawRate(player, loc.getYaw(), now, loc.getWorld().getName(), pData)) {
                    // No else if with this, could be cancelled due to other checks feeding, does not have actions.
                    event.setCancelled(true);
                }

                final InventoryConfig cc = pData.getGenericInstance(InventoryConfig.class);
                // Still check instantBow, whatever yawrate says.
                if (instantBow.check(player, event.getForce(), now)) {
                    event.setCancelled(true);
                }
                else if (cc.instantBowImprobableWeight > 0.0f) {
                    if (cc.instantBowImprobableFeedOnly) {
                        Improbable.feed(player, cc.instantBowImprobableWeight, now);
                    }
                    else if (Improbable.check(player, cc.instantBowImprobableWeight, now, "inventory.instantbow", pData)) {
                        // Combined fighting speed (Else if: Matter of taste, preventing extreme cascading and actions spam).
                        event.setCancelled(true);
                    }
                }
                useLoc.setWorld(null);
            }  
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onFoodLevelChange(final FoodLevelChangeEvent event) {
        // Only if a player ate food.
        if (event.getEntity() instanceof Player) {
            final Player player = (Player) event.getEntity();
            final IPlayerData pData = DataManager.getPlayerData(player);
            if (player.isDead() && BridgeHealth.getHealth(player) <= 0.0) {
                // Eat after death.
                event.setCancelled(true);
                counters.addPrimaryThread(idCancelDead, 1);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        final long now = System.currentTimeMillis();
        final HumanEntity entity = event.getWhoClicked();
        if (!(entity instanceof Player)) {
            return;
        }
        final Player player = (Player) entity;
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        final int slot = event.getSlot();
        final String inventoryAction = hasInventoryAction ? event.getAction().name() : null;
        final InventoryConfig cc = pData.getGenericInstance(InventoryConfig.class);
        if (pData.isDebugActive(checkType)) {
            outputDebugInventoryClick(player, slot, event, inventoryAction);
        }
        if (slot == InventoryView.OUTSIDE || slot < 0) {
            // This is used by CHECKS
            data.lastClickTime = now;
            // This is used to determine if the player could have opened their own inventory
            if (data.firstClickTime == 0) {
                data.firstClickTime = now;
                if (pData.isDebugActive(CheckType.INVENTORY)) {
                    debug(player, "On inventory click (outside): register time of the first click (assume inventory is open)");
                }
            }
            return;
        }

        final ItemStack cursor = event.getCursor();
        final ItemStack clicked = event.getCurrentItem();
        boolean cancel = false;
        
        // Fast inventory manipulation check.
        if (fastClick.isEnabled(player, pData)) {

            if (!((event.getView().getType().equals(InventoryType.CREATIVE) || player.getGameMode() == GameMode.CREATIVE) && cc.fastClickSpareCreative)) {
                boolean check = true;
                try {
                    check = !cc.inventoryExemptions.contains(ChatColor.stripColor(event.getView().getTitle()));
                }
                catch (final IllegalStateException e) {
                    // Uhm... Can this ISE be fixed?
                    check = true; 
                }
                
                // Check for too quick interactions first (we don't need to check for fast clicking if the interaction is inhumanly fast)
                if (check && InventoryUtil.isContainerInventory(event.getInventory().getType())
                    && fastClick.checkContainerInteraction(player, data, cc)) {
                    cancel = true;
                    keepCancel = true;
                }
                // Then check for too fast inventory clicking
                if (check && fastClick.check(player, now, event.getView(), slot, cursor, clicked, event.isShiftClick(), 
                                            inventoryAction, data, cc, pData)) {  
                    cancel = true;
                }
            }
        }
        
        // This is used by CHECKS
        data.lastClickTime = now;
        data.clickedSlotType = event.getSlotType();
        // This is used to determine if the player could have opened their own inventory
        if (data.firstClickTime == 0) {
            data.firstClickTime = now;
            if (pData.isDebugActive(CheckType.INVENTORY)) {
                debug(player, "On inventory click: register time of the first click (assume inventory is open)");
            }
        }
        // Cancel the event.
        if (cancel || keepCancel) {
            event.setCancelled(true);
            // pData.requestUpdateInventory();
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryClose(final InventoryCloseEvent event) {
        final HumanEntity entity = event.getPlayer();
        if (entity instanceof Player) {
            final Player player = (Player) entity;
            if (player != null) {
                final IPlayerData pData = DataManager.getPlayerData(player);
                final InventoryData data = pData.getGenericInstance(InventoryData.class);
                data.firstClickTime = 0;
                data.containerInteractTime = 0;
                if (pData.isDebugActive(CheckType.INVENTORY)) {
                    debug(player, "On inventory close: reset timing data.");
                }
            }
        }
        keepCancel = false;
    }
    

    /**
     * Debug inventory classes. Contains information about classes, to indicate
     * if cross-plugin compatibility issues can be dealt with easily.
     * 
     * @param player
     * @param slot
     * @param event
     */
    private void outputDebugInventoryClick(final Player player, final int slot, final InventoryClickEvent event, 
                                           final String action) {
        // TODO: Consider only logging where different from expected (CraftXY, more/other viewer than player). 

        final StringBuilder builder = new StringBuilder(512);
        final InventoryData data = DataManager.getPlayerData(player).getGenericInstance(InventoryData.class);
        builder.append("Inventory click: slot: " + slot);
        builder.append(" , Inventory has been opened for: " + (System.currentTimeMillis() - data.firstClickTime));
        builder.append(" , Time between inventory click and last interaction time: " + (data.lastClickTime - data.containerInteractTime));

        // Viewers.
        builder.append(" , Viewers: ");
        for (final HumanEntity entity : event.getViewers()) {
            builder.append(entity.getName());
            builder.append("(");
            builder.append(entity.getClass().getName());
            builder.append(")");
        }

        // Inventory view.
        builder.append(" , View: ");
        final InventoryView view = event.getView();
        builder.append(view.getClass().getName());

        // Bottom inventory.
        addInventory(view.getBottomInventory(), view, " , Bottom: ", builder);

        // Top inventory.
        addInventory(view.getBottomInventory(), view, " , Top: ", builder);
        
        if (action != null) {
            builder.append(" , Action: ");
            builder.append(action);
        }

        // Event class.
        builder.append(" , Event: ");
        builder.append(event.getClass().getName());

        // Log debug.
        debug(player, builder.toString());
    }

    private void addInventory(final Inventory inventory, final InventoryView view, final String prefix,
            final StringBuilder builder) {
        builder.append(prefix);
        if (inventory == null) {
            builder.append("(none)");
        }
        else {
            String name = view.getTitle();
            builder.append(name);
            builder.append("/");
            builder.append(inventory.getClass().getName());
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public final void onPlayerInteract(final PlayerInteractEvent event) {
    	final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        // Set the container opening time.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
            // Sneaking and right clicking with a block in hand will cause the player to place the block down, not to open the container.
            && !(cData.isUsingItem || player.isSneaking() && event.isBlockInHand())) {
            if (BlockProperties.isContainer(event.getClickedBlock().getType())) {
                data.containerInteractTime = System.currentTimeMillis();
                if (pData.isDebugActive(CheckType.INVENTORY)) {
                    debug(player, "Interacted with a container: register the interaction time.");
                }
            }
        } 
        // Only interested in right-clicks while holding an item.
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        boolean resetAll = false;
        if (event.hasItem()) {
            final ItemStack item = event.getItem();
            final Material type = item.getType();
            // TODO: Get Magic values (800) from the config.
            // TODO: Cancelled / deny use item -> reset all?
            if (type == Material.BOW) {
                final long now = System.currentTimeMillis();
                // It was a bow, the player starts to pull the string, remember this time.
                data.instantBowInteract = (data.instantBowInteract > 0 && now - data.instantBowInteract < 800) ? Math.min(System.currentTimeMillis(), data.instantBowInteract) : System.currentTimeMillis();
            }
            else if (InventoryUtil.isConsumable(type)) {
                final long now = System.currentTimeMillis();
                // It was food, the player starts to eat some food, remember this time and the type of food.
                data.fastConsumeFood = type;
                data.fastConsumeInteract = (data.fastConsumeInteract > 0 && now - data.fastConsumeInteract < 800) ? Math.min(System.currentTimeMillis(), data.fastConsumeInteract) : System.currentTimeMillis();
                data.instantBowInteract = 0; 
            } 
            else resetAll = true;
        }
        else resetAll = true;

        if (resetAll) {
            // Nothing that we are interested in, reset data.
            data.instantBowInteract = 0;
            data.fastConsumeInteract = 0;
            data.fastConsumeFood = null;
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public final void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || !DataManager.getPlayerData(player).isCheckActive(CheckType.INVENTORY, player)) {
            return;
        }
        if (player.isDead() && BridgeHealth.getHealth(player) <= 0.0) {
            // No zombies.
            event.setCancelled(true);
            counters.addPrimaryThread(idCancelDead, 1);
            return;
        }
        else if (MovingUtil.hasScheduledPlayerSetBack(player)) {
            event.setCancelled(true);
            return;
        }
    }
    
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public final void onPlayerInventoryOpen(final InventoryOpenEvent event) {
        // Possibly already prevented by block + entity interaction.
        final long now = System.currentTimeMillis();
        final HumanEntity entity = event.getPlayer();
        if (entity instanceof Player) {
            final Player player = (Player) entity;
            if (player != null) {
                final IPlayerData pData = DataManager.getPlayerData(player);
                final InventoryData data = pData.getGenericInstance(InventoryData.class);
                if (MovingUtil.hasScheduledPlayerSetBack(player)) {
                    // Don't allow players to open inventories on set-backs.
                    event.setCancelled(true);
                    data.firstClickTime = 0;
                }
                else if (data.firstClickTime == 0) {
                    // Only set the inventory opening time, if a setback is not scheduled.
                	data.firstClickTime = now;
                     if (pData.isDebugActive(CheckType.INVENTORY)) {
                        debug(player, "Container is open (InventoryOpenEvent): register time.");
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldChange(final PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        if (pData.isDebugActive(checkType) && data.fastConsumeFood != null) {
            debug(player, "PlayerItemHeldEvent, reset fastconsume.");
        }
        data.instantBowInteract = 0;
        data.fastConsumeInteract = System.currentTimeMillis();
        data.fastConsumeFood = null;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        open.check(event.getPlayer());
        data.firstClickTime = 0;
        data.containerInteractTime = 0;
        if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
            debug(player, "Force-close inventory on changing worlds and reset timings data.");
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPortal(final PlayerPortalEvent event) {
        // Note: ignore cancelother setting.
    	final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        open.check(event.getPlayer());
        data.firstClickTime = 0;
        data.containerInteractTime = 0;
        if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
            debug(player, "Force-close inventory on using a portal and reset timings data.");
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        open.check(event.getPlayer());
        data.firstClickTime = 0;
        data.containerInteractTime = 0;
        if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
            debug(player, "Force-close inventory on respawn and reset timings data.");
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            final Player player = (Player) entity;
            if (player != null) {
                final IPlayerData pData = DataManager.getPlayerData(player);
                final InventoryData data = pData.getGenericInstance(InventoryData.class);
                open.check(player);
                data.firstClickTime = 0;
                data.containerInteractTime = 0;
                if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
                    debug(player, "Force-close inventory on death and reset timings data.");
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSleep(final PlayerBedEnterEvent event) {
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        open.check(player);
        data.firstClickTime = 0;
        if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
            debug(player, "Force-close inventory on sleeping and reset timings data.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerWake(final PlayerBedLeaveEvent event) {
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        open.check(player);
        data.firstClickTime = 0;
        if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
            debug(player, "Force-close inventory on waking up and reset timings data.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPortal(final EntityPortalEnterEvent event) {
        // Check passengers flat for now.
        final Entity entity = event.getEntity();
        if (entity instanceof Player) {
        	final IPlayerData pData = DataManager.getPlayerData((Player) entity);
            final InventoryData data = pData.getGenericInstance(InventoryData.class);
            open.check((Player) entity);
            data.firstClickTime = 0;
            data.containerInteractTime = 0;
            if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
                debug((Player) entity, "Force-close inventory on using a portal (entity) and reset timings data.");
            }
        }
        else {
            for (final Entity passenger : handleVehicles.getHandle().getEntityPassengers(entity)) {
                if (passenger instanceof Player) {
                    // Note: ignore cancelother setting.
                    open.check((Player) passenger);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final Location from = event.getFrom();
        final Location to = event.getTo();
        final boolean PoYdiff = from.getPitch() != to.getPitch() || from.getYaw() != to.getYaw();
        final IPlayerData pData = DataManager.getPlayerData(player);
        if (pData == null) {
            return;
        }
        final InventoryData iData = pData.getGenericInstance(InventoryData.class);
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        final InventoryConfig cc = pData.getGenericInstance(InventoryConfig.class);
        final Inventory inv = player.getOpenInventory().getTopInventory();
        if (moreInv.isEnabled(player, pData) 
            && moreInv.check(player, cData, pData, inv.getType(), inv, PoYdiff)) {
            for (int i = 1; i <= 4; i++) {
                final ItemStack item = inv.getItem(i);
                // Ensure air-clicking is not detected... :)
                if (item != null && !BlockProperties.isAir(item.getType())) {
                    // Note: dropItemsNaturally does not fire InvDrop events, simply close the inventory
                    player.closeInventory();
                    if (pData.isDebugActive(CheckType.INVENTORY_MOREINVENTORY)) {
                        debug(player, "On PlayerMoveEvent: force-close inventory on MoreInv detection.");
                    }
                    break;
                }
            }
        }
        // Determine if the inventory should be closed.
        if (cc.openCancelOnMove && !pData.hasBypass(CheckType.INVENTORY_OPEN, player)) {
            if (InventoryUtil.hasAnyInventoryOpen(player) && open.shouldCloseInventory(player, pData)) {
                // Force-close
                open.check(player);
                if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
                    debug(player, "On PlayerMoveEvent: force-close open inventory.");
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        // Note: ignore cancelother setting.
        open.check(event.getPlayer());
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        data.firstClickTime = 0; 
        data.containerInteractTime = 0;
        if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
            debug(player, "Force-close inventory on teleporting and reset timings data.");
        }
    }

    @Override
    public void playerJoins(Player player) {
        // Just to be sure...
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        data.firstClickTime = 0;
        if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
            debug(player, "Reset inventory timings data on join.");
        }
    }

    @Override
    public void playerLeaves(Player player) {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        data.firstClickTime = 0;
        data.containerInteractTime = 0;
        open.check(player);
        if (pData.isDebugActive(CheckType.INVENTORY_OPEN)) {
            debug(player, "Force-close inventory on leaving the server and reset timings data.");
        }
    }
}
