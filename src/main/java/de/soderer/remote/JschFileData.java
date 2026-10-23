package de.soderer.remote;

import java.time.LocalDate;
import java.time.LocalDateTime;

import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.StringUtilities;

/**
 * File information parsed from a line of an "ls -l" listing (as received via an SSH exec channel).
 */
public class JschFileData {
	/**
	 * Type of a file, as given by the first character of an "ls -l" line
	 */
	public enum JschFileType {
		/** Regular file ('-') */
		RegularFile,
		/** Directory ('d') */
		Directory,
		/** Character device file ('c') */
		CharacterDeviceFile,
		/** Block device file ('b') */
		BlockDeviceFile,
		/** Local socket file ('s') */
		LocalSocketFile,
		/** Named pipe ('p') */
		NamedPipe,
		/** Symbolic link ('l') */
		SymbolicLink
	}

	private JschFileType fileType;
	private String filePath;
	private String fileName;
	private long fileSize;
	private LocalDateTime changeDate;

	/**
	 * Parses a line of an "ls -l" listing, e.g. "-rw-r--r-- 1 user group 23 Feb 2 23:37 /home/user/test.txt".
	 * Dates without year (files changed within the last months) are assigned to the current year, or to the previous year
	 * if that date would be in the future. For symbolic links the link target ("name -&gt; target") is not part of the name.
	 *
	 * @param dataString line of an "ls -l" listing
	 * @throws Exception if the line cannot be parsed
	 */
	public JschFileData(final String dataString) throws Exception {
		if (StringUtilities.isBlank(dataString)) {
			throw new Exception("Invalid file data string: " + dataString);
		}
		// -rw-r--r-- 1 root root 23 Feb 2 23:37 test.txt
		// -rw-rw-r-- 1 user group 8 Feb 2 23:22 test with blank.txt
		// drwx------ 2 root root 4096 Feb 2 21:19 dirname
		// lrwxrwxrwx 1 root root 33 Jun 29 06:23 //initrd.img -> boot/initrd.img-4.4.0-154-generic
		// -rw-------. 1 user@local.com namex 1176 Jan 23 15:03 /home/user/.ssh/authorized_keys
		// -rw-r--r-- 1 user@local.com namex 1182 Jun 14  2024 /home/user/.ssh/authorized_keys
		// crw-rw-rw- 1 root root 1, 3 Jun 1 10:00 null

		try {
			final FieldReader reader = new FieldReader(dataString);

			final String fileTypeAndRights = reader.nextField();
			fileType = getFileType(fileTypeAndRights.charAt(0));

			// inode links count, owner, group
			reader.nextField();
			reader.nextField();
			reader.nextField();

			// size (for device files the major device number)
			final String sizeString = reader.nextField();
			if (fileType == JschFileType.RegularFile) {
				fileSize = Long.parseLong(sizeString);
			}

			if (fileType == JschFileType.CharacterDeviceFile || fileType == JschFileType.BlockDeviceFile) {
				// Minor device number and date fields, the date of device files is not evaluated
				reader.nextField();
				reader.nextField();
				reader.nextField();
				reader.nextField();

				filePath = "";
				fileName = reader.remainder();
			} else {
				// change date: month, day and time or year
				final String monthString = reader.nextField();
				final String dayString = reader.nextField();
				final String timeOrYearString = reader.nextField();
				changeDate = parseChangeDate(monthString, dayString, timeOrYearString);

				// name: the rest of the line, keeping blanks inside the name
				String filePathAndName = reader.remainder();
				if (fileType == JschFileType.SymbolicLink && filePathAndName.contains(" -> ")) {
					// Remove the link target before splitting path and name, because the target may contain '/' too
					filePathAndName = filePathAndName.substring(0, filePathAndName.indexOf(" -> "));
				}
				if (filePathAndName.contains("/")) {
					filePath = filePathAndName.substring(0, filePathAndName.lastIndexOf("/"));
					fileName = filePathAndName.substring(filePathAndName.lastIndexOf("/") + 1);
				} else {
					filePath = "";
					fileName = filePathAndName;
				}
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			throw new Exception("Invalid file data string: " + dataString);
		}
	}

	private static JschFileType getFileType(final char fileTypeChar) throws Exception {
		switch (fileTypeChar) {
			case '-':
				return JschFileType.RegularFile;
			case 'd':
				return JschFileType.Directory;
			case 'c':
				return JschFileType.CharacterDeviceFile;
			case 'b':
				return JschFileType.BlockDeviceFile;
			case 's':
				return JschFileType.LocalSocketFile;
			case 'p':
				return JschFileType.NamedPipe;
			case 'l':
				return JschFileType.SymbolicLink;
			default:
				throw new Exception("Invalid file fileTypeChar: " + fileTypeChar);
		}
	}

	/**
	 * Parses the date fields of an "ls -l" line, e.g. "Feb 2 23:37" or "Jun 14 2024".
	 *
	 * @return change date, or null if it cannot be parsed
	 */
	private static LocalDateTime parseChangeDate(final String monthString, final String dayString, final String timeOrYearString) {
		try {
			final String changeDateString = DateUtilities.parseThreeLetterMonth(monthString) + " " + dayString + " " + timeOrYearString;
			try {
				LocalDateTime parsedDate = DateUtilities.parseLocalDateTime("M d HH:mm yyyy", changeDateString + " " + LocalDate.now().getYear());
				// "ls" omits the year for dates within the last six months, which may be in the previous year
				if (parsedDate.isAfter(LocalDateTime.now().plusDays(1))) {
					parsedDate = parsedDate.minusYears(1);
				}
				return parsedDate;
			} catch (@SuppressWarnings("unused") final Exception e) {
				return DateUtilities.parseLocalDate("M d yyyy", changeDateString).atStartOfDay();
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			return null;
		}
	}

	/**
	 * Splits a line into whitespace separated fields, while the remainder of the line (the file name) keeps its blanks.
	 */
	private static class FieldReader {
		private final String line;
		private int position = 0;

		FieldReader(final String line) {
			this.line = line;
		}

		private void skipWhitespace() {
			while (position < line.length() && Character.isWhitespace(line.charAt(position))) {
				position++;
			}
		}

		/**
		 * Returns the next field.
		 *
		 * @throws Exception if the line has no more fields
		 */
		String nextField() throws Exception {
			skipWhitespace();
			final int start = position;
			while (position < line.length() && !Character.isWhitespace(line.charAt(position))) {
				position++;
			}
			if (start == position) {
				throw new Exception("Missing field");
			}
			return line.substring(start, position);
		}

		/**
		 * Returns the rest of the line after the separating whitespace.
		 *
		 * @throws Exception if the rest of the line is empty
		 */
		String remainder() throws Exception {
			skipWhitespace();
			if (position >= line.length()) {
				throw new Exception("Missing file name");
			}
			return line.substring(position);
		}
	}

	/**
	 * Type of the file.
	 *
	 * @return file type
	 */
	public JschFileType getFileType() {
		return fileType;
	}

	/**
	 * Size of a regular file.
	 *
	 * @return size in bytes, 0 for all other file types
	 */
	public long getSize() {
		return fileSize;
	}

	/**
	 * Last change date (minute precision, or day precision for older files).
	 *
	 * @return change date, or null for device files or if it could not be parsed
	 */
	public LocalDateTime getChangeDate() {
		return changeDate;
	}

	/**
	 * Directory path of the file as given in the listing (without trailing '/').
	 *
	 * @return file path, empty if the listing contained no path
	 */
	public String getFilePath() {
		return filePath;
	}

	/**
	 * Name of the file without path (and without link target for symbolic links).
	 *
	 * @return file name
	 */
	public String getFileName() {
		return fileName;
	}
}
