package de.soderer.remote;

/**
 * Result of a remote command execution (e.g. via {@link SftpConnection#executeCommandWithResult(String)}),
 * keeping stdout, stderr and the exit code separately available instead of collapsing them into a
 * single string or deciding success/failure purely by whether stderr received any output.
 */
public class CommandResult {
	private final String stdOut;
	private final String stdErr;
	private final int exitCode;

	/**
	 * Creates a command result.
	 *
	 * @param stdOut standard output of the command
	 * @param stdErr error output of the command
	 * @param exitCode exit code of the command
	 */
	public CommandResult(final String stdOut, final String stdErr, final int exitCode) {
		this.stdOut = stdOut;
		this.stdErr = stdErr;
		this.exitCode = exitCode;
	}

	/**
	 * Returns the standard output of the command.
	 *
	 * @return standard output
	 */
	public String getStdOut() {
		return stdOut;
	}

	/**
	 * Returns the error output of the command.
	 *
	 * @return error output
	 */
	public String getStdErr() {
		return stdErr;
	}

	/**
	 * Returns the exit code of the command.
	 *
	 * @return exit code, 0 for success
	 */
	public int getExitCode() {
		return exitCode;
	}

	/**
	 * Checks whether the command succeeded.
	 *
	 * @return true if the exit code is 0
	 */
	public boolean isSuccessful() {
		return exitCode == 0;
	}

	@Override
	public String toString() {
		return "exitCode=" + exitCode + ", stdOut='" + stdOut + "', stdErr='" + stdErr + "'";
	}
}
