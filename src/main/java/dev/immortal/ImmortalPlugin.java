package dev.immortal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ImmortalPlugin extends JavaPlugin implements Listener, TabExecutor {

    private final Set<UUID> immortal = new HashSet<>();
    private File dataFile;
    private boolean blockKillCommand;
    private boolean blockVoid;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        blockKillCommand = getConfig().getBoolean("block-kill-command", false);
        blockVoid = getConfig().getBoolean("block-void", false);

        dataFile = new File(getDataFolder(), "data.yml");
        loadData();

        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("immortal");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ---------------------------------------------------------------- damage

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!immortal.contains(player.getUniqueId())) return;

        DamageCause cause = event.getCause();
        if (cause == DamageCause.KILL && !blockKillCommand) return;
        if (cause == DamageCause.VOID && !blockVoid) return;

        // Not fatal? Let it through normally.
        if (event.getFinalDamage() < player.getHealth()) return;

        // Fatal, but a totem is held: let vanilla use the totem.
        if (hasTotem(player)) return;

        // Fatal and no totem: cancel it and leave the player at half a heart.
        event.setCancelled(true);
        player.setHealth(1.0);
    }

    private boolean hasTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    // --------------------------------------------------------------- command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;

        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage(Component.text("Usage: /" + label + " <player>", NamedTextColor.RED));
                return true;
            }
            if (!sender.hasPermission("immortal.use")) {
                sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return true;
            }
            target = self;
        } else {
            if (!sender.hasPermission("immortal.others")) {
                sender.sendMessage(Component.text("You don't have permission to do that for other players.", NamedTextColor.RED));
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[0] + "' is not online.", NamedTextColor.RED));
                return true;
            }
        }

        boolean nowOn = immortal.add(target.getUniqueId());
        if (!nowOn) immortal.remove(target.getUniqueId());
        saveData();

        Component state = nowOn
                ? Component.text("ON", NamedTextColor.GREEN)
                : Component.text("OFF", NamedTextColor.RED);

        if (sender.equals(target)) {
            sender.sendMessage(Component.text("Immortality: ", NamedTextColor.GRAY).append(state));
        } else {
            sender.sendMessage(Component.text("Immortality for " + target.getName() + ": ", NamedTextColor.GRAY).append(state));
            target.sendMessage(Component.text("Immortality: ", NamedTextColor.GRAY).append(state));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("immortal.others")) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    // ------------------------------------------------------------ persistence

    private void loadData() {
        immortal.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String s : yaml.getStringList("immortal")) {
            try {
                immortal.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("immortal", immortal.stream().map(UUID::toString).collect(Collectors.toList()));
        try {
            getDataFolder().mkdirs();
            yaml.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }
}
