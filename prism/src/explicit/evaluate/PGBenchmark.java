package explicit.evaluate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import explicit.PG;
import explicit.PGReader;
import explicit.PGSolver;
import explicit.PriorityPromotionSolver;
import explicit.SmallProgressMeasuresSolver;
import explicit.ZielonkaRecursiveSolver;
import prism.PrismComponent;

public abstract class PGBenchmark
{

	/** Location to the PGSolver bin folder. Change this. */
	public static final String PGSOLVER_BIN = "";

	/** Locations to various folders where parity games are generated/stored. */
	public static final String PRISM_TESTS = "./prism/tests";
	public static final String PRISM_PG = PRISM_TESTS + "/pg";
	public static final String PRISM_BENCHMARK = PRISM_TESTS + "/benchmark";
	public static final String PRISM_BENCHMARK_RANDOM = PRISM_TESTS + "/benchmark/random";
	public static final String PRISM_BENCHMARK_LIFT = PRISM_TESTS + "/benchmark/lift";
	public static final String PRISM_BENCHMARK_CLIQUE = PRISM_TESTS + "/benchmark/clique";
	public static final String PRISM_BENCHMARK_ONEBIT = PRISM_TESTS + "/benchmark/onebit";
	public static final String PRISM_BENCHMARK_LEADER_ELECTION = PRISM_TESTS + "/benchmark/leaderelection";
	public static final String PRISM_BENCHMARK_BOARD_GAMES = PRISM_TESTS + "/benchmark/boardgames";

	public final PGReader pgReader = new PGReader();

	private final PrismComponent prismComponent = new PrismComponent();
	public final PGSolver zr = new ZielonkaRecursiveSolver(prismComponent);
	public final PGSolver pp = new PriorityPromotionSolver(prismComponent);
	public final PGSolver spm = new SmallProgressMeasuresSolver(prismComponent);

	private ExecutorService executor;
	/** The timeout when solving parity games, in minutes. */
	public static final int TIMEOUT_MINS = 20;

	/** Random game parameters. */
	public static class Random
	{

		public static final int[] STATES = { 1000, 5000, 10_000, 50_000, 100_000, 500_000, 1_000_000 };
		public static final int[] PRIORITIES = { 2, 3, 5, 10, 50, 100, 500, 1000 };

	}

	/** Clique game parameters. */
	public static class Clique
	{

		public static final int[] STATES = { 1000, 2000, 3000, 4000, 5000, 6000 };

	}

	/** Elevator system parameters. */
	public static class Lift
	{

		public static final int[] N = { 2, 3, 4, 5, 6, 7, 8 };

	}

	/** Benchmark parity games. */
	public abstract void benchmark();

	/**
	 * Time the solution of a parity game using a given solver i.e. algorithm.
	 * 
	 * In special cases:
	 * -1 is returned upon a timeout of TIMEOUT_MINS
	 * -2 is returned upon running out of memory
	 * -3 is returned upon an uncaught exception
	 * 
	 * @param pgSolver parity game algorithm
	 * @param pg parity game
	 * @return Time taken to solve in millis.
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public long time(PGSolver pgSolver, PG pg) throws InterruptedException, ExecutionException
	{
		if (executor == null) {
			executor = Executors.newSingleThreadExecutor();
		}

		Future<Long> future = executor.submit(() -> {
			long before = System.currentTimeMillis();

			pgSolver.solve(pg);

			long after = System.currentTimeMillis();

			return after - before;
		});

		try {
			return future.get(TIMEOUT_MINS, TimeUnit.MINUTES);
		} catch (TimeoutException e) {
			future.cancel(true);
			return -1; // We denote by -1 timeout
		} catch (ExecutionException e) {
			if (e.getCause() instanceof OutOfMemoryError) {
				e.printStackTrace();
				return -2; // We denote by -2 out of memory
			} else {
				e.printStackTrace();
				return -3; // We denote by -3 otherwise
			}
		} finally {
			executor.shutdownNow();
			executor = Executors.newSingleThreadExecutor();

			System.gc();
		}
	}

	/** 
	 * Round up when dividing two longs.
	 * @return the result
	 */
	public static long roundUp(long num, long divisor)
	{
		return (num + divisor - 1) / divisor;
	}

}