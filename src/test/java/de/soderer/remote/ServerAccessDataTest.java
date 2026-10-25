package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

@SuppressWarnings("static-method")
public class ServerAccessDataTest {
	@Test
	public void testSftp() {
		final char[] password = "secret".toCharArray();
		final ServerAccessData data = new ServerAccessData(" SFTP ", "host", 2222, null, "user", "~/.ssh/id_rsa", password, "SHA256:abc");
		assertEquals("sftp", data.getProtocol());
		assertEquals("host", data.getHostname());
		assertEquals(2222, data.getPort());
		assertNull(data.getSharename());
		assertEquals("user", data.getUsername());
		assertEquals("~/.ssh/id_rsa", data.getKeyFile());
		// The password array is stored without copying
		assertSame(password, data.getPassword());
		assertEquals("SHA256:abc", data.getHostKey());
		assertEquals("sftp://user@host:2222", data.toString());
	}

	@Test
	public void testSshDefaultPort() {
		final ServerAccessData data = new ServerAccessData("ssh", "host", 0, "", "user", null, null, null);
		assertEquals("ssh", data.getProtocol());
		assertEquals(22, data.getPort());
		// Without explicit port, the display string has no port
		assertEquals("ssh://user@host", data.toString());
	}

	@Test
	public void testSftpDefaultPort() {
		assertEquals(22, new ServerAccessData("sftp", "host", 0, null, null, null, null, null).getPort());
	}

	@Test
	public void testSftpRejectsSharename() {
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new ServerAccessData("sftp", "host", 0, "share", "user", null, null, null));
		assertTrue(exception.getMessage().contains("sharename"), exception.getMessage());
	}

	@Test
	public void testFtp() {
		final ServerAccessData data = new ServerAccessData("FTP", "host", 0, null, "user", null, "pw".toCharArray(), null);
		assertEquals("ftp", data.getProtocol());
		assertEquals(21, data.getPort());
		assertNull(data.getKeyFile());
		assertNull(data.getHostKey());
		assertEquals("ftp://user@host", data.toString());
	}

	@Test
	public void testFtpRejectsUnsupportedArguments() {
		assertTrue(assertThrows(IllegalArgumentException.class, () -> new ServerAccessData("ftp", "host", 0, null, "user", "keyfile", null, null)).getMessage().contains("keyFilePath"));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> new ServerAccessData("ftp", "host", 0, null, "user", null, null, "hostkey")).getMessage().contains("hostKey"));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> new ServerAccessData("ftp", "host", 0, "share", "user", null, null, null)).getMessage().contains("sharename"));
	}

	@Test
	public void testSmb() {
		final ServerAccessData data = new ServerAccessData("smb", "host", 0, "share", "user", null, "pw".toCharArray(), null);
		assertEquals("smb", data.getProtocol());
		assertEquals(445, data.getPort());
		assertEquals("share", data.getSharename());
		assertEquals("smb://user@host/share", data.toString());
	}

	@Test
	public void testSmb1() {
		final ServerAccessData data = new ServerAccessData("smb1", "host", 139, null, null, null, null, null);
		assertEquals("smb1", data.getProtocol());
		assertEquals(139, data.getPort());
		assertEquals("smb1://host:139", data.toString());
		assertEquals(445, new ServerAccessData("smb1", "host", 0, "share", null, null, null, null).getPort());
	}

	@Test
	public void testSmbRejectsUnsupportedArguments() {
		assertTrue(assertThrows(IllegalArgumentException.class, () -> new ServerAccessData("smb", "host", 0, "share", "user", "keyfile", null, null)).getMessage().contains("keyFilePath"));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> new ServerAccessData("smb1", "host", 0, "share", "user", null, null, "hostkey")).getMessage().contains("hostKey"));
	}

	@Test
	public void testHttp() {
		final ServerAccessData data = new ServerAccessData("http", "host", 0, "start/path", "user", null, null, null);
		assertEquals("http", data.getProtocol());
		// No default port for http, the connection decides
		assertEquals(0, data.getPort());
		assertEquals("start/path", data.getSharename());
		// The base path is not part of the display string
		assertEquals("http://user@host", data.toString());
	}

	@Test
	public void testHttps() {
		final ServerAccessData data = new ServerAccessData("https", "host", 8443, null, null, null, null, null);
		assertEquals("https", data.getProtocol());
		assertEquals(8443, data.getPort());
		assertEquals("https://host:8443", data.toString());
	}

	@Test
	public void testHttpRejectsUnsupportedArguments() {
		assertTrue(assertThrows(IllegalArgumentException.class, () -> new ServerAccessData("http", "host", 0, null, "user", "keyfile", null, null)).getMessage().contains("keyFilePath"));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> new ServerAccessData("https", "host", 0, null, "user", null, null, "hostkey")).getMessage().contains("hostKey"));
	}

	@Test
	public void testInvalidProtocol() {
		assertTrue(assertThrows(IllegalArgumentException.class, () -> new ServerAccessData(null, "host", 0, null, null, null, null, null)).getMessage().contains("empty protocol"));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> new ServerAccessData("  ", "host", 0, null, null, null, null, null)).getMessage().contains("empty protocol"));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> new ServerAccessData("gopher", "host", 0, null, null, null, null, null)).getMessage().contains("Unsupported protocol: gopher"));
	}

	@Test
	public void testToStringOfEmptyDerivedClass() {
		final ServerAccessData data = new ServerAccessData() {
			// Derived classes may leave all fields unset
		};
		assertEquals("<No protocol defined>://<No host defined>", data.toString());
		assertEquals(0, data.getPort());
	}

	@Test
	public void testParseFilestring() throws Exception {
		final String fileString = ServerAccessData.PRELUDE + "myServer:/path/to/file.txt";
		assertEquals("myServer", ServerAccessData.parseFilestringToServerEntryName(fileString));
		assertEquals("/path/to/file.txt", ServerAccessData.parseFilestringToFilePath(fileString));
	}

	@Test
	public void testParseFilestringUsesLastColon() throws Exception {
		final String fileString = ServerAccessData.PRELUDE + "server:with:colons:/file";
		assertEquals("server:with:colons", ServerAccessData.parseFilestringToServerEntryName(fileString));
		assertEquals("/file", ServerAccessData.parseFilestringToFilePath(fileString));
	}

	@Test
	public void testParseInvalidFilestring() {
		assertThrows(Exception.class, () -> ServerAccessData.parseFilestringToServerEntryName(null));
		assertThrows(Exception.class, () -> ServerAccessData.parseFilestringToServerEntryName("server:/file"));
		assertThrows(Exception.class, () -> ServerAccessData.parseFilestringToServerEntryName(ServerAccessData.PRELUDE + "server/file"));
		assertThrows(Exception.class, () -> ServerAccessData.parseFilestringToFilePath(""));
		assertThrows(Exception.class, () -> ServerAccessData.parseFilestringToFilePath("server:/file"));
		assertThrows(Exception.class, () -> ServerAccessData.parseFilestringToFilePath(ServerAccessData.PRELUDE + "server/file"));
	}
}
