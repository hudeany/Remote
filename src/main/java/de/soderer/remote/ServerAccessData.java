package de.soderer.remote;

import java.util.Locale;

import de.soderer.utilities.StringUtilities;

/**
 * Access data of a remote server for the protocols ssh, sftp, ftp, smb, smb1, http and https
 * (hostname, port, credentials, share name and SSH host key verification setting).
 * The password is kept as the given char array (not copied).
 */
public class ServerAccessData {
	/** Prefix of remote file strings like "\\serverEntryName:/path/file" (two backslashes) */
	public static final String PRELUDE = "\\\\";

	private String protocol;
	private String hostname;
	private String sharename;
	private int port = 0;
	private String username;
	private String keyFilePath;
	private char[] password;

	/**
	 * SSH host key verification (ssh and sftp only):
	 * <ul>
	 * <li>null or empty: check against the local known hosts file (~/.ssh/known_hosts)</li>
	 * <li>file path: check against this known hosts file</li>
	 * <li>fingerprint ("SHA256:..." or MD5 hex): check against this fingerprint</li>
	 * <li>{@link SftpConnection#DISABLE_HOST_KEY_CHECK}: no check at all (insecure)</li>
	 * </ul>
	 */
	private String hostKey;

	/**
	 * Constructor for derived classes, which set the fields themselves
	 */
	protected ServerAccessData() {
	}

	/**
	 * Creates access data and checks that only the parameters supported by the protocol are set.
	 *
	 * @param protocol one of "ssh", "sftp", "ftp", "smb", "smb1", "http", "https" (case insensitive)
	 * @param hostname hostname or IP address of the server
	 * @param port port, or 0 for the protocol's default port
	 * @param sharename share name (smb, smb1, http and https only), may be null
	 * @param username username, may be null
	 * @param keyFilePath path of a private key file (ssh and sftp only), may be null
	 * @param password password, may be null; the array is stored without copying
	 * @param hostKey host key verification (ssh and sftp only):
	 *            <ul>
	 *            <li>null or empty: check against the local known hosts file (~/.ssh/known_hosts)</li>
	 *            <li>file path: check against a specific known host keys file</li>
	 *            <li>hash value: check against this fingerprint</li>
	 *            </ul>
	 * @throws IllegalArgumentException if the protocol is empty or unsupported, or a parameter is set that the protocol does not support
	 */
	public ServerAccessData(final String protocol, final String hostname, final int port, final String sharename, final String username, final String keyFilePath, final char[] password, final String hostKey) {
		if (StringUtilities.isBlank(protocol)) {
			throw new IllegalArgumentException("Invalid empty protocol");
		} else {
			this.protocol = protocol.trim().toLowerCase(Locale.ROOT);
			if ("ssh".equalsIgnoreCase(this.protocol) || "sftp".equalsIgnoreCase(this.protocol)) {
				this.hostname = hostname;
				this.port = port;
				this.username = username;
				this.keyFilePath = keyFilePath;
				this.password = password;
				this.hostKey = hostKey;
				if (StringUtilities.isNotBlank(sharename)) {
					throw new IllegalArgumentException("Invalid argument sharename '" + sharename + "' for protocol: " + this.protocol);
				}
			} else 	if ("ftp".equalsIgnoreCase(this.protocol)) {
				this.hostname = hostname;
				this.port = port;
				this.username = username;
				this.password = password;
				if (StringUtilities.isNotBlank(keyFilePath)) {
					throw new IllegalArgumentException("Invalid argument keyFilePath '" + keyFilePath + "' for protocol: " + this.protocol);
				} else if (StringUtilities.isNotBlank(hostKey)) {
					throw new IllegalArgumentException("Invalid argument hostKey '" + hostKey + "' for protocol: " + this.protocol);
				} else if (StringUtilities.isNotBlank(sharename)) {
					throw new IllegalArgumentException("Invalid argument sharename '" + sharename + "' for protocol: " + this.protocol);
				}
			} else if ("smb".equalsIgnoreCase(this.protocol) || "smb1".equalsIgnoreCase(this.protocol)) {
				this.hostname = hostname;
				this.port = port;
				this.sharename = sharename;
				this.username = username;
				this.password = password;
				if (StringUtilities.isNotBlank(keyFilePath)) {
					throw new IllegalArgumentException("Invalid argument keyFilePath '" + keyFilePath + "' for protocol: " + this.protocol);
				} else if (StringUtilities.isNotBlank(hostKey)) {
					throw new IllegalArgumentException("Invalid argument hostKey '" + hostKey + "' for protocol: " + this.protocol);
				}
			} else if ("http".equalsIgnoreCase(this.protocol) || "https".equalsIgnoreCase(this.protocol)) {
				this.hostname = hostname;
				this.sharename = sharename;
				this.port = port;
				this.username = username;
				this.password = password;
				if (StringUtilities.isNotBlank(keyFilePath)) {
					throw new IllegalArgumentException("Invalid argument keyFilePath '" + keyFilePath + "' for protocol: " + this.protocol);
				} else if (StringUtilities.isNotBlank(hostKey)) {
					throw new IllegalArgumentException("Invalid argument hostKey '" + hostKey + "' for protocol: " + this.protocol);
				}
			} else {
				throw new IllegalArgumentException("Unsupported protocol: " + this.protocol);
			}
		}
	}

	/**
	 * Protocol in lower case.
	 *
	 * @return protocol
	 */
	public String getProtocol() {
		return protocol;
	}

	/**
	 * Hostname or IP address of the server.
	 *
	 * @return hostname
	 */
	public String getHostname() {
		return hostname;
	}

	/**
	 * Port of the server. If no port was set, the default port of the protocol is returned
	 * (22 for ssh/sftp, 21 for ftp, 445 for smb/smb1, 0 for http/https).
	 *
	 * @return port
	 */
	public int getPort() {
		if (port == 0) {
			if ("ssh".equalsIgnoreCase(protocol)) {
				return 22;
			} else if ("sftp".equalsIgnoreCase(protocol)) {
				return 22;
			} else if ("ftp".equalsIgnoreCase(protocol)) {
				return 21;
			} else if ("smb".equalsIgnoreCase(protocol)) {
				return 445;
			} else if ("smb1".equalsIgnoreCase(protocol)) {
				return 445;
			} else {
				return port;
			}
		} else {
			return port;
		}
	}

	/**
	 * Share name (smb) or base path (http).
	 *
	 * @return share name, may be null
	 */
	public String getSharename() {
		return sharename;
	}

	/**
	 * Username.
	 *
	 * @return username, may be null
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Path of the private key file.
	 *
	 * @return key file path, may be null
	 */
	public String getKeyFile() {
		return keyFilePath;
	}

	/**
	 * Password (the stored array itself, not a copy).
	 *
	 * @return password, may be null
	 */
	public char[] getPassword() {
		return password;
	}

	/**
	 * Host key verification setting, see the constructor.
	 *
	 * @return host key setting, may be null
	 */
	public String getHostKey() {
		return hostKey;
	}

	/**
	 * Extracts the server entry name of a remote file string like "\\serverEntryName:/path/file".
	 *
	 * @param fileName remote file string
	 * @return server entry name (text between prelude and the last ':')
	 * @throws Exception if the string has no prelude or no ':'
	 */
	public static String parseFilestringToServerEntryName(final String fileName) throws Exception {
		if (StringUtilities.isNotBlank(fileName) && fileName.startsWith(PRELUDE) && fileName.contains(":")) {
			return fileName.substring(PRELUDE.length(), fileName.lastIndexOf(":"));
		} else {
			throw new Exception("Invalid SSH-filename");
		}
	}

	/**
	 * Extracts the file path of a remote file string like "\\serverEntryName:/path/file".
	 *
	 * @param fileName remote file string
	 * @return file path (text after the last ':')
	 * @throws Exception if the string has no prelude or no ':'
	 */
	public static String parseFilestringToFilePath(final String fileName) throws Exception {
		if (StringUtilities.isNotBlank(fileName) && fileName.startsWith(PRELUDE) && fileName.contains(":")) {
			return fileName.substring(fileName.lastIndexOf(":") + 1);
		} else {
			throw new Exception("Invalid SSH-filename");
		}
	}

	/**
	 * Display string like "sftp://user@host:22" (without password).
	 *
	 * @return display string
	 */
	@Override
	public String toString() {
		final StringBuilder returnValue = new StringBuilder();
		if (protocol != null) {
			returnValue.append(protocol);
		} else {
			returnValue.append("<No protocol defined>");
		}
		returnValue.append("://");
		if (username != null) {
			returnValue.append(username);
			returnValue.append("@");
		}
		if (hostname != null) {
			returnValue.append(hostname);
		} else {
			returnValue.append("<No host defined>");
		}
		if (port != 0) {
			returnValue.append(":");
			returnValue.append(port);
		}
		if ("smb".equalsIgnoreCase(protocol) || "smb1".equalsIgnoreCase(protocol)) {
			if (sharename != null) {
				returnValue.append("/");
				returnValue.append(sharename);
			}
		}
		return returnValue.toString();
	}
}