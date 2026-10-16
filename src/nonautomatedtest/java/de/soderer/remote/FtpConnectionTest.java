package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.soderer.remote.worker.RemoteDirectoryDownloadWorker;
import de.soderer.remote.worker.RemoteDirectoryUploadWorker;
import de.soderer.remote.worker.RemoteFileDownloadWorker;
import de.soderer.remote.worker.RemoteFileUploadWorker;

@SuppressWarnings("static-method")
public class FtpConnectionTest {
	/** Local working directory, deleted automatically by JUnit after each test */
	@TempDir
	Path tempDirectory;

	/** Local source directory of the uploads */
	private File localTestDirectory;
	/** Local target directory of the downloads */
	private File localTestDirectory2;

	private RemoteConnection getConnection() throws Exception {
		return new FtpConnection(System.getenv().get("FTP_HOSTNAME"), System.getenv().get("FTP_USERNAME"), System.getenv().get("FTP_PASSWORD").toCharArray());
	}

	@BeforeEach
	public void setup() {
		localTestDirectory = new File(tempDirectory.toFile(), "test");
		localTestDirectory.mkdir();
		localTestDirectory2 = new File(tempDirectory.toFile(), "test2");
	}

	@Test
	public void testFolderInfo() {
		try (RemoteConnection connection = getConnection()) {
			final FolderInfo remoteFolderInfo = connection.getFolderInfo("/soderer.de/files/download");
			assertEquals("download", remoteFolderInfo.getFolderName());
		} catch (final Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void directoryRoundTrip() {
		try (RemoteConnection connection = getConnection()) {
			if (connection.getFolderInfo("/soderer.de/files/download/test") != null) {
				connection.deleteFolder("/soderer.de/files/download/test");
			}
			try {
				final File testFolderZipFile = new File(getClass().getClassLoader().getResource("TestFolder.zip").getFile());
				RemoteTestUtilities.unzipFile(testFolderZipFile, localTestDirectory);
				new RemoteDirectoryUploadWorker(null, connection, localTestDirectory, "/soderer.de/files/download", "test").work();
				final FolderInfo remoteFolderInfo = connection.getFolderInfo("/soderer.de/files/download/test");
				assertEquals("test", remoteFolderInfo.getFolderName());
				new RemoteDirectoryDownloadWorker(null, connection, "/soderer.de/files/download/test", localTestDirectory2.getParentFile(), "test2").work();
				RemoteTestUtilities.assertDirectoriesEqual(localTestDirectory, localTestDirectory2);
			} catch (final Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			} finally {
				if (connection.getFolderInfo("/soderer.de/files/download/test") != null) {
					connection.deleteFolder("/soderer.de/files/download/test");
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void fileRoundTrip() {
		try (RemoteConnection connection = getConnection()) {
			if (connection.getFolderInfo("/soderer.de/files/download") != null) {
				connection.deleteFolder("/soderer.de/files/download/test");
			}
			connection.createFolder("/soderer.de/files/download", "test");
			localTestDirectory2.mkdir();
			try {
				final File testFolderZipFile = new File(getClass().getClassLoader().getResource("TestFolder.zip").getFile());
				RemoteTestUtilities.unzipFile(testFolderZipFile, localTestDirectory);
				new RemoteFileUploadWorker(null, connection, new File(localTestDirectory, "TextFileWithUmlautÄ.txt"), "/soderer.de/files/download/test/TextFile With Umlaut Ä.txt").work();
				final FileInfo remoteFileInfo = connection.getFileInfo("/soderer.de/files/download/test/TextFile With Umlaut Ä.txt");
				assertEquals("TextFile With Umlaut Ä.txt", remoteFileInfo.getFileName());
				new RemoteFileDownloadWorker(null, connection, "/soderer.de/files/download/test/TextFile With Umlaut Ä.txt", new File(localTestDirectory2, "TextFileWithUmlautÄ.txt")).work();
				RemoteTestUtilities.assertFilesEqual(new File(localTestDirectory, "TextFileWithUmlautÄ.txt"), new File(localTestDirectory2, "TextFileWithUmlautÄ.txt"));
			} catch (final Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			} finally {
				if (connection.getFolderInfo("/soderer.de/files/download/test") != null) {
					connection.deleteFolder("/soderer.de/files/download/test");
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testRootFolderInfo() {
		try (RemoteConnection connection = getConnection()) {
			final FolderInfo remoteFolderInfo = connection.getFolderInfo("/");
			assertEquals("", remoteFolderInfo.getFolderName());
		} catch (final Exception e) {
			fail(e.getMessage());
		}
	}
}
