package explicit.evaluate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import explicit.Utils;
import explicit.evaluate.PGBenchmark.Lift;

public class PGGeneratorLift extends PGGenerator
{

	public static void main(String[] args)
	{
		PGGeneratorLift pgGeneratorLift = new PGGeneratorLift();
		pgGeneratorLift.generate();
	}

	@Override
	public void generate()
	{
		File dir = new File(PGBenchmark.PRISM_BENCHMARK_LIFT);
		Utils.purgeDirectory(dir);

		for (int i = 0; i < Lift.N.length; i++) {
			int n = Lift.N[i];

			File file = new File(dir + "/" + n + ".gm");
			try {
				file.createNewFile();
				InputStream is = generateLift(n);
				Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
	}

}
