package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.time.Month;

import org.junit.jupiter.api.Test;

import de.soderer.remote.JschFileData.JschFileType;

@SuppressWarnings("static-method")
public class JschFileDataTest {
	@Test
	public void testRegularFile() throws Exception {
		final JschFileData data = new JschFileData("-rw-r--r-- 1 root root 23 Jun 14  2024 test.txt");
		assertEquals(JschFileType.RegularFile, data.getFileType());
		assertEquals(23, data.getSize());
		assertEquals("", data.getFilePath());
		assertEquals("test.txt", data.getFileName());
		// Older files have a year instead of a time
		assertEquals(LocalDateTime.of(2024, 6, 14, 0, 0), data.getChangeDate());
	}

	@Test
	public void testFileNameWithBlanksAndPath() throws Exception {
		final JschFileData data = new JschFileData("-rw-------. 1 user@local.com namex 1176 Jan 23  2023 /home/user/my file  with blanks.txt");
		assertEquals(JschFileType.RegularFile, data.getFileType());
		assertEquals(1176, data.getSize());
		assertEquals("/home/user", data.getFilePath());
		assertEquals("my file  with blanks.txt", data.getFileName());
		assertEquals(LocalDateTime.of(2023, 1, 23, 0, 0), data.getChangeDate());
	}

	@Test
	public void testDateWithTimeInThePast() throws Exception {
		// A date some days ago is assigned to the year it was in
		final LocalDateTime expectedDate = LocalDateTime.now().minusDays(3).withHour(23).withMinute(37).withSecond(0).withNano(0);
		final JschFileData data = new JschFileData("-rw-r--r-- 1 root root 8 " + getMonthName(expectedDate.getMonth()) + " " + expectedDate.getDayOfMonth() + " 23:37 test.txt");
		assertEquals(expectedDate, data.getChangeDate());
	}

	@Test
	public void testDateWithTimeInTheFutureBelongsToPreviousYear() throws Exception {
		// "ls" omits the year for recent dates, so a date in the future of the current year must be from the previous year
		final LocalDateTime futureDate = LocalDateTime.now().plusDays(10);
		LocalDateTime expectedDate = LocalDateTime.of(LocalDateTime.now().getYear(), futureDate.getMonth(), futureDate.getDayOfMonth(), 12, 0);
		if (expectedDate.isAfter(LocalDateTime.now().plusDays(1))) {
			expectedDate = expectedDate.minusYears(1);
		}
		final JschFileData data = new JschFileData("-rw-r--r-- 1 root root 8 " + getMonthName(futureDate.getMonth()) + " " + futureDate.getDayOfMonth() + " 12:00 test.txt");
		assertEquals(expectedDate, data.getChangeDate());
	}

	@Test
	public void testUnparsableDateIsNull() throws Exception {
		final JschFileData data = new JschFileData("-rw-r--r-- 1 root root 8 Xyz 2 23:37 test.txt");
		assertNull(data.getChangeDate());
		assertEquals("test.txt", data.getFileName());
	}

	@Test
	public void testDirectory() throws Exception {
		final JschFileData data = new JschFileData("drwx------ 2 root root 4096 Feb 2  2020 dirname");
		assertEquals(JschFileType.Directory, data.getFileType());
		// The size is only evaluated for regular files
		assertEquals(0, data.getSize());
		assertEquals("dirname", data.getFileName());
	}

	@Test
	public void testSymbolicLinkWithoutTarget() throws Exception {
		final JschFileData data = new JschFileData("lrwxrwxrwx 1 root root 33 Jun 29  2019 //initrd.img -> boot/initrd.img-4.4.0-154-generic");
		assertEquals(JschFileType.SymbolicLink, data.getFileType());
		assertEquals("/", data.getFilePath());
		assertEquals("initrd.img", data.getFileName());
	}

	@Test
	public void testCharacterDevice() throws Exception {
		final JschFileData data = new JschFileData("crw-rw-rw- 1 root root 1, 3 Jun 1 10:00 null");
		assertEquals(JschFileType.CharacterDeviceFile, data.getFileType());
		assertEquals("", data.getFilePath());
		assertEquals("null", data.getFileName());
		// The date of device files is not evaluated
		assertNull(data.getChangeDate());
		assertEquals(0, data.getSize());
	}

	@Test
	public void testBlockDevice() throws Exception {
		final JschFileData data = new JschFileData("brw-rw---- 1 root disk 8, 0 Jun 1 10:00 sda");
		assertEquals(JschFileType.BlockDeviceFile, data.getFileType());
		assertEquals("sda", data.getFileName());
	}

	@Test
	public void testSocketAndNamedPipe() throws Exception {
		assertEquals(JschFileType.LocalSocketFile, new JschFileData("srwxr-xr-x 1 root root 0 Jan 1  2020 socket").getFileType());
		assertEquals(JschFileType.NamedPipe, new JschFileData("prw-r--r-- 1 root root 0 Jan 1  2020 /tmp/pipe").getFileType());
	}

	@Test
	public void testInvalidLines() {
		assertThrows(Exception.class, () -> new JschFileData(null));
		assertThrows(Exception.class, () -> new JschFileData("   "));
		// Unknown file type
		assertThrows(Exception.class, () -> new JschFileData("xrw-r--r-- 1 root root 23 Jun 14  2024 test.txt"));
		// Missing fields
		assertThrows(Exception.class, () -> new JschFileData("-rw-r--r-- 1 root"));
		// Missing file name
		assertThrows(Exception.class, () -> new JschFileData("-rw-r--r-- 1 root root 23 Jun 14  2024   "));
		// Invalid size
		assertThrows(Exception.class, () -> new JschFileData("-rw-r--r-- 1 root root abc Jun 14  2024 test.txt"));
	}

	@Test
	public void testFileTypeEnum() {
		assertEquals(7, JschFileType.values().length);
		assertEquals(JschFileType.NamedPipe, JschFileType.valueOf("NamedPipe"));
	}

	/**
	 * Month name as used by "ls" (independent of CLDR locale data, which may use e.g. "Sept").
	 */
	private static String getMonthName(final Month month) {
		return new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" }[month.getValue() - 1];
	}
}
