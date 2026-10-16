package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.soderer.remote.worker.RemoteDirectoryDownloadWorker;
import de.soderer.remote.worker.RemoteFileDownloadWorker;
import de.soderer.utilities.FileUtilities;

@SuppressWarnings("static-method")
public class HttpConnectionTest {
	private File localTestDirectory;

	private RemoteConnection getConnection() throws Exception {
		return new HttpFileConnection(true, "www-us.apache.org", "dist", null, null, 443);
	}

	@BeforeEach
	public void setup() throws Exception {
		localTestDirectory = Files.createTempDirectory("HttpConnectionTest_").toFile();
	}

	@AfterEach
	public void tearDown() throws Exception {
		if (localTestDirectory.exists()) {
			FileUtilities.delete(localTestDirectory);
		}
	}

	@Test
	public void directoryRoundTrip() {
		try (RemoteConnection connection = getConnection()) {
			new RemoteDirectoryDownloadWorker(null, connection, "/tomcat/tomcat-9/v9.0.43/bin", localTestDirectory.getParentFile(), "test").work();
		} catch (final Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void fileRoundTrip() {
		try (RemoteConnection connection = getConnection()) {
			new RemoteFileDownloadWorker(null, connection, "/tomcat/tomcat-9/v9.0.43/bin/apache-tomcat-9.0.43.tar.gz", new File(localTestDirectory, "apache-tomcat-9.0.43.tar.gz")).work();
		} catch (final Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
}
