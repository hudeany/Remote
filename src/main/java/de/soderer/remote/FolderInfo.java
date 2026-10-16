package de.soderer.remote;

import java.time.ZonedDateTime;

import de.soderer.utilities.DateUtilities;

/**
 * Information about a remote folder.
 */
public class FolderInfo {
	private final String folderName;
	private final ZonedDateTime modificationDate;

	/**
	 * Creates folder information.
	 *
	 * @param folderName name of the folder without path, "/" for the root folder
	 * @param modificationDate time of the last modification, may be null if unknown
	 */
	public FolderInfo(final String folderName, final ZonedDateTime modificationDate) {
		this.folderName = folderName;
		this.modificationDate = modificationDate;
	}

	/**
	 * Returns the name of the folder.
	 *
	 * @return folder name without path
	 */
	public String getFolderName() {
		return folderName;
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
		return folderName + " [" + (getModificationDate() != null ? DateUtilities.formatDate(DateUtilities.YYYY_MM_DD_HHMMSS, getModificationDate().toLocalDateTime()) : "Unknown modification date") + "]";
	}
}
