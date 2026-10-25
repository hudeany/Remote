package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

@SuppressWarnings("static-method")
public class SCPFileTest {
	private static final String PRELUDE = SCPFile.PRELUDE;

	@Test
	public void testParseUnixFile() throws Exception {
		final SCPFile file = new SCPFile(PRELUDE + "host/path1/path2/file.txt");
		assertEquals("host", file.getHostname());
		assertEquals("/path1/path2/", file.getPath());
		assertEquals("file.txt", file.getFilename());
		assertTrue(file.isFile());
		assertFalse(file.isDirectory());
		assertEquals("/path1/path2/file.txt", file.getPathAndFilename());
		assertEquals(PRELUDE + "host/path1/path2/file.txt", file.toString());
	}

	@Test
	public void testParseDosFile() throws Exception {
		final SCPFile file = new SCPFile(PRELUDE + "host\\path1\\path2\\file.txt");
		assertEquals("host", file.getHostname());
		assertEquals("\\path1\\path2\\", file.getPath());
		assertEquals("file.txt", file.getFilename());
		assertEquals(PRELUDE + "host\\path1\\path2\\file.txt", file.toString());
	}

	@Test
	public void testParseMixedSeparators() throws Exception {
		// The first separator of either kind ends the hostname
		final SCPFile file = new SCPFile(PRELUDE + "host\\path1/file.txt");
		assertEquals("host", file.getHostname());
		assertEquals("\\path1/", file.getPath());
		assertEquals("file.txt", file.getFilename());

		final SCPFile otherFile = new SCPFile(PRELUDE + "host/path1\\file.txt");
		assertEquals("host", otherFile.getHostname());
		assertEquals("/path1\\", otherFile.getPath());
	}

	@Test
	public void testParseDirectory() throws Exception {
		final SCPFile directory = new SCPFile(PRELUDE + "host/path1/path2/");
		assertEquals("/path1/path2/", directory.getPath());
		assertEquals("", directory.getFilename());
		assertTrue(directory.isDirectory());
		assertFalse(directory.isFile());
		assertEquals("/path1/path2/", directory.getPathAndFilename());
		assertEquals(PRELUDE + "host/path1/path2/", directory.toString());
	}

	@Test
	public void testParseFileInRootDirectory() throws Exception {
		final SCPFile file = new SCPFile(PRELUDE + "host/file*.txt");
		assertEquals("host", file.getHostname());
		assertEquals("/", file.getPath());
		// Wildcards are allowed
		assertEquals("file*.txt", file.getFilename());
	}

	@Test
	public void testParseInvalid() {
		assertThrows(Exception.class, () -> new SCPFile(null));
		assertThrows(Exception.class, () -> new SCPFile(""));
		assertThrows(Exception.class, () -> new SCPFile("host/path/file.txt"));
		// No path separator after the hostname
		assertThrows(Exception.class, () -> new SCPFile(PRELUDE + "host"));
		// Empty hostname
		assertThrows(Exception.class, () -> new SCPFile(PRELUDE + "/path/file.txt"));
	}

	@Test
	public void testCreateFromParts() throws Exception {
		final SCPFile file = new SCPFile("host", "/path", "file.txt");
		assertEquals("host", file.getHostname());
		// A missing trailing separator is appended
		assertEquals("/path/", file.getPath());
		assertEquals(PRELUDE + "host/path/file.txt", file.toString());

		final SCPFile dosFile = new SCPFile("host", "\\path", "file.txt");
		assertEquals("\\path\\", dosFile.getPath());

		final SCPFile directory = new SCPFile("host", "/path/");
		assertEquals("/path/", directory.getPath());
		assertNull(directory.getFilename());
		assertTrue(directory.isDirectory());
		assertEquals(PRELUDE + "host/path/", directory.toString());
		assertEquals("/path/", directory.getPathAndFilename());
	}

	@Test
	public void testCreateFromInvalidParts() {
		assertThrows(Exception.class, () -> new SCPFile(null, "/path", "file.txt"));
		assertThrows(Exception.class, () -> new SCPFile("host", "", "file.txt"));
		assertThrows(Exception.class, () -> new SCPFile("host", "/path", "sub/file.txt"));
		assertThrows(Exception.class, () -> new SCPFile("host", "/path", "sub\\file.txt"));
	}

	@Test
	public void testSetters() throws Exception {
		final SCPFile file = new SCPFile("host", "/path", "file.txt");
		assertSame(file, file.setHostname("other"));
		assertEquals("other", file.getHostname());

		assertSame(file, file.setPath("C:\\temp"));
		assertEquals("C:\\temp\\", file.getPath());
		file.setPath("/var/");
		assertEquals("/var/", file.getPath());

		assertSame(file, file.setFilename("new.txt"));
		assertEquals(PRELUDE + "other/var/new.txt", file.toString());

		// Without filename the descriptor denotes the directory
		file.setFilename(null);
		assertTrue(file.isDirectory());

		assertThrows(Exception.class, () -> file.setFilename("a/b"));
		assertThrows(Exception.class, () -> file.setFilename("a\\b"));
	}

	@Test
	public void testGetDirectory() throws Exception {
		final SCPFile directory = new SCPFile(PRELUDE + "host/path1/file.txt").getDirectory();
		assertTrue(directory.isDirectory());
		assertEquals(PRELUDE + "host/path1/", directory.toString());
	}

	@Test
	public void testGetParent() throws Exception {
		final SCPFile file = new SCPFile(PRELUDE + "host/path1/path2/file.txt");

		final SCPFile parentOfFile = file.getParent();
		assertEquals(PRELUDE + "host/path1/path2/", parentOfFile.toString());

		final SCPFile parentOfDirectory = parentOfFile.getParent();
		assertEquals(PRELUDE + "host/path1/", parentOfDirectory.toString());

		final SCPFile root = parentOfDirectory.getParent();
		assertEquals("/", root.getPath());

		// The root directory has no parent
		assertThrows(Exception.class, () -> root.getParent());
	}

	@Test
	public void testGetParentOfDosDirectory() throws Exception {
		final SCPFile directory = new SCPFile("host", "\\path1\\path2\\");
		assertEquals("\\path1\\", directory.getParent().getPath());
	}
}
