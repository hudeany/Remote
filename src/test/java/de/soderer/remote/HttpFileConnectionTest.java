package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Tests of {@link HttpFileConnection} against a local web server of the JDK, which delivers Apache style directory
 * listings.
 */
@SuppressWarnings("static-method")
public class HttpFileConnectionTest {
	private static final byte[] README_DATA = "Hello World!".getBytes(StandardCharsets.UTF_8);
	private static final byte[] INNER_DATA = "inner".getBytes(StandardCharsets.UTF_8);

	private HttpServer server;
	private int port;

	/** Path of the last request */
	private volatile String lastRequestPath;

	/** Raw query of the last request */
	private volatile String lastRequestQuery;

	/** Folder listings by request path without trailing "/" */
	private final Map<String, String> listings = new HashMap<>();

	/** File contents by request path */
	private final Map<String, byte[]> files = new HashMap<>();

	@BeforeEach
	public void setup() throws Exception {
		listings.put("/files", createListing(
				folderLine("sub/", "2024-01-14 09:15"),
				folderLine("undated/", "-"),
				fileLine("[TXT]", "readme.txt", "2024-01-15 10:30", "12"),
				fileLine("[TXT]", "file%20with%20blanks.txt", "2024-01-16 11:00", "1.5K"),
				fileLine("[   ]", "a+b.bin", "2024-01-17 12:00", "2M"),
				fileLine("[   ]", "huge.iso", "2024-01-18 13:00", "1G"),
				fileLine("[   ]", "giant.img", "2024-01-19 14:00", "1T")));
		listings.put("/files/sub", createListing(
				folderLine("deeper/", "2024-02-02 08:00"),
				fileLine("[TXT]", "inner.txt", "2024-02-01 08:00", "5")));
		listings.put("/files/undated", createListing());
		listings.put("/", createListing(folderLine("files/", "2024-01-01 00:00")));
		files.put("/files/readme.txt", README_DATA);
		files.put("/files/sub/inner.txt", INNER_DATA);
		// An index page instead of a directory listing
		files.put("/files/nolisting", "<html><body>Welcome</body></html>".getBytes(StandardCharsets.UTF_8));
		files.put("/files/nolisting/", "<html><body>Welcome</body></html>".getBytes(StandardCharsets.UTF_8));

		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", this::handle);
		server.start();
		port = server.getAddress().getPort();
	}

	@AfterEach
	public void tearDown() {
		server.stop(0);
	}

	private void handle(final HttpExchange exchange) throws IOException {
		final String path = exchange.getRequestURI().getPath();
		lastRequestPath = path;
		lastRequestQuery = exchange.getRequestURI().getRawQuery();

		final String listingKey = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
		byte[] response = files.get(path);
		if (response == null && listings.containsKey(listingKey)) {
			response = listings.get(listingKey).getBytes(StandardCharsets.UTF_8);
		}

		if (response == null) {
			// Like Apache, a file path with trailing "/" is not found either
			exchange.sendResponseHeaders(404, -1);
		} else {
			exchange.sendResponseHeaders(200, response.length);
			try (OutputStream outputStream = exchange.getResponseBody()) {
				outputStream.write(response);
			}
		}
		exchange.close();
	}

	/**
	 * Creates a listing in the format of Apache's mod_autoindex with FancyIndexing.
	 */
	private static String createListing(final String... entryLines) {
		final StringBuilder listing = new StringBuilder();
		listing.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n<html>\n<head><title>Index of /files</title></head>\n<body>\n<h1>Index of /files</h1>\n");
		// Header and parent directory are in one line in Apache's output
		listing.append("<pre><img src=\"/icons/blank.gif\" alt=\"Icon \"> <a href=\"?C=N;O=D\">Name</a>                    <a href=\"?C=M;O=A\">Last modified</a>      <a href=\"?C=S;O=A\">Size</a>  <a href=\"?C=D;O=A\">Description</a><hr><img src=\"/icons/back.gif\" alt=\"[PARENTDIR]\"> <a href=\"/\">Parent Directory</a>                             -   \n");
		for (final String entryLine : entryLines) {
			listing.append(entryLine).append("\n");
		}
		listing.append("<hr></pre>\n</body></html>\n");
		return listing.toString();
	}

	private static String folderLine(final String href, final String date) {
		return "<img src=\"/icons/folder.gif\" alt=\"[DIR]\"> <a href=\"" + href + "\">" + href + "</a>                    " + date + "    -   ";
	}

	private static String fileLine(final String alt, final String href, final String date, final String size) {
		return "<img src=\"/icons/text.gif\" alt=\"" + alt + "\"> <a href=\"" + href + "\">" + href + "</a>             " + date + "  " + size + "  ";
	}

	@SuppressWarnings("resource")
	private HttpFileConnection createConnection() {
		return new HttpFileConnection("127.0.0.1", "files", port).withProxy(Proxy.NO_PROXY);
	}

	private static Map<String, FileInfo> getFilesByName(final DirectoryListing listing) {
		return listing.files().stream().collect(Collectors.toMap(FileInfo::getFileName, fileInfo -> fileInfo));
	}

	@Test
	public void testGetDirectory() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			final DirectoryListing listing = connection.getDirectory("/");

			assertEquals(List.of("sub", "undated"), listing.folders().stream().map(FolderInfo::getFolderName).collect(Collectors.toList()));
			assertEquals(LocalDateTime.of(2024, 1, 14, 9, 15), listing.folders().get(0).getModificationDate().toLocalDateTime());
			// Unparsable dates of folders are tolerated
			assertNull(listing.folders().get(1).getModificationDate());

			final Map<String, FileInfo> filesByName = getFilesByName(listing);
			assertEquals(5, filesByName.size());
			assertEquals(12, filesByName.get("readme.txt").getFileSize());
			assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), filesByName.get("readme.txt").getModificationDate().toLocalDateTime());
			// URL encoded names are decoded, a literal "+" stays a "+"
			assertNotNull(filesByName.get("file with blanks.txt"));
			assertNotNull(filesByName.get("a+b.bin"));
		}
	}

	@Test
	public void testGetDirectoryApproximatesSizes() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			final Map<String, FileInfo> filesByName = getFilesByName(connection.getDirectory(""));
			// The size data of listings is imprecise and rounded down to the unit
			assertEquals(1_000L, filesByName.get("file with blanks.txt").getFileSize());
			assertEquals(2_000_000L, filesByName.get("a+b.bin").getFileSize());
			assertEquals(1_000_000_000L, filesByName.get("huge.iso").getFileSize());
			assertEquals(1_000_000_000_000L, filesByName.get("giant.img").getFileSize());
		}
	}

	@Test
	public void testGetSubDirectory() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			final DirectoryListing listing = connection.getDirectory("/sub/");
			assertEquals("/files/sub", lastRequestPath);
			assertEquals(List.of("deeper"), listing.folders().stream().map(FolderInfo::getFolderName).collect(Collectors.toList()));
			assertEquals(1, listing.files().size());
			assertEquals("inner.txt", listing.files().get(0).getFileName());
			assertEquals(5, listing.files().get(0).getFileSize());
		}
	}

	@Test
	public void testGetEmptyDirectory() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			final DirectoryListing listing = connection.getDirectory("undated");
			assertTrue(listing.folders().isEmpty());
			assertTrue(listing.files().isEmpty());
		}
	}

	@Test
	public void testGetDirectoryWithoutListing() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			// A page without "<pre>" has no entries
			final DirectoryListing listing = connection.getDirectory("nolisting/");
			assertTrue(listing.folders().isEmpty());
			assertTrue(listing.files().isEmpty());
		}
	}

	@Test
	public void testGetMissingDirectory() {
		try (HttpFileConnection connection = createConnection()) {
			final Exception exception = assertThrows(Exception.class, () -> connection.getDirectory("missing"));
			assertTrue(exception.getMessage().startsWith("Cannot download directory data for 'missing'"), exception.getMessage());
		}
	}

	@Test
	public void testGetFolderInfo() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			final FolderInfo folderInfo = connection.getFolderInfo("/sub/");
			assertNotNull(folderInfo);
			assertEquals("sub", folderInfo.getFolderName());
			assertNull(folderInfo.getModificationDate());
			// The trailing "/" requests the listing
			assertEquals("/files/sub/", lastRequestPath);
		}
	}

	@Test
	public void testGetFolderInfoOfStartFolder() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			final FolderInfo folderInfo = connection.getFolderInfo("/");
			assertNotNull(folderInfo);
			assertEquals("/", folderInfo.getFolderName());
		}
	}

	@Test
	public void testGetFolderInfoOfFileOrMissingFolder() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			assertNull(connection.getFolderInfo("readme.txt"));
			assertNull(connection.getFolderInfo("missing"));
			// A page without directory listing is no folder
			assertNull(connection.getFolderInfo("nolisting"));
		}
	}

	@Test
	public void testGetFileInfo() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			final FileInfo fileInfo = connection.getFileInfo("/sub/inner.txt");
			assertNotNull(fileInfo);
			assertEquals("inner.txt", fileInfo.getFileName());
			assertEquals(5, fileInfo.getFileSize());
			assertEquals(LocalDateTime.of(2024, 2, 1, 8, 0), fileInfo.getModificationDate().toLocalDateTime());
		}
	}

	@Test
	public void testGetFileInfoOfMissingFile() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			assertNull(connection.getFileInfo("sub/missing.txt"));
			// Folders are no files
			assertNull(connection.getFileInfo("sub/deeper"));
			// Missing parent folder
			assertNull(connection.getFileInfo("missing/file.txt"));
		}
	}

	@Test
	public void testDownloadFile() throws Exception {
		try (HttpFileConnection connection = createConnection();
				InputStream inputStream = connection.downloadFile("/readme.txt")) {
			assertArrayEquals(README_DATA, inputStream.readAllBytes());
			assertEquals("/files/readme.txt", lastRequestPath);
			assertNull(lastRequestQuery);
		}
	}

	@Test
	public void testDownloadMissingFile() {
		try (HttpFileConnection connection = createConnection()) {
			final Exception exception = assertThrows(Exception.class, () -> connection.downloadFile("missing.txt"));
			assertTrue(exception.getMessage().startsWith("Cannot download file 'missing.txt'"), exception.getMessage());
		}
	}

	@Test
	public void testCredentialsAreSentAsParameters() throws Exception {
		try (HttpFileConnection connection = new HttpFileConnection("127.0.0.1", "/files/", "user name", "p&ss=word", port);
				InputStream inputStream = connection.downloadFile("sub/inner.txt")) {
			assertArrayEquals(INNER_DATA, inputStream.readAllBytes());
			assertEquals("/files/sub/inner.txt", lastRequestPath);
			assertEquals(Map.of("username", "user name", "password", "p&ss=word"), parseQuery(lastRequestQuery));
		}
	}

	@Test
	public void testUsernameWithoutPassword() throws Exception {
		try (HttpFileConnection connection = new HttpFileConnection(false, "127.0.0.1", "files", "user", null, port);
				InputStream inputStream = connection.downloadFile("readme.txt")) {
			assertArrayEquals(README_DATA, inputStream.readAllBytes());
			assertEquals("username=user", lastRequestQuery);
		}
	}

	@Test
	public void testWithoutStartPath() throws Exception {
		try (HttpFileConnection connection = new HttpFileConnection("127.0.0.1", null, port)) {
			final FolderInfo folderInfo = connection.getFolderInfo("files");
			assertNotNull(folderInfo);
			assertEquals("files", folderInfo.getFolderName());
			assertEquals("/files/", lastRequestPath);

			assertEquals(List.of("files"), connection.getDirectory("/").folders().stream().map(FolderInfo::getFolderName).collect(Collectors.toList()));
		}
	}

	@Test
	public void testFromServerAccessData() throws Exception {
		final ServerAccessData serverAccessData = new ServerAccessData("http", "127.0.0.1", port, "files", "user", null, "pw".toCharArray(), null);
		try (HttpFileConnection connection = new HttpFileConnection(serverAccessData);
				InputStream inputStream = connection.downloadFile("readme.txt")) {
			assertArrayEquals(README_DATA, inputStream.readAllBytes());
			assertEquals(Map.of("username", "user", "password", "pw"), parseQuery(lastRequestQuery));
		}
	}

	@Test
	public void testFromServerAccessDataWithoutPassword() throws Exception {
		final ServerAccessData serverAccessData = new ServerAccessData("http", "127.0.0.1", port, "files", null, null, null, null);
		try (HttpFileConnection connection = new HttpFileConnection(serverAccessData);
				InputStream inputStream = connection.downloadFile("readme.txt")) {
			assertArrayEquals(README_DATA, inputStream.readAllBytes());
			assertNull(lastRequestQuery);
		}
	}

	@Test
	public void testFromServerAccessDataWithInvalidProtocol() {
		final ServerAccessData serverAccessData = new ServerAccessData("ftp", "127.0.0.1", port, null, null, null, null, null);
		final Exception exception = assertThrows(Exception.class, () -> new HttpFileConnection(serverAccessData));
		assertTrue(exception.getMessage().contains("Invalid protocol 'ftp'"), exception.getMessage());
	}

	@Test
	public void testHttpsToNonTlsServerFails() throws Exception {
		// The http server of this test must not be used here: it waits for a request line while the TLS client waits
		// for the server's handshake answer, so each call would only end by the read timeout of 60 seconds.
		// A server closing each connection immediately makes the handshake fail at once, which proves that https is
		// used on the given port.
		try (ServerSocket closingServerSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			final Thread closingThread = new Thread(() -> {
				while (!closingServerSocket.isClosed()) {
					try {
						// Close the connection without answering
						closingServerSocket.accept().close();
					} catch (@SuppressWarnings("unused") final IOException e) {
						// Server socket was closed at the end of the test
					}
				}
			});
			closingThread.setDaemon(true);
			closingThread.start();

			final int closingPort = closingServerSocket.getLocalPort();
			try (HttpFileConnection connection = new HttpFileConnection(true, "127.0.0.1", "files", closingPort)) {
				connection.withProxy(Proxy.NO_PROXY);
				assertThrows(Exception.class, () -> connection.downloadFile("readme.txt"));
			}
			try (HttpFileConnection connection = new HttpFileConnection(true, "127.0.0.1", "files", "user", "pw", closingPort)) {
				connection.withProxy(Proxy.NO_PROXY);
				assertThrows(Exception.class, () -> connection.getFileInfo("readme.txt"));
				assertThrows(Exception.class, () -> connection.getFolderInfo("sub"));
			}
		}
	}

	@Test
	public void testSetProxy() throws Exception {
		try (HttpFileConnection connection = new HttpFileConnection("127.0.0.1", "files", port)) {
			assertSame(connection, connection.withProxy(Proxy.NO_PROXY));
			// null uses the JVM default proxy settings again, which do not apply to the loopback address
			connection.setProxy(null);
			try (InputStream inputStream = connection.downloadFile("readme.txt")) {
				assertArrayEquals(README_DATA, inputStream.readAllBytes());
			}
		}
	}

	@Test
	public void testUnsupportedOperations() {
		final List<HttpFileConnection> connections = List.of(
				new HttpFileConnection(false, "localhost", "files"),
				new HttpFileConnection(true, "localhost", "files"),
				new HttpFileConnection("localhost", "files"),
				new HttpFileConnection("localhost", "files", "user", "pw"),
				new HttpFileConnection(true, "localhost", "files", "user", "pw"),
				new HttpFileConnection(false, "localhost", "files", "user", "pw"),
				new HttpFileConnection("localhost", "files", "user", "pw", 8080));
		for (final HttpFileConnection connection : connections) {
			assertTrue(assertThrows(Exception.class, () -> connection.uploadFile("file.txt", new ByteArrayInputStream(new byte[0]))).getMessage().contains("not supported"));
			assertTrue(assertThrows(Exception.class, () -> connection.deleteFile("file.txt")).getMessage().contains("not supported"));
			assertTrue(assertThrows(Exception.class, () -> connection.deleteFolder("folder")).getMessage().contains("not supported"));
			assertTrue(assertThrows(Exception.class, () -> connection.createFolder("", "folder")).getMessage().contains("not supported"));
			connection.close();
		}
	}

	@Test
	public void testCreateFolderPathOnlyAcceptsExistingFolders() throws Exception {
		try (HttpFileConnection connection = createConnection()) {
			connection.createFolderPath("/sub");
			assertThrows(Exception.class, () -> connection.createFolderPath("/sub/new"));
		}
	}

	private static Map<String, String> parseQuery(final String query) {
		final Map<String, String> parameters = new HashMap<>();
		for (final String parameter : query.split("&")) {
			final String[] parts = parameter.split("=", 2);
			parameters.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
		}
		return parameters;
	}
}
