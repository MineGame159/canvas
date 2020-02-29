/*******************************************************************************
 * Copyright 2019, 2020 grondag
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package grondag.canvas.perf;

import grondag.canvas.CanvasMod;
import grondag.canvas.Configurator;
import grondag.fermion.sc.concurrency.ConcurrentPerformanceCounter;

public abstract class ChunkRebuildCounters {
	private ChunkRebuildCounters() {}

	public static final boolean ENABLED = Configurator.enablePerformanceTrace;

	private static final ConcurrentPerformanceCounter buildCounter = new ConcurrentPerformanceCounter();
	private static final ConcurrentPerformanceCounter copyCounter = new ConcurrentPerformanceCounter();
	private static final ConcurrentPerformanceCounter uploadCounter = new ConcurrentPerformanceCounter();

	public static void reset() {
		buildCounter.clearStats();
		copyCounter.clearStats();
	}

	private static final ThreadLocal<Long> chunkStart = ThreadLocal.withInitial(() -> 0L);

	public static void startChunk() {
		chunkStart.set(System.nanoTime());
	}

	public static void completeChunk() {
		buildCounter.endRun(chunkStart.get());
		final int chunkCount = buildCounter.addCount(1);

		if(chunkCount == 2000) {
			CanvasMod.LOG.info(String.format("Rebuild elapsed time per region for last 2000 chunks = %,dns", buildCounter.runTime() / 2000));

			final int copyCount = copyCounter.runCount();
			CanvasMod.LOG.info(String.format("World copy time per chunk for last %d regions = %,dns", copyCount, copyCount == 0 ? 0 : copyCounter.runTime() / copyCount));

			final int uploadCount = uploadCounter.runCount();
			CanvasMod.LOG.info(String.format("Upload time per region for last %d regions = %,dns", uploadCount, uploadCount == 0 ? 0 : uploadCounter.runTime() / uploadCount));
			reset();

			CanvasMod.LOG.info("");
		}

	}

	private static final ThreadLocal<Long> copyStart = ThreadLocal.withInitial(() -> 0L);

	public static void startCopy() {
		copyStart.set(System.nanoTime());
	}

	public static void completeCopy() {
		copyCounter.endRun(copyStart.get());
		copyCounter.addCount(1);
	}

	private static final ThreadLocal<Long> uploadStart = ThreadLocal.withInitial(() -> 0L);

	public static void startUpload() {
		uploadStart.set(System.nanoTime());
	}

	public static void completeUpload() {
		uploadCounter.endRun(uploadStart.get());
		uploadCounter.addCount(1);
	}
}
