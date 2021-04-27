package explicit.evaluate;

import java.io.IOException;
import java.io.InputStream;

public abstract class PGGenerator {

	public abstract void generate();

	public InputStream generateRandom(int states, int priorities, int outdegreeLower, int outdegreeHigher)
			throws IOException {
		Process process = runCommand(PGBenchmark.PGSOLVER_BIN + "/randomgame", states + "", priorities + "",
				outdegreeLower + "", outdegreeHigher + "");
		return process.getInputStream();
	}

	public InputStream generateClique(int states) throws IOException {
		Process process = runCommand(PGBenchmark.PGSOLVER_BIN + "/cliquegame", states + "");
		return process.getInputStream();
	}

	public InputStream generateLift(int n) throws IOException {
		Process process = runCommand(PGBenchmark.PGSOLVER_BIN + "/elevators", n + "");
		return process.getInputStream();
	}

	private static Process runCommand(String... command) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		return builder.start();
	}

}
