package explicit;

import java.io.IOException;

public class PGGenerator
{

	private final PGReader pgReader = new PGReader();

	public static void main(String[] args) throws IOException
	{
		PGGenerator pgGenerator = new PGGenerator();
		pgGenerator.generateRandom(2000, 2, 2, 5);
	}

	public PG generateRandom(int states, int priorities, int outdegree) throws IOException
	{
		return generateRandom(states, priorities, outdegree, outdegree);
	}

	public PG generateRandom(int states, int priorities, int outdegreeLower, int outdegreeHigher) throws IOException
	{
		Process process = runCommand("~/pgsolver/bin/randomgame", states + "", priorities + "", outdegreeLower + "", outdegreeHigher + "");
		PG pg = pgReader.read(process.getInputStream());
		return pg;
	}

	private static Process runCommand(String... command) throws IOException
	{
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		return builder.start();
	}

}
