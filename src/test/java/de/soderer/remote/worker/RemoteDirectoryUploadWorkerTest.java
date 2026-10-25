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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.soderer.remote.InMemoryRemoteConnection;
import de.soderer.remote.RemoteConnection;

public class RemoteDirectoryUploadWorkerTest {
	private static final byte[] DATA_A = "Content of file a".getBytes(StandardCharsets.UTF_8);
	private static final byte[] DATA_B = "Content of file b in a sub directory".getBytes(StandardCharsets.UTF_8);

	@TempDir
	File tempDirectory;

	/** Local directory "upload" with a.txt, sub/b.txt and the empty directory emptySub */
	private File localDirectory;
	private InMemoryRemoteConnection connection;

	@SuppressWarnings("resource")
	@BeforeEach
	public void setup() throws Exception {
		localDirectory = new File(tempDirectory, "upload");
		new File(localDirectory, "sub").mkdirs();
		new File(localDirectory, "emptySub").mkdirs();
		Files.write(new File(localDirectory, "a.txt").toPath(), DATA_A);
		Files.write(new File(localDirectory, "sub/b.txt").toPath(), DATA_B);
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
		final RemoteDirectoryUploadWorker worker = new RemoteDirectoryUploadWorker(null, connection, localDirectory, "/remote");
		assertSame(worker, worker.work());

		assertTrue(connection.hasFolder("/remote/upload"));
		assertTrue(connection.hasFolder("/remote/upload/sub"));
		assertTrue(connection.hasFolder("/remote/upload/emptySub"));
		assertArrayEquals(DATA_A, connection.getFileData("/remote/upload/a.txt"));
		assertArrayEquals(DATA_B, connection.getFileData("/remote/upload/sub/b.txt"));
		assertEquals(Set.of("remote/upload/a.txt", "remote/upload/sub/b.txt"), connection.getFilePaths());
	}

	@Test
	public void testUploadWithOtherNameAndTrailingSeparator() throws Exception {
		new RemoteDirectoryUploadWorker(null, connection, localDirectory, "/remote/", "renamed").work();

		assertArrayEquals(DATA_A, connection.getFileData("/remote/renamed/a.txt"));
		assertArrayEquals(DATA_B, connection.getFileData("/remote/renamed/sub/b.txt"));
		// The trailing separator of the parent path is removed
		assertTrue(connection.createFolderCalls.contains("/remote|renamed"), connection.createFolderCalls.toString());
	}

	@Test
	public void testUploadIntoRootFolder() throws Exception {
		new RemoteDirectoryUploadWorker(null, connection, localDirectory, "/").work();

		assertTrue(connection.createFolderCalls.contains("/|upload"), connection.createFolderCalls.toString());
		assertArrayEquals(DATA_A, connection.getFileData("/upload/a.txt"));
		assertArrayEquals(DATA_B, connection.getFileData("/upload/sub/b.txt"));
	}

	@Test
	public void testSymbolicLinksToDirectoriesAreSkipped() throws Exception {
		try {
			Files.createSymbolicLink(new File(localDirectory, "link").toPath(), new File(localDirectory, "sub").toPath());
		} catch (@SuppressWarnings("unused") final Exception e) {
			// Symbolic links are not available on this system (e.g. Windows without the needed privilege)
			return;
		}

		new RemoteDirectoryUploadWorker(null, connection, localDirectory, "/remote").work();

		assertFalse(connection.hasFolder("/remote/upload/link"));
		assertEquals(Set.of("remote/upload/a.txt", "remote/upload/sub/b.txt"), connection.getFilePaths());
	}

	@Test
	public void testInvalidParameters() throws Exception {
		final File missingDirectory = new File(tempDirectory, "missing");
		final File file = new File(localDirectory, "a.txt");

		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryUploadWorker(null, null, localDirectory, "/remote")).getMessage().contains("No RemoteConnection"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryUploadWorker(null, connection, null, "/remote")).getMessage().contains("No localDestination"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryUploadWorker(null, connection, missingDirectory, "/remote")).getMessage().contains("Source directory not found"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryUploadWorker(null, connection, file, "/remote")).getMessage().contains("is no directory"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryUploadWorker(null, connection, localDirectory, " ")).getMessage().contains("No remoteParentDirectoryPath"));
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryUploadWorker(null, connection, localDirectory, "/missing")).getMessage().contains("Remote directory not found"));
	}

	@Test
	public void testExistingRemoteDirectoryIsRejected() {
		connection.withFolder("/remote/upload");
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryUploadWorker(null, connection, localDirectory, "/remote")).getMessage().contains("already exists"));
	}

	@Test
	public void testExistingRemoteFileIsRejected() {
		connection.withFile("/remote/upload", DATA_A);
		assertTrue(assertThrows(Exception.class, () -> new RemoteDirectoryUploadWorker(null, connection, localDirectory, "/remote")).getMessage().contains("already exists"));
	}

	@Test
	public void testFailedUploadDeletesPartialFile() throws Exception {
		final RemoteDirectoryUploadWorker worker = new RemoteDirectoryUploadWorker(null, connection, localDirectory, "/remote");
		connection.failUploads = true;

		assertThrows(Exception.class, () -> worker.work());
		assertTrue(connection.getFilePaths().isEmpty(), connection.getFilePaths().toString());
		assertEquals(1, connection.deletedFiles.size());
	}

	@Test
	public void testCancelDuringUploadDeletesPartialFile() throws Exception {
		final CancellableUploadWorker worker = new CancellableUploadWorker(connection, localDirectory, "/remote");
		connection.uploadReadListener = worker::requestCancel;

		// A cancellation is no error and no further files are uploaded
		assertSame(worker, worker.work());
		assertTrue(connection.getFilePaths().isEmpty(), connection.getFilePaths().toString());
		assertEquals(1, connection.deletedFiles.size());
	}

	@Test
	public void testCancelAfterFileUploadDeletesFileAndStops() throws Exception {
		final CancellableUploadWorker worker = new CancellableUploadWorker(connection, localDirectory, "/remote");
		connection.uploadCompletedListener = worker::requestCancel;

		worker.work();
		assertTrue(connection.getFilePaths().isEmpty(), connection.getFilePaths().toString());
		assertEquals(1, connection.deletedFiles.size());
	}

	@Test
	public void testFailedDeletionAfterCancelIsIgnored() throws Exception {
		final CancellableUploadWorker worker = new CancellableUploadWorker(connection, localDirectory, "/remote");
		connection.uploadCompletedListener = () -> {
			worker.requestCancel();
			connection.failFileInfo = true;
		};

		worker.work();
		assertEquals(List.of(), connection.deletedFiles);
	}

	/**
	 * Allows to cancel the worker from a hook of the connection.
	 */
	private static class CancellableUploadWorker extends RemoteDirectoryUploadWorker {
		CancellableUploadWorker(final RemoteConnection connection, final File localDirectory, final String remoteParentDirectoryPath) throws Exception {
			super(null, connection, localDirectory, remoteParentDirectoryPath);
		}

		void requestCancel() {
			cancel = true;
		}
	}
}
