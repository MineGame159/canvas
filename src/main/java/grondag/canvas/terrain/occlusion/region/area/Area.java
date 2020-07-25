package grondag.canvas.terrain.occlusion.region.area;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import grondag.canvas.terrain.occlusion.region.OcclusionBitPrinter;

public class Area {
	private static final int[] AREA_KEY_TO_INDEX = new int[0x10000];
	private static final int[] AREA_INDEX_TO_KEY;

	public static final int AREA_COUNT;

	private static final int[] SECTION_KEYS;

	public static final int SECTION_COUNT;

	public static int keyToIndex(int key) {
		return AREA_KEY_TO_INDEX[key];
	}

	public static int indexToKey(int index) {
		return AREA_INDEX_TO_KEY[index];
	}

	public static int sectionKey(int sectionIndex) {
		return SECTION_KEYS[sectionIndex];
	}

	static {
		final IntOpenHashSet areas = new IntOpenHashSet();

		areas.add(Area.areaKey(0, 0, 15, 15));

		areas.add(Area.areaKey(1, 0, 15, 15));
		areas.add(Area.areaKey(0, 0, 14, 15));
		areas.add(Area.areaKey(0, 1, 15, 15));
		areas.add(Area.areaKey(0, 0, 15, 14));

		for (int x0 = 0; x0 <= 15; x0++) {
			for (int x1 = x0; x1 <= 15; x1++) {
				for (int y0 = 0; y0 <= 15; y0++) {
					for(int y1 = y0; y1 <= 15; y1++) {
						areas.add(Area.areaKey(x0, y0, x1, y1));
					}
				}
			}
		}

		AREA_COUNT = areas.size();
		AREA_INDEX_TO_KEY = new int[AREA_COUNT];

		int i = 0;

		for(final int k : areas) {
			AREA_INDEX_TO_KEY[i++] = k;
		}

		IntArrays.quickSort(AREA_INDEX_TO_KEY, (a, b) -> {
			final int result = Integer.compare(Area.size(b), Area.size(a));

			// within same area size, prefer more compact rectangles
			return result == 0 ? Integer.compare(Area.edgeCount(a), Area.edgeCount(b)) : result;
		});

		for (int j = 0; j < AREA_COUNT; j++) {
			AREA_KEY_TO_INDEX[AREA_INDEX_TO_KEY[j]] = j;
		}

		final IntArrayList sections = new IntArrayList();

		for (int j = 0; j < AREA_COUNT; ++j) {
			final int a = AREA_INDEX_TO_KEY[j];

			if ((Area.x0(a) == 0  &&  Area.x1(a) == 15) || (Area.y0(a) == 0  &&  Area.y1(a) == 15)) {
				sections.add(indexToKey(j));
			}
		}

		SECTION_COUNT = sections.size();
		SECTION_KEYS = sections.toArray(new int[SECTION_COUNT]);
	}

	public static boolean isIncludedBySample(long[] sample, int sampleStart, int areaKey) {
		final long template = bits(areaKey, 0);
		final long template1 = bits(areaKey, 1);
		final long template2 = bits(areaKey, 2);
		final long template3 = bits(areaKey, 3);

		return (template & sample[sampleStart]) == template
				&& (template1 & sample[sampleStart + 1]) == template1
				&& (template2 & sample[sampleStart + 2]) == template2
				&& (template3 & sample[sampleStart + 3]) == template3;
	}

	public static boolean intersects(int areaKeyA, int areaKeyB) {
		return (bits(areaKeyA, 0) & bits(areaKeyB, 0)) != 0
				|| (bits(areaKeyA, 1) & bits(areaKeyB, 1)) != 0
				|| (bits(areaKeyA, 2) & bits(areaKeyB, 2)) != 0
				|| (bits(areaKeyA, 3) & bits(areaKeyB, 3)) != 0;
	}

	public static boolean intersectsWithSample(long[] sample, int sampleStart, int areaKey) {
		return (bits(areaKey, 0) & sample[sampleStart]) != 0
				|| (bits(areaKey, 1) & sample[++sampleStart]) != 0
				|| (bits(areaKey, 2) & sample[++sampleStart]) != 0
				|| (bits(areaKey, 3) & sample[++sampleStart]) != 0;
	}

	public static boolean isAdditive(long[] sample, int sampleStart, int areaKey) {
		return (bits(areaKey, 0) | sample[sampleStart]) != sample[sampleStart]
				|| (bits(areaKey, 1) | sample[++sampleStart]) != sample[sampleStart]
						|| (bits(areaKey, 2) | sample[++sampleStart]) != sample[sampleStart]
								|| (bits(areaKey, 3) | sample[++sampleStart]) != sample[sampleStart];
	}

	public static void setBits(long[] targetBits, int startIndex, int areaKey) {
		targetBits[startIndex] |= bits(areaKey, 0);
		targetBits[++startIndex] |= bits(areaKey, 1);
		targetBits[++startIndex] |= bits(areaKey, 2);
		targetBits[++startIndex] |= bits(areaKey, 3);
	}

	public static void clearBits(long[] targetBits, int startIndex, int areaKey) {
		targetBits[startIndex] &= ~bits(areaKey, 0);
		targetBits[++startIndex] &= ~bits(areaKey, 1);
		targetBits[++startIndex] &= ~bits(areaKey, 2);
		targetBits[++startIndex] &= ~bits(areaKey, 3);
	}

	private static final long[] X_MASKS = new long[256];
	private static final int[] Y_BITS = new int[256];
	private static final long[] Y_MASKS = new long[16];

	static {
		for (int x0 = 0; x0 <= 15; ++x0) {
			for (int x1 = x0; x1 <= 15; ++x1) {
				final long template  = (0xFFFF << x0) & (0xFFFF >> (15 - x1));
				X_MASKS[x0 | (x1 << 4)] = template | (template << 16) | (template << 32) | (template << 48);
			}
		}

		for (int y0 = 0; y0 <= 15; ++y0) {
			for (int y1 = y0; y1 <= 15; ++y1) {
				Y_BITS[y0 | (y1 << 4)] = (0xFFFF << y0) & (0xFFFF >> (15 - y1));
			}
		}

		Y_MASKS[0b0000] = 0L;
		Y_MASKS[0b0001] = 0x000000000000FFFFL;
		Y_MASKS[0b0010] = 0x00000000FFFF0000L;
		Y_MASKS[0b0100] = 0x0000FFFF00000000L;
		Y_MASKS[0b1000] = 0xFFFF000000000000L;
		Y_MASKS[0b0011] = 0x00000000FFFFFFFFL;
		Y_MASKS[0b0110] = 0x0000FFFFFFFF0000L;
		Y_MASKS[0b1100] = 0xFFFFFFFF00000000L;
		Y_MASKS[0b0111] = 0x0000FFFFFFFFFFFFL;
		Y_MASKS[0b1110] = 0xFFFFFFFFFFFF0000L;
		Y_MASKS[0b1111] = 0xFFFFFFFFFFFFFFFFL;
	}

	private static long bits(int areaKey, int y) {
		return Y_MASKS[(Y_BITS[areaKey >> 8] >> (y << 2)) & 0xF] & X_MASKS[areaKey & 0xFF];
	}

	public static void printShape(int areaKey) {
		final long[] bits = new long[4];
		bits[0] = bits(areaKey, 0);
		bits[1] = bits(areaKey, 1);
		bits[2] = bits(areaKey, 2);
		bits[3] = bits(areaKey, 3);

		OcclusionBitPrinter.printShape(bits, 0);
	}

	public static int areaKey(int x0, int y0, int x1, int y1) {
		return x0 | (x1 << 4) | (y0 << 8) | (y1 << 12);
	}

	public static int x0(int areaKey) {
		return areaKey & 15;
	}

	public static int y0(int areaKey) {
		return (areaKey >> 8) & 15;
	}

	public static int x1(int areaKey) {
		return (areaKey >> 4) & 15;
	}

	public static int y1(int areaKey) {
		return (areaKey >> 12) & 15;
	}

	public static int size(int areaKey) {
		final int x0 = x0(areaKey);
		final int y0 = y0(areaKey);
		final int x1 = x1(areaKey);
		final int y1 = y1(areaKey);

		return (x1 - x0 + 1) * (y1 - y0 + 1);
	}

	public static int edgeCount(int areaKey) {
		final int x0 = x0(areaKey);
		final int y0 = y0(areaKey);
		final int x1 = x1(areaKey);
		final int y1 = y1(areaKey);

		final int x = x1 - x0 + 1;
		final int y = y1 - y0 + 1;
		return x + y;
	}

	public static void printArea(int areaKey) {
		final int x0 = x0(areaKey);
		final int y0 = y0(areaKey);
		final int x1 = x1(areaKey);
		final int y1 = y1(areaKey);

		final int x = x1 - x0 + 1;
		final int y = y1 - y0 + 1;
		final int a = x * y;
		System.out.println(String.format("%d x %d, area %d, (%d, %d) to (%d, %d)", x, y, a, x0, y0, x1, y1));
	}
}