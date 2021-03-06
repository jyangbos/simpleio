package simpleio;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import randomfile.EmptyData;
import randomfile.RandomData;
import simpleio.benchmarks.FirstReadJob;
import simpleio.benchmarks.FirstWriteJob;
import simpleio.benchmarks.RereadJob;
import simpleio.benchmarks.RewriteJob;
import simpleio.job.Job;
import simpleio.job.JobResult;
import simpleio.job.JobType;
import simpleio.job.Job.Status;
/**
 * Execute the benchmark
 * 
 * @author jimyang
 *
 */
public class Benchmark {
	public static final int blockSize = 1024*1024;
	private String[] testFilePaths = null;
	private ExecutorService execSvr;
	private PrintStream logFileOut;

	private Benchmark(PrintStream s) {
		this.logFileOut = s;
	}
	
	/**
	 * Display benchmark result
	 * 
	 * @param futures
	 * @param elapseTime
	 */
	private static void displayResult(List<Future<JobResult>> futures, long elapseTime, PrintStream logFileOut) {
		int count = 0;
		JobResult aggResult = null;
		if (logFileOut != null) {
			logFileOut.println("Per Thread Result:");
		}
		for (Future<JobResult> future : futures) {
			try {
				JobResult result = future.get();
				if (logFileOut != null) {
					result.display(logFileOut);
					logFileOut.println();
				}
				if (result.getJob().getStatus() == Status.OK) {
					if (aggResult == null) {
						aggResult = result;
					}
					else {
						aggResult.aggregate(result);
					}
					count++;
				}
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Thread status: " + count + " out of " + futures.size() + " completed normally.");
		if (aggResult != null) {
			System.out.println("Average Result:");
			JobResult.display(System.out, aggResult.getJob().getType(), Status.OK, aggResult.getByteCount(), aggResult.getExecTime(), true, count);
			System.out.println();
			System.out.println("Aggregated Result:");
			JobResult.display(System.out, aggResult.getJob().getType(), Status.OK, aggResult.getByteCount(), elapseTime);
			System.out.println();
		}
	}

	/**
	 * Run all supported benchmark
	 * 
	 * @param targetDir
	 * @param benchmarkName
	 * @param fileSize
	 * @param threadCount
	 */
	public static void start(String targetDir, String benchmarkName, long fileSize, int threadCount, String logFilePath) {
		PrintStream logPrintStream = null;
		if (logFilePath != null) {
			try {
				logPrintStream = new PrintStream(new FileOutputStream(logFilePath));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
		}
		Benchmark mark = new Benchmark(logPrintStream);

		mark.prepare(targetDir, benchmarkName, threadCount);
		
		mark.runBenchmark(JobType.FIRST_WRITE, targetDir, benchmarkName, fileSize, threadCount);
		mark.runBenchmark(JobType.REWRITE, targetDir, benchmarkName, fileSize, threadCount);
		mark.runBenchmark(JobType.FIRST_READ, targetDir, benchmarkName, fileSize, threadCount);
		mark.runBenchmark(JobType.REREAD, targetDir, benchmarkName, fileSize, threadCount);
		
		mark.cleanup();
	}

	/**
	 * Run a single benchmark and display the result. Each thread run the same IO job on different files.
	 * 
	 * @param type
	 * @param targetDir
	 * @param benchmarkName
	 * @param fileSize
	 * @param threadCount
	 */
	public void runBenchmark(JobType type, String targetDir, String benchmarkName, long fileSize, int threadCount) {
		List<Job> allThreadOps = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			allThreadOps.add(createJob(type, testFilePaths[i], fileSize));
		}
		try {
			System.out.println("Benchmark " + type + " started at " +  Utils.df.format(new Date()));
			List<Future<JobResult>> futures;
			long start = System.nanoTime();
			futures = execSvr.invokeAll(allThreadOps);
			System.out.println("Benchmark " + type + " completed at " +  Utils.df.format(new Date()));
			displayResult(futures, System.nanoTime() - start, logFileOut);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void prepare(String targetDir, String partialName, int count) {
		if (testFilePaths == null) {
			testFilePaths = new String[count];
			String partialPath = targetDir + File.separatorChar+ partialName;
			for (int i = 0; i < count; i++) {
				testFilePaths[i] = partialPath + Integer.toString(i) + ".dat";
			}
		}
		execSvr = Executors.newFixedThreadPool(count);
	}
	
	private void cleanup() {
		execSvr.shutdown();
		if (logFileOut != null) {
			logFileOut.close();
			logFileOut = null;
		}
	}
	
	private Job createJob(JobType type, String path, long size) {
		switch (type) {
		case FIRST_WRITE:
			return new FirstWriteJob(path, size, new RandomData(blockSize));
		case REWRITE:
			return new RewriteJob(path, size, new RandomData(blockSize));
		case FIRST_READ:
			return new FirstReadJob(path, new EmptyData(blockSize));
		case REREAD:
			return new RereadJob(path, new EmptyData(blockSize));
		default:
			assert false : "unknown job type" + type;
			return null;
		}
	}
}
