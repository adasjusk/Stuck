package com.orang.stuck.fabric;
import com.orang.stuck.common.BlockView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Locale;

public class FabricBlockView implements BlockView {
	private final ServerLevel level;

	public FabricBlockView(ServerLevel level) {
		this.level = level;
	}
	@Override
	public int minY() {
		return level.getMinY();
	}
	@Override
	public int maxY() {
		return level.getMaxY();
	}
	@Override
	public boolean isNether() {
		return level.dimension().equals(Level.NETHER);
	}
	private BlockState state(int x, int y, int z) {
		return level.getBlockState(new BlockPos(x, y, z));
	}
	@Override
	public boolean isAir(int x, int y, int z) {
		return state(x, y, z).isAir();
	}
	@Override
	public boolean isSolid(int x, int y, int z) {
		return state(x, y, z).blocksMotion();
	}
	@Override
	public boolean isLiquid(int x, int y, int z) {
		return !state(x, y, z).getFluidState().isEmpty();
	}
	@Override
	public String blockName(int x, int y, int z) {
		return BuiltInRegistries.BLOCK.getKey(state(x, y, z).getBlock())
				.getPath().toUpperCase(Locale.ROOT);
	}
}