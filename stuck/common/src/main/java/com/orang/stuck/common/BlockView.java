package com.orang.stuck.common;
public interface BlockView {
	int minY();
	int maxY();
	boolean isNether();
	boolean isAir(int x, int y, int z);
	boolean isSolid(int x, int y, int z);
	boolean isLiquid(int x, int y, int z);
	String blockName(int x, int y, int z);
}