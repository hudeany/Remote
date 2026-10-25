package de.soderer.remote;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Captures everything written to System.out and System.err (from all threads) until it is closed, so expected
 * warnings and stack traces of tests do not show up in the console and can be checked instead.
 *
 * <pre>
 * try (ConsoleCapture console = new ConsoleCapture()) {
 *     ...
 *     assertTrue(console.getErr().contains("WARNING"));
 * }
 * </pre>
 *
 * The streams are replaced JVM wide, so tests using this class must not run in parallel with tests writing to the
 * console.
 */
public final class ConsoleCapture implements AutoCloseable {
	private final PrintStream originalOut;
	private final PrintStream originalErr;
	private final ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
	private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();

	/**
	 * Starts capturing.
	 */
	public ConsoleCapture() {
		originalOut = System.out;
		originalErr = System.err;
		System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
	}

	/**
	 * Text written to System.out so far.
	 *
	 * @return captured standard output
	 */
	public String getOut() {
		System.out.flush();
		return capturedOut.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Text written to System.err so far.
	 *
	 * @return captured error output
	 */
	public String getErr() {
		System.err.flush();
		return capturedErr.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Restores the original streams.
	 */
	@Override
	public void close() {
		System.setOut(originalOut);
		System.setErr(originalErr);
	}
}
