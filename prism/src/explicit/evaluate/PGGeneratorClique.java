package explicit.evaluate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import explicit.Utils;
import explicit.evaluate.PGBenchmark.Clique;

public class PGGeneratorClique extends PGGenerator {

	public static void main(String[] args) {
		PGGeneratorClique pgGeneratorClique = new PGGeneratorClique();
		pgGeneratorClique.generate();
	}

	@Override
	public void generate() {
		File dir = new File(PGBenchmark.PRISM_BENCHMARK_CLIQUE);
		Utils.purgeDirectory(dir);

		for (int i = 0; i < Clique.STATES.length; i++) {
			int states = Clique.STATES[i];

			File file = new File(dir + "/" + states + ".gm");
			try {
				file.createNewFile();
				InputStream is = generateClique(states);
				Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
	}

}
