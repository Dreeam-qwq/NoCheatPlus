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
package fr.neatmonster.nocheatplus.utilities.entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.checks.inventory.InventoryData;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.bukkit.BridgeBukkitAPI;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

/**
 * Auxiliary/convenience methods for inventories.
 * @author asofold
 *
 */
public class InventoryUtil {

    private final static IGenericInstanceHandle<MCAccess> mcAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(MCAccess.class);

    private static final Map<String, InventoryType> all = new HashMap<String, InventoryType>();
    static {
        for (InventoryType type : InventoryType.values()) {
            String name = type.name().toLowerCase(Locale.ROOT);
            all.put(name, type);
        }
    }

    public static InventoryType get(String name) {
        return all.get(name.toLowerCase());
    }

    public static final Set<InventoryType> CONTAINER_LIST = Collections.unmodifiableSet(
            getAll("chest", "ender_chest", "dispenser", "dropper", "hopper", "barrel", "shulker_box", "chiseled_bookshelf")
            );

    public static Set<InventoryType> getAll(String... names) {
        final LinkedHashSet<InventoryType> res = new LinkedHashSet<InventoryType>();
        for (final String name : names) {
            final InventoryType mat = get(name);
            if (mat != null) {
                res.add(mat);
            }
        }
        return res;
    }

    /**
     * Collect non-block items by suffix of their Material name (case insensitive).
     * @param suffix
     * @return
     */
    public static List<Material> collectItemsBySuffix(String suffix) {
        suffix = suffix.toLowerCase();
        final List<Material> res = new LinkedList<Material>();
        for (final Material mat : Material.values()) {
            if (!mat.isBlock() && mat.name().toLowerCase().endsWith(suffix)) {
                res.add(mat);
            }
        }
        return res;
    }

    /**
     * Collect non-block items by suffix of their Material name (case insensitive).
     * @param prefix
     * @return
     */
    public static List<Material> collectItemsByPrefix(String prefix) {
        prefix = prefix.toLowerCase();
        final List<Material> res = new LinkedList<Material>();
        for (final Material mat : Material.values()) {
            if (!mat.isBlock() && mat.name().toLowerCase().startsWith(prefix)) {
                res.add(mat);
            }
        }
        return res;
    }

    /**
     * Does not account for special slots like armor.
     *
     * @param inventory
     *            the inventory
     * @return the free slots
     */
    public static int getFreeSlots(final Inventory inventory) {
        final ItemStack[] contents = inventory.getContents();
        int count = 0;
        for (ItemStack content : contents) {
            if (BlockProperties.isAir(content)) {
                count ++;
            }
        }
        return count;
    }

    /**
     * Count slots with type-id and data (enchantments and other meta data are
     * ignored at present).
     *
     * @param inventory
     *            the inventory
     * @param reference
     *            the reference
     * @return the stack count
     */
    public static int getStackCount(final Inventory inventory, final ItemStack reference) {
        if (inventory == null) return 0;
        if (reference == null) return getFreeSlots(inventory);
        final Material mat = reference.getType();
        final int durability = reference.getDurability();
        final ItemStack[] contents = inventory.getContents();
        int count = 0;
        for (final ItemStack stack : contents) {
            if (stack == null) {
                continue;
            }
            else if (stack.getType() == mat && stack.getDurability() == durability) {
                count ++;
            }
        }
        return count;
    }

    /**
     * Sum of bottom + top inventory slots with item type / data, see:
     * getStackCount(Inventory, reference).
     *
     * @param view
     *            the view
     * @param reference
     *            the reference
     * @return the stack count
     */
    public static int getStackCount(final InventoryView view, final ItemStack reference) {
        return getStackCount(view.getBottomInventory(), reference) + getStackCount(view.getTopInventory(), reference);
    }

    //    /**
    //     * Search for players / passengers (broken by name: closes the inventory of
    //     * first player found including entity and passengers recursively).
    //     *
    //     * @param entity
    //     *            the entity
    //     * @return true, if successful
    //     */
    //    public static boolean closePlayerInventoryRecursively(Entity entity) {
    //        // Find a player.
    //        final Player player = PassengerUtil.getFirstPlayerIncludingPassengersRecursively(entity);
    //        if (player != null && closeOpenInventory((Player) entity)) {
    //            return true;
    //        } else {
    //            return false;
    //        }
    //    }

    /**
     * Check if the player's inventory is open by looking up the current InventoryView type, via player#getOpenInventory().
     * Note that this method cannot be used to check for one's own inventory, because Bukkit returns CRAFTING/CREATIVE as default InventoryView type (due to Minercaft not sending anything when pressing E).
     * (See InventoryData.inventoryOpenTime)
     *
     * @param player
     *            the player
     * @return True, if the opened inventory is of any type that isn't CRAFTING/CREATIVE and is not null.
     */
    public static boolean hasInventoryOpenOwnExcluded(final Player player) {
        return BridgeBukkitAPI.hasInventoryOpenOwnExcluded(player);
    }

   /**
    * Check if the player has opened any kind of inventory (including their own).
    * If the inventory status cannot be assumed from player#getOpenInventory() (see hasInventoryOpenOwnExcluded(player)), look up if we have registered the first inventory click time.
    * 
    * @param player
    *            the player
    * @return True, if inventory status is known, or can be assumed with InventoryData.inventoryOpenTime
    */
    public static boolean hasInventoryOpen(final Player player) {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        return hasInventoryOpenOwnExcluded(player) || data.inventoryOpenTime != 0; 
    }
    
   /**
    * Test if the player has recently opened an inventory of any type (own, containers).
    * 
    * @param player
    * @param timeAge In milliseconds to be considered as 'recent activity' (inclusive)
    * @return True if the inventory has been opened within the specified time-frame.
    *         False if they've been in the inventory for some time (beyond age).
    * @throws IllegalArgumentException If the timeAge parameter is negative
    */
    public static boolean hasOpenedInvRecently(final Player player, final long timeAge) {
        if (timeAge < 0) {
            throw new IllegalArgumentException("timeAge cannot be negative.");
        }
        final long now = System.currentTimeMillis();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        return hasInventoryOpen(player) && (now - data.inventoryOpenTime <= timeAge);     
    }
    
   /** 
    * Checks if the time between block interaction and inventory click is recent.
    * 
    * @param player
    * @param timeAge In milliseconds between the BLOCK interaction and inventory click to be considered as 'recent activity' (exclusive)
    * @return True if the time between interaction and inventory click is too recent, false otherwise (beyond age).
    * @throws IllegalArgumentException If the timeAge parameter is negative
    */
    public static boolean isContainerInteractionRecent(final Player player, final long timeAge) {
        if (timeAge < 0) {
            throw new IllegalArgumentException("timeAge cannot be negative.");
        }
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        if (data.containerInteractTime == 0 || data.lastClickTime == 0) {
            return false;
        }
        // The player first interacts with the container, then clicks in its inventory, so interaction should always be smaller than click time
        return Math.abs(data.lastClickTime - data.containerInteractTime) < timeAge;
    }

    /**
     * Return the first consumable item found, checking main hand first and then
     * off hand, if available. Concerns food/edible, potions, milk bucket.
     *
     * @param player
     *            the player
     * @return null in case no item is consumable.
     */
    public static ItemStack getFirstConsumableItemInHand(final Player player) {
        ItemStack actualStack = Bridge1_9.getItemInMainHand(player);
        if (
                Bridge1_9.hasGetItemInOffHand()
                && (actualStack == null || !InventoryUtil.isConsumable(actualStack.getType()))
                ) {
            // Assume this to make sense.
            actualStack = Bridge1_9.getItemInOffHand(player);
            if (actualStack == null || !InventoryUtil.isConsumable(actualStack.getType())) {
                actualStack = null;
            }
        }
        return actualStack;
    }

    /**
     * Test if the ItemStack is consumable, like food, potions, milk bucket.
     *
     * @param stack
     *            May be null, would return false.
     * @return true, if is consumable
     */
    public static boolean isConsumable(final ItemStack stack) {
        if (stack == null) {
            return false;
        }
        return isConsumable(stack.getType());
    }

    /**
     * Test if the given InventoryType can hold items.
     * Meant for check-related contexts, thus containers with just 1 or 2 slots are excluded for convenience (performing checks for those would be over doing it).
     *
     * @param type
     *            May be null, would return false.
     * @return true, if is a container
     */
    public static boolean isContainerInventory(final InventoryType type) {
        if (type == null) {
            return false;
        }
        return CONTAINER_LIST.contains(type);
    }

    /**
     * Test if the Material is consumable, like food, potions, milk bucket.
     *
     * @param type
     *            May be null, would return false.
     * @return true, if is consumable
     */
    public static boolean isConsumable(final Material type) {
        return type != null && (type.isEdible() || type == Material.POTION || type == Material.MILK_BUCKET);
    }

    /**
     * Test for max durability, only makes sense with items that can be in
     * inventory once broken, such as elytra. This method does not (yet) provide
     * legacy support. This tests for ItemStack.getDurability() >=
     * Material.getMaxDurability, so it only is suited for a context where this is what you want to check for.
     * 
     * @param stack
     *            May be null, would yield true.
     * @return
     */
    public static boolean isItemBroken(final ItemStack stack) {
        if (stack == null) {
            return true;
        }
        final Material mat = stack.getType();
        return stack.getDurability() >= mat.getMaxDurability();
    }

    /**
     * Test if the player has an arrow in its inventory
     * 
     * @param i
     * @param fw
     * @return True, if there's an arrow in the inv.
     */
    public static boolean hasArrow(final PlayerInventory i, final boolean fw) {
        if (Bridge1_9.hasElytra()) {
            final Material m = i.getItemInOffHand().getType();
            return (fw && m == Material.FIREWORK_ROCKET) 
                    || m.toString().endsWith("ARROW")
                    || i.contains(Material.ARROW) || i.contains(Material.TIPPED_ARROW) || i.contains(Material.SPECTRAL_ARROW);
        }
        return i.contains(Material.ARROW);
    }
    
    /**
     * Attempt to resync the player's item, by forcibly releasing it via NMS or refreshing it via a Bukkit method. <br>
     * This is not context-aware. You'll need to check for preconditions yourself before calling this method.
     * 
     * @param player
     * @param pData
     */
    public static void itemResyncTask(final Player player, final IPlayerData pData) {
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        final boolean ServerIsAtLeast1_13 = ServerVersion.isAtLeast("1.13");
        // Handle via NMS
        if (mcAccess.getHandle().resetActiveItem(player)) {
            // Released, reset all data and request an inventory update to the server, for good measure.
            tryResetItemUsageStatus(pData);
            pData.requestUpdateInventory();
            return;
        }
        // NMS service isn't available, fall-back to a Bukkit-based method: attempt to get and set the item that is currently in hand.
        if (Bridge1_9.hasGetItemInOffHand() && data.offHandUse) {
            // Offhand
            ItemStack stack = Bridge1_9.getItemInOffHand(player);
            if (stack != null) {
                if (ServerIsAtLeast1_13) {
                    if (player.isHandRaised()) {
                        // Does nothing
                    }
                    else {
                        // False positive
                    	tryResetItemUsageStatus(pData);
                    }
                } 
                else {
                    // Refresh.
                    player.getInventory().setItemInOffHand(stack);
                    tryResetItemUsageStatus(pData);
                }
            }
            return;
        }
        if (!data.offHandUse) {
            // Main hand
            ItemStack stack = Bridge1_9.getItemInMainHand(player);
            if (stack != null) {
                if (ServerIsAtLeast1_13) {
                    if (player.isHandRaised()) {
                        //data.olditemslot = player.getInventory().getHeldItemSlot();
                        //if (stack != null) player.setCooldown(stack.getType(), 10);
                        //player.getInventory().setHeldItemSlot((data.olditemslot + 1) % 9);
                        //data.changeslot = true;
                        // Does nothing
                    }
                    // False positive
                    else {
                        tryResetItemUsageStatus(pData);
                    }
                }
                else {
                    // Refresh.
                    Bridge1_9.setItemInMainHand(player, stack);
                    tryResetItemUsageStatus(pData);
                }
            } 
        }
    }

    private static void tryResetItemUsageStatus(final IPlayerData pData) {
        if (BridgeMisc.hasGetItemInUseMethod()) {
            // No need to do anything as we did not have to set the item in the first place
            return;
        }
        pData.setItemInUse(null);
    }
}