package explicit;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PGReader
{

	public PG read(InputStream is) throws FileNotFoundException
	{
		PG pg = new PG();
		pg.tg = new TGSimple();
		pg.priorities = new ArrayList<>();

		try (Scanner scanner = new Scanner(is)) {
			scanner.useDelimiter(";");

			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();

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

				int s = pg.tg.addState(owner == 0 ? 1 : 2);
				pg.tg.trans.set(s, successors);
				pg.priorities.set(s, priority);
			}
		}

		return pg;
	}

}
