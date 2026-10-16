package de.soderer.remote.worker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import de.soderer.remote.DirectoryListing;
import de.soderer.remote.FileInfo;
import de.soderer.remote.FolderInfo;
import de.soderer.remote.RemoteConnection;
import de.soderer.utilities.FileUtilities;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerDual;
import de.soderer.utilities.worker.WorkerParentDual;

/**
 * Worker that downloads a remote directory with all its files and subdirectories into a new local directory.
 * Remote names leading outside of the local destination are rejected (path traversal), the entries "." and ".." are skipped.
 */
public class RemoteDirectoryDownloadWorker extends WorkerDual<RemoteDirectoryDownloadWorker> {
	private final RemoteConnection connection;
	private final String remoteDirectoryPath;
	private final File localParentDirectory;
	private final String localDirectoryName;

	/**
	 * Creates a download worker. The remote directory is copied into a new local directory with the same name.
	 *
	 * @param parent parent receiving the progress signals, may be null
	 * @param connection connection to download from
	 * @param remoteDirectoryPath path of the remote directory
	 * @param localParentDirectory existing local directory to create the new directory in
	 * @throws Exception if a parameter is missing, the remote directory does not exist or the local directory exists already
	 */
	public RemoteDirectoryDownloadWorker(final WorkerParentDual parent, final RemoteConnection connection, final String remoteDirectoryPath, final File localParentDirectory) throws Exception {
		this(parent, connection, remoteDirectoryPath, localParentDirectory, null);
	}

	/**
	 * Remote directory "remoteDirectoryPath" will be copied to local "localParentDirectoryPath/localDirectoryName"
	 *
	 * @param parent parent receiving the progress signals, may be null
	 * @param connection connection to download from
	 * @param remoteDirectoryPath path of the remote directory
	 * @param localParentDirectory existing local directory to create the new directory in
	 * @param localDirectoryName name of the new local directory, or null for the name of the remote directory
	 * @throws Exception if a parameter is missing, the remote directory does not exist or the local directory exists already
	 */
	public RemoteDirectoryDownloadWorker(final WorkerParentDual parent, final RemoteConnection connection, final String remoteDirectoryPath, final File localParentDirectory, final String localDirectoryName) throws Exception {
		super(parent);

		if (connection == null) {
			throw new Exception("No RemoteConnection");
		} else if (Utilities.isBlank(remoteDirectoryPath)) {
			throw new Exception("No remoteDirectoryPath");
		} else if (localParentDirectory == null) {
			throw new Exception("No localDestination");
		}

		this.connection = connection;
		this.remoteDirectoryPath = FileUtilities.trimTrailingFileSeparator(remoteDirectoryPath);
		this.localParentDirectory = localParentDirectory;
		if (Utilities.isBlank(localDirectoryName)) {
			this.localDirectoryName = FileUtilities.getFilenameFromPath(this.remoteDirectoryPath);
			if (Utilities.isBlank(this.localDirectoryName)) {
				throw new Exception("Cannot derive the local directory name from remote directory \"" + remoteDirectoryPath + "\", localDirectoryName is needed");
			}
		} else {
			this.localDirectoryName = localDirectoryName;
		}

		if (this.connection.getFolderInfo(this.remoteDirectoryPath) == null) {
			throw new Exception("Remote directory not found or is no directory: \"" + this.remoteDirectoryPath + "\"");
		} else if (!this.localParentDirectory.exists()) {
			throw new Exception("Destination not found: \"" + this.localParentDirectory.getAbsolutePath() + "\"");
		} else if (!this.localParentDirectory.isDirectory()) {
			throw new Exception("Destination is no directory: \"" + this.localParentDirectory.getAbsolutePath() + "\"");
		}

		if (new File(this.localParentDirectory, this.localDirectoryName).exists()) {
			throw new Exception("Local directory already exists: \"" + new File(this.localParentDirectory, this.localDirectoryName).getAbsolutePath() + "\"");
		}
	}

	@Override
	public RemoteDirectoryDownloadWorker work() throws Exception {
		signalUnlimitedProgress();
		itemsToDo = getRemoteDirectorySize(remoteDirectoryPath);
		signalProgress(true);
		final File destinationParentDirectoryPath = new File(localParentDirectory, localDirectoryName);
		assertNoPathTraversal(localParentDirectory, destinationParentDirectoryPath);
		createDirectory(destinationParentDirectoryPath);
		recursiveDownload(remoteDirectoryPath, destinationParentDirectoryPath);
		return this;
	}

	private long getRemoteDirectorySize(final String subDirectoryPath) throws Exception {
		final DirectoryListing directory = connection.getDirectory(subDirectoryPath);
		long fileSizeSum = 0;

		for (final FileInfo fileInfo : directory.files()) {
			fileSizeSum += fileInfo.getFileSize();
		}

		for (final FolderInfo folderInfo : directory.folders()) {
			if (isSelfOrParentEntry(folderInfo.getFolderName())) {
				continue;
			}
			fileSizeSum += getRemoteDirectorySize(subDirectoryPath + "/" + folderInfo.getFolderName());
		}

		return fileSizeSum;
	}

	private void recursiveDownload(final String remoteFolderPath, final File destinationParentDirectory) throws Exception {
		final DirectoryListing directory = connection.getDirectory(remoteFolderPath);

		for (final FileInfo fileInfo : directory.files()) {
			if (!cancel && !isSelfOrParentEntry(fileInfo.getFileName())) {
				final String nextRemoteFile = remoteFolderPath + "/" + fileInfo.getFileName();
				final File nextLocalFile = new File(destinationParentDirectory, fileInfo.getFileName());
				assertNoPathTraversal(destinationParentDirectory, nextLocalFile);
				subItemsDone = 0;
				subItemsToDo = fileInfo.getFileSize();
				signalItemStart(nextRemoteFile.substring(remoteDirectoryPath.length() + 1), null);
				signalProgress();
				try (InputStream inputStream = connection.downloadFile(nextRemoteFile);
						OutputStream outputStream = new FileOutputStream(nextLocalFile)) {

					final byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = inputStream.read(buffer)) >= 0) {
						if (cancel) {
							break;
						} else {
							outputStream.write(buffer, 0, bytesRead);
							subItemsDone += bytesRead;
							signalItemProgress();
						}
					}
					Utilities.clear(buffer);
					itemsDone += subItemsDone;
					signalProgress();
				} catch (final Exception e) {
					// The local file is new, so a partially written file can be removed
					nextLocalFile.delete();
					throw e;
				}

				if (cancel) {
					nextLocalFile.delete();
				}
			}
		}

		for (final FolderInfo subFolderInfo : directory.folders()) {
			if (!cancel && !isSelfOrParentEntry(subFolderInfo.getFolderName())) {
				final File nextFolderDestination = new File(destinationParentDirectory, subFolderInfo.getFolderName());
				assertNoPathTraversal(destinationParentDirectory, nextFolderDestination);
				createDirectory(nextFolderDestination);
				recursiveDownload(remoteFolderPath + "/" + subFolderInfo.getFolderName(), nextFolderDestination);
			}
		}
	}

	/**
	 * The remote server supplies file/folder names that we use to build local paths.
	 * A malicious or corrupted server response (e.g. a name containing "../" or an
	 * absolute path) could otherwise make the download escape destinationParentDirectory
	 * and write files anywhere on the local file system (path traversal / "zip-slip"-style attack).
	 */
	private static void assertNoPathTraversal(final File destinationParentDirectory, final File candidate) throws Exception {
		String parentCanonicalPath = destinationParentDirectory.getCanonicalPath();
		if (!parentCanonicalPath.endsWith(File.separator)) {
			// A root directory like "/" or "C:\" already ends with a separator
			parentCanonicalPath += File.separator;
		}
		final String candidateCanonicalPath = candidate.getCanonicalPath();
		// The candidate must be strictly inside the parent directory, a name like "." would point to the parent itself
		if (!candidateCanonicalPath.startsWith(parentCanonicalPath) || candidateCanonicalPath.length() <= parentCanonicalPath.length()) {
			throw new Exception("Remote item name leads outside of the download destination: \"" + candidate.getName() + "\"");
		}
	}

	/**
	 * Checks for the directory entries "." and "..", which some servers include in their listings.
	 * Following "." would download the same directory endlessly.
	 */
	private static boolean isSelfOrParentEntry(final String name) {
		return ".".equals(name) || "..".equals(name);
	}

	private static void createDirectory(final File directory) throws Exception {
		// An existing directory is accepted, e.g. for remote names differing only in case on a case insensitive file system
		if (!directory.mkdir() && !directory.isDirectory()) {
			throw new Exception("Cannot create local directory: \"" + directory.getAbsolutePath() + "\"");
		}
	}
}
