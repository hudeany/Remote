package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link SftpConnection} that need no SSH server: the static helpers and the error handling while connecting.
 * Connections go to a local port without listener, so they fail fast without network access.
 */
public class SftpConnectionOfflineTest {
	private static final char[] PASSWORD = "password".toCharArray();

	@TempDir
	File tempDirectory;

	/**
	 * Returns a local port, which currently has no listener.
	 */
	private static int getClosedPort() throws Exception {
		try (ServerSocket serverSocket = new ServerSocket(0)) {
			return serverSocket.getLocalPort();
		}
	}

	@Test
	@SuppressWarnings("static-method")
	public void testIsHostKeyFingerprint() {
		assertTrue(SftpConnection.isHostKeyFingerprint("SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"));
		assertTrue(SftpConnection.isHostKeyFingerprint(" sha256:abc= "));
		assertTrue(SftpConnection.isHostKeyFingerprint("d4:1d:8c:d9:8f:00:b2:04:e9:80:09:98:ec:f8:42:7e"));
		assertTrue(SftpConnection.isHostKeyFingerprint("MD5:d4:1d:8c:d9:8f:00:b2:04:e9:80:09:98:ec:f8:42:7e"));
		assertTrue(SftpConnection.isHostKeyFingerprint("d41d8cd98f00b204e9800998ecf8427e"));

		assertFalse(SftpConnection.isHostKeyFingerprint(null));
		assertFalse(SftpConnection.isHostKeyFingerprint(""));
		assertFalse(SftpConnection.isHostKeyFingerprint("~/.ssh/known_hosts"));
		assertFalse(SftpConnection.isHostKeyFingerprint("SHA256:"));
		// Too short for MD5
		assertFalse(SftpConnection.isHostKeyFingerprint("d4:1d:8c:d9"));
		assertFalse(SftpConnection.isHostKeyFingerprint(SftpConnection.DISABLE_HOST_KEY_CHECK));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testMatchesHostKeyFingerprintSha256() throws Exception {
		final byte[] hostKeyBlob = "host key blob".getBytes(StandardCharsets.UTF_8);
		final String sha256 = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(hostKeyBlob));

		assertTrue(SftpConnection.matchesHostKeyFingerprint("SHA256:" + sha256, hostKeyBlob));
		// Padding and case of the prefix are irrelevant
		assertTrue(SftpConnection.matchesHostKeyFingerprint(" sha256:" + sha256.replace("=", "") + " ", hostKeyBlob));
		assertFalse(SftpConnection.matchesHostKeyFingerprint("SHA256:" + sha256, "other key".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testMatchesHostKeyFingerprintMd5() throws Exception {
		final byte[] hostKeyBlob = "host key blob".getBytes(StandardCharsets.UTF_8);
		final StringBuilder md5Hex = new StringBuilder();
		final StringBuilder md5HexWithColons = new StringBuilder();
		for (final byte nextByte : MessageDigest.getInstance("MD5").digest(hostKeyBlob)) {
			md5Hex.append(String.format("%02x", nextByte));
			if (md5HexWithColons.length() > 0) {
				md5HexWithColons.append(':');
			}
			md5HexWithColons.append(String.format("%02X", nextByte));
		}

		assertTrue(SftpConnection.matchesHostKeyFingerprint(md5Hex.toString(), hostKeyBlob));
		assertTrue(SftpConnection.matchesHostKeyFingerprint(md5HexWithColons.toString(), hostKeyBlob));
		assertTrue(SftpConnection.matchesHostKeyFingerprint("MD5:" + md5HexWithColons, hostKeyBlob));
		assertFalse(SftpConnection.matchesHostKeyFingerprint(md5Hex.toString(), "other key".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testQuoteForShell() {
		assertEquals("'abc'", SftpConnection.quoteForShell("abc"));
		assertEquals("'a b'", SftpConnection.quoteForShell("a b"));
		assertEquals("''", SftpConnection.quoteForShell(""));
		// Shell expansions stay literal within single quotes
		assertEquals("'$(rm -rf /)'", SftpConnection.quoteForShell("$(rm -rf /)"));
		// A single quote ends the quoting, is added within double quotes and the quoting is continued
		assertEquals("'it'\"'\"'s'", SftpConnection.quoteForShell("it's"));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testQuotePathForShell() {
		assertEquals("'/tmp/file'", SftpConnection.quotePathForShell("/tmp/file"));
		// Relative paths starting with "-" would be read as option
		assertEquals("'./-rf'", SftpConnection.quotePathForShell("-rf"));
		assertEquals("'dir/-file'", SftpConnection.quotePathForShell("dir/-file"));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testInvalidProtocol() {
		final ServerAccessData serverAccessData = new ServerAccessData("ftp", "127.0.0.1", 21, null, "user", null, PASSWORD, null);
		final Exception exception = assertThrows(Exception.class, () -> new SftpConnection(serverAccessData));
		assertTrue(exception.getMessage().contains("Invalid protocol 'ftp'"), exception.getMessage());
	}

	@Test
	@SuppressWarnings("static-method")
	public void testMissingCredentials() throws Exception {
		final int port = getClosedPort();
		final Exception exception = assertThrows(Exception.class, () -> new SftpConnection("127.0.0.1", "user", null, null, port, SftpConnection.DISABLE_HOST_KEY_CHECK));
		assertTrue(exception.getMessage().contains("At least one of keyFileData or password is expected"), exception.getMessage());
	}

	@Test
	public void testMissingKnownHostsFile() throws Exception {
		final int port = getClosedPort();
		final String missingKnownHostsFile = new File(tempDirectory, "missing_known_hosts").getAbsolutePath();
		final Exception exception = assertThrows(Exception.class, () -> new SftpConnection("127.0.0.1", "user", null, PASSWORD, port, missingKnownHostsFile));
		assertTrue(exception.getMessage().contains("Cannot find known hosts file"), exception.getMessage());
	}

	@Test
	public void testConnectionRefusedWithKnownHostsFile() throws Exception {
		final int port = getClosedPort();
		final File knownHostsFile = new File(tempDirectory, "known_hosts");
		Files.writeString(knownHostsFile.toPath(), "");
		assertThrows(Exception.class, () -> new SftpConnection("127.0.0.1", "user", null, PASSWORD, port, knownHostsFile.getAbsolutePath()));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testConnectionRefusedWithDisabledHostKeyCheck() throws Exception {
		final int port = getClosedPort();
		final ServerAccessData serverAccessData = new ServerAccessData("sftp", "127.0.0.1", port, null, "user", null, PASSWORD, SftpConnection.DISABLE_HOST_KEY_CHECK);
		try (ConsoleCapture console = new ConsoleCapture()) {
			assertThrows(Exception.class, () -> new SftpConnection(serverAccessData));
			// The insecure setting must be visible to the user
			assertTrue(console.getErr().contains("WARNING: SSH host key checking is explicitly disabled for host '127.0.0.1'"), console.getErr());
		}
	}

	@Test
	@SuppressWarnings("static-method")
	public void testConnectionRefusedWithFingerprint() throws Exception {
		final int port = getClosedPort();
		final ServerAccessData serverAccessData = new ServerAccessData("ssh", "127.0.0.1", port, null, "user", null, PASSWORD, "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU");
		assertThrows(Exception.class, () -> new SftpConnection(serverAccessData, new ByteArrayOutputStream()));
	}

	@Test
	public void testInvalidKeyFile() throws Exception {
		final int port = getClosedPort();
		final File keyFile = new File(tempDirectory, "id_invalid");
		Files.writeString(keyFile.toPath(), "this is no private key");
		final ServerAccessData serverAccessData = new ServerAccessData("sftp", "127.0.0.1", port, null, "user", keyFile.getAbsolutePath(), PASSWORD, SftpConnection.DISABLE_HOST_KEY_CHECK);
		assertThrows(Exception.class, () -> new SftpConnection(serverAccessData));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testInvalidKeyDataWithoutPassphrase() throws Exception {
		final int port = getClosedPort();
		final byte[] keyFileData = "this is no private key".getBytes(StandardCharsets.UTF_8);
		assertThrows(Exception.class, () -> new SftpConnection("127.0.0.1", "user", keyFileData, null, port, SftpConnection.DISABLE_HOST_KEY_CHECK));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testUnknownHost() {
		// The top level domain "invalid" is reserved and never resolves
		// A fingerprint instead of the disabled host key check avoids the warning on System.err
		final Exception exception = assertThrows(Exception.class, () -> new SftpConnection("unknown.host.invalid", "user", null, PASSWORD, 22, "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"));
		assertTrue(exception.getMessage().contains("unknown.host.invalid"), exception.getMessage());
	}
}
