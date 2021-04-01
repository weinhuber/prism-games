package explicit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import prism.PrismComponent;

public class PGReader
{

	private static final boolean DEBUG = false;

	public static void main(String[] args) throws IOException
	{
		PGReader pgReader = new PGReader();

		boolean correct = true;

		for (int i = 1; i <= 20; i++) {
			File file = new File("H:\\pgsolver\\src\\main\\resources\\random" + i + ".gm");
			PG pg = pgReader.read(new FileInputStream(file));

			System.out.println("random" + i + ".gm");

			BitSet win1 = new ZielonkaRecursive(new PrismComponent()).solve(pg);
			System.out.println(win1);
			BitSet win2 = new SmallProgressMeasures(new PrismComponent()).solve(pg);
			System.out.println(win2);
			BitSet win3 = new PriorityPromotion(new PrismComponent()).solve(pg);
			System.out.println(win3);

			boolean instanceCorrect = win1.equals(win2) && win2.equals(win3);
			System.out.println(instanceCorrect);

			correct = correct && instanceCorrect;
		}

		System.out.println(correct);
	}

	public PG read(InputStream is) throws IOException
	{
		TGSimple tg = new TGSimple();
		List<Integer> priorities = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (DEBUG) {
					System.out.println(line);
				}

				if (line.startsWith("parity")) {
					continue;
				}
				String[] nodeSpec = line.split(" ");

				// int identifier;
				int priority;
				int owner;
				List<Integer> successors = new ArrayList<>();

				if (nodeSpec.length >= 4) {
					// identifier = Integer.valueOf(nodeSpec[0]);
					priority = Integer.valueOf(nodeSpec[1]);
					owner = Integer.valueOf(nodeSpec[2]);
					if (!nodeSpec[3].contains("\"")) {
						for (String successor : nodeSpec[3].split(",")) {
							successors.add(Integer.valueOf(successor));
						}
					}
				} else {
					continue;
				}

				int s = tg.addState();
				tg.setPlayer(s, owner == 0 ? 1 : 2);
				tg.trans.set(s, successors);
				priorities.add(priority);
			}
		}

		return new PG(tg, priorities);
	}

}
