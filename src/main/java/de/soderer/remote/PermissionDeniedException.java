package de.soderer.remote;

/**
 * Exception signaling that the access to a remote folder was denied.
 */
public class PermissionDeniedException extends Exception {
	private static final long serialVersionUID = 6255198725581994792L;

	/** Path of the folder the access was denied for */
	private final String folderPath;

	/**
	 * Creates the exception.
	 *
	 * @param message error message
	 * @param folderPath path of the folder the access was denied for
	 */
	public PermissionDeniedException(final String message, final String folderPath) {
		super(message);

		this.folderPath = folderPath;
	}

	/**
	 * Returns the path of the folder the access was denied for.
	 *
	 * @return folder path
	 */
	public String getFolderPath() {
		return folderPath;
	}
}
