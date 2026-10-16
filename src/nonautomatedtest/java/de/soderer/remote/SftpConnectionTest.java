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
import de.soderer.utilities.FileUtilities;

@SuppressWarnings("static-method")
public class SftpConnectionTest {
	/** Local working directory, deleted automatically by JUnit after each test */
	@TempDir
	Path tempDirectory;

	/** Local source directory of the uploads */
	private File localTestDirectory;
	/** Local target directory of the downloads */
	private File localTestDirectory2;

	private RemoteConnection getConnection() throws Exception {
		final byte[] keyFileData = FileUtilities.readFileToByteArray(new File(System.getenv().get("SFTP_KEYFILE")));
		return new SftpConnection(System.getenv().get("SFTP_HOSTNAME"), System.getenv().get("SFTP_USERNAME"), keyFileData, System.getenv().get("SFTP_PASSWORD").toCharArray(), 22, System.getenv().get("SFTP_HOSTKEY"));
	}

	@BeforeEach
	public void setup() {
		localTestDirectory = new File(tempDirectory.toFile(), "test");
		localTestDirectory.mkdir();
		localTestDirectory2 = new File(tempDirectory.toFile(), "test2");
	}

	@Test
	public void directoryRoundTrip() {
		try (RemoteConnection connection = getConnection()) {
			if (connection.getFolderInfo("/home/andreas/test") != null) {
				connection.deleteFolder("/home/andreas/test");
			}
			try {
				final File testFolderZipFile = new File(getClass().getClassLoader().getResource("TestFolder.zip").getFile());
				RemoteTestUtilities.unzipFile(testFolderZipFile, localTestDirectory);
				new RemoteDirectoryUploadWorker(null, connection, localTestDirectory, "/home/andreas", "test").work();
				final FolderInfo remoteFolderInfo = connection.getFolderInfo("/home/andreas/test");
				assertEquals("test", remoteFolderInfo.getFolderName());
				new RemoteDirectoryDownloadWorker(null, connection, "/home/andreas/test", localTestDirectory2.getParentFile(), "test2").work();
				RemoteTestUtilities.assertDirectoriesEqual(localTestDirectory, localTestDirectory2);
			} catch (final Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			} finally {
				if (connection.getFolderInfo("/home/andreas/test") != null) {
					connection.deleteFolder("/home/andreas/test");
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
			if (connection.getFolderInfo("/home/andreas/test") != null) {
				connection.deleteFolder("/home/andreas/test");
			}
			connection.createFolder("/home/andreas", "test");
			localTestDirectory2.mkdir();
			try {
				final File testFolderZipFile = new File(getClass().getClassLoader().getResource("TestFolder.zip").getFile());
				RemoteTestUtilities.unzipFile(testFolderZipFile, localTestDirectory);
				new RemoteFileUploadWorker(null, connection, new File(localTestDirectory, "TextFileWithUmlautÄ.txt"), "/home/andreas/test/TextFile With Umlaut Ä.txt").work();
				final FileInfo remoteFileInfo = connection.getFileInfo("/home/andreas/test/TextFile With Umlaut Ä.txt");
				assertEquals("TextFile With Umlaut Ä.txt", remoteFileInfo.getFileName());
				new RemoteFileDownloadWorker(null, connection, "/home/andreas/test/TextFile With Umlaut Ä.txt", new File(localTestDirectory2, "TextFileWithUmlautÄ.txt")).work();
				RemoteTestUtilities.assertFilesEqual(new File(localTestDirectory, "TextFileWithUmlautÄ.txt"), new File(localTestDirectory2, "TextFileWithUmlautÄ.txt"));
			} catch (final Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			} finally {
				if (connection.getFolderInfo("/home/andreas/test") != null) {
					connection.deleteFolder("/home/andreas/test");
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
}
