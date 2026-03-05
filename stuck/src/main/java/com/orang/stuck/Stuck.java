package com.orang.stuck;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class Stuck extends JavaPlugin {
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();
    private int cooldownSeconds;
    private int searchRadius;
    private int maxAttempts;
    private int minY;
    private int maxY;
    private boolean debug;
    private boolean avoidHazardousBlocks;
    private boolean avoidUnstableBlocks;
    private boolean printSuccessMessage;
    private boolean isPaper;
    private boolean isFolia;
    @Override
    public void onEnable() {
        isPaper = detectPaper();
        isFolia = detectFolia();
        if (isFolia) {
            getLogger().info("Folia detected! Using regionized scheduling.");
        } else if (isPaper) {
            getLogger().info("Paper detected! Using async teleportation.");
        } else {
            getLogger().info("Spigot detected. Using synchronous teleportation.");
        }
        saveDefaultConfig();
        loadConfigValues();
        if (getCommand("stuck") == null) {
            getLogger().severe("Command 'stuck' not defined in plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Objects.requireNonNull(getCommand("stuck")).setExecutor(this);
        getLogger().info("stuck enabled thanks for using it!");
    }
    private boolean detectPaper() {
        try {
            Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    @Override
    public void onDisable() {
        getLogger().info("stuck disabled.");
    }
    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        cooldownSeconds = config.getInt("cooldown-seconds", 30);
        searchRadius = config.getInt("search-radius", 5);
        maxAttempts = config.getInt("max-attempts", 50);
        minY = config.getInt("min-y", 32);
        maxY = config.getInt("max-y", 120);
        debug = config.getBoolean("debug", false);
        avoidHazardousBlocks = config.getBoolean("avoid-hazardous-blocks", true);
        avoidUnstableBlocks = config.getBoolean("avoid-unstable-blocks", true);
        printSuccessMessage = config.getBoolean("print-success-message", true);
        getLogger().info("Loaded configuration: cooldown=" + cooldownSeconds + "s, radius=" + searchRadius +
                           ", maxAttempts=" + maxAttempts + ", Y-range=" + minY + "-" + maxY +
                           ", avoidHazards=" + avoidHazardousBlocks + ", avoidUnstable=" + avoidUnstableBlocks +
                           ", printSuccess=" + printSuccessMessage);
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("stuck")) {
            return false;
        }
        if (debug) {
            getLogger().info("Command triggered by " + sender.getName() + " using alias: " + label);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("stuck.use")) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(uuid)) {
            long elapsed = now - cooldowns.get(uuid);
            long remaining = (cooldownSeconds * 1000L - elapsed) / 1000;
            if (remaining > 0) {
                player.sendMessage(Component.text("Please wait " + remaining + " seconds before using this command again.", NamedTextColor.RED));
                return true;
            }
        }
        World world = player.getWorld();
        if (world == null) {
            player.sendMessage(Component.text("Error: Could not determine your current world.", NamedTextColor.RED));
            getLogger().warning("Player " + player.getName() + " has a null world reference!");
            return true;
        }
        player.sendMessage(Component.text("Searching for a safe location...", NamedTextColor.YELLOW));
        Location safeLoc = findSafeLocation(player.getLocation());
        if (safeLoc != null) {
            cooldowns.put(uuid, now);
            player.showTitle(Title.title(
                    Component.text("Escaping...", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Hold tight!", NamedTextColor.DARK_PURPLE),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
            ));
            spawnParticleEffect(player.getLocation(), Particle.PORTAL);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 1.2f, 0.9f);
            if (debug) {
                getLogger().info("Found safe location for " + player.getName() + ": " +
                                   safeLoc.getWorld().getName() + " at " + safeLoc.getX() + ", " +
                                   safeLoc.getY() + ", " + safeLoc.getZ());
            }
            schedulePlayerTask(player, () -> {
                try {
                    if (isPaper) {
                        performTeleport(player, safeLoc);
                    } else if (!safeLoc.getWorld().isChunkLoaded(safeLoc.getBlockX() >> 4, safeLoc.getBlockZ() >> 4)) {
                        safeLoc.getChunk().load();
                        schedulePlayerTask(player, () -> performTeleport(player, safeLoc), 5L);
                    } else {
                        performTeleport(player, safeLoc);
                    }
                } catch (Exception e) {
                    player.sendMessage(Component.text("Failed to prepare teleportation location: " + e.getMessage(), NamedTextColor.RED));
                    getLogger().severe("Chunk loading error for " + player.getName() + ": " + e.getMessage());
                }
            }, 40L);
        } else {
            player.sendMessage(Component.text("Could not find a safe location. Please try again or contact an admin.", NamedTextColor.RED));
            getLogger().warning("Failed to find safe location for " + player.getName() + " in world " + world.getName());
        }
        return true;
    }
    private void performTeleport(Player player, Location safeLoc) {
        if (isPaper) {
            player.teleportAsync(safeLoc).thenAccept(success -> {
                if (success) {
                    onTeleportSuccess(player, safeLoc);
                } else {
                    onTeleportFail(player, safeLoc);
                }
            });
        } else {
            try {
                boolean success = player.teleport(safeLoc);
                if (success) {
                    onTeleportSuccess(player, safeLoc);
                } else {
                    onTeleportFail(player, safeLoc);
                }
            } catch (Exception e) {
                player.sendMessage(Component.text("An error occurred during teleportation: " + e.getMessage(), NamedTextColor.RED));
                getLogger().severe("Error teleporting " + player.getName() + ": " + e.getMessage());
            }
        }
    }
    private void onTeleportSuccess(Player player, Location safeLoc) {
        playTeleportSound(safeLoc);
        spawnParticleEffect(safeLoc, Particle.END_ROD);
        if (printSuccessMessage) {
            player.sendMessage(Component.text("There you go! You've been teleported to a safe location.", NamedTextColor.GREEN));
        }
        if (debug) {
            getLogger().info("Successfully teleported " + player.getName() + " to " +
                               safeLoc.getWorld().getName() + " at " + safeLoc.getBlockX() + ", " +
                               safeLoc.getBlockY() + ", " + safeLoc.getBlockZ());
        }
    }
    private void onTeleportFail(Player player, Location safeLoc) {
        player.sendMessage(Component.text("Teleportation failed. Try again or contact an admin.", NamedTextColor.RED));
        getLogger().warning("Teleport failed for " + player.getName() + " to " +
                              safeLoc.getWorld().getName() + " at " + safeLoc.getX() + ", " +
                              safeLoc.getY() + ", " + safeLoc.getZ());
    }
    private void schedulePlayerTask(Player player, Runnable task, long delayTicks) {
        if (isPaper) {
            player.getScheduler().runDelayed(this, scheduledTask -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(this, task, delayTicks);
        }
    }
    private static final int NETHER_ROOF_Y = 127;
    private Location findSafeLocation(Location start) {
        World world = start.getWorld();
        if (world == null) {
            getLogger().warning("Failed to get world from location");
            return null;
        }
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;
        boolean onNetherRoof = isNether && start.getBlockY() >= NETHER_ROOF_Y;
        int worldMinY = Math.max(world.getMinHeight() + 1, minY);
        int worldMaxY = Math.min(world.getMaxHeight() - 3, maxY);
        if (minY == 32 && maxY == 120) {
            worldMinY = Math.max(world.getMinHeight() + 5, -50);
            worldMaxY = Math.min(world.getMaxHeight() - 3, 200);
        }
        if (isNether) {
            worldMaxY = Math.min(worldMaxY, NETHER_ROOF_Y - 1); // cap at 125
        }
        int effectiveRadius = searchRadius;
        int effectiveAttempts = maxAttempts;
        if (onNetherRoof) {
            effectiveRadius = Math.max(searchRadius, 16);
            effectiveAttempts = Math.max(maxAttempts, 200);
            if (debug) {
                getLogger().info("Player is on nether roof — expanding search radius to " +
                                   effectiveRadius + " and attempts to " + effectiveAttempts);
            }
        }
        if (debug) {
            getLogger().info("Looking for safe location in world " + world.getName() +
                               " around " + start.getBlockX() + ", " + start.getBlockY() + ", " + start.getBlockZ() +
                               " with Y range " + worldMinY + "-" + worldMaxY +
                               (isNether ? " (Nether mode)" : ""));
        }
        for (int attempt = 0; attempt < effectiveAttempts; attempt++) {
            int xOffset = random.nextInt(effectiveRadius * 2 + 1) - effectiveRadius;
            int zOffset = random.nextInt(effectiveRadius * 2 + 1) - effectiveRadius;
            int x = start.getBlockX() + xOffset;
            int z = start.getBlockZ() + zOffset;
            for (int y = worldMaxY; y >= worldMinY; y--) {
                try {
                    Block ground = world.getBlockAt(x, y - 1, z);
                    Block feet = world.getBlockAt(x, y, z);
                    Block head = world.getBlockAt(x, y + 1, z);
                    if (debug && attempt % 10 == 0) {
                        getLogger().info("Attempt " + attempt + " checking (" + x + ", " + y + ", " + z + "): " +
                                           ground.getType() + ", " + feet.getType() + ", " + head.getType());
                    }
                    if (isNether && y - 1 >= 123 && ground.getType() == Material.BEDROCK) {
                        continue;
                    }
                    if (ground.getType().isSolid() &&
                        (!avoidUnstableBlocks || (!ground.getType().toString().contains("LEAVES") &&
                        !ground.getType().toString().contains("SNOW"))) &&
                        feet.getType() == Material.AIR &&
                        head.getType() == Material.AIR &&
                        !ground.isLiquid() &&
                        (!avoidHazardousBlocks || !isHazardousBlock(ground))) {
                        Location safeLocation = new Location(world, x + 0.5, y, z + 0.5, start.getYaw(), start.getPitch());
                        if (debug) {
                            getLogger().info("Found safe location at " + safeLocation.getBlockX() + ", " +
                                               safeLocation.getBlockY() + ", " + safeLocation.getBlockZ());
                        }
                        return safeLocation;
                    }
                } catch (Exception e) {
                    getLogger().warning("Error checking location at (" + x + ", " + y + ", " + z + "): " + e.getMessage());
                }
            }
        }
        if (debug) {
            getLogger().warning("Could not find safe location after " + effectiveAttempts + " attempts");
        }
        return null;
    }
    private void spawnParticleEffect(Location loc, Particle particle) {
        World world = loc.getWorld();
        if (world != null) {
            try {
                world.spawnParticle(particle, loc.clone().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.1);
            } catch (Exception e) {
                if (debug) {
                    getLogger().warning("Failed to spawn particles: " + e.getMessage());
                }
            }
        }
    }
    private void playTeleportSound(Location loc) {
        World world = loc.getWorld();
        if (world != null) {
            try {
                world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.0f);
            } catch (Exception e) {
                if (debug) {
                    getLogger().warning("Failed to play sound: " + e.getMessage());
                }
            }
        }
    }
    private boolean isHazardousBlock(Block block) {
        Material type = block.getType();
        String typeName = type.toString();
        return typeName.contains("LAVA") ||
               typeName.contains("FIRE") ||
               typeName.contains("MAGMA") ||
               typeName.contains("CACTUS") ||
               typeName.contains("SWEET_BERRY") ||
               typeName.contains("CAMPFIRE") ||
               typeName.contains("WITHER_ROSE") ||
               typeName.contains("POINTED_DRIPSTONE") ||
               type == Material.SOUL_FIRE ||
               type == Material.SOUL_CAMPFIRE ||
               (typeName.contains("COPPER") && typeName.contains("EXPOSED")) ||
               (typeName.contains("TRIAL") && typeName.contains("SPAWNER"));
    }
}