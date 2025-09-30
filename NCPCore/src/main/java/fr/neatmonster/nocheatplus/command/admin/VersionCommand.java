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
package fr.neatmonster.nocheatplus.command.admin;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.hooks.NCPHook;
import fr.neatmonster.nocheatplus.hooks.NCPHookManager;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

public class VersionCommand extends BaseCommand {

    public VersionCommand(JavaPlugin plugin) {
        super(plugin, "version", Permissions.COMMAND_VERSION, new String[]{"versions", "ver"});
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final Component info = getVersionInfo();

        NCPAPIProvider.getNoCheatPlusAPI().adventure().sender(sender).sendMessage(info);

        return true;
    }

    public static Component getVersionInfo() {
        final TextComponent.@NotNull Builder info = Component.text();

        final TextComponent.@NotNull Builder featureTagsComponent = Component.text();
        final TextComponent.@NotNull Builder hooksComponent = Component.text();
        final TextComponent.@NotNull Builder relatedPluginsComponent = Component.text();

        final MCAccess mcAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(MCAccess.class);
        final Map<String, Set<String>> featureTags = NCPAPIProvider.getNoCheatPlusAPI().getAllFeatureTags();
        final Collection<NCPHook> hooks = NCPHookManager.getAllHooks();
        final List<String> relatedPlugins = new LinkedList<>();

        info
                .append(Component.text("»Version information«", NamedTextColor.RED).decorate(TextDecoration.BOLD)).appendNewline()
                .append(Component.text("Server:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)).appendNewline()
                .append(Component.text(format(Bukkit.getServer().getVersion()), NamedTextColor.GRAY)).appendNewline()
                .append(Component.text("Detected: ", NamedTextColor.YELLOW)).append(Component.text(format(ServerVersion.getMinecraftVersion()), NamedTextColor.GRAY)).appendNewline()
                .append(Component.text("NoCheatPlus:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)).appendNewline()
                .append(Component.text("Plugin: ", NamedTextColor.YELLOW).append(Component.text(format(Bukkit.getPluginManager().getPlugin("NoCheatPlus").getDescription().getVersion()), NamedTextColor.GRAY)).appendNewline())
                .append(Component.text("MCAccess: ", NamedTextColor.YELLOW)).append(Component.text(format(mcAccess.getMCVersion() + " / " + mcAccess.getServerVersionTag()), NamedTextColor.GRAY)).appendNewline();

        if (!featureTags.isEmpty()) {
            final List<String> features = new LinkedList<>();
            // Add present features.
            for (final Entry<String, Set<String>> entry : featureTags.entrySet()) {
                features.add(format("&e" + entry.getKey() + "&7: " + StringUtil.join(entry.getValue(), "&f, &7")));
            }
            // Sort and add.
            features.sort(String.CASE_INSENSITIVE_ORDER);

            featureTagsComponent.append(Component.text("Features:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)).appendNewline();

            for (int i = 0; i < features.size(); i++) {
                final String feature = features.get(i);
                final Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(feature);
                featureTagsComponent.append(component);

                if (i < features.size() - 1) {
                    featureTagsComponent.appendNewline();
                }
            }

            featureTagsComponent.appendNewline();
            info.append(featureTagsComponent.build());
        }

        if (!hooks.isEmpty()) {
            final List<String> fullNames = new LinkedList<>();
            for (final NCPHook hook : hooks) {
                fullNames.add(format(hook.getHookName() + " " + hook.getHookVersion()));
            }
            fullNames.sort(String.CASE_INSENSITIVE_ORDER);

            final String hooksStr = "&6&lHooks:\n&7" + StringUtil.join(fullNames, "&f, &7");

            hooksComponent.append(LegacyComponentSerializer.legacyAmpersand().deserialize(hooksStr)).appendNewline();
            info.append(hooksComponent.build());
        }

        for (final String name : new String[]{"CompatNoCheatPlus", "ProtocolLib", "ViaVersion", "ProtocolSupport", "PNCP", "NTAC"}) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null) {
                relatedPlugins.add(format(plugin.getDescription().getFullName()));
            }
        }

        if (!relatedPlugins.isEmpty()) {
            relatedPluginsComponent
                    .append(Component.text("»Related Plugins«", NamedTextColor.RED).decorate(TextDecoration.BOLD)).appendNewline()
                    .append(LegacyComponentSerializer.legacyAmpersand().deserialize(StringUtil.join(relatedPlugins, "&f, &7")));
        }

        return info.build();
    }

    public static String getFormattedVersionInfo() {
        final Component formatted = getVersionInfo().replaceText(TextReplacementConfig.builder().matchLiteral("\n").replacement(Component.newline()).build());

        return PlainTextComponentSerializer.plainText().serialize(formatted);
    }

    private static String format(String x) {
        return x.replace('(', '~').replace(')', '~');
    }

}
