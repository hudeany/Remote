package de.soderer.remote.worker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import de.soderer.remote.RemoteConnection;
import de.soderer.utilities.FileUtilities;
import de.soderer.utilities.StringUtilities;
import de.soderer.utilities.worker.WorkerDual;
import de.soderer.utilities.worker.WorkerParentDual;

/**
 * Worker that uploads a local directory with all its files and subdirectories into a new remote directory.
 * Symbolic links to directories are not followed. A cancellation aborts the running file upload and deletes the
 * partially uploaded file.
 */
public class RemoteDirectoryUploadWorker extends WorkerDual<RemoteDirectoryUploadWorker> {
	/**
	 * Return value of InputStream read methods at the end of the stream.
	 */
	public static final int EOF = -1;

	private final RemoteConnection connection;
	private final File localDirectory;
	private String remoteParentDirectoryPath;
	private final String remoteDirectoryName;

	/**
	 * Creates an upload worker. The local directory is copied into a new remote directory with the same name.
	 *
	 * @param parent parent receiving the progress signals, may be null
	 * @param connection connection to upload to
	 * @param localDirectory local directory to upload
	 * @param remoteParentDirectoryPath existing remote directory to create the new directory in
	 * @throws Exception if a parameter is missing, a directory does not exist or the remote directory exists already
	 */
	public RemoteDirectoryUploadWorker(final WorkerParentDual parent, final RemoteConnection connection, final File localDirectory, final String remoteParentDirectoryPath) throws Exception {
		this(parent, connection, localDirectory, remoteParentDirectoryPath, null);
	}

	/**
	 * Local directory "localDirectory" will be copied to remote "remoteParentDirectoryPath/remoteDirectoryName"
	 *
	 * @param parent parent receiving the progress signals, may be null
	 * @param connection connection to upload to
	 * @param localDirectory local directory to upload
	 * @param remoteParentDirectoryPath existing remote directory to create the new directory in
	 * @param remoteDirectoryName name of the new remote directory, or null for the name of the local directory
	 * @throws Exception if a parameter is missing, a directory does not exist or the remote directory exists already
	 */
	public RemoteDirectoryUploadWorker(final WorkerParentDual parent, final RemoteConnection connection, final File localDirectory, final String remoteParentDirectoryPath, final String remoteDirectoryName) throws Exception {
		super(parent);

		this.connection = connection;
		this.localDirectory = localDirectory;
		this.remoteParentDirectoryPath = remoteParentDirectoryPath;

		if (connection == null) {
			throw new Exception("No RemoteConnection");
		} else if (localDirectory == null) {
			throw new Exception("No localDestination");
		} else if (!localDirectory.exists()) {
			throw new Exception("Source directory not found: \"" + localDirectory.getAbsolutePath() + "\"");
		} else if (!localDirectory.isDirectory()) {
			throw new Exception("Source directory is no directory: \"" + localDirectory.getAbsolutePath() + "\"");
		} else if (StringUtilities.isBlank(remoteParentDirectoryPath)) {
			throw new Exception("No remoteParentDirectoryPath");
		} else if (!"/".equals(remoteParentDirectoryPath) && connection.getFolderInfo(remoteParentDirectoryPath) == null) {
			throw new Exception("Remote directory not found or is no directory: \"" + remoteParentDirectoryPath + "\"");
		}

		// The root directory "/" keeps its separator
		if (this.remoteParentDirectoryPath.length() > 1 && this.remoteParentDirectoryPath.endsWith("/")) {
			this.remoteParentDirectoryPath = this.remoteParentDirectoryPath.substring(0, this.remoteParentDirectoryPath.length() - 1);
		}

		if (StringUtilities.isBlank(remoteDirectoryName)) {
			this.remoteDirectoryName = this.localDirectory.getName();
		} else {
			this.remoteDirectoryName = remoteDirectoryName;
		}

		final String remotePath = joinRemotePath(this.remoteParentDirectoryPath, this.remoteDirectoryName);
		if (connection.getFolderInfo(remotePath) != null || connection.getFileInfo(remotePath) != null) {
			throw new Exception("Remote directory already exists: \"" + remotePath + "\"");
		}
	}

	@Override
	public RemoteDirectoryUploadWorker work() throws Exception {
		signalUnlimitedProgress();
		itemsToDo = FileUtilities.getAllSubfilesSize(localDirectory);
		signalProgress(true);
		connection.createFolder(remoteParentDirectoryPath, remoteDirectoryName);
		recursiveUpload(localDirectory, joinRemotePath(remoteParentDirectoryPath, remoteDirectoryName));
		return this;
	}

	private static String joinRemotePath(final String parentPath, final String name) {
		if (parentPath.endsWith("/")) {
			return parentPath + name;
		} else {
			return parentPath + "/" + name;
		}
	}

	private void recursiveUpload(final File localUploadDirectory, final String destinationDirectory) throws Exception {
		final File[] subFiles = localUploadDirectory.listFiles();
		if (subFiles != null) {
			for (final File file : subFiles) {
				if (cancel) {
					break;
				} else if (file.isDirectory()) {
					// Symbolic links to directories are skipped like in FileUtilities.getAllSubfilesSize, which also prevents endless loops
					if (!Files.isSymbolicLink(file.toPath())) {
						connection.createFolder(destinationDirectory, file.getName());
						recursiveUpload(file, joinRemotePath(destinationDirectory, file.getName()));
					}
				} else {
					final String nextRemoteFile = joinRemotePath(destinationDirectory, file.getName());
					subItemsDone = 0;
					subItemsToDo = file.length();
					signalItemStart(file.getName(), null);
					signalProgress();
					try (InputStream inputStream = new ProgressInputStream(new FileInputStream(file))) {
						connection.uploadFile(nextRemoteFile, inputStream);
					} catch (final Exception e) {
						deleteRemoteFileQuietly(nextRemoteFile);
						if (cancel) {
							return;
						} else {
							throw e;
						}
					}
					itemsDone += subItemsDone;
					signalProgress();

					if (cancel) {
						deleteRemoteFileQuietly(nextRemoteFile);
					}
				}
			}
		}
	}

	/**
	 * Deletes a (partially) uploaded remote file. The whole remote directory is new, so the file did not exist before.
	 */
	private void deleteRemoteFileQuietly(final String remoteFilePath) {
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
				subItemsDone++;
				signalItemProgress();
			}
			return readByte;
		}

		@Override
		public int read(final byte b[], final int off, final int len) throws IOException {
			checkCancel();
			final int readBytes = super.read(b, off, len);
			if (readBytes > 0) {
				subItemsDone += readBytes;
				signalItemProgress();
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
