package de.soderer.remote;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.codelibs.jcifs.smb.CIFSContext;
import org.codelibs.jcifs.smb.SmbConstants;
import org.codelibs.jcifs.smb.config.PropertyConfiguration;
import org.codelibs.jcifs.smb.context.BaseContext;
import org.codelibs.jcifs.smb.impl.NtlmPasswordAuthenticator;
import org.codelibs.jcifs.smb.impl.SmbFile;

import de.soderer.utilities.FileUtilities;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.Utilities;

/**
 * SMB remote connection based on jcifs (org.codelibs:jcifs 3.x).
 *
 * By default SMB 2.0.2 up to SMB 3.1.1 is negotiated. The outdated and insecure SMB1 protocol
 * is only allowed if explicitly requested (protocol "smb1" or allowSmb1 = true).
 *
 * Lost connections (network drop, server restart) are re-established by jcifs automatically
 * on the next operation, as long as close() has not been called.
 */
public class SmbConnection implements RemoteConnection {
	/** Default port for SMB over TCP. */
	public static final int DEFAULT_PORT = 445;

	/** The host. */
	private final String hostname;

	/** The port. */
	private int port = DEFAULT_PORT;

	/** The share name. */
	private final String sharename;

	/** Whether the outdated SMB1 protocol may be negotiated. */
	private final boolean allowSmb1;

	/** jcifs context holding configuration, credentials and the transport pool. */
	private CIFSContext context = null;

	/** Root folder of the share, all other paths are resolved relative to it. */
	private SmbFile shareRoot = null;

	/** Set by close(); prevents any further usage. */
	private boolean closed = false;

	/**
	 * Creates a connection to a share using SMB2/3.
	 *
	 * @param hostname host name of the server
	 * @param port port of the server, 445 if out of range
	 * @param username user name, blank for anonymous access
	 * @param sharename name of the share
	 * @param password password, not stored by this class
	 * @throws Exception if the connection fails
	 */
	public SmbConnection(final String hostname, final int port, final String username, final String sharename, final char[] password) throws Exception {
		this(hostname, port, username, sharename, password, false);
	}

	/**
	 * Creates a connection to a share.
	 *
	 * @param hostname host name of the server
	 * @param port port of the server, 445 if out of range
	 * @param username user name, blank for anonymous access
	 * @param sharename name of the share
	 * @param password password, not stored by this class
	 * @param allowSmb1 true to also allow the outdated and insecure SMB1 protocol
	 * @throws Exception if the connection fails
	 */
	public SmbConnection(final String hostname, final int port, final String username, final String sharename, final char[] password, final boolean allowSmb1) throws Exception {
		this.hostname = hostname;
		if (0 < port && port <= 65535) {
			this.port = port;
		}
		this.sharename = sharename == null ? "" : sharename;
		this.allowSmb1 = allowSmb1;

		connect(username, password);
	}

	/**
	 * Creates a connection from access data.
	 *
	 * @param serverEntry access data with protocol "smb" (SMB2/3) or "smb1" (SMB1 allowed as fallback)
	 * @throws Exception if the protocol is not supported or the connection fails
	 */
	public SmbConnection(final ServerAccessData serverEntry) throws Exception {
		if ("smb".equalsIgnoreCase(serverEntry.getProtocol())) {
			allowSmb1 = false;
		} else if ("smb1".equalsIgnoreCase(serverEntry.getProtocol())) {
			allowSmb1 = true;
		} else {
			throw new Exception("Invalid protocol '" + serverEntry.getProtocol() + "' for smb connection. Only 'smb' and 'smb1' are allowed");
		}

		hostname = serverEntry.getHostname();
		if (0 < serverEntry.getPort() && serverEntry.getPort() <= 65535) {
			port = serverEntry.getPort();
		}
		sharename = serverEntry.getSharename() == null ? "" : serverEntry.getSharename();

		connect(serverEntry.getUsername(), serverEntry.getPassword());
	}

	/**
	 * Creates the jcifs context and checks that the share is accessible.
	 */
	private void connect(final String username, final char[] password) throws Exception {
		try {
			final Properties properties = new Properties();
			properties.setProperty("jcifs.client.minVersion", allowSmb1 ? "SMB1" : "SMB202");
			properties.setProperty("jcifs.client.maxVersion", "SMB311");
			if (allowSmb1) {
				// Old SMB1-only devices often only listen on the NetBIOS session port 139, so fall back to it if 445 fails
				properties.setProperty("jcifs.client.port139.enabled", "true");
			}
			// Never silently fall back to a guest login if the given credentials are rejected
			properties.setProperty("jcifs.client.allowGuestFallback", "false");

			final CIFSContext baseContext = new BaseContext(new PropertyConfiguration(properties));
			if (Utilities.isNotBlank(username)) {
				// jcifs keeps the password internally as String, so the char[] cannot be wiped completely
				context = baseContext.withCredentials(new NtlmPasswordAuthenticator("", username, password == null ? "" : new String(password)));
			} else {
				context = baseContext.withAnonymousCredentials();
			}

			shareRoot = new SmbFile("smb://" + hostname + ":" + port + "/" + sharename + "/", context);

			// jcifs connects lazily, so access the share once to detect unreachable hosts, rejected credentials and missing shares now
			if (!shareRoot.exists()) {
				throw new Exception("Share '" + sharename + "' does not exist");
			}
		} catch (final Exception e) {
			disconnect();
			// Keep the original exception as cause, so callers can tell unreachable host, auth failure and missing share apart
			throw new Exception("Connection to smb://" + hostname + ":" + port + "/" + sharename + " failed: " + e.getMessage(), e);
		}
	}

	private void checkForConnection() throws Exception {
		if (closed || context == null || shareRoot == null) {
			throw new Exception("SmbConnection is closed");
		}
	}

	/**
	 * Normalizes a path: forward slashes, no leading or trailing separator.
	 */
	private static String normalizePath(final String path) {
		if (path == null) {
			return "";
		} else {
			final String normalized = FileUtilities.trimLeadingFileSeparator(FileUtilities.trimTrailingFileSeparator(path.replace("\\", "/")));
			return normalized == null ? "" : normalized;
		}
	}

	private static String getLastPathElement(final String normalizedPath) {
		if (normalizedPath.contains("/")) {
			return normalizedPath.substring(normalizedPath.lastIndexOf("/") + 1);
		} else {
			return normalizedPath;
		}
	}

	private static ZonedDateTime toZonedDateTime(final long epochMillis) {
		return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault());
	}

	/**
	 * Resolves a path relative to the share root. jcifs requires a trailing slash for folders.
	 * The returned SmbFile holds a tree handle and must be closed by the caller.
	 */
	private SmbFile resolve(final String path, final boolean isFolder) throws Exception {
		final String normalized = normalizePath(path);
		if (normalized.length() == 0) {
			// jcifs rejects an empty relative name, so create a separate handle for the share root itself
			return new SmbFile(shareRoot.getURL(), context);
		} else {
			// The relative constructor encodes special characters like '#', '%' or spaces itself
			return new SmbFile(shareRoot, isFolder ? normalized + "/" : normalized);
		}
	}

	@Override
	public void deleteFolder(final String folderPath) throws Exception {
		checkForConnection();
		if (normalizePath(folderPath).length() == 0) {
			throw new Exception("The share root cannot be deleted");
		}
		try (SmbFile folder = resolve(folderPath, true)) {
			// Deletes the folder including its whole content
			folder.delete();
		}
	}

	@Override
	public void createFolder(final String parentFolderPath, final String folderName) throws Exception {
		checkForConnection();
		final String parent = normalizePath(parentFolderPath);
		try (SmbFile folder = resolve((parent.length() > 0 ? parent + "/" : "") + folderName, true)) {
			folder.mkdir();
		}
	}

	/**
	 * Creates a folder including all missing parent folders (like mkdir -p).
	 *
	 * @param folderPath path of the folder relative to the share root
	 * @throws Exception if a folder cannot be created
	 */
	public void createFolderPath(final String folderPath) throws Exception {
		checkForConnection();
		if (normalizePath(folderPath).length() == 0) {
			return;
		}
		try (SmbFile folder = resolve(folderPath, true)) {
			if (!folder.exists()) {
				folder.mkdirs();
			} else if (!folder.isDirectory()) {
				throw new Exception("Path '" + folderPath + "' already exists as a file");
			}
		}
	}

	@Override
	public void deleteFile(final String filePath) throws Exception {
		checkForConnection();
		try (SmbFile file = resolve(filePath, false)) {
			if (file.isDirectory()) {
				// Prevent deleteFile from recursively deleting a whole folder by mistake
				throw new Exception("Path '" + filePath + "' is a folder");
			}
			file.delete();
		}
	}

	/**
	 * Checks whether a file exists.
	 *
	 * @param filePath path of the file relative to the share root
	 * @return true if the file exists
	 * @throws Exception if the server cannot be accessed
	 */
	public boolean fileExists(final String filePath) throws Exception {
		checkForConnection();
		try (SmbFile file = resolve(filePath, false)) {
			return file.exists() && !file.isDirectory();
		}
	}

	/**
	 * Checks whether a folder exists.
	 *
	 * @param folderPath path of the folder relative to the share root
	 * @return true if the folder exists
	 * @throws Exception if the server cannot be accessed
	 */
	public boolean folderExists(final String folderPath) throws Exception {
		checkForConnection();
		try (SmbFile folder = resolve(folderPath, true)) {
			return folder.exists() && folder.isDirectory();
		}
	}

	@Override
	public FileInfo getFileInfo(final String filePath) throws Exception {
		checkForConnection();
		final String normalized = normalizePath(filePath);
		try (SmbFile file = resolve(normalized, false)) {
			if (!file.exists() || file.isDirectory()) {
				// Like the other RemoteConnection implementations: null for missing paths and for folders
				return null;
			}
			return new FileInfo(getLastPathElement(normalized), file.length(), toZonedDateTime(file.lastModified()));
		}
	}

	@Override
	public InputStream downloadFile(final String filePath) throws Exception {
		checkForConnection();
		// Ownership of the SmbFile is transferred to the returned stream, which closes it
		@SuppressWarnings("resource")
		final SmbFile file = resolve(filePath, false);
		try {
			return new SmbInputStream(file.openInputStream(SmbConstants.DEFAULT_SHARING), file);
		} catch (final Exception e) {
			// Do not leak the tree handle if the stream cannot be opened
			file.close();
			throw e;
		}
	}

	/**
	 * Uploads a new file. Fails if the file already exists (O_EXCL is mapped to the atomic exclusive create
	 * disposition FILE_CREATE), which also makes this method usable for creating lock files.
	 */
	@Override
	public void uploadFile(final String filePath, final InputStream fileInputStream) throws Exception {
		checkForConnection();
		try (SmbFile file = resolve(filePath, false);
				OutputStream outputStream = file.openOutputStream(false, SmbConstants.O_CREAT | SmbConstants.O_WRONLY | SmbConstants.O_EXCL, 0, SmbConstants.DEFAULT_SHARING)) {
			IoUtilities.copy(fileInputStream, outputStream);
		}
	}

	/**
	 * Renames or moves a file within the same share.
	 *
	 * @param fromPath current path relative to the share root
	 * @param toPath target path relative to the share root
	 * @param replaceExisting if true an existing target file is replaced
	 * @throws Exception if the file cannot be renamed
	 */
	public void renameFile(final String fromPath, final String toPath, final boolean replaceExisting) throws Exception {
		checkForConnection();
		try (SmbFile source = resolve(fromPath, false);
				SmbFile target = resolve(toPath, false)) {
			source.renameTo(target, replaceExisting);
		}
	}

	/**
	 * Sets the last write time of a file, leaving all other timestamps and attributes untouched.
	 *
	 * @param filePath path of the file relative to the share root
	 * @param epochMillis new last write time in milliseconds since 1970
	 * @throws Exception if the time cannot be set
	 */
	public void setLastModified(final String filePath, final long epochMillis) throws Exception {
		checkForConnection();
		try (SmbFile file = resolve(filePath, false)) {
			// jcifs passes 0 for the other timestamps and the attributes, which means "do not change"
			file.setLastModified(epochMillis);
		}
	}

	@Override
	public FolderInfo getFolderInfo(final String folderPath) throws Exception {
		checkForConnection();
		final String normalized = normalizePath(folderPath);
		String folderName = getLastPathElement(normalized);
		if (Utilities.isEmpty(folderName)) {
			folderName = "/";
		}

		try (SmbFile folder = resolve(normalized, true)) {
			if (!folder.exists() || !folder.isDirectory()) {
				// Like the other RemoteConnection implementations: null for missing paths and for files
				return null;
			}
			return new FolderInfo(folderName, toZonedDateTime(folder.lastModified()));
		}
	}

	@Override
	public DirectoryListing getDirectory(final String folderPath) throws Exception {
		checkForConnection();

		final List<FolderInfo> folderInfoList = new ArrayList<>();
		final List<FileInfo> fileInfoList = new ArrayList<>();
		try (SmbFile folder = resolve(folderPath, true)) {
			for (final SmbFile entry : folder.listFiles()) {
				try (entry) {
					// Listed entries already carry size, attributes and timestamps, no extra request per entry needed
					final ZonedDateTime changeDate = toZonedDateTime(entry.lastModified());
					if (entry.isDirectory()) {
						// jcifs returns folder names with a trailing slash
						folderInfoList.add(new FolderInfo(FileUtilities.trimTrailingFileSeparator(entry.getName()), changeDate));
					} else {
						fileInfoList.add(new FileInfo(entry.getName(), entry.length(), changeDate));
					}
				}
			}
		}

		return new DirectoryListing(folderInfoList, fileInfoList);
	}

	/**
	 * Closes the connection permanently and releases all transports of this connection.
	 */
	@Override
	public void close() {
		closed = true;
		disconnect();
	}

	private void disconnect() {
		if (shareRoot != null) {
			try {
				shareRoot.close();
			} catch (@SuppressWarnings("unused") final Exception e) {
				// do nothing
			}
			shareRoot = null;
		}

		if (context != null) {
			try {
				// Closes the transport pool of this context, the context is not shared with other connections
				context.close();
			} catch (@SuppressWarnings("unused") final Exception e) {
				// do nothing
			}
			context = null;
		}
	}

	/**
	 * InputStream wrapper that also releases the SmbFile and its tree handle.
	 */
	static class SmbInputStream extends FilterInputStream {
		private final SmbFile remoteFile;
		private boolean isClosed = false;

		protected SmbInputStream(final InputStream inputStream, final SmbFile remoteFile) {
			super(inputStream);
			this.remoteFile = remoteFile;
		}

		@Override
		public void close() throws IOException {
			if (isClosed) {
				return;
			}
			isClosed = true;
			try {
				super.close();
			} finally {
				// Always release the remote handle, even if closing the stream failed
				remoteFile.close();
			}
		}
	}
}
