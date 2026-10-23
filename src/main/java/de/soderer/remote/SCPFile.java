package de.soderer.remote;

import de.soderer.utilities.StringUtilities;

/**
 * Descriptor of a remote file or directory for SCP transfers in the form "\\hostname/path/filename".
 * Path separators (Unix "/" or DOS "\") are kept as they are, unlike java.io.File, which would convert them.
 * The filename may contain wildcards like '*'. A descriptor without filename denotes a directory.
 * This class only parses and composes descriptors, it does not access any server.
 */
public class SCPFile {
	/** Prefix of every file descriptor (two backslashes) */
	public static final String PRELUDE = "\\\\";
	private static final String UNIX_SEPARATOR = "/";
	private static final String DOS_SEPARATOR = "\\";

	private String hostname = null;
	private String path = null; // always ends with a Separator
	private String filename = null; // also allows wildcards like '*'

	/**
	 * Parses a file descriptor.
	 * Allowed formats:
	 * <ul>
	 * <li>"\\hostname\path1\path2\filename": DOS file</li>
	 * <li>"\\hostname/path1/path2/filename": Unix file</li>
	 * <li>"\\hostname/path1/path2/": directory</li>
	 * <li>"\\hostname/filename": file in the root directory</li>
	 * </ul>
	 * The filename may contain wildcards like '*'.
	 *
	 * @param hostnamePathAndFileName file descriptor
	 * @throws Exception if the descriptor is empty, lacks the prelude, a path or a hostname
	 */
	public SCPFile(final String hostnamePathAndFileName) throws Exception {
		if (StringUtilities.isEmpty(hostnamePathAndFileName)) {
			throw new Exception("SCPFile: Empty filespecifier");
		}
		if (!hostnamePathAndFileName.startsWith(PRELUDE)) {
			throw new Exception("SCPFile: Invalid prelude in filespecifier: " + hostnamePathAndFileName);
		}

		final int firstIndexOfUnixPathSeparator = hostnamePathAndFileName.indexOf(UNIX_SEPARATOR, PRELUDE.length());
		final int firstIndexOfWindowsPathSeparator = hostnamePathAndFileName.indexOf(DOS_SEPARATOR, PRELUDE.length());
		int firstIndexOfPathSeparator = -1;
		if (firstIndexOfUnixPathSeparator != -1 && firstIndexOfWindowsPathSeparator != -1) {
			firstIndexOfPathSeparator = Math.min(firstIndexOfUnixPathSeparator, firstIndexOfWindowsPathSeparator);
		} else if (firstIndexOfUnixPathSeparator != -1) {
			firstIndexOfPathSeparator = firstIndexOfUnixPathSeparator;
		} else {
			firstIndexOfPathSeparator = firstIndexOfWindowsPathSeparator;
		}

		int lastIndexOfPathSeparator = Math.max(hostnamePathAndFileName.lastIndexOf(UNIX_SEPARATOR), hostnamePathAndFileName.lastIndexOf(DOS_SEPARATOR));
		if (lastIndexOfPathSeparator < PRELUDE.length()) {
			lastIndexOfPathSeparator = -1;
		}

		if (firstIndexOfPathSeparator > -1 && lastIndexOfPathSeparator > -1) {
			hostname = hostnamePathAndFileName.substring(PRELUDE.length(), firstIndexOfPathSeparator);
			path = hostnamePathAndFileName.substring(firstIndexOfPathSeparator, lastIndexOfPathSeparator + 1);
			filename = hostnamePathAndFileName.substring(lastIndexOfPathSeparator + 1);
		} else {
			throw new Exception("SCPFile: Missing path- or filedescription in filespecifier: " + hostnamePathAndFileName);
		}

		if (StringUtilities.isEmpty(hostname)) {
			throw new Exception("SCPFile: Empty hostname: " + hostnamePathAndFileName);
		}
	}

	/**
	 * Creates a descriptor from its parts. A missing trailing separator is appended to the path.
	 *
	 * @param hostname hostname of the server
	 * @param path directory path
	 * @param filename filename (may contain wildcards like '*'), or null for the directory itself
	 * @throws Exception if hostname or path is empty or the filename contains a path separator
	 */
	public SCPFile(final String hostname, final String path, final String filename) throws Exception {
		if (StringUtilities.isEmpty(hostname)) {
			throw new Exception("SCPFile: Empty hostname");
		}
		if (StringUtilities.isEmpty(path)) {
			throw new Exception("SCPFile: Empty path");
		}
		if (filename != null && (filename.contains(UNIX_SEPARATOR) || filename.contains(DOS_SEPARATOR))) {
			throw new Exception("SCPFile: Invalid filename: " + filename);
		}

		this.hostname = hostname;
		setPath(path);
		this.filename = filename;
	}

	/**
	 * Creates a directory descriptor.
	 *
	 * @param hostname hostname of the server
	 * @param path directory path
	 * @throws Exception if hostname or path is empty
	 */
	public SCPFile(final String hostname, final String path) throws Exception {
		this(hostname, path, null);
	}

	/**
	 * Hostname of the server.
	 *
	 * @return hostname
	 */
	public String getHostname() {
		return hostname;
	}

	/**
	 * Sets the hostname of the server.
	 *
	 * @param hostname hostname
	 * @return this instance for chaining
	 */
	public SCPFile setHostname(final String hostname) {
		this.hostname = hostname;
		return this;
	}

	/**
	 * Directory path, always ending with a path separator.
	 *
	 * @return path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Sets the directory path. A missing trailing separator is appended ("\\" if the path contains DOS separators, otherwise "/").
	 *
	 * @param path directory path, must not be null
	 * @return this instance for chaining
	 */
	public SCPFile setPath(final String path) {
		if (path.endsWith(DOS_SEPARATOR) || path.endsWith(UNIX_SEPARATOR)) {
			this.path = path;
		} else if (path.contains(DOS_SEPARATOR)) {
			this.path = path + DOS_SEPARATOR;
		} else {
			this.path = path + UNIX_SEPARATOR;
		}

		return this;
	}

	/**
	 * Filename, which may contain wildcards like '*'.
	 *
	 * @return filename, or null (or empty) for a directory descriptor
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * Full descriptor in the form "\\hostname/path/filename".
	 *
	 * @return descriptor string
	 */
	@Override
	public String toString() {
		if (StringUtilities.isEmpty(filename)) {
			return PRELUDE + hostname + path;
		} else {
			return PRELUDE + hostname + path + filename;
		}
	}

	/**
	 * Path and filename without host part.
	 *
	 * @return path and filename, or only the path for a directory descriptor
	 */
	public String getPathAndFilename() {
		if (StringUtilities.isEmpty(filename)) {
			return path;
		} else {
			return path + filename;
		}
	}

	/**
	 * Sets the filename, which may contain wildcards like '*'.
	 * Setting null turns this descriptor into the descriptor of its directory, setting a name for a directory
	 * descriptor denotes a file within that directory.
	 *
	 * @param filename new filename, or null
	 * @return this instance for chaining
	 * @throws Exception if the filename contains a path separator
	 */
	public SCPFile setFilename(final String filename) throws Exception {
		if (filename != null && (filename.contains(UNIX_SEPARATOR) || filename.contains(DOS_SEPARATOR))) {
			throw new Exception("SCPFile: Invalid new filename: " + filename);
		}
		this.filename = filename;
		return this;
	}

	/**
	 * Descriptor of the directory of this descriptor.
	 *
	 * @return new directory descriptor
	 * @throws Exception if the hostname or path is empty
	 */
	public SCPFile getDirectory() throws Exception {
		return new SCPFile(hostname, path);
	}

	/**
	 * Whether this descriptor denotes a directory (has no filename).
	 *
	 * @return true for a directory
	 */
	public boolean isDirectory() {
		return StringUtilities.isEmpty(filename);
	}

	/**
	 * Whether this descriptor denotes a file (has a filename).
	 *
	 * @return true for a file
	 */
	public boolean isFile() {
		return !isDirectory();
	}

	/**
	 * Parent descriptor: the directory of a file, or the parent directory of a directory.
	 *
	 * @return new parent directory descriptor
	 * @throws Exception if a directory has no parent (root directory)
	 */
	public SCPFile getParent() throws Exception {
		if (isDirectory()) {
			final String newPath = path.substring(0, path.length() - 1);
			if (newPath.length() == 0) {
				throw new Exception("SCPFile: No parentpath available for: " + path);
			}
			final int lastIndexOfPathSeparator = Math.max(newPath.lastIndexOf(UNIX_SEPARATOR), newPath.lastIndexOf(DOS_SEPARATOR));
			return new SCPFile(hostname, newPath.substring(0, lastIndexOfPathSeparator + 1));
		} else {
			return new SCPFile(hostname, path); // without filename => directory
		}
	}
}
