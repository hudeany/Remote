package de.soderer.remote.worker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.soderer.remote.InMemoryRemoteConnection;
import de.soderer.remote.RemoteConnection;

public class RemoteFileDownloadWorkerTest {
	private static final byte[] DATA = "Some data to download, longer than one chunk".getBytes(StandardCharsets.UTF_8);

	@TempDir
	File tempDirectory;

	private InMemoryRemoteConnection connection;

	@SuppressWarnings("resource")
	@BeforeEach
	public void setup() {
		connection = new InMemoryRemoteConnection().withFile("/remote/file.txt", DATA);
	}

	@AfterEach
	public void tearDown() {
		if (connection != null) {
			connection.close();
			connection = null;
		}
	}

	@Test
	public void testDownload() throws Exception {
		final File localFile = new File(tempDirectory, "downloaded.txt");
		final RemoteFileDownloadWorker worker = new RemoteFileDownloadWorker(null, connection, "/remote/file.txt", localFile);
		assertSame(worker, worker.work());
		assertArrayEquals(DATA, Files.readAllBytes(localFile.toPath()));
	}

	@Test
	public void testInvalidParameters() throws Exception {
		final File localFile = new File(tempDirectory, "downloaded.txt");
		final File existingFile = new File(tempDirectory, "existing.txt");
		Files.write(existingFile.toPath(), DATA);
		final File fileInMissingDirectory = new File(new File(tempDirectory, "missing"), "downloaded.txt");

		assertTrue(assertThrows(Exception.class, () -> new RemoteFileDownloadWorker(null, null, "/remote/file.txt", localFile)).getMessage().contains("No RemoteConnection"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileDownloadWorker(null, connection, " ", localFile)).getMessage().contains("No remoteFilePath"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileDownloadWorker(null, connection, "/remote/missing.txt", localFile)).getMessage().contains("Remote file not found"));
		// A folder is no file
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileDownloadWorker(null, connection, "/remote", localFile)).getMessage().contains("Remote file not found"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileDownloadWorker(null, connection, "/remote/file.txt", null)).getMessage().contains("No localFilePath"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileDownloadWorker(null, connection, "/remote/file.txt", fileInMissingDirectory)).getMessage().contains("parent directory does not exist"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileDownloadWorker(null, connection, "/remote/file.txt", existingFile)).getMessage().contains("already exists"));
	}

	@Test
	public void testRemoteFileDeletedBeforeWork() throws Exception {
		final File localFile = new File(tempDirectory, "downloaded.txt");
		final RemoteFileDownloadWorker worker = new RemoteFileDownloadWorker(null, connection, "/remote/file.txt", localFile);
		connection.deleteFile("/remote/file.txt");

		assertTrue(assertThrows(Exception.class, () -> worker.work()).getMessage().contains("Remote file not found"));
		assertFalse(localFile.exists());
	}

	@Test
	public void testFailedDownloadDeletesPartialFile() throws Exception {
		final File localFile = new File(tempDirectory, "downloaded.txt");
		final RemoteFileDownloadWorker worker = new RemoteFileDownloadWorker(null, connection, "/remote/file.txt", localFile);
		connection.failDownloads = true;

		assertThrows(Exception.class, () -> worker.work());
		assertFalse(localFile.exists());
	}

	@Test
	public void testCancelDeletesPartialFile() throws Exception {
		final File localFile = new File(tempDirectory, "downloaded.txt");
		final CancellableDownloadWorker worker = new CancellableDownloadWorker(connection, "/remote/file.txt", localFile);
		connection.downloadReadListener = worker::requestCancel;

		assertSame(worker, worker.work());
		assertFalse(localFile.exists());
	}

	/**
	 * Allows to cancel the worker from a hook of the connection.
	 */
	private static class CancellableDownloadWorker extends RemoteFileDownloadWorker {
		CancellableDownloadWorker(final RemoteConnection connection, final String remoteFilePath, final File localFile) throws Exception {
			super(null, connection, remoteFilePath, localFile);
		}

		void requestCancel() {
			cancel = true;
		}
	}
}
