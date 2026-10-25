package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests that the connections reject access data of other protocols before any connection attempt.
 */
@SuppressWarnings("static-method")
public class ConnectionProtocolCheckTest {
	private static ServerAccessData createAccessData(final String protocol) {
		return new ServerAccessData(protocol, "127.0.0.1", 1, null, "user", null, "password".toCharArray(), null);
	}

	@Test
	public void testFtpConnectionRejectsOtherProtocols() {
		for (final String protocol : new String[] { "sftp", "ssh", "smb", "http" }) {
			final Exception exception = assertThrows(Exception.class, () -> new FtpConnection(createAccessData(protocol)));
			assertTrue(exception.getMessage().contains("Invalid protocol '" + protocol + "' for FTP connection"), exception.getMessage());
		}
	}

	@Test
	public void testSmbConnectionRejectsOtherProtocols() {
		for (final String protocol : new String[] { "ftp", "sftp", "https" }) {
			final Exception exception = assertThrows(Exception.class, () -> new SmbConnection(createAccessData(protocol)));
			assertTrue(exception.getMessage().contains("Invalid protocol '" + protocol + "' for smb connection"), exception.getMessage());
		}
	}

	@Test
	public void testSmb1ConnectionRejectsOtherProtocols() {
		for (final String protocol : new String[] { "ftp", "ssh", "http" }) {
			final Exception exception = assertThrows(Exception.class, () -> new Smb1Connection(createAccessData(protocol)));
			assertTrue(exception.getMessage().contains("Invalid protocol '" + protocol + "' for smb v1 connection"), exception.getMessage());
		}
	}

	@Test
	public void testSftpConnectionRejectsOtherProtocols() {
		for (final String protocol : new String[] { "ftp", "smb1", "http" }) {
			final Exception exception = assertThrows(Exception.class, () -> new SftpConnection(createAccessData(protocol)));
			assertTrue(exception.getMessage().contains("Invalid protocol '" + protocol + "' for SFTP connection"), exception.getMessage());
		}
	}

	@Test
	public void testHttpFileConnectionRejectsOtherProtocols() {
		for (final String protocol : new String[] { "ftp", "sftp", "smb" }) {
			final Exception exception = assertThrows(Exception.class, () -> new HttpFileConnection(createAccessData(protocol)));
			assertTrue(exception.getMessage().contains("Invalid protocol '" + protocol + "' for HTTP/HTTPS connection"), exception.getMessage());
		}
	}
}
