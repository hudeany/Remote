package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests of the simple data and exception classes of the remote package.
 */
@SuppressWarnings("static-method")
public class RemoteDataClassesTest {
	private static final ZonedDateTime DATE = ZonedDateTime.of(2024, 2, 3, 4, 5, 6, 0, ZoneId.systemDefault());

	@Test
	public void testFileInfo() {
		final FileInfo fileInfo = new FileInfo("test.txt", 123, DATE);
		assertEquals("test.txt", fileInfo.getFileName());
		assertEquals(123, fileInfo.getFileSize());
		assertSame(DATE, fileInfo.getModificationDate());
		assertTrue(fileInfo.toString().startsWith("test.txt [123 Byte, "), fileInfo.toString());
		assertFalse(fileInfo.toString().contains("Unknown modification date"), fileInfo.toString());
	}

	@Test
	public void testFileInfoWithoutModificationDate() {
		final FileInfo fileInfo = new FileInfo("test.txt", 0, null);
		assertNull(fileInfo.getModificationDate());
		assertEquals("test.txt [0 Byte, Unknown modification date]", fileInfo.toString());
	}

	@Test
	public void testFolderInfo() {
		final FolderInfo folderInfo = new FolderInfo("folder", DATE);
		assertEquals("folder", folderInfo.getFolderName());
		assertSame(DATE, folderInfo.getModificationDate());
		assertTrue(folderInfo.toString().startsWith("folder ["), folderInfo.toString());
		assertFalse(folderInfo.toString().contains("Unknown modification date"), folderInfo.toString());
	}

	@Test
	public void testFolderInfoWithoutModificationDate() {
		final FolderInfo folderInfo = new FolderInfo("/", null);
		assertNull(folderInfo.getModificationDate());
		assertEquals("/ [Unknown modification date]", folderInfo.toString());
	}

	@Test
	public void testDirectoryListing() {
		final List<FolderInfo> folders = List.of(new FolderInfo("sub", null));
		final List<FileInfo> files = List.of(new FileInfo("a.txt", 1, null));
		final DirectoryListing listing = new DirectoryListing(folders, files);
		assertSame(folders, listing.folders());
		assertSame(files, listing.files());
		assertEquals(new DirectoryListing(folders, files), listing);
	}

	@Test
	public void testCommandResultSuccessful() {
		final CommandResult result = new CommandResult("out", "", 0);
		assertEquals("out", result.getStdOut());
		assertEquals("", result.getStdErr());
		assertEquals(0, result.getExitCode());
		assertTrue(result.isSuccessful());
		assertEquals("exitCode=0, stdOut='out', stdErr=''", result.toString());
	}

	@Test
	public void testCommandResultFailed() {
		final CommandResult result = new CommandResult("", "error", 2);
		assertEquals("error", result.getStdErr());
		assertEquals(2, result.getExitCode());
		assertFalse(result.isSuccessful());
	}

	@Test
	public void testPermissionDeniedException() {
		final PermissionDeniedException exception = new PermissionDeniedException("denied", "/root");
		assertEquals("denied", exception.getMessage());
		assertEquals("/root", exception.getFolderPath());
	}

	@Test
	public void testUnknownHostKeyException() {
		final UnknownHostKeyException exception = new UnknownHostKeyException("unknown", "example.com", "SHA256:abc");
		assertEquals("unknown", exception.getMessage());
		assertEquals("example.com", exception.getHostnameOrIp());
		assertEquals("SHA256:abc", exception.getHostKeyFingerPrint());
	}
}
