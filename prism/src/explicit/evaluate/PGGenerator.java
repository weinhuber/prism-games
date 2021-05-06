package explicit.evaluate;

import java.io.IOException;
import java.io.InputStream;

public abstract class PGGenerator
{

	/** Generate parity games. */
	public abstract void generate();

	/** 
	 * Generate a random game through PGSolver's randomgame program. 
	 * @return an InputStream of the game in PGSolver's format
	 */
	public InputStream generateRandom(int states, int priorities, int outdegreeLower, int outdegreeHigher) throws IOException
	{
		Process process = runCommand(PGBenchmark.PGSOLVER_BIN + 
				"/randomgame", states + "", priorities + "", outdegreeLower + "", outdegreeHigher + "");
		return process.getInputStream();
	}

	/** 
	 * Generate a clique game through PGSolver's cliquegame program. 
	 * @return an InputStream of the game in PGSolver's format
	 */
	public InputStream generateClique(int states) throws IOException
	{
		Process process = runCommand(PGBenchmark.PGSOLVER_BIN + "/cliquegame", states + "");
		return process.getInputStream();
	}

	/** 
	 * Generate an elevator system game through PGSolver's elevators program. 
	 * @return an InputStream of the game in PGSolver's format
	 */
	public InputStream generateLift(int n) throws IOException
	{
		Process process = runCommand(PGBenchmark.PGSOLVER_BIN + "/elevators", n + "");
		return process.getInputStream();
	}

	private static Process runCommand(String... command) throws IOException
	{
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		return builder.start();
	}

}
