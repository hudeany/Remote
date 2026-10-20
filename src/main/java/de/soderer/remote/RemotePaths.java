package de.soderer.remote;

/**
 * Helper for remote paths in the format of {@link RemoteConnection}: "/" as separator, a leading "/" for absolute paths,
 * no leading "/" for paths relative to the user's home folder.
 */
final class RemotePaths {
	private RemotePaths() {
		// Utility class
	}

	/**
	 * Appends a name to a folder path without changing whether the path is absolute or relative.
	 *
	 * @param parentFolderPath folder path, "" or null for the home folder, "/" for the root folder
	 * @param name name of the sub folder or file
	 * @return combined path, e.g. "a/b" for ("a", "b"), "/b" for ("/", "b"), "b" for ("", "b")
	 */
	static String join(final String parentFolderPath, final String name) {
		if (parentFolderPath == null || parentFolderPath.isEmpty()) {
			// Without parent the result stays relative to the home folder
			return name;
		} else if (parentFolderPath.endsWith("/")) {
			return parentFolderPath + name;
		} else {
			return parentFolderPath + "/" + name;
		}
	}
}
