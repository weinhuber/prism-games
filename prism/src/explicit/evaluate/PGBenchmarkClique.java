package explicit.evaluate;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import explicit.PG;

public class PGBenchmarkClique extends PGBenchmark
{

	public static void main(String[] args)
	{
		PGBenchmarkClique pgBenchmarkClique = new PGBenchmarkClique();
		pgBenchmarkClique.benchmark();
	}

	@Override
	public void benchmark()
	{
		System.out.println("states,edges,zr,pp,spm");

		for (int i = 0; i < Clique.STATES.length; i++) {
			int states = Clique.STATES[i];

			File file = new File(PRISM_BENCHMARK_CLIQUE + "/" + states + ".gm");
			PG pg;
			int transitions;
			try {
				pg = pgReader.read(file);
				transitions = pg.getTG().getNumTransitions();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

			long zrAvgTime = 0;
			long ppAvgTime = 0;
			long spmAvgTime = 0;

			for (int j = 1; j <= 3; j++) {
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

			System.out.printf("%d,%d,%d,%d,%d", states, transitions, zrAvgTime, ppAvgTime, spmAvgTime);
			System.out.println();
		}
	}

}
