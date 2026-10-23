package de.soderer.remote.worker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import de.soderer.remote.FileInfo;
import de.soderer.remote.RemoteConnection;
import de.soderer.utilities.StringUtilities;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

/**
 * Worker that downloads a file from a {@link RemoteConnection} into a new local file.
 * If it is cancelled, the partially downloaded file is deleted.
 */
public class RemoteFileDownloadWorker extends WorkerSimple<RemoteFileDownloadWorker> {
	private RemoteConnection connection = null;

	private String remoteFilePath = null;
	private File localFile = null;

	/**
	 * Creates a download worker after checking the remote file and the local destination.
	 *
	 * @param parent parent receiving the progress signals, may be null
	 * @param connection connection to download from
	 * @param remoteFilePath path of the remote file
	 * @param localFile local file to create, must not exist yet, its directory must exist
	 * @throws Exception if a parameter is missing, the remote file does not exist or the local file cannot be created
	 */
	public RemoteFileDownloadWorker(final WorkerParentSimple parent, final RemoteConnection connection, final String remoteFilePath, final File localFile) throws Exception {
		super(parent);

		this.connection = connection;
		this.remoteFilePath = remoteFilePath;
		this.localFile = localFile;

		if (connection == null) {
			throw new Exception("No RemoteConnection");
		} else if (StringUtilities.isBlank(remoteFilePath)) {
			throw new Exception("No remoteFilePath");
		} else if (connection.getFileInfo(remoteFilePath) == null) {
			throw new Exception("Remote file not found or is no file: \"" + remoteFilePath + "\"");
		} else if (localFile == null) {
			throw new Exception("No localFilePath");
		} else if (!localFile.getAbsoluteFile().getParentFile().exists()) {
			throw new Exception("Local file parent directory does not exist: \"" + localFile.getAbsolutePath() + "\"");
		} else if (localFile.exists()) {
			throw new Exception("Local file already exists: \"" + localFile.getAbsolutePath() + "\"");
		}
	}

	@Override
	public RemoteFileDownloadWorker work() throws Exception {
		signalUnlimitedProgress();

		final FileInfo fileInfo = connection.getFileInfo(remoteFilePath);
		if (fileInfo == null) {
			throw new Exception("Remote file not found or is no file: \"" + remoteFilePath + "\"");
		}
		itemsToDo = fileInfo.getFileSize();

		signalProgress();

		try (InputStream inputStream = connection.downloadFile(remoteFilePath);
				OutputStream outputStream = new FileOutputStream(localFile)) {
			final byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) >= 0) {
				if (cancel) {
					break;
				} else {
					outputStream.write(buffer, 0, bytesRead);
					itemsDone += bytesRead;
					signalProgress();
				}
			}
			Utilities.clear(buffer);
		} catch (final Exception e) {
			// The local file did not exist before, so a partially written file can be removed
			localFile.delete();
			throw e;
		}

		if (cancel) {
			localFile.delete();
		} else {
			signalProgress(true);
		}

		return this;
	}
}
