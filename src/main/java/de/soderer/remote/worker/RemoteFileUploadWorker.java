package de.soderer.remote.worker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import de.soderer.remote.RemoteConnection;
import de.soderer.utilities.FileUtilities;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

/**
 * Worker that uploads a local file to a {@link RemoteConnection}.
 * A cancellation aborts the running upload. If the upload is cancelled or fails, the partially uploaded file is deleted.
 * Note: Some servers may need a moment until a deletion after an aborted transfer is possible, so the deletion is only
 * tried once.
 */
public class RemoteFileUploadWorker extends WorkerSimple<RemoteFileUploadWorker> {
	/**
	 * Return value of InputStream read methods at the end of the stream.
	 */
	public static final int EOF = -1;

	private final RemoteConnection connection;

	private final File localFile;
	private final String remoteFilePath;

	/**
	 * Creates an upload worker after checking the local file and the remote destination.
	 *
	 * @param parent parent receiving the progress signals, may be null
	 * @param connection connection to upload to
	 * @param localFile local file to upload
	 * @param remoteFilePath remote file path, or a remote directory path ending with "/" to keep the local file name
	 * @throws Exception if a parameter is missing, the local file does not exist, the remote folder does not exist or the remote file exists already
	 */
	public RemoteFileUploadWorker(final WorkerParentSimple parent, final RemoteConnection connection, final File localFile, final String remoteFilePath) throws Exception {
		super(parent);

		this.connection = connection;
		this.localFile = localFile;

		if (connection == null) {
			throw new Exception("No RemoteConnection");
		} else if (localFile == null) {
			throw new Exception("No localFile");
		} else if (!localFile.exists()) {
			throw new Exception("Local file does not exist: \"" + localFile.getAbsolutePath() + "\"");
		} else if (!localFile.isFile()) {
			throw new Exception("Local file is not a file: \"" + localFile.getAbsolutePath() + "\"");
		}

		if (remoteFilePath != null && remoteFilePath.endsWith("/")) {
			this.remoteFilePath = remoteFilePath + localFile.getName();
		} else {
			this.remoteFilePath = remoteFilePath;
		}

		if (Utilities.isBlank(this.remoteFilePath)) {
			throw new Exception("No remoteFilePath");
		} else if (!"/".equals(FileUtilities.getParentFromPath(this.remoteFilePath)) && connection.getFolderInfo(FileUtilities.getParentFromPath(this.remoteFilePath)) == null) {
			throw new Exception("Remote file parent folder does not exist: \"" + this.remoteFilePath + "\"");
		} else if (connection.getFileInfo(this.remoteFilePath) != null) {
			throw new Exception("Remote file already exists: \"" + this.remoteFilePath + "\"");
		}
	}

	@Override
	public RemoteFileUploadWorker work() throws Exception {
		signalUnlimitedProgress();

		itemsToDo = localFile.length();

		signalProgress();
		try (InputStream inputStream = new ProgressInputStream(new FileInputStream(localFile))) {
			connection.uploadFile(remoteFilePath, inputStream);
		} catch (final Exception e) {
			deleteRemoteFileQuietly();
			if (cancel) {
				return this;
			} else {
				throw e;
			}
		}

		if (cancel) {
			deleteRemoteFileQuietly();
		} else {
			signalProgress(true);
		}

		return this;
	}

	/**
	 * Deletes the (partially) uploaded remote file. The remote file did not exist before, which is checked by the constructor.
	 */
	private void deleteRemoteFileQuietly() {
		try {
			if (connection.getFileInfo(remoteFilePath) != null) {
				connection.deleteFile(remoteFilePath);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			// The connection may be unusable after an aborted transfer, nothing more can be done here
		}
	}

	/**
	 * Counts the uploaded bytes and aborts the upload with an IOException when the worker is cancelled.
	 */
	private class ProgressInputStream extends FilterInputStream {
		protected ProgressInputStream(final InputStream inputStream) {
			super(inputStream);
		}

		@Override
		public int read() throws IOException {
			checkCancel();
			final int readByte = super.read();
			if (readByte != EOF) {
				itemsDone++;
				signalProgress();
			}
			return readByte;
		}

		@Override
		public int read(final byte b[], final int off, final int len) throws IOException {
			checkCancel();
			final int readBytes = super.read(b, off, len);
			if (readBytes > 0) {
				itemsDone += readBytes;
				signalProgress();
			}
			return readBytes;
		}

		private void checkCancel() throws IOException {
			if (cancel) {
				throw new IOException("Upload cancelled");
			}
		}
	}
}
