package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests of {@link SshFileChangeWatcherThread} without SSH server. The connection fails, which must not escape the
 * thread.
 */
@SuppressWarnings("static-method")
public class SshFileChangeWatcherThreadTest {
	private static ServerAccessData createUnreachableServer() throws Exception {
		final int closedPort;
		try (ServerSocket serverSocket = new ServerSocket(0)) {
			closedPort = serverSocket.getLocalPort();
		}
		// A fingerprint instead of the disabled host key check avoids the warning on System.err
		return new ServerAccessData("ssh", "127.0.0.1", closedPort, null, "user", null, "password".toCharArray(), "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU");
	}

	@Test
	public void testIsDaemon() throws Exception {
		final SshFileChangeWatcherThread watcher = new SshFileChangeWatcherThread(createUnreachableServer(), "/var/log/syslog", 10, data -> {
			// Not called
		});
		assertTrue(watcher.isDaemon());
		assertTrue(watcher.running);
	}

	@Test
	public void testConnectionFailureEndsThread() throws Exception {
		final List<byte[]> receivedData = new ArrayList<>();
		final SshFileChangeWatcherThread watcher = new SshFileChangeWatcherThread(createUnreachableServer(), "/var/log/syslog", 10, receivedData::add);
		try (ConsoleCapture console = new ConsoleCapture()) {
			watcher.start();
			watcher.join(60_000);

			assertFalse(watcher.isAlive());
			assertTrue(receivedData.isEmpty());
			// The thread reports the connection failure via printStackTrace
			assertTrue(console.getErr().contains("Exception"), console.getErr());
		}
	}

	@Test
	public void testKillStopsRestartOnInterrupt() throws Exception {
		final CountingWatcher watcher = new CountingWatcher(createUnreachableServer());
		watcher.kill();
		assertFalse(watcher.running);

		// A killed watcher does not restart after an interrupt
		watcher.handleInterrupt();
		assertTrue(watcher.runCount == 0);
	}

	@Test
	public void testInterruptRestartsRunningWatcher() throws Exception {
		final CountingWatcher watcher = new CountingWatcher(createUnreachableServer());

		watcher.handleInterrupt();
		assertTrue(watcher.runCount == 1);
	}

	/**
	 * Counts the runs instead of connecting.
	 */
	private static class CountingWatcher extends SshFileChangeWatcherThread {
		int runCount = 0;

		CountingWatcher(final ServerAccessData serverData) {
			super(serverData, "/var/log/syslog", 10, data -> {
				// Not called
			});
		}

		@Override
		public void run() {
			runCount++;
		}
	}
}
