package de.soderer.remote;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.FileUtilities;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.StringUtilities;

/**
 * Read-only remote connection to files offered by a web server with directory listings (Apache style "index of" pages).
 * Credentials are sent as URL parameters "username" and "password", so they may show up in server and proxy logs
 * and are transferred unencrypted without TLS.
 *
 * There is no home folder, so absolute paths ("/a/b") and relative paths ("a/b") are both resolved against the start
 * path. {@link #createFolderPath(String)} only succeeds if the folder already exists, as folders cannot be created.
 */
public class HttpFileConnection implements RemoteConnection {
	/** Timeout in milliseconds for connecting and for each read */
	private static final int TIMEOUT_MILLIS = 60_000;

	/** The useTLS. */
	private boolean useTLS = false;

	private Proxy proxy = null;

	/** The host. */
	private String hostname = null;

	/** The startPath. */
	private String startPath = null;

	/** The user. */
	private String username = null;

	/** The password. */
	private String password = null;

	/** The port. */
	private int port = 80;

	/**
	 * Creates a connection with the default port (80 or 443).
	 *
	 * @param useTLS true for https
	 * @param hostname host name of the server
	 * @param startPath base path on the server, may be null
	 */
	public HttpFileConnection(final boolean useTLS, final String hostname, final String startPath) {
		this.useTLS = useTLS;
		if (useTLS) {
			port = 443;
		}
		this.hostname = hostname;
		this.startPath = startPath;
	}

	/**
	 * Creates an http connection with port 80.
	 *
	 * @param hostname host name of the server
	 * @param startPath base path on the server, may be null
	 */
	public HttpFileConnection(final String hostname, final String startPath) {
		this.hostname = hostname;
		this.startPath = startPath;
	}

	/**
	 * Creates a connection.
	 *
	 * @param useTLS true for https
	 * @param hostname host name of the server
	 * @param startPath base path on the server, may be null
	 * @param port port of the server
	 */
	public HttpFileConnection(final boolean useTLS, final String hostname, final String startPath, final int port) {
		this.useTLS = useTLS;
		this.hostname = hostname;
		this.startPath = startPath;
		this.port = port;
	}

	/**
	 * Creates an http connection.
	 *
	 * @param hostname host name of the server
	 * @param startPath base path on the server, may be null
	 * @param port port of the server
	 */
	public HttpFileConnection(final String hostname, final String startPath, final int port) {
		this.hostname = hostname;
		this.startPath = startPath;
		this.port = port;
	}

	/**
	 * Creates an http connection with port 80 and credentials.
	 *
	 * @param hostname host name of the server
	 * @param startPath base path on the server, may be null
	 * @param username user name, may be null
	 * @param password password, may be null
	 */
	public HttpFileConnection(final String hostname, final String startPath, final String username, final String password) {
		this.hostname = hostname;
		this.startPath = startPath;
		this.username = username;
		this.password = password;
	}

	/**
	 * Creates a connection with the default port (80 or 443) and credentials.
	 *
	 * @param useTLS true for https
	 * @param hostname host name of the server
	 * @param startPath base path on the server, may be null
	 * @param username user name, may be null
	 * @param password password, may be null
	 */
	public HttpFileConnection(final boolean useTLS, final String hostname, final String startPath, final String username, final String password) {
		this.useTLS = useTLS;
		if (useTLS) {
			port = 443;
		}
		this.hostname = hostname;
		this.startPath = startPath;
		this.username = username;
		this.password = password;
	}

	/**
	 * Creates a connection with credentials.
	 *
	 * @param useTLS true for https
	 * @param hostname host name of the server
	 * @param startPath base path on the server, may be null
	 * @param username user name, may be null
	 * @param password password, may be null
	 * @param port port of the server
	 */
	public HttpFileConnection(final boolean useTLS, final String hostname, final String startPath, final String username, final String password, final int port) {
		this.useTLS = useTLS;
		this.hostname = hostname;
		this.startPath = startPath;
		this.username = username;
		this.password = password;
		this.port = port;
	}

	/**
	 * Creates an http connection with credentials.
	 *
	 * @param hostname host name of the server
	 * @param startPath base path on the server, may be null
	 * @param username user name, may be null
	 * @param password password, may be null
	 * @param port port of the server
	 */
	public HttpFileConnection(final String hostname, final String startPath, final String username, final String password, final int port) {
		this.hostname = hostname;
		this.startPath = startPath;
		this.username = username;
		this.password = password;
		this.port = port;
	}

	/**
	 * Creates a connection from access data. The share name is used as base path.
	 *
	 * @param serverEntry access data with protocol "http" or "https"
	 * @throws Exception if the protocol is neither "http" nor "https"
	 */
	public HttpFileConnection(final ServerAccessData serverEntry) throws Exception {
		if (!"http".equalsIgnoreCase(serverEntry.getProtocol()) && !"https".equalsIgnoreCase(serverEntry.getProtocol())) {
			throw new Exception("Invalid protocol '" + serverEntry.getProtocol() + "' for HTTP/HTTPS connection. Only 'http' and 'https' are allowed");
		} else {
			useTLS = "https".equalsIgnoreCase(serverEntry.getProtocol());
			hostname = serverEntry.getHostname();
			startPath = serverEntry.getSharename();
			username = serverEntry.getUsername();
			password = serverEntry.getPassword() == null ? null : new String(serverEntry.getPassword());
			port = serverEntry.getPort();
		}
	}

	/**
	 * Sets the proxy for the requests.
	 *
	 * @param proxy proxy, {@link Proxy#NO_PROXY} for a direct connection, or null to use the JVM default proxy
	 *            settings ({@link java.net.ProxySelector#getDefault()})
	 */
	public void setProxy(final Proxy proxy) {
		this.proxy = proxy;
	}

	/**
	 * Sets the proxy for the requests.
	 *
	 * @param newProxy proxy, {@link Proxy#NO_PROXY} for a direct connection, or null to use the JVM default proxy
	 *            settings ({@link java.net.ProxySelector#getDefault()})
	 * @return this connection for chaining
	 */
	public HttpFileConnection withProxy(final Proxy newProxy) {
		setProxy(newProxy);
		return this;
	}

	/**
	 * Not supported, always throws an Exception.
	 */
	@Override
	public void uploadFile(final String filePath, final InputStream inputStream) throws Exception {
		throw new Exception("uploadFile is not supported by HttpFileConnection");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InputStream downloadFile(String filePath) throws Exception {
		filePath = FileUtilities.trimLeadingFileSeparator(filePath);
		try {
			final URLConnection urlConnection = openUrlConnection(filePath);
			return new BufferedInputStream(urlConnection.getInputStream());
		} catch (final Exception e) {
			throw new Exception("Cannot download file '" + filePath + "': " + e.getMessage(), e);
		}
	}

	/**
	 * Close connection
	 */
	@Override
	public void close() {
		// do nothing
	}

	@Override
	public void deleteFile(final String filePath) throws Exception {
		throw new Exception("deleteFile is not supported by HttpFileConnection");
	}

	@Override
	public void deleteFolder(final String folderPath) throws Exception {
		throw new Exception("deleteFolder is not supported by HttpFileConnection");
	}

	@Override
	public void createFolder(final String parentFolderPath, final String folderName) throws Exception {
		throw new Exception("createFolder is not supported by HttpFileConnection");
	}

	@Override
	public FileInfo getFileInfo(String filePath) throws Exception {
		filePath = FileUtilities.trimLeadingFileSeparator(filePath);
		final String folderPath = FileUtilities.getParentFromPath(filePath);
		final String fileName = FileUtilities.getFilenameFromPath(filePath);
		try {
			final URLConnection urlConnection = openUrlConnection(folderPath);

			try (BufferedInputStream inputStream = new BufferedInputStream(urlConnection.getInputStream())) {
				final byte[] data = IoUtilities.toByteArray(inputStream);
				for (final String fileLine : getFileLines(data)) {
					final String fileNameFromLine = getEntryNameFromLine(fileLine);
					if (fileNameFromLine != null && fileNameFromLine.equals(fileName)) {
						final String fileInfoPart = fileLine.substring(fileLine.lastIndexOf("</a>") + 4).trim();
						final int separatorIndex = fileInfoPart.lastIndexOf(" ");
						final String changeDateString = fileInfoPart.substring(0, separatorIndex).trim();
						final LocalDateTime modificationDate = DateUtilities.parseLocalDateTime(DateUtilities.YYYY_MM_DD_HH_MM, changeDateString);
						final String sizeString = fileInfoPart.substring(separatorIndex + 1).trim().toLowerCase(Locale.ROOT);
						final long fileSize = getFileSize(sizeString);
						return new FileInfo(fileName, fileSize, modificationDate.atZone(ZoneId.systemDefault()));
					}
				}
				return null;
			}
		} catch (@SuppressWarnings("unused") final FileNotFoundException e) {
			// HTTP 404: the parent folder does not exist, so neither does the file
			return null;
		} catch (final Exception e) {
			throw new Exception("Cannot download file info for '" + filePath + "': " + e.getMessage(), e);
		}
	}

	@Override
	public FolderInfo getFolderInfo(String folderPath) throws Exception {
		folderPath = FileUtilities.trimLeadingFileSeparator(FileUtilities.trimTrailingFileSeparator(folderPath));
		final String folderName = StringUtilities.isEmpty(folderPath) ? "/" : FileUtilities.getFilenameFromPath(folderPath);
		try {
			// The trailing "/" requests the directory listing; for a file path the server answers with 404
			final URLConnection urlConnection = openUrlConnection(folderPath.length() == 0 ? "" : folderPath + "/");

			try (BufferedInputStream inputStream = new BufferedInputStream(urlConnection.getInputStream())) {
				final byte[] data = IoUtilities.toByteArray(inputStream);
				if (!new String(data, StandardCharsets.UTF_8).contains("<pre>")) {
					// No directory listing (e.g. a file served despite the trailing "/")
					return null;
				}
				// Check existence only. Modification date is not available in general.
				return new FolderInfo(folderName, null);
			}
		} catch (@SuppressWarnings("unused") final FileNotFoundException e) {
			// HTTP 404: like the other RemoteConnection implementations, null for missing paths
			return null;
		} catch (final Exception e) {
			throw new Exception("Cannot download directory data for '" + folderPath + "': " + e.getMessage(), e);
		}
	}

	private URL getUrl(final String path) throws IOException, MalformedURLException {
		return URI.create(
				(useTLS ? "https://" : "http://")
				+ hostname
				+ (useTLS ? (port == 443 ? "" : ":" + port) : (port == 80 ? "" : ":" + port))
				+ (StringUtilities.isNotBlank(FileUtilities.trimLeadingFileSeparator(startPath)) ? "/" + encodePath(FileUtilities.trimTrailingFileSeparator(FileUtilities.trimLeadingFileSeparator(startPath))) : "")
				+ "/" + encodePath(path)
				+ (username != null ? "?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + (password != null ? "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8) : "") : "")
				).toURL();
	}

	private URLConnection openUrlConnection(final String path) throws IOException {
		final URL url = getUrl(path);
		final URLConnection urlConnection;
		if (proxy != null) {
			urlConnection = url.openConnection(proxy);
		} else {
			urlConnection = url.openConnection();
		}
		// Without timeouts an unresponsive server would block forever
		urlConnection.setConnectTimeout(TIMEOUT_MILLIS);
		urlConnection.setReadTimeout(TIMEOUT_MILLIS);
		return urlConnection;
	}

	/**
	 * Percent-encodes each segment of a path, keeping the "/" separators. Unencoded characters like blanks make
	 * URI.create fail.
	 */
	private static String encodePath(final String path) {
		final StringBuilder encodedPath = new StringBuilder();
		for (final String segment : path.split("/", -1)) {
			if (encodedPath.length() > 0 || path.startsWith("/")) {
				encodedPath.append('/');
			}
			encodedPath.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
		}
		return path.startsWith("/") ? encodedPath.substring(1) : encodedPath.toString();
	}

	private static List<String> getEntryLines(final byte[] data) throws Exception {
		final String rawDataString = new String(data, StandardCharsets.UTF_8);
		final int dataStart = rawDataString.indexOf("<pre>");
		if (dataStart >= 0) {
			final int dataEnd = rawDataString.indexOf("</pre>", dataStart + 5);
			if (dataEnd >= 0) {
				final String dataString = rawDataString.substring(dataStart + 5, dataEnd);
				final BufferedReader reader = new BufferedReader(new StringReader(dataString));
				final List<String> entryLines = new ArrayList<>();
				String nextLine;
				while ((nextLine = reader.readLine()) != null) {
					if (!nextLine.contains("alt=\"[PARENTDIR]\">")) {
						entryLines.add(nextLine);
					}
				}
				return entryLines;
			} else {
				return new ArrayList<>();
			}
		} else {
			// No directory listing
			return new ArrayList<>();
		}
	}

	private static String getEntryNameFromLine(final String line) {
		final int entryNameStart = line.indexOf("<a href=\"");
		if (entryNameStart >= 0) {
			final int entryNameEnd = line.indexOf("\"", entryNameStart + 9);
			if (entryNameEnd >= 0) {
				// href values are URL encoded (e.g. "%20" for blanks)
				return URLDecoder.decode(line.substring(entryNameStart + 9, entryNameEnd).replace("+", "%2B"), StandardCharsets.UTF_8);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	private static List<String> getFileLines(final byte[] data) throws Exception {
		final List<String> fileLines = new ArrayList<>();
		for (final String nextLine : getEntryLines(data)) {
			if (nextLine.contains("alt=") && !nextLine.contains("alt=\"[DIR]\">")) {
				fileLines.add(nextLine);
			}
		}
		return fileLines;
	}

	private static List<String> getFolderLines(final byte[] data) throws Exception {
		final List<String> folderLines = new ArrayList<>();
		for (final String nextLine : getEntryLines(data)) {
			if (nextLine.contains("alt=\"[DIR]\">")) {
				folderLines.add(nextLine);
			}
		}
		return folderLines;
	}

	/**
	 * Reads the modification date of a listing line, or null if it is missing or cannot be parsed.
	 */
	private static LocalDateTime getModificationDateFromLine(final String line) {
		try {
			final String infoPart = line.substring(line.lastIndexOf("</a>") + 4).trim();
			final int separatorIndex = infoPart.lastIndexOf(" ");
			return DateUtilities.parseLocalDateTime(DateUtilities.YYYY_MM_DD_HH_MM, infoPart.substring(0, separatorIndex).trim());
		} catch (@SuppressWarnings("unused") final Exception e) {
			return null;
		}
	}

	/**
	 * This size data is very imprecise
	 */
	private static long getFileSize(final String sizeString) {
		long fileSize;
		if (sizeString.endsWith("k")) {
			fileSize = (long) (Double.parseDouble(sizeString.substring(0, sizeString.length() - 1)) * 1024);
			fileSize = (fileSize / 1000) * 1000;
		} else if (sizeString.endsWith("m")) {
			fileSize = (long) (Double.parseDouble(sizeString.substring(0, sizeString.length() - 1)) * 1024 * 1024);
			fileSize = (fileSize / (long) Math.pow(1000, 2)) * (long) Math.pow(1000, 2);
		} else if (sizeString.endsWith("g")) {
			fileSize = (long) (Double.parseDouble(sizeString.substring(0, sizeString.length() - 1)) * 1024 * 1024 * 1024);
			fileSize = (fileSize / (long) Math.pow(1000, 3)) * (long) Math.pow(1000, 3);
		} else if (sizeString.endsWith("t")) {
			fileSize = (long) (Double.parseDouble(sizeString.substring(0, sizeString.length() - 1)) * 1024 * 1024 * 1024 * 1024);
			fileSize = (fileSize / (long) Math.pow(1000, 4)) * (long) Math.pow(1000, 4);
		} else {
			fileSize = Long.parseLong(sizeString);
		}
		return fileSize;
	}

	@Override
	public DirectoryListing getDirectory(String folderPath) throws Exception {
		folderPath = FileUtilities.trimLeadingFileSeparator(FileUtilities.trimTrailingFileSeparator(folderPath));
		try {
			final URLConnection urlConnection = openUrlConnection(folderPath);

			try (BufferedInputStream inputStream = new BufferedInputStream(urlConnection.getInputStream())) {
				final byte[] data = IoUtilities.toByteArray(inputStream);
				final List<FolderInfo> folderInfoList = new ArrayList<>();
				for (final String folderLine : getFolderLines(data)) {
					// The listing itself proves the existence, so no request per sub folder is needed
					final String subFolderName = FileUtilities.trimTrailingFileSeparator(getEntryNameFromLine(folderLine));
					final LocalDateTime modificationDate = getModificationDateFromLine(folderLine);
					folderInfoList.add(new FolderInfo(subFolderName, modificationDate == null ? null : modificationDate.atZone(ZoneId.systemDefault())));
				}
				final List<FileInfo> fileInfoList = new ArrayList<>();
				for (final String fileLine : getFileLines(data)) {
					final String fileName = getEntryNameFromLine(fileLine);
					final String fileInfoPart = fileLine.substring(fileLine.lastIndexOf("</a>") + 4).trim();
					final int separatorIndex = fileInfoPart.lastIndexOf(" ");
					final String changeDateString = fileInfoPart.substring(0, separatorIndex).trim();
					final LocalDateTime modificationDate = DateUtilities.parseLocalDateTime(DateUtilities.YYYY_MM_DD_HH_MM, changeDateString);
					final String sizeString = fileInfoPart.substring(separatorIndex + 1).trim().toLowerCase(Locale.ROOT);
					final long fileSize = getFileSize(sizeString);
					fileInfoList.add(new FileInfo(fileName, fileSize, modificationDate.atZone(ZoneId.systemDefault())));
				}
				return new DirectoryListing(folderInfoList, fileInfoList);
			}
		} catch (final Exception e) {
			throw new Exception("Cannot download directory data for '" + folderPath + "': " + e.getMessage(), e);
		}
	}
}
