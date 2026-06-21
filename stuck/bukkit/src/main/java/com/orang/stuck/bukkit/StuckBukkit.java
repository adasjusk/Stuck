package com.orang.stuck.bukkit;
import com.orang.stuck.common.SafeSpotFinder;
import com.orang.stuck.common.StuckConfig;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class StuckBukkit extends JavaPlugin {
	private final Map<UUID, Long> cooldowns = new HashMap<>();
	private final StuckConfig cfg = new StuckConfig();
	private SafeSpotFinder finder;
	private boolean isPaper;
	@Override
	public void onEnable() {
		isPaper = detectPaper();
		boolean isFolia = detectFolia();
		if (isFolia) {
			getLogger().info("Folia detected!. Wow you run big server huh");
		} else if (isPaper) {
			getLogger().info("Paper detected!. Ok decent choice, you should be good.");
		} else {
			getLogger().info("Spigot detected, why tf you even use it, upgrade to paper already..");
		}
		saveDefaultConfig();
		loadConfigValues();
		finder = new SafeSpotFinder(cfg, msg -> getLogger().info(msg));
		if (getCommand("stuck") == null) {
			getLogger().severe("Command 'stuck' not defined in plugin.yml! define it or else, idk");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		Objects.requireNonNull(getCommand("stuck")).setExecutor(this);
		getLogger().info("stuck is enabled and thanks for using it! We will not use your server's analytics.");
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
		cfg.cooldownSeconds = config.getInt("cooldown-seconds", 30);
		cfg.searchRadius = config.getInt("search-radius", 5);
		cfg.maxAttempts = config.getInt("max-attempts", 50);
		cfg.minY = config.getInt("min-y", 32);
		cfg.maxY = config.getInt("max-y", 120);
		cfg.debug = config.getBoolean("debug", false);
		cfg.avoidHazardousBlocks = config.getBoolean("avoid-hazardous-blocks", true);
		cfg.avoidUnstableBlocks = config.getBoolean("avoid-unstable-blocks", true);
		cfg.printSuccessMessage = config.getBoolean("print-success-message", true);
		getLogger().info("Loaded configuration: cooldown=" + cfg.cooldownSeconds + "s, radius=" + cfg.searchRadius +
				", maxAttempts=" + cfg.maxAttempts + ", Y-range=" + cfg.minY + "-" + cfg.maxY +
				", avoidHazards=" + cfg.avoidHazardousBlocks + ", avoidUnstable=" + cfg.avoidUnstableBlocks +
				", printSuccess=" + cfg.printSuccessMessage);
	}
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!cmd.getName().equalsIgnoreCase("stuck")) {
			return false;
		}
		if (!(sender instanceof Player player)) {
			sender.sendMessage(ChatColor.RED + "Only players can use this command.");
			return true;
		}
		if (!player.hasPermission("stuck.use")) {
			player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
			return true;
		}
		UUID uuid = player.getUniqueId();
		long now = System.currentTimeMillis();
		if (cooldowns.containsKey(uuid)) {
			long remaining = (cfg.cooldownSeconds * 1000L - (now - cooldowns.get(uuid))) / 1000;
			if (remaining > 0) {
				player.sendMessage(ChatColor.RED + "Please wait " + remaining + " seconds before using this command again.");
				return true;
			}
		}
		World world = player.getWorld();
		if (world == null) {
			player.sendMessage(ChatColor.RED + "Error: Could not determine your current world.");
			return true;
		}
		player.sendMessage(ChatColor.YELLOW + "Searching for a safe location...");
		Location start = player.getLocation();
		int[] spot = finder.find(new BukkitBlockView(world), start.getBlockX(), start.getBlockY(), start.getBlockZ());
		if (spot == null) {
			player.sendMessage(ChatColor.RED + "Could not find a safe location. Please try again or contact an admin.");
			return true;
		}
		cooldowns.put(uuid, now);
		Location safeLoc = new Location(world, spot[0] + 0.5, spot[1], spot[2] + 0.5, start.getYaw(), start.getPitch());
		player.sendTitle(ChatColor.LIGHT_PURPLE + "Escaping...", ChatColor.DARK_PURPLE + "Hold tight!", 10, 40, 10);
		spawnParticleEffect(start, Particle.PORTAL);
		world.playSound(start, Sound.ENTITY_ENDERMAN_AMBIENT, 1.2f, 0.9f);
		schedulePlayerTask(player, () -> {
			try {
				if (isPaper) {
					performTeleport(player, safeLoc);
				} else if (!world.isChunkLoaded(safeLoc.getBlockX() >> 4, safeLoc.getBlockZ() >> 4)) {
					safeLoc.getChunk().load();
					schedulePlayerTask(player, () -> performTeleport(player, safeLoc), 5L);
				} else {
					performTeleport(player, safeLoc);
				}
			} catch (Exception e) {
				player.sendMessage(ChatColor.RED + "Failed to prepare teleportation location: " + e.getMessage());
				getLogger().severe("Chunk loading error for " + player.getName() + ": " + e.getMessage());
			}
		}, 40L);
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
				if (player.teleport(safeLoc)) {
					onTeleportSuccess(player, safeLoc);
				} else {
					onTeleportFail(player, safeLoc);
				}
			} catch (Exception e) {
				player.sendMessage(ChatColor.RED + "An error occurred during teleportation: " + e.getMessage());
				getLogger().severe("Error teleporting " + player.getName() + ": " + e.getMessage());
			}
		}
	}
	private void onTeleportSuccess(Player player, Location safeLoc) {
		playTeleportSound(safeLoc);
		spawnParticleEffect(safeLoc, Particle.END_ROD);
		if (cfg.printSuccessMessage) {
			player.sendMessage(ChatColor.GREEN + "There you go! You've been teleported to a safe location.");
		}
	}

	private void onTeleportFail(Player player, Location safeLoc) {
		player.sendMessage(ChatColor.RED + "Teleportation failed. Try again or contact an admin.");
		getLogger().warning("Teleport failed for " + player.getName());
	}

	private void schedulePlayerTask(Player player, Runnable task, long delayTicks) {
		if (isPaper) {
			player.getScheduler().runDelayed(this, scheduledTask -> task.run(), null, delayTicks);
		} else {
			Bukkit.getScheduler().runTaskLater(this, task, delayTicks);
		}
	}

	private void spawnParticleEffect(Location loc, Particle particle) {
		World world = loc.getWorld();
		if (world != null) {
			try {
				world.spawnParticle(particle, loc.clone().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.1);
			} catch (Exception ignored) {
}	}	}

	private void playTeleportSound(Location loc) {
		World world = loc.getWorld();
		if (world != null) {
			try {
				world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.0f);
			} catch (Exception ignored) {
			}
}	}	}