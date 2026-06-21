package com.orang.stuck.bukkit;
import com.orang.stuck.common.BlockView;
import org.bukkit.World;
import org.bukkit.block.Block;

public class BukkitBlockView implements BlockView {
	private final World world;

	public BukkitBlockView(World world) {
		this.world = world;
	}

	@Override
	public int minY() {
		return world.getMinHeight();
	}

	@Override
	public int maxY() {
		return world.getMaxHeight();
	}

	@Override
	public boolean isNether() {
		return world.getEnvironment() == World.Environment.NETHER;
	}

	@Override
	public boolean isAir(int x, int y, int z) {
		return world.getBlockAt(x, y, z).getType().isAir();
	}

	@Override
	public boolean isSolid(int x, int y, int z) {
		return world.getBlockAt(x, y, z).getType().isSolid();
	}

	@Override
	public boolean isLiquid(int x, int y, int z) {
		Block b = world.getBlockAt(x, y, z);
		return b.isLiquid();
	}

	@Override
	public String blockName(int x, int y, int z) {
		return world.getBlockAt(x, y, z).getType().toString();
	}
}