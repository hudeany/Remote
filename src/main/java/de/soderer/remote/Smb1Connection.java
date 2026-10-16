package de.soderer.remote;



/**
 * Remote connection that also allows the outdated and insecure SMB version 1 protocol, based on jcifs 3.
 *
 * SMB 2 and 3 are still preferred if the server supports them, SMB1 is only negotiated as fallback.
 * If port 445 is not reachable, the NetBIOS session port 139 is tried as well.
 * Use {@link SmbConnection} directly if SMB1 is not needed.
 */
public class Smb1Connection extends SmbConnection {
	/**
	 * Creates a connection to a share on the default port 445 (with fallback to port 139).
	 *
	 * @param hostname host name of the server
	 * @param username user name, blank for anonymous access
	 * @param sharename name of the share
	 * @param password password, not stored by this class
	 * @throws Exception if the connection fails
	 */
	public Smb1Connection(final String hostname, final String username, final String sharename, final char[] password) throws Exception {
		super(hostname, DEFAULT_PORT, username, sharename, password, true);
	}

	/**
	 * Creates a connection from access data.
	 *
	 * @param serverEntry access data with protocol "smb1" or "smb"
	 * @throws Exception if the protocol is not supported or the connection fails
	 */
	public Smb1Connection(final ServerAccessData serverEntry) throws Exception {
		super(checkProtocol(serverEntry).getHostname(), serverEntry.getPort(), serverEntry.getUsername(), serverEntry.getSharename(), serverEntry.getPassword(), true);
	}

	/**
	 * Validates the protocol before the super constructor connects.
	 */
	private static ServerAccessData checkProtocol(final ServerAccessData serverEntry) throws Exception {
		if (!"smb1".equalsIgnoreCase(serverEntry.getProtocol()) && !"smb".equalsIgnoreCase(serverEntry.getProtocol())) {
			throw new Exception("Invalid protocol '" + serverEntry.getProtocol() + "' for smb v1 connection. Only 'smb1' and 'smb' are allowed");
		}
		return serverEntry;
	}

	/**
	 * Returns the content of a folder, or null if the path is not an existing folder (behavior of the former jcifs smb1 implementation).
	 */
	@Override
	public DirectoryListing getDirectory(final String folderPath) throws Exception {
		if (!folderExists(folderPath)) {
			return null;
		}
		return super.getDirectory(folderPath);
	}
}
