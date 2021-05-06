package explicit.evaluate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import explicit.Utils;
import explicit.evaluate.PGBenchmark.Random;

public class PGGeneratorRandom extends PGGenerator
{

	public static void main(String[] args)
	{
		PGGeneratorRandom pgGeneratorRandom = new PGGeneratorRandom();
		pgGeneratorRandom.generate();
	}

	@Override
	public void generate()
	{
		File dir = new File(PGBenchmark.PRISM_BENCHMARK_RANDOM);
		Utils.purgeDirectory(dir);

		for (int i = 0; i < Random.STATES.length; i++) {
			int states = Random.STATES[i];

			for (int j = 0; j < Random.PRIORITIES.length; j++) {
				int priorities = Random.PRIORITIES[j];

				File subdir = new File(dir + "/" + states + "_" + priorities);
				subdir.mkdirs();

				for (int n = 1; n <= 50; n++) {
					File file = new File(subdir, n + ".gm");
					try {
						file.createNewFile();
						InputStream is = generateRandom(states, priorities, 5, 10);
						Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						e.printStackTrace();
						return;
					}
				}
			}
		}
	}

}
