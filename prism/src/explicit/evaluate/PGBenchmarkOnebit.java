package explicit.evaluate;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import explicit.PG;

public class PGBenchmarkOnebit extends PGBenchmark
{

	public static void main(String[] args)
	{
		PGBenchmarkOnebit pgBenchmarkOnebit = new PGBenchmarkOnebit();
		pgBenchmarkOnebit.benchmark();
	}

	@Override
	public void benchmark()
	{
		System.out.println("property,states,zr,pp,spm");

		File dir = new File(PRISM_BENCHMARK_ONEBIT);
		File[] files = dir.listFiles();

		for (File file : files) {
			PG pg;
			int states;
			try {
				pg = pgReader.read(file);
				states = pg.getTG().getNumStates();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

			long zrAvgTime = 0;
			long ppAvgTime = 0;
			long spmAvgTime = 0;

			for (int i = 1; i <= 3; i++) {
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
				zrAvgTime = roundUp(zrAvgTime, 3);
			}
			if (ppAvgTime >= 0) {
				ppAvgTime = roundUp(ppAvgTime, 3);
			}
			if (spmAvgTime >= 0) {
				spmAvgTime = roundUp(spmAvgTime, 3);
			}

			System.out.printf("%s,%d,%d,%d,%d", file.getName(), states, zrAvgTime, ppAvgTime, spmAvgTime);
			System.out.println();
		}
	}

}
