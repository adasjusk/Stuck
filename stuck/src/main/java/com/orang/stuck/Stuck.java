package com.orang.stuck;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class Stuck extends JavaPlugin {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();

    // Configurable parameters
    private int cooldownSeconds;
    private int searchRadius;
    private int maxAttempts;
    private int minY;
    private int maxY;
    private boolean debug;
    private boolean avoidHazardousBlocks;
    private boolean avoidUnstableBlocks;
    private boolean printSuccessMessage;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
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
        // We only registered the "stuck" command; both /stuck and /escape will trigger this
        if (!cmd.getName().equalsIgnoreCase("stuck")) {
            return false;
        }
        
        // Debug message to check which alias was used (optional)
        if (debug) {
            getLogger().info("Command triggered by " + sender.getName() + " using alias: " + label);
        }
        
        // Ensure the sender is a player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        
        // Check permission
        if (!player.hasPermission("stuck.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        // Check cooldown
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(uuid)) {
            long elapsed = now - cooldowns.get(uuid);
            long remaining = (cooldownSeconds * 1000L - elapsed) / 1000;
            if (remaining > 0) {
                player.sendMessage(ChatColor.RED + "Please wait " + remaining + " seconds before using this command again.");
                return true;
            }
        }
        
        // Validate current world
        World world = player.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Error: Could not determine your current world.");
            getLogger().warning("Player " + player.getName() + " has a null world reference!");
            return true;
        }
        
        // Notify player that search is beginning
        player.sendMessage(ChatColor.YELLOW + "Searching for a safe location...");
        
        // Attempt to find a safe location
        Location safeLoc = findSafeLocation(player.getLocation());
        
        if (safeLoc != null) {
            // Set cooldown immediately to prevent spam
            cooldowns.put(uuid, now);
            
            // Show title and effects before teleporting
            player.sendTitle(ChatColor.LIGHT_PURPLE + "Escaping...", ChatColor.DARK_PURPLE + "Hold tight!", 10, 40, 10);
            spawnParticleEffect(player.getLocation(), Particle.PORTAL);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 1.2f, 0.9f);
            
            if (debug) {
                getLogger().info("Found safe location for " + player.getName() + ": " +
                                   safeLoc.getWorld().getName() + " at " + safeLoc.getX() + ", " +
                                   safeLoc.getY() + ", " + safeLoc.getZ());
            }
            
            // Delay teleport by 2 seconds (40 ticks) for dramatic effect
            Bukkit.getScheduler().runTaskLater(this, () -> {
                // Ensure the destination chunk is loaded (improved for 1.21.10)
                try {
                    if (!safeLoc.getWorld().isChunkLoaded(safeLoc.getBlockX() >> 4, safeLoc.getBlockZ() >> 4)) {
                        safeLoc.getChunk().load();
                        // Give chunk a moment to fully load
                        Bukkit.getScheduler().runTaskLater(this, () -> performTeleport(player, safeLoc), 5L);
                    } else {
                        performTeleport(player, safeLoc);
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Failed to prepare teleportation location: " + e.getMessage());
                    getLogger().severe("Chunk loading error for " + player.getName() + ": " + e.getMessage());
                }
            }, 40L);
        } else {
            player.sendMessage(ChatColor.RED + "Could not find a safe location. Please try again or contact an admin.");
            getLogger().warning("Failed to find safe location for " + player.getName() + " in world " + world.getName());
        }
        return true;
    }

    /**
     * Performs the actual teleportation with proper error handling.
     * Separated for better code organization and reusability.
     */
    private void performTeleport(Player player, Location safeLoc) {
        try {
            boolean success = player.teleport(safeLoc);
            if (!success) {
                player.sendMessage(ChatColor.RED + "Teleportation failed. Try again or contact an admin.");
                getLogger().warning("Teleport failed for " + player.getName() + " to " +
                                      safeLoc.getWorld().getName() + " at " + safeLoc.getX() + ", " +
                                      safeLoc.getY() + ", " + safeLoc.getZ());
            } else {
                playTeleportSound(safeLoc);
                spawnParticleEffect(safeLoc, Particle.END_ROD);
                if (printSuccessMessage) {
                    player.sendMessage(ChatColor.GREEN + "There you go! You've been teleported to a safe location.");
                }
                
                if (debug) {
                    getLogger().info("Successfully teleported " + player.getName() + " to " +
                                       safeLoc.getWorld().getName() + " at " + safeLoc.getBlockX() + ", " +
                                       safeLoc.getBlockY() + ", " + safeLoc.getBlockZ());
                }
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "An error occurred during teleportation: " + e.getMessage());
            getLogger().severe("Error teleporting " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Searches for a safe location based on configuration values.
     * Enhanced for 1.21.10 with better world height handling and hazard detection.
     */
    private Location findSafeLocation(Location start) {
        World world = start.getWorld();
        if (world == null) {
            getLogger().warning("Failed to get world from location");
            return null;
        }

        // Adjust min/max Y based on world environment and modern world heights
        int worldMinY = Math.max(world.getMinHeight() + 1, minY); // +1 to avoid bedrock level
        int worldMaxY = Math.min(world.getMaxHeight() - 3, maxY); // -3 for head clearance
        
        // For 1.21+ worlds, use extended height ranges if not configured
        if (minY == 32 && maxY == 120) { // Default values
            worldMinY = Math.max(world.getMinHeight() + 5, -50); // Better default for new worlds
            worldMaxY = Math.min(world.getMaxHeight() - 3, 200); // Better default for new worlds
        }
        
        if (debug) {
            getLogger().info("Looking for safe location in world " + world.getName() +
                               " around " + start.getBlockX() + ", " + start.getBlockY() + ", " + start.getBlockZ() +
                               " with Y range " + worldMinY + "-" + worldMaxY);
        }

        // Try to find a safe spot up to maxAttempts times
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int xOffset = random.nextInt(searchRadius * 2 + 1) - searchRadius;
            int zOffset = random.nextInt(searchRadius * 2 + 1) - searchRadius;
            int x = start.getBlockX() + xOffset;
            int z = start.getBlockZ() + zOffset;

            // Loop through Y from top to bottom in the specified range
            for (int y = worldMaxY; y >= worldMinY; y--) {
                try {
                    Block ground = world.getBlockAt(x, y - 1, z);
                    Block feet = world.getBlockAt(x, y, z);
                    Block head = world.getBlockAt(x, y + 1, z);
                    
                    if (debug && attempt % 10 == 0) {
                        getLogger().info("Attempt " + attempt + " checking (" + x + ", " + y + ", " + z + "): " +
                                           ground.getType() + ", " + feet.getType() + ", " + head.getType());
                    }
                    
                    // Check for a safe location (solid ground, air at feet and head level)
                    if (ground.getType().isSolid() &&
                        (!avoidUnstableBlocks || (!ground.getType().toString().contains("LEAVES") && // Avoid leaves as they can be temporary
                        !ground.getType().toString().contains("SNOW"))) && // Avoid snow layers
                        feet.getType() == Material.AIR &&
                        head.getType() == Material.AIR &&
                        !ground.isLiquid() &&
                        (!avoidHazardousBlocks || !isHazardousBlock(ground))) { // Check for hazardous blocks
                        
                        // Create location with centered coordinates and correct orientation
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
            getLogger().warning("Could not find safe location after " + maxAttempts + " attempts");
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

    /**
     * Checks if a block is hazardous to teleport on top of.
     * Updated for 1.21.10 compatibility with additional hazard checks.
     */
    private boolean isHazardousBlock(Block block) {
        Material type = block.getType();
        String typeName = type.toString();
        
        // Check for dangerous blocks (updated for 1.21.10)
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
               // Additional 1.21+ hazards
               (typeName.contains("COPPER") && typeName.contains("EXPOSED")) ||
               (typeName.contains("TRIAL") && typeName.contains("SPAWNER"));
    }
}