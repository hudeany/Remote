package de.soderer.remote;

import java.io.Closeable;
import java.io.InputStream;


/**
 * Access to the files and folders of a remote server (e.g. via SFTP, FTP, SMB or HTTP directory listings).
 * Paths use "/" as separator. Methods not supported by an implementation throw an Exception.
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
}