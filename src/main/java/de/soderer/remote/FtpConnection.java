package de.soderer.remote;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.FileUtilities;

/**
 * Remote connection via FTP, based on Apache Commons Net. Transfers use binary mode.
 * File and folder information is read via MLST/MLSD (RFC 3659) if the server supports it, otherwise via LIST
 * (e.g. vsftpd). Credentials and data are transferred unencrypted.
 *
 * Relative paths are resolved by the server against the working directory, which is the user's home folder after
 * login. This class never changes the working directory permanently.
 */
public class FtpConnection implements RemoteConnection {
	/** Timeout in milliseconds for connecting */
	private static final int CONNECT_TIMEOUT_MILLIS = 30_000;

	/** Timeout in milliseconds for waiting on the server's replies and on data */
	private static final int READ_TIMEOUT_MILLIS = 60_000;

	/** The host. */
	private String hostname = null;

	/** The user. */
	private String username = null;

	/** The password. */
	private char[] password = null;

	/** The port. */
	private int port = 21; // Default port for FTP is 21

	/**
	 * usePassiveMode
	 */
	private final boolean usePassiveMode;

	/**
	 * Currently connected client
	 */
	private FTPClient ftpClient = null;

	private Proxy proxy = null;

	/**
	 * Instantiates a new FtpConnection.
	 * Default port for FTP is 21.
	 * Uses FTP passive mode.
	 *
	 * @param hostname host name of the server
	 * @param username user name
	 * @param password password
	 * @throws Exception if the connection or login fails
	 */
	public FtpConnection(final String hostname, final String username, final char[] password) throws Exception {
		this.hostname = hostname;
		this.username = username;
		this.password = password;
		usePassiveMode = true;

		connect();
	}

	/**
	 * Instantiates a new FtpConnection.
	 * Uses FTP passive mode.
	 *
	 * @param hostname host name of the server
	 * @param username user name
	 * @param password password
	 * @param port port of the server
	 * @throws Exception if the connection or login fails
	 */
	public FtpConnection(final String hostname, final String username, final char[] password, final int port) throws Exception {
		this.hostname = hostname;
		this.username = username;
		this.password = password;
		this.port = port;
		usePassiveMode = true;

		connect();
	}

	/**
	 * Instantiates a new FtpConnection.
	 *
	 * @param hostname host name of the server
	 * @param username user name
	 * @param password password
	 * @param port port of the server
	 * @param usePassiveMode true for passive mode (usually needed behind firewalls and NAT)
	 * @throws Exception if the connection or login fails
	 */
	public FtpConnection(final String hostname, final String username, final char[] password, final int port, final boolean usePassiveMode) throws Exception {
		this.hostname = hostname;
		this.username = username;
		this.password = password;
		this.port = port;
		this.usePassiveMode = usePassiveMode;

		connect();
	}

	/**
	 * Instantiates a new FtpConnection in passive mode.
	 *
	 * @param serverEntry access data with protocol "ftp"
	 * @throws Exception if the protocol is not "ftp" or the connection or login fails
	 */
	public FtpConnection(final ServerAccessData serverEntry) throws Exception {
		if (!"ftp".equalsIgnoreCase(serverEntry.getProtocol())) {
			throw new Exception("Invalid protocol '" + serverEntry.getProtocol() + "' for FTP connection. Only 'ftp' is allowed");
		} else {
			hostname = serverEntry.getHostname();
			username = serverEntry.getUsername();
			password = serverEntry.getPassword();
			port = serverEntry.getPort();
			usePassiveMode = true;

			connect();
		}
	}

	/**
	 * Sets the proxy. As the connection is established by the constructor already, it is established again with the
	 * proxy.
	 *
	 * @param proxy SOCKS proxy, {@link Proxy#NO_PROXY} for a direct connection, or null to use the JVM default
	 *            settings (system properties "socksProxyHost" and "socksProxyPort")
	 * @throws IllegalStateException if the new connection fails
	 */
	public void setProxy(final Proxy proxy) {
		this.proxy = proxy;
		// The constructors connect immediately, so the proxy only takes effect with a new connection
		if (ftpClient != null) {
			close();
			try {
				connect();
			} catch (final Exception e) {
				throw new IllegalStateException("Cannot reconnect with proxy: " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Sets the proxy. As the connection is established by the constructor already, it is established again with the
	 * proxy.
	 *
	 * @param newProxy SOCKS proxy, {@link Proxy#NO_PROXY} for a direct connection, or null to use the JVM default
	 *            settings (system properties "socksProxyHost" and "socksProxyPort")
	 * @return this connection for chaining
	 * @throws IllegalStateException if the new connection fails
	 */
	public FtpConnection withProxy(final Proxy newProxy) {
		setProxy(newProxy);
		return this;
	}

	/**
	 * Connect.
	 */
	private void connect() throws Exception {
		try {
			ftpClient = new FTPClient();

			if (proxy != null) {
				ftpClient.setProxy(proxy);
			}

			ftpClient.setAutodetectUTF8(true);
			// Without timeouts an unresponsive server would block reads and transfers forever
			ftpClient.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
			ftpClient.setDefaultTimeout(READ_TIMEOUT_MILLIS);
			ftpClient.setDataTimeout(Duration.ofMillis(READ_TIMEOUT_MILLIS));
			ftpClient.connect(hostname, port);
			if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
				// e.g. "421 Too many connections"
				throw new Exception("Server refused connection: " + ftpClient.getReplyString());
			}
			if (usePassiveMode) {
				ftpClient.enterLocalPassiveMode();
			}
			if (!ftpClient.login(username, password == null ? "" : new String(password))) {
				ftpClient.logout();
				throw new Exception("Authentication failed");
			}
			// Binary mode must be set after login, many servers reject the TYPE command before
			if (!ftpClient.setFileType(FTP.BINARY_FILE_TYPE)) {
				throw new Exception("Cannot set binary transfer mode: " + ftpClient.getReplyString());
			}
			ftpClient.setListHiddenFiles(true);
		} catch (final Exception e) {
			close();
			throw new Exception("Connection failed", e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void uploadFile(final String filePath, final InputStream inputStream) throws Exception {
		checkForConnection();
		if (!ftpClient.storeFile(filePath, inputStream)) {
			throw new Exception("Cannot upload file '" + filePath + "': " + ftpClient.getReplyString());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InputStream downloadFile(final String filePath) throws Exception {
		checkForConnection();
		// FTPClient returns a stream only after a positive preliminary reply (1xx), any error reply (e.g. 550) results in null
		final InputStream inputStream = ftpClient.retrieveFileStream(filePath);
		if (inputStream == null) {
			throw new Exception("File not found: '" + filePath + "' (" + ftpClient.getReplyString().trim() + ")");
		}
		return new FtpInputStream(inputStream, this);
	}

	private void checkForConnection() throws Exception {
		if (ftpClient == null) {
			throw new Exception("FtpConnection is not connected");
		} else if (!ftpClient.isConnected()) {
			ftpClient = null;
			throw new Exception("FtpConnection is not connected anymore");
		}
	}

	/**
	 * Close connection
	 */
	@Override
	public void close() {
		if (ftpClient != null) {
			if (ftpClient.isConnected()) {
				try {
					ftpClient.disconnect();
				} catch (@SuppressWarnings("unused") final Exception e) {
					// do nothing
				}
			}
			ftpClient = null;
		}
	}

	private boolean completePendingCommand() {
		if (ftpClient != null) {
			if (ftpClient.isConnected()) {
				try {
					return ftpClient.completePendingCommand();
				} catch (@SuppressWarnings("unused") final Exception e) {
					// do nothing
				}
			}
		}
		return false;
	}

	@Override
	public void deleteFile(final String filePath) throws Exception {
		checkForConnection();
		if (!ftpClient.deleteFile(filePath)) {
			throw new Exception("Cannot delete file '" + filePath + "': " + ftpClient.getReplyString());
		}
	}

	@Override
	public void deleteFolder(final String folderPath) throws Exception {
		checkForConnection();
		final FTPFile[] files = listFilesWithBlankWorkaround(folderPath);
		if (files.length > 0) {
			for (final FTPFile ftpFile : files) {
				if (ftpFile == null) {
					// Entry of the listing which could not be parsed
					continue;
				}
				// Some servers return the complete path as name
				final String fileName = FileUtilities.getFilenameFromPath(ftpFile.getName());
				if (!".".equals(fileName) && !"..".equals(fileName)) {
					if (ftpFile.isDirectory()) {
						deleteFolder(folderPath + "/" + fileName);
					} else if (!ftpClient.deleteFile(folderPath + "/" + fileName)) {
						throw new Exception("Cannot delete file '" + folderPath + "/" + fileName + "': " + ftpClient.getReplyString());
					}
				}
			}
		}
		if (!ftpClient.removeDirectory(folderPath)) {
			throw new Exception("Cannot delete folder '" + folderPath + "': " + ftpClient.getReplyString());
		}
	}

	@Override
	public void createFolder(final String parentFolderPath, final String folderName) throws Exception {
		checkForConnection();
		// An empty parent must stay relative to the home folder instead of becoming "/folderName"
		final String folderPath = RemotePaths.join(parentFolderPath, folderName);
		if (!ftpClient.makeDirectory(folderPath)) {
			throw new Exception("Cannot create folder '" + folderPath + "': " + ftpClient.getReplyString());
		}
	}

	@Override
	public FileInfo getFileInfo(final String filePath) throws Exception {
		checkForConnection();

		final String fileName = FileUtilities.getFilenameFromPath(FileUtilities.trimTrailingFileSeparator(filePath.replace("\\", "/")));
		final FTPFile file;
		if (supportsMlst()) {
			file = ftpClient.mlistFile(filePath);
		} else {
			// LIST of a file path returns just this file, LIST of a folder path returns the folder content
			final FTPFile[] listedFiles = listFilesWithBlankWorkaround(filePath);
			if (listedFiles != null && listedFiles.length == 1 && listedFiles[0] != null && listedFiles[0].isFile()
					&& fileName.equals(FileUtilities.getFilenameFromPath(listedFiles[0].getName()))) {
				file = listedFiles[0];
			} else {
				file = null;
			}
		}
		if (file == null || file.isDirectory()) {
			return null;
		} else {
			return new FileInfo(fileName, file.getSize(), getChangeDate(file));
		}
	}

	@Override
	public FolderInfo getFolderInfo(final String folderPath) throws Exception {
		checkForConnection();
		final String commandPath = toCommandPath(folderPath);

		if (supportsMlst()) {
			final FTPFile file = ftpClient.mlistFile(commandPath);
			if (file == null || file.isFile()) {
				return null;
			} else {
				return new FolderInfo(getFolderName(folderPath), getChangeDate(file));
			}
		} else {
			// Without MLST a folder is detected by changing into it; LIST does not report the folder itself
			final String currentWorkingDirectory = ftpClient.printWorkingDirectory();
			if (!ftpClient.changeWorkingDirectory(commandPath)) {
				return null;
			}
			if (currentWorkingDirectory != null && !ftpClient.changeWorkingDirectory(currentWorkingDirectory)) {
				throw new Exception("Cannot return to working directory '" + currentWorkingDirectory + "': " + ftpClient.getReplyString());
			}
			return new FolderInfo(getFolderName(folderPath), null);
		}
	}

	/**
	 * Returns the path to send in a command. An empty path means the home folder, which is the working directory, so it
	 * is sent as "." because many servers reject commands with an empty argument.
	 */
	private static String toCommandPath(final String path) {
		final String normalizedPath = path == null ? "" : path.replace("\\", "/");
		return normalizedPath.isEmpty() ? "." : normalizedPath;
	}

	private static String getFolderName(final String folderPath) {
		final String trimmedPath = FileUtilities.trimTrailingFileSeparator(folderPath.replace("\\", "/"));
		final String folderName = trimmedPath == null ? "" : FileUtilities.getFilenameFromPath(trimmedPath);
		return folderName == null || folderName.length() == 0 ? "/" : folderName;
	}

	/**
	 * Checks whether the server supports the MLST and MLSD commands (RFC 3659). The FEAT reply is cached by FTPClient.
	 */
	private boolean supportsMlst() throws IOException {
		return ftpClient.hasFeature("MLST");
	}

	/**
	 * Lists a path via LIST. Some servers split the LIST argument at blanks, so blanks are escaped first; servers taking
	 * the argument literally find nothing for the escaped path, so it is retried unescaped.
	 */
	private FTPFile[] listFilesWithBlankWorkaround(final String path) throws IOException {
		if (path.contains(" ")) {
			final FTPFile[] escapedResult = ftpClient.listFiles(path.replace(" ", "\\ "));
			if (escapedResult != null && escapedResult.length > 0) {
				return escapedResult;
			}
		}
		return ftpClient.listFiles(path);
	}

	private static ZonedDateTime getChangeDate(final FTPFile file) {
		if (file.getTimestamp() == null) {
			// Not every server reports a timestamp
			return null;
		} else {
			return DateUtilities.getLocalDateTimeForDate(file.getTimestamp().getTime()).atZone(ZoneId.systemDefault());
		}
	}

	class FtpInputStream extends FilterInputStream {
		private final FtpConnection ftpConnection;
		private boolean isClosed = false;

		protected FtpInputStream(final InputStream inputStream, final FtpConnection ftpConnection) {
			super(inputStream);
			this.ftpConnection = ftpConnection;
		}

		@Override
		public void close() throws IOException {
			super.close();
			if (!isClosed) {
				ftpConnection.completePendingCommand();
			}

			isClosed = true;
		}
	}

	@Override
	public DirectoryListing getDirectory(String folderPath) throws Exception {
		checkForConnection();

		folderPath = toCommandPath(folderPath);

		// One listing provides names, sizes and dates of all entries; MLSD has exact timestamps and a standardized format
		final FTPFile[] entries = supportsMlst() ? ftpClient.mlistDir(folderPath) : listFilesWithBlankWorkaround(folderPath);

		final List<FolderInfo> folderInfoList = new ArrayList<>();
		final List<FileInfo> fileInfoList = new ArrayList<>();
		if (entries != null) {
			for (final FTPFile entry : entries) {
				if (entry == null) {
					// Entry of the listing which could not be parsed
					continue;
				}
				final String entryName = FileUtilities.getFilenameFromPath(entry.getName());
				if (".".equals(entryName) || "..".equals(entryName) || entryName.length() == 0) {
					continue;
				} else if (entry.isDirectory()) {
					folderInfoList.add(new FolderInfo(entryName, getChangeDate(entry)));
				} else if (entry.isFile()) {
					fileInfoList.add(new FileInfo(entryName, entry.getSize(), getChangeDate(entry)));
				}
			}
		}

		return new DirectoryListing(folderInfoList, fileInfoList);
	}
}