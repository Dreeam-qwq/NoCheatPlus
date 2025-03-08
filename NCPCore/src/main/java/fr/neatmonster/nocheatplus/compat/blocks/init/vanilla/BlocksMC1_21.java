package fr.neatmonster.nocheatplus.compat.blocks.init.vanilla;

import fr.neatmonster.nocheatplus.compat.blocks.BlockPropertiesSetup;
import fr.neatmonster.nocheatplus.compat.blocks.init.BlockInit;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.config.WorldConfigProvider;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import org.bukkit.Material;

public class BlocksMC1_21 implements BlockPropertiesSetup {

    @Override
    public void setupBlockProperties(WorldConfigProvider<?> worldConfigProvider) {

        if (ServerVersion.compareMinecraftVersion("1.21.4") >= 0) {
            BlockInit.setAs("RESIN_BLOCK", Material.ANDESITE);
            BlockInit.setAs("RESIN_BRICKS", Material.ANDESITE);
            BlockInit.setAs("RESIN_BRICK_SLAB", Material.ANDESITE_SLAB);
            BlockInit.setAs("RESIN_BRICK_STAIRS", Material.ANDESITE_STAIRS);
            BlockInit.setAs("RESIN_BRICK_WALL", Material.ANDESITE_WALL);
            BlockInit.setAs("CHISELED_RESIN_BRICKS", Material.ANDESITE);

            BlockInit.setAs("CREAKING_HEART", Material.ANDESITE);

            BlockInit.setInstantPassable("RESIN_CLUMP");
            BlockInit.setInstantPassable("CLOSED_EYEBLOSSOM");
            BlockInit.setInstantPassable("OPEN_EYEBLOSSOM");
            BlockInit.setInstantPassable("PALE_HANGING_MOSS");

            BlockInit.setAs("PALE_OAK_LOG", Material.OAK_LOG);
            BlockInit.setAs("PALE_OAK_WOOD", Material.OAK_WOOD);
            BlockInit.setAs("PALE_OAK_PLANKS", Material.OAK_PLANKS);
            BlockInit.setAs("PALE_OAK_SLAB", Material.OAK_SLAB);
            BlockInit.setAs("PALE_OAK_STAIRS", Material.OAK_STAIRS);
            BlockInit.setAs("PALE_OAK_FENCE", Material.OAK_FENCE);
            BlockInit.setAs("PALE_OAK_FENCE_GATE", Material.OAK_FENCE_GATE);
            BlockInit.setAs("PALE_OAK_DOOR", Material.OAK_DOOR);
            BlockInit.setAs("PALE_OAK_TRAPDOOR", Material.OAK_TRAPDOOR);
            BlockInit.setAs("PALE_OAK_HANGING_SIGN", Material.OAK_HANGING_SIGN);
            BlockInit.setAs("PALE_MOSS_BLOCK", Material.MOSS_BLOCK);
            BlockInit.setAs("PALE_OAK_LEAVES", Material.OAK_LEAVES);
            BlockInit.setAs("PALE_OAK_SIGN", Material.OAK_SIGN);
            BlockInit.setAs("PALE_OAK_WALL_SIGN", Material.OAK_WALL_SIGN);
            BlockInit.setAs("PALE_OAK_WALL_HANGING_SIGN", Material.OAK_WALL_HANGING_SIGN);

            BlockInit.setInstantPassable("PALE_OAK_PRESSURE_PLATE");
            BlockInit.setInstantPassable("PALE_OAK_BUTTON");
        }

        ConfigFile config = ConfigManager.getConfigFile();
        if (config.getBoolean(ConfPaths.BLOCKBREAK_DEBUG, config.getBoolean(ConfPaths.CHECKS_DEBUG, false))) {
            StaticLog.logInfo("Added block-info for Minecraft 1.21 blocks");
        }
    }
}

