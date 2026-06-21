package com.orang.stuck.fabric;
import com.mojang.brigadier.context.CommandContext;
import com.orang.stuck.common.SafeSpotFinder;
import com.orang.stuck.common.StuckConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

public class StuckFabric implements ModInitializer {
	private static final Logger LOGGER = Logger.getLogger("stuck");
	private final Map<UUID, Long> cooldowns = new HashMap<>();
	private final StuckConfig cfg = new StuckConfig();
	private SafeSpotFinder finder;
	@Override
	public void onInitialize() {
		loadConfig();
		finder = new SafeSpotFinder(cfg, LOGGER::info);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("stuck").executes(this::runStuck));
			dispatcher.register(Commands.literal("escape").executes(this::runStuck));
		});
		LOGGER.info("stuck enabled thanks for using it!");
	}

	private int runStuck(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only players can use this command."));
			return 0;
		}
		UUID uuid = player.getUUID();
		long now = System.currentTimeMillis();
		Long last = cooldowns.get(uuid);
		if (last != null) {
			long remaining = (cfg.cooldownSeconds * 1000L - (now - last)) / 1000;
			if (remaining > 0) {
				source.sendFailure(Component.literal("Please wait " + remaining + " seconds before using this command again."));
				return 0;
			}
		}
		ServerLevel level = player.level();
		source.sendSystemMessage(Component.literal("Searching for a safe location...").withStyle(ChatFormatting.YELLOW));

		int sx = player.blockPosition().getX();
		int sy = player.blockPosition().getY();
		int sz = player.blockPosition().getZ();
		int[] spot = finder.find(new FabricBlockView(level), sx, sy, sz);
		if (spot == null) {
			source.sendFailure(Component.literal("Could not find a safe location. Please try again or contact an admin."));
			return 0;
		}

		cooldowns.put(uuid, now);
		player.teleportTo(spot[0] + 0.5, spot[1], spot[2] + 0.5);
		if (cfg.printSuccessMessage) {
			source.sendSuccess(() -> Component.literal("There you go! You've been teleported to a safe location.")
					.withStyle(ChatFormatting.GREEN), false);
		}
		return 1;
	}

	private void loadConfig() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("stuck.properties");
		Properties p = new Properties();
		try {
			if (Files.exists(file)) {
				try (InputStream in = Files.newInputStream(file)) {
					p.load(in);
				}
			} else {
				writeDefaults(file);
			}
		} catch (IOException e) {
			LOGGER.warning("Could not load stuck.properties, using defaults: " + e.getMessage());
		}
		cfg.cooldownSeconds = getInt(p, "cooldown-seconds", cfg.cooldownSeconds);
		cfg.searchRadius = getInt(p, "search-radius", cfg.searchRadius);
		cfg.maxAttempts = getInt(p, "max-attempts", cfg.maxAttempts);
		cfg.minY = getInt(p, "min-y", cfg.minY);
		cfg.maxY = getInt(p, "max-y", cfg.maxY);
		cfg.debug = getBool(p, "debug", cfg.debug);
		cfg.avoidHazardousBlocks = getBool(p, "avoid-hazardous-blocks", cfg.avoidHazardousBlocks);
		cfg.avoidUnstableBlocks = getBool(p, "avoid-unstable-blocks", cfg.avoidUnstableBlocks);
		cfg.printSuccessMessage = getBool(p, "print-success-message", cfg.printSuccessMessage);
	}

	private void writeDefaults(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Properties p = new Properties();
		p.setProperty("cooldown-seconds", String.valueOf(cfg.cooldownSeconds));
		p.setProperty("search-radius", String.valueOf(cfg.searchRadius));
		p.setProperty("max-attempts", String.valueOf(cfg.maxAttempts));
		p.setProperty("min-y", String.valueOf(cfg.minY));
		p.setProperty("max-y", String.valueOf(cfg.maxY));
		p.setProperty("debug", String.valueOf(cfg.debug));
		p.setProperty("avoid-hazardous-blocks", String.valueOf(cfg.avoidHazardousBlocks));
		p.setProperty("avoid-unstable-blocks", String.valueOf(cfg.avoidUnstableBlocks));
		p.setProperty("print-success-message", String.valueOf(cfg.printSuccessMessage));
		try (OutputStream out = Files.newOutputStream(file)) {
			p.store(out, "Stuck config (Fabric)");
		}
	}

	private int getInt(Properties p, String key, int def) {
		try {
			return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private boolean getBool(Properties p, String key, boolean def) {
		return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)).trim());
}	}