package explicit.evaluate;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import explicit.PG;

public class PGBenchmarkRandom extends PGBenchmark
{

	public static void main(String[] args)
	{
		PGBenchmarkRandom pgBenchmarkRandom = new PGBenchmarkRandom();
		pgBenchmarkRandom.benchmark();
	}

	@Override
	public void benchmark()
	{
		System.out.println("states,priorities,zr,pp,spm");

		for (int i = 0; i < Random.STATES.length; i++) {
			int states = Random.STATES[i];

			for (int j = 0; j < Random.PRIORITIES.length; j++) {
				int priorities = Random.PRIORITIES[j];

				File dir = new File(PRISM_BENCHMARK_RANDOM + "/" + states + "_" + priorities);

				long zrAvgTime = 0;
				long ppAvgTime = 0;
				long spmAvgTime = 0;

				for (int n = 1; n <= 50; n++) {
					File file = new File(dir, n + ".gm");
					PG pg;
					try {
						pg = pgReader.read(file);
					} catch (IOException e) {
						e.printStackTrace();
						return;
					}

					long zrTime = 0;
					long ppTime = 0;
					long spmTime = 0;
					try {
						if (zrAvgTime >= 0) {
							zrTime = time(zr, pg);
						}
						if (ppAvgTime >= 0) {
							ppTime = time(pp, pg);
						}
						if (spmAvgTime >= 0) {
							spmTime = time(spm, pg);
						}
					} catch (InterruptedException | ExecutionException e) {
						e.printStackTrace();
						return;
					}

					if (zrTime >= 0) {
						zrAvgTime += zrTime;
					} else if (zrAvgTime >= 0) {
						zrAvgTime = zrTime;
					}
					if (ppTime >= 0) {
						ppAvgTime += ppTime;
					} else if (ppAvgTime >= 0) {
						ppAvgTime = ppTime;
					}
					if (spmTime >= 0) {
						spmAvgTime += spmTime;
					} else if (spmAvgTime >= 0) {
						spmAvgTime = spmTime;
					}
				}

				if (zrAvgTime >= 0) {
					zrAvgTime = roundUp(zrAvgTime, 50);
				}
				if (ppAvgTime >= 0) {
					ppAvgTime = roundUp(ppAvgTime, 50);
				}
				if (spmAvgTime >= 0) {
					spmAvgTime = roundUp(spmAvgTime, 50);
				}

				System.out.printf("%d,%d,%d,%d,%d", states, priorities, zrAvgTime, ppAvgTime, spmAvgTime);
				System.out.println();
			}
		}
	}

}
