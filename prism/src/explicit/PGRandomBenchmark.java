package explicit;

import java.io.IOException;

import prism.PrismComponent;

public class PGRandomBenchmark
{

	private final PGGenerator pgGenerator = new PGGenerator();
	private final PrismComponent prismComponent = new PrismComponent();

	private int[] states = { 1000, 2000, 3000, 4000, 5000, 7000, 10_000, 20_000, 30_000, 40_000, 50_000, 70_000, 100_000 };
	private int[] priorities = { 2, 3, 5, 10, 15, 20 };

	public static void main(String[] args) throws IOException
	{
		PGRandomBenchmark pgRandomBenchmark = new PGRandomBenchmark();
		pgRandomBenchmark.benchmark();
	}

	public void benchmark()
	{
		for (int i = 0; i < states.length; i++) {
			for (int j = 0; i < priorities.length; i++) {
				int states = this.states[i];
				int priorities = this.priorities[j];

				long zielonkaRecursiveTime = 0;
				long smallProgressMeasuresTime;
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
				smallProgressMeasuresTime = time(new SmallProgressMeasures(prismComponent), pg);
				// priorityPromotionTime = time(new PriorityPromotion(prismComponent), pg);

				System.out.printf("States {0}, Priorities {1}", states, priorities);
				System.out.printf("{0} {1} {2}", zielonkaRecursiveTime, smallProgressMeasuresTime, priorityPromotionTime);
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
