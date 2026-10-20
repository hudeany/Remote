package de.soderer.remote;

import java.io.Closeable;
import java.io.InputStream;

/**
 * Access to the files and folders of a remote server (e.g. via SFTP, FTP, SMB or HTTP directory listings).
 * Methods not supported by an implementation throw an Exception.
 *
 * <p>Paths use "/" as separator. A path with leading "/" is absolute and starts at the root folder of the server
 * ("/" is the root folder itself). A path without leading "/" is relative to the home folder of the user
 * ("" is the home folder itself).</p>
 *
 * <p>Implementations without a separate home folder resolve both kinds of paths against their single base folder:
 * {@link SmbConnection} against the share root, {@link HttpFileConnection} against its start path.</p>
 */
public interface RemoteConnection extends Closeable {
	/**
	 * Creates a new folder.
	 *
	 * @param parentFolderPath path of the existing parent folder
	 * @param folderName name of the new folder
	 * @throws Exception if the folder cannot be created
	 */
	void createFolder(String parentFolderPath, String folderName) throws Exception;

	/**
	 * Returns information about a folder.
	 *
	 * @param folderPath path of the folder
	 * @return folder information, or null if the path does not exist or is no folder
	 * @throws Exception if the server cannot be accessed
	 */
	FolderInfo getFolderInfo(String folderPath) throws Exception;

	/**
	 * Lists the content of a folder.
	 *
	 * @param path path of the folder
	 * @return sub folders and files of the folder
	 * @throws Exception if the folder cannot be listed
	 */
	DirectoryListing getDirectory(String path) throws Exception;

	/**
	 * Deletes a folder including its content.
	 *
	 * @param folderPath path of the folder
	 * @throws Exception if the folder cannot be deleted
	 */
	void deleteFolder(String folderPath) throws Exception;

	/**
	 * Uploads data into a remote file.
	 *
	 * @param filePath path of the remote file
	 * @param fileInputStream data to upload, is not closed
	 * @throws Exception if the file cannot be written
	 */
	void uploadFile(String filePath, InputStream fileInputStream) throws Exception;

	/**
	 * Returns information about a file.
	 *
	 * @param filePath path of the file
	 * @return file information, or null if the path does not exist or is no file
	 * @throws Exception if the server cannot be accessed
	 */
	FileInfo getFileInfo(String filePath) throws Exception;

	/**
	 * Opens a remote file for reading.
	 *
	 * @param filePath path of the file
	 * @return stream with the file content, the caller must close it
	 * @throws Exception if the file cannot be read
	 */
	InputStream downloadFile(String filePath) throws Exception;

	/**
	 * Deletes a file.
	 *
	 * @param filePath path of the file
	 * @throws Exception if the file cannot be deleted
	 */
	void deleteFile(String filePath) throws Exception;

	/**
	 * Whether a file exists at this path (false for folders).
	 *
	 * @param filePath path of the file
	 * @return true if a file exists at this path
	 * @throws Exception if the server cannot be accessed
	 */
	default boolean fileExists(final String filePath) throws Exception {
		return getFileInfo(filePath) != null;
	}

	/**
	 * Whether a folder exists at this path (false for files).
	 *
	 * @param folderPath path of the folder
	 * @return true if a folder exists at this path
	 * @throws Exception if the server cannot be accessed
	 */
	default boolean folderExists(final String folderPath) throws Exception {
		return getFolderInfo(folderPath) != null;
	}

	/**
	 * Creates a folder including all missing parent folders (like mkdir -p). Does nothing if the folder exists.
	 *
	 * @param folderPath path of the folder
	 * @throws Exception if the path exists as file or a folder cannot be created
	 */
	default void createFolderPath(final String folderPath) throws Exception {
		final String path = folderPath == null ? "" : folderPath.replace("\\", "/");
		// Keep the path absolute or relative, depending on its leading separator
		String currentPath = path.startsWith("/") ? "/" : "";
		for (final String folderName : path.split("/")) {
			if (folderName.isEmpty()) {
				// Leading, trailing or doubled separator
				continue;
			}
			final String parentPath = currentPath;
			currentPath = RemotePaths.join(parentPath, folderName);
			if (!folderExists(currentPath)) {
				if (fileExists(currentPath)) {
					throw new Exception("Path '" + currentPath + "' already exists as a file");
				}
				try {
					createFolder(parentPath, folderName);
				} catch (final Exception e) {
					// Another client may have created the folder in the meantime
					if (!folderExists(currentPath)) {
						throw e;
					}
				}
			}
		}
	}
}
