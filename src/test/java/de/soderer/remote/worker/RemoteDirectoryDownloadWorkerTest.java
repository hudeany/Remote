package de.soderer.remote.worker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.soderer.remote.InMemoryRemoteConnection;
import de.soderer.remote.RemoteConnection;

public class RemoteDirectoryDownloadWorkerTest {
	private static final byte[] DATA_A = "Content of file a".getBytes(StandardCharsets.UTF_8);
	private static final byte[] DATA_B = "Content of file b in a sub directory".getBytes(StandardCharsets.UTF_8);

	@TempDir
	File tempDirectory;

	/** Remote directory "/remote/download" with a.txt, sub/b.txt and the empty directory emptySub */
	private InMemoryRemoteConnection connection;

	@SuppressWarnings("resource")
	@BeforeEach
	public void setup() {
		connection = new InMemoryRemoteConnection()
				.withFile("/remote/download/a.txt", DATA_A)
				.withFile("/remote/download/sub/b.txt", DATA_B)
				.withFolder("/remote/download/emptySub");
	}

	@AfterEach
	public void tearDown() {
		if (connection != null) {
			connection.close();
			connection = null;
		}
	}

	private static Set<String> listRecursively(final File directory) {
		final Set<String> entries = new TreeSet<>();
		listRecursively(directory, "", entries);
		return entries;
	}

	private static void listRecursively(final File directory, final String prefix, final Set<String> entries) {
		final File[] subFiles = directory.listFiles();
		if (subFiles != null) {
			for (final File subFile : subFiles) {
				entries.add(prefix + subFile.getName() + (subFile.isDirectory() ? "/" : ""));
				if (subFile.isDirectory()) {
					listRecursively(subFile, prefix + subFile.getName() + "/", entries);
				}
			}
		}
	}

	@Test
	public void testDownload() throws Exception {
		final RemoteDirectoryDownloadWorker worker = new RemoteDirectoryDownloadWorker(null, connection, "/remote/download", tempDirectory);
		assertSame(worker, worker.work());

		final File localDirectory = new File(tempDirectory, "download");
		assertEquals(Set.of("a.txt", "emptySub/", "sub/", "sub/b.txt"), listRecursively(localDirectory));
		assertArrayEquals(DATA_A, Files.readAllBytes(new File(localDirectory, "a.txt").toPath()));
		assertArrayEquals(DATA_B, Files.readAllBytes(new File(localDirectory, "sub/b.txt").toPath()));
	}

	@Test
	public void testDownloadWithOtherNameAndTrailingSeparator() throws Exception {
		new RemoteDirectoryDownloadWorker(null, connection, "/remote/download/", tempDirectory, "renamed").work();

		final File localDirectory = new File(tempDirectory, "renamed");
		assertEquals(Set.of("a.txt", "emptySub/", "sub/", "sub/b.txt"), listRecursively(localDirectory));
	}

	@Test
	public void testSelfAndParentEntriesAreSkipped() throws Exception {
		// Some servers list "." and ".." (as folders and even as files), following them would loop endlessly
		connection.withListingFolderName("/remote/download", ".")
				.withListingFolderName("/remote/download", "..")
				.withListingFolderName("/remote/download/sub", ".")
				.withListingFileName("/remote/download/sub", "..", 0);

		new RemoteDirectoryDownloadWorker(null, connection, "/remote/download", tempDirectory).work();

		assertEquals(Set.of("a.txt", "emptySub/", "sub/", "sub/b.txt"), listRecursively(new File(tempDirectory, "download")));
	}

	@Test
	public void testFileNameWithPathTraversalIsRejected() {
		connection.withListingFileName("/remote/download", "../evil.txt", 4);

		assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, connection, "/remote/download", tempDirectory).work());
		assertFalse(new File(tempDirectory, "evil.txt").exists());
	}

	@Test
	public void testFolderNameWithPathTraversalIsRejected() {
		// A malicious server also answers the listing request for the manipulated path
		connection.withFolder("/remote/download/sub/../../evil")
				.withListingFolderName("/remote/download/sub", "../../evil");

		final Exception exception = assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, connection, "/remote/download", tempDirectory).work());
		assertTrue(exception.getMessage().contains("leads outside of the download destination"), exception.getMessage());
		assertFalse(new File(tempDirectory, "evil").exists());
	}

	@Test
	public void testInvalidParameters() throws Exception {
		final File file = new File(tempDirectory, "file.txt");
		Files.write(file.toPath(), DATA_A);
		final File missingDirectory = new File(tempDirectory, "missing");

		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, null, "/remote/download", tempDirectory)).getMessage().contains("No RemoteConnection"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, connection, " ", tempDirectory)).getMessage().contains("No remoteDirectoryPath"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, connection, "/remote/download", null)).getMessage().contains("No localDestination"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, connection, "/remote/missing", tempDirectory)).getMessage().contains("Remote directory not found"));
		// A file is no directory
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, connection, "/remote/download/a.txt", tempDirectory)).getMessage().contains("Remote directory not found"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, connection, "/remote/download", missingDirectory)).getMessage().contains("Destination not found"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, connection, "/remote/download", file)).getMessage().contains("Destination is no directory"));
	}

	@Test
	public void testRootDirectoryNeedsLocalName() {
		// No local name can be derived from the root directory
		assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, connection, "/", tempDirectory));
	}

	@Test
	public void testExistingLocalDirectoryIsRejected() {
		new File(tempDirectory, "download").mkdir();
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryDownloadWorker(null, connection, "/remote/download", tempDirectory)).getMessage().contains("already exists"));
	}

	@Test
	public void testFailedDownloadDeletesPartialFile() throws Exception {
		final RemoteDirectoryDownloadWorker worker = new RemoteDirectoryDownloadWorker(null, connection, "/remote/download", tempDirectory);
		connection.failDownloads = true;

		assertThrows(Exception.class, () -> worker.work());
		assertFalse(new File(tempDirectory, "download/a.txt").exists());
	}

	@Test
	public void testCancelDeletesPartialFileAndStops() throws Exception {
		final CancellableDownloadWorker worker = new CancellableDownloadWorker(connection, "/remote/download", tempDirectory);
		connection.downloadReadListener = worker::requestCancel;

		assertSame(worker, worker.work());
		// The local directory is created, but neither the cancelled file nor further entries are downloaded
		assertEquals(Set.of(), listRecursively(new File(tempDirectory, "download")));
	}

	/**
	 * Allows to cancel the worker from a hook of the connection.
	 */
	private static class CancellableDownloadWorker extends RemoteDirectoryDownloadWorker {
		CancellableDownloadWorker(final RemoteConnection connection, final String remoteDirectoryPath, final File localParentDirectory) throws Exception {
			super(null, connection, remoteDirectoryPath, localParentDirectory);
		}

		void requestCancel() {
			cancel = true;
		}
	}
}
