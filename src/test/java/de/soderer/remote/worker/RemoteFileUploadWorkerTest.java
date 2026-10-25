package de.soderer.remote.worker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.soderer.remote.InMemoryRemoteConnection;
import de.soderer.remote.RemoteConnection;

public class RemoteFileUploadWorkerTest {
	private static final byte[] DATA = "Some data to upload, longer than one chunk".getBytes(StandardCharsets.UTF_8);

	@TempDir
	File tempDirectory;

	private File localFile;
	private InMemoryRemoteConnection connection;

	@SuppressWarnings("resource")
	@BeforeEach
	public void setup() throws Exception {
		localFile = new File(tempDirectory, "local.txt");
		Files.write(localFile.toPath(), DATA);
		connection = new InMemoryRemoteConnection().withFolder("/remote");
	}

	@AfterEach
	public void tearDown() {
		if (connection != null) {
			connection.close();
			connection = null;
		}
	}

	@Test
	public void testUpload() throws Exception {
		final RemoteFileUploadWorker worker = new RemoteFileUploadWorker(null, connection, localFile, "/remote/uploaded.txt");
		assertSame(worker, worker.work());
		assertArrayEquals(DATA, connection.getFileData("/remote/uploaded.txt"));
	}

	@Test
	public void testUploadIntoFolderKeepsLocalName() throws Exception {
		new RemoteFileUploadWorker(null, connection, localFile, "/remote/").work();
		assertArrayEquals(DATA, connection.getFileData("/remote/local.txt"));
	}

	@Test
	public void testUploadIntoRootFolder() throws Exception {
		new RemoteFileUploadWorker(null, connection, localFile, "/root.txt").work();
		assertArrayEquals(DATA, connection.getFileData("/root.txt"));
	}

	@Test
	public void testUploadEmptyFile() throws Exception {
		final File emptyFile = new File(tempDirectory, "empty.txt");
		Files.write(emptyFile.toPath(), new byte[0]);
		new RemoteFileUploadWorker(null, connection, emptyFile, "/remote/empty.txt").work();
		assertArrayEquals(new byte[0], connection.getFileData("/remote/empty.txt"));
	}

	@Test
	public void testInvalidParameters() throws Exception {
		final File missingFile = new File(tempDirectory, "missing.txt");
		connection.withFile("/remote/existing.txt", DATA);

		assertTrue(assertThrows(Exception.class, () -> new RemoteFileUploadWorker(null, null, localFile, "/remote/a.txt")).getMessage().contains("No RemoteConnection"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileUploadWorker(null, connection, null, "/remote/a.txt")).getMessage().contains("No localFile"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileUploadWorker(null, connection, missingFile, "/remote/a.txt")).getMessage().contains("does not exist"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileUploadWorker(null, connection, tempDirectory, "/remote/a.txt")).getMessage().contains("is not a file"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileUploadWorker(null, connection, localFile, null)).getMessage().contains("No remoteFilePath"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileUploadWorker(null, connection, localFile, " ")).getMessage().contains("No remoteFilePath"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileUploadWorker(null, connection, localFile, "/missing/a.txt")).getMessage().contains("parent folder does not exist"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteFileUploadWorker(null, connection, localFile, "/remote/existing.txt")).getMessage().contains("already exists"));
	}

	@Test
	public void testFailedUploadDeletesPartialFile() throws Exception {
		connection.failUploads = true;
		final RemoteFileUploadWorker worker = new RemoteFileUploadWorker(null, connection, localFile, "/remote/uploaded.txt");

		assertThrows(Exception.class, () -> worker.work());
		assertNull(connection.getFileData("/remote/uploaded.txt"));
		assertEquals(List.of("remote/uploaded.txt"), connection.deletedFiles);
	}

	@Test
	public void testFailedDeletionAfterFailedUploadIsIgnored() throws Exception {
		connection.failUploads = true;
		final RemoteFileUploadWorker worker = new RemoteFileUploadWorker(null, connection, localFile, "/remote/uploaded.txt");
		// The connection may be unusable after an aborted transfer
		connection.uploadReadListener = () -> connection.failFileInfo = true;

		final Exception exception = assertThrows(Exception.class, () -> worker.work());
		assertTrue(exception.getMessage().contains("Simulated upload failure"), exception.getMessage());
	}

	@Test
	public void testCancelDuringUploadDeletesPartialFile() throws Exception {
		final CancellableUploadWorker worker = new CancellableUploadWorker(connection, localFile, "/remote/uploaded.txt");
		connection.uploadReadListener = worker::requestCancel;

		// A cancellation is no error
		assertSame(worker, worker.work());
		assertNull(connection.getFileData("/remote/uploaded.txt"));
		assertEquals(List.of("remote/uploaded.txt"), connection.deletedFiles);
	}

	@Test
	public void testCancelAfterUploadDeletesFile() throws Exception {
		final CancellableUploadWorker worker = new CancellableUploadWorker(connection, localFile, "/remote/uploaded.txt");
		connection.uploadCompletedListener = worker::requestCancel;

		worker.work();
		assertNull(connection.getFileData("/remote/uploaded.txt"));
		assertEquals(List.of("remote/uploaded.txt"), connection.deletedFiles);
	}

	/**
	 * Allows to cancel the worker from a hook of the connection.
	 */
	private static class CancellableUploadWorker extends RemoteFileUploadWorker {
		CancellableUploadWorker(final RemoteConnection connection, final File localFile, final String remoteFilePath) throws Exception {
			super(null, connection, localFile, remoteFilePath);
		}

		void requestCancel() {
			cancel = true;
		}
	}
}
