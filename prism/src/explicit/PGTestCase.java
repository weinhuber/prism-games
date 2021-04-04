package explicit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.BitSet;

import prism.PrismComponent;

public class PGTestCase
{

	public static void main(String[] args) throws IOException
	{
		PGReader pgReader = new PGReader();

		boolean correct = true;

		for (int i = 1; i <= 5; i++) {
			File file = new File("./prism/tests/pg/random" + i + ".gm");
			PG pg = pgReader.read(new FileInputStream(file));

			System.out.println("random" + i + ".gm");

			BitSet win1 = new ZielonkaRecursive(new PrismComponent()).solve(pg);
			System.out.println(win1);
			BitSet win2 = new PriorityPromotion(new PrismComponent()).solve(pg);
			System.out.println(win2);
			BitSet win3 = new SmallProgressMeasures(new PrismComponent()).solve(pg);
			System.out.println(win3);

			boolean instanceCorrect = win1.equals(win2) && win2.equals(win3);
			System.out.println(instanceCorrect);

			correct = correct && instanceCorrect;
		}

		System.out.println(correct);
	}

}
