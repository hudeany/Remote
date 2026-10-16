package de.soderer.remote;

import java.time.ZonedDateTime;

import de.soderer.utilities.DateUtilities;

/**
 * Information about a remote file.
 */
public class FileInfo {
	private final String fileName;
	private final long fileSize;
	private final ZonedDateTime modificationDate;

	/**
	 * Creates file information.
	 *
	 * @param fileName name of the file without path
	 * @param fileSize size of the file in bytes
	 * @param modificationDate time of the last modification, may be null if unknown
	 */
	public FileInfo(final String fileName, final long fileSize, final ZonedDateTime modificationDate) {
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.modificationDate = modificationDate;
	}

	/**
	 * Returns the name of the file.
	 *
	 * @return file name without path
	 */
	public String getFileName() {
		return fileName;
	}

	/**
	 * Returns the size of the file.
	 *
	 * @return size in bytes
	 */
	public long getFileSize() {
		return fileSize;
	}

	/**
	 * Returns the time of the last modification.
	 *
	 * @return modification time, may be null if unknown
	 */
	public ZonedDateTime getModificationDate() {
		return modificationDate;
	}

	@Override
	public String toString() {
		return fileName + " [" + fileSize + " Byte, " + (modificationDate != null ? DateUtilities.formatDate(DateUtilities.YYYY_MM_DD_HHMMSS, modificationDate.toLocalDateTime()) : "Unknown modification date") + "]";
	}
}
