package explicit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import prism.PrismComponent;

public class PGReader
{

	private static final boolean debug = true;

	public static void main(String[] args) throws IOException
	{
		File file = new File("H:\\pgsolver\\src\\main\\resources\\random4.gm");
		PGReader pgReader = new PGReader();
		PG pg = pgReader.read(new FileInputStream(file));

		PriorityPromotion pgSolver = new PriorityPromotion(new PrismComponent(), pg);
		System.out.println("Winning " + pgSolver.solve());
	}

	public PG read(InputStream is) throws IOException
	{
		TGSimple tg = new TGSimple();
		List<Integer> priorities = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (debug) {
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
