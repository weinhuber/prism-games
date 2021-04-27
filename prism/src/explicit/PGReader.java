package explicit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Read parity games (PGs) in the PGSolver format.
 */
public class PGReader
{

	/**
	 * Read in through a File. 
	 * @param file parity game
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public PG read(File file) throws FileNotFoundException, IOException
	{
		return read(new FileInputStream(file));
	}

	/**
	 * Read in through an InputStream. 
	 * @param is parity game
	 * @throws IOException
	 */
	public PG read(InputStream is) throws IOException
	{
		TGSimple tg = new TGSimple();
		List<Integer> priorities = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("parity")) {
					continue;
				}
				line = line.replace(";", "");

				// Accommodate the slightly varying PGSolver format found in the paper/repo:
				// Benchmarks for Parity Games.
				String[] nodeSpec = line.split(" ");
				if (3 < nodeSpec.length && line.contains(", ")) {
					String newLine = String.join(" ", Arrays.copyOfRange(nodeSpec, 0, 3)) + " "
							+ String.join("", Arrays.copyOfRange(nodeSpec, 3, nodeSpec.length));
					nodeSpec = newLine.split(" ");
				}

				// int identifier;
				int priority;
				int owner;
				List<Integer> successors = new ArrayList<>();

				if (nodeSpec.length >= 4) {
					// identifier = Integer.valueOf(nodeSpec[0]);
					priority = Integer.valueOf(nodeSpec[1]);
					owner = Integer.valueOf(nodeSpec[2]);
					for (String successor : nodeSpec[3].split(",")) {
						successors.add(Integer.valueOf(successor));
					}
				} else {
					continue;
				}

				int s = tg.addState();
				tg.setPlayer(s, owner == 0 ? 1 : 2);
				tg.trans.set(s, successors);
				tg.numTransitions += successors.size();
				priorities.add(priority);
			}
		}

		return new PG(tg, priorities);
	}

}
