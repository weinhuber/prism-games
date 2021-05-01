package explicit.evaluate;

import java.io.File;
import java.io.IOException;
import java.util.BitSet;

import explicit.PG;
import explicit.PGReader;
import explicit.PGSolver;
import explicit.PriorityPromotionSolver;
import explicit.SmallProgressMeasuresSolver;
import explicit.ZielonkaRecursiveSolver;
import prism.PrismComponent;

public class PGTestCase
{

	private final PGReader pgReader = new PGReader();

	private final PrismComponent prismComponent = new PrismComponent();
	private final PGSolver zr = new ZielonkaRecursiveSolver(prismComponent);
	private final PGSolver pp = new PriorityPromotionSolver(prismComponent);
	private final PGSolver spm = new SmallProgressMeasuresSolver(prismComponent);

	public static void main(String[] args)
	{
		PGTestCase pgTestCase = new PGTestCase();
		pgTestCase.test();
	}

	public void test()
	{
		boolean correct = true;

		for (int i = 1; i <= 40; i++) {
			File file = new File(PGBenchmark.PRISM_PG + "/random" + i + ".gm");
			PG pg;
			try {
				pg = pgReader.read(file);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

			System.out.println("random" + i + ".gm");

			// Winning regions are compared.
			// Winning strategies are not compared as they are not unique.

			BitSet win1 = zr.solve(pg).get(1).getRegion();
			System.out.println(win1);
			BitSet win2 = pp.solve(pg).get(1).getRegion();
			System.out.println(win2);
			BitSet win3 = spm.solve(pg).get(1).getRegion();
			System.out.println(win3);

			boolean instanceCorrect = win1.equals(win2) && win2.equals(win3);
			System.out.println(instanceCorrect);

			correct = correct && instanceCorrect;
		}

		System.out.println(correct);
	}

}
