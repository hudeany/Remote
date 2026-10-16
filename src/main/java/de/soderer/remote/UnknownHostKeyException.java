package de.soderer.remote;

/**
 * Signals that the host key presented by a remote server (e.g. via SSH) is not known or not trusted yet.
 * Carries the host and the key's fingerprint, so the user may be asked whether to trust it.
 */
public class UnknownHostKeyException extends Exception {
	private static final long serialVersionUID = -7464906738157348128L;

	/** Hostname or IP address of the remote server */
	private final String hostnameOrIp;

	/** Fingerprint of the unknown host key */
	private final String hostKeyFingerPrint;

	/**
	 * Creates a new exception.
	 *
	 * @param message detail message
	 * @param hostnameOrIp hostname or IP address of the remote server
	 * @param hostKeyFingerPrint fingerprint of the unknown host key
	 */
	public UnknownHostKeyException(final String message, final String hostnameOrIp, final String hostKeyFingerPrint) {
		super(message);
		this.hostnameOrIp = hostnameOrIp;
		this.hostKeyFingerPrint = hostKeyFingerPrint;
	}

	/**
	 * Hostname or IP address of the remote server.
	 *
	 * @return hostname or IP address
	 */
	public String getHostnameOrIp() {
		return hostnameOrIp;
	}

	/**
	 * Fingerprint of the unknown host key.
	 *
	 * @return host key fingerprint
	 */
	public String getHostKeyFingerPrint() {
		return hostKeyFingerPrint;
	}
}
