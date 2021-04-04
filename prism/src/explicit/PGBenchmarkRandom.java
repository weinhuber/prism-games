package explicit;

import java.io.IOException;

import prism.PrismComponent;

public class PGBenchmarkRandom
{

	private final PGGenerator pgGenerator = new PGGenerator();
	private final PrismComponent prismComponent = new PrismComponent();

	private int[] states = { 1000, 2000, 3000, 4000, 7000, 10_000, 20_000, 30_000, 40_000, 70_000, 100_000 };
	private int[] priorities = { 2, 3, 5, 10, 100, 250 };

	public static void main(String[] args) throws IOException
	{
		PGBenchmarkRandom pgBenchmarkRandom = new PGBenchmarkRandom();
		pgBenchmarkRandom.benchmark();
	}

	public void benchmark()
	{
		for (int i = 0; i < states.length; i++) {
			int states = this.states[i];
			
			for (int j = 0; j < priorities.length; j++) {
				int priorities = this.priorities[j];

				long zielonkaRecursiveTime = 0;
				long smallProgressMeasuresTime = 0;
				long priorityPromotionTime = 0;

				System.out.println(states + " " + priorities);
				PG pg;
				try {
					pg = pgGenerator.generateRandom(states, priorities, 2);
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}

				// zielonkaRecursiveTime = time(new ZielonkaRecursive(prismComponent), pg);
				// priorityPromotionTime = time(new PriorityPromotion(prismComponent), pg);
				// smallProgressMeasuresTime = time(new SmallProgressMeasures(prismComponent), pg);

				System.out.printf("States %d, Priorities %d", states, priorities);
				System.out.println();
				System.out.printf("%d %d %d", zielonkaRecursiveTime, smallProgressMeasuresTime, priorityPromotionTime);
				System.out.println();
			}
		}
	}

	private static long time(PGSolver pgSolver, PG pg)
	{
		long before = System.currentTimeMillis();

		pgSolver.solve(pg);

		long after = System.currentTimeMillis();

		return after - before;
	}

}
