package com.orang.stuck.common;
import java.util.Random;
import java.util.function.Consumer;

// the actual unstuck logic, shared by every platform. 
// operates only through BlockView
public final class SafeSpotFinder {
	public static final int NETHER_ROOF_Y = 127;
	private final StuckConfig cfg;
	private final Random random = new Random();
	private final Consumer<String> debugLog;

	public SafeSpotFinder(StuckConfig cfg, Consumer<String> debugLog) {
		this.cfg = cfg;
		this.debugLog = debugLog;
	}

	private void debug(String msg) {
		if (cfg.debug && debugLog != null) {
			debugLog.accept(msg);
		}
	}

	// fallback for fix
	public int[] find(BlockView view, int startX, int startY, int startZ) {
		boolean isNether = view.isNether();
		boolean onNetherRoof = isNether && startY >= NETHER_ROOF_Y;
		int worldMinY = Math.max(view.minY() + 1, cfg.minY);
		int worldMaxY = Math.min(view.maxY() - 3, cfg.maxY);
		if (cfg.minY == 32 && cfg.maxY == 120) {
			worldMinY = Math.max(view.minY() + 5, -50);
			worldMaxY = Math.min(view.maxY() - 3, 200);
		}
		if (isNether) {
			worldMaxY = Math.min(worldMaxY, NETHER_ROOF_Y - 1); // cap at 126
		}

		int effectiveRadius = cfg.searchRadius;
		int effectiveAttempts = cfg.maxAttempts;
		if (onNetherRoof) {
			effectiveRadius = Math.max(cfg.searchRadius, 16);
			effectiveAttempts = Math.max(cfg.maxAttempts, 200);
			debug("Player on nether roof — radius=" + effectiveRadius + ", attempts=" + effectiveAttempts);
		}
		debug("Searching around " + startX + "," + startY + "," + startZ +
				" Y-range " + worldMinY + "-" + worldMaxY + (isNether ? " (Nether)" : ""));
		for (int attempt = 0; attempt < effectiveAttempts; attempt++) {
			int x = startX + random.nextInt(effectiveRadius * 2 + 1) - effectiveRadius;
			int z = startZ + random.nextInt(effectiveRadius * 2 + 1) - effectiveRadius;
			for (int y = worldMaxY; y >= worldMinY; y--) {
				int gy = y - 1;
				if (isNether && gy >= 123 && view.blockName(x, gy, z).contains("BEDROCK")) {
					continue;
				}
				String groundName = view.blockName(x, gy, z);
				boolean stable = !cfg.avoidUnstableBlocks ||
						(!groundName.contains("LEAVES") && !groundName.contains("SNOW"));
				if (view.isSolid(x, gy, z) && stable &&
						view.isAir(x, y, z) && view.isAir(x, y + 1, z) &&
						!view.isLiquid(x, gy, z) &&
						(!cfg.avoidHazardousBlocks || !isHazardous(groundName))) {
					debug("Found safe spot at " + x + "," + y + "," + z);
					return new int[]{x, y, z};
				}
			}
		}
		debug("No safe spot after " + effectiveAttempts + " attempts");
		return null;
	}

	public static boolean isHazardous(String name) {
		return name.contains("LAVA") ||
				name.contains("FIRE") ||
				name.contains("MAGMA") ||
				name.contains("CACTUS") ||
				name.contains("SWEET_BERRY") ||
				name.contains("CAMPFIRE") ||
				name.contains("WITHER_ROSE") ||
				name.contains("POINTED_DRIPSTONE") ||
				name.equals("SOUL_FIRE") ||
				name.equals("SOUL_CAMPFIRE") ||
				(name.contains("COPPER") && name.contains("EXPOSED")) ||
				(name.contains("TRIAL") && name.contains("SPAWNER"));
	}
}