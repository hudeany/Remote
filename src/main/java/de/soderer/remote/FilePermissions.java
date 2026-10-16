package de.soderer.remote;

/**
 * Unix file permissions (read, write, execute for user, group and others), e.g. for uploads via SFTP.
 */
public class FilePermissions {
	boolean userRead = false;
	boolean userWrite = false;
	boolean userExecute = false;

	boolean groupRead = false;
	boolean groupWrite = false;
	boolean groupExecute = false;

	boolean othersRead = false;
	boolean othersWrite = false;
	boolean othersExecute = false;

	/**
	 * Creates permissions without any rights.
	 */
	public FilePermissions() {
	}

	/**
	 * Creates permissions with rights for the user only.
	 *
	 * @param userRead read right of the user
	 * @param userWrite write right of the user
	 * @param userExecute execute right of the user
	 */
	public FilePermissions(final boolean userRead, final boolean userWrite, final boolean userExecute) {
		this.userRead = userRead;
		this.userWrite = userWrite;
		this.userExecute = userExecute;
	}

	/**
	 * Creates permissions with rights for user and group.
	 *
	 * @param userRead read right of the user
	 * @param userWrite write right of the user
	 * @param userExecute execute right of the user
	 * @param groupRead read right of the group
	 * @param groupWrite write right of the group
	 * @param groupExecute execute right of the group
	 */
	public FilePermissions(final boolean userRead, final boolean userWrite, final boolean userExecute, final boolean groupRead, final boolean groupWrite, final boolean groupExecute) {
		this.userRead = userRead;
		this.userWrite = userWrite;
		this.userExecute = userExecute;

		this.groupRead = groupRead;
		this.groupWrite = groupWrite;
		this.groupExecute = groupExecute;
	}

	/**
	 * Creates permissions with rights for user, group and others.
	 *
	 * @param userRead read right of the user
	 * @param userWrite write right of the user
	 * @param userExecute execute right of the user
	 * @param groupRead read right of the group
	 * @param groupWrite write right of the group
	 * @param groupExecute execute right of the group
	 * @param othersRead read right of others
	 * @param othersWrite write right of others
	 * @param othersExecute execute right of others
	 */
	public FilePermissions(final boolean userRead, final boolean userWrite, final boolean userExecute, final boolean groupRead, final boolean groupWrite, final boolean groupExecute, final boolean othersRead, final boolean othersWrite, final boolean othersExecute) {
		this.userRead = userRead;
		this.userWrite = userWrite;
		this.userExecute = userExecute;

		this.groupRead = groupRead;
		this.groupWrite = groupWrite;
		this.groupExecute = groupExecute;

		this.othersRead = othersRead;
		this.othersWrite = othersWrite;
		this.othersExecute = othersExecute;
	}

	/**
	 * Returns the read right of the user.
	 *
	 * @return true if set
	 */
	public boolean isUserRead() {
		return userRead;
	}

	/**
	 * Sets the read right of the user.
	 *
	 * @param userRead true to set the right
	 * @return this object for chaining
	 */
	public FilePermissions setUserRead(final boolean userRead) {
		this.userRead = userRead;
		return this;
	}

	/**
	 * Returns the write right of the user.
	 *
	 * @return true if set
	 */
	public boolean isUserWrite() {
		return userWrite;
	}

	/**
	 * Sets the write right of the user.
	 *
	 * @param userWrite true to set the right
	 * @return this object for chaining
	 */
	public FilePermissions setUserWrite(final boolean userWrite) {
		this.userWrite = userWrite;
		return this;
	}

	/**
	 * Returns the execute right of the user.
	 *
	 * @return true if set
	 */
	public boolean isUserExecute() {
		return userExecute;
	}

	/**
	 * Sets the execute right of the user.
	 *
	 * @param userExecute true to set the right
	 * @return this object for chaining
	 */
	public FilePermissions setUserExecute(final boolean userExecute) {
		this.userExecute = userExecute;
		return this;
	}

	/**
	 * Returns the read right of the group.
	 *
	 * @return true if set
	 */
	public boolean isGroupRead() {
		return groupRead;
	}

	/**
	 * Sets the read right of the group.
	 *
	 * @param groupRead true to set the right
	 * @return this object for chaining
	 */
	public FilePermissions setGroupRead(final boolean groupRead) {
		this.groupRead = groupRead;
		return this;
	}

	/**
	 * Returns the write right of the group.
	 *
	 * @return true if set
	 */
	public boolean isGroupWrite() {
		return groupWrite;
	}

	/**
	 * Sets the write right of the group.
	 *
	 * @param groupWrite true to set the right
	 * @return this object for chaining
	 */
	public FilePermissions setGroupWrite(final boolean groupWrite) {
		this.groupWrite = groupWrite;
		return this;
	}

	/**
	 * Returns the execute right of the group.
	 *
	 * @return true if set
	 */
	public boolean isGroupExecute() {
		return groupExecute;
	}

	/**
	 * Sets the execute right of the group.
	 *
	 * @param groupExecute true to set the right
	 * @return this object for chaining
	 */
	public FilePermissions setGroupExecute(final boolean groupExecute) {
		this.groupExecute = groupExecute;
		return this;
	}

	/**
	 * Returns the read right of others.
	 *
	 * @return true if set
	 */
	public boolean isOthersRead() {
		return othersRead;
	}

	/**
	 * Sets the read right of others.
	 *
	 * @param othersRead true to set the right
	 * @return this object for chaining
	 */
	public FilePermissions setOthersRead(final boolean othersRead) {
		this.othersRead = othersRead;
		return this;
	}

	/**
	 * Returns the write right of others.
	 *
	 * @return true if set
	 */
	public boolean isOthersWrite() {
		return othersWrite;
	}

	/**
	 * Sets the write right of others.
	 *
	 * @param othersWrite true to set the right
	 * @return this object for chaining
	 */
	public FilePermissions setOthersWrite(final boolean othersWrite) {
		this.othersWrite = othersWrite;
		return this;
	}

	/**
	 * Returns the execute right of others.
	 *
	 * @return true if set
	 */
	public boolean isOthersExecute() {
		return othersExecute;
	}

	/**
	 * Sets the execute right of others.
	 *
	 * @param othersExecute true to set the right
	 * @return this object for chaining
	 */
	public FilePermissions setOthersExecute(final boolean othersExecute) {
		this.othersExecute = othersExecute;
		return this;
	}

	/**
	 * Returns the permissions in octal notation, e.g. "644".
	 *
	 * @return three digit octal permission String
	 */
	public String getPermissionMapString() {
		int userNumber = 0;
		if (userRead) {
			userNumber += 4;
		}
		if (userWrite) {
			userNumber += 2;
		}
		if (userExecute) {
			userNumber += 1;
		}

		int groupNumber = 0;
		if (groupRead) {
			groupNumber += 4;
		}
		if (groupWrite) {
			groupNumber += 2;
		}
		if (groupExecute) {
			groupNumber += 1;
		}

		int othersNumber = 0;
		if (othersRead) {
			othersNumber += 4;
		}
		if (othersWrite) {
			othersNumber += 2;
		}
		if (othersExecute) {
			othersNumber += 1;
		}

		return "" + userNumber + groupNumber + othersNumber;
	}
}
