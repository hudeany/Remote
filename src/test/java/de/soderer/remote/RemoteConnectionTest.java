package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests of the default methods of {@link RemoteConnection}.
 */
@SuppressWarnings("static-method")
public class RemoteConnectionTest {
	@Test
	public void testFileAndFolderExists() throws Exception {
		try (InMemoryRemoteConnection connection = new InMemoryRemoteConnection()) {
			connection.withFile("/a/file.txt", "data".getBytes(StandardCharsets.UTF_8));

			assertTrue(connection.fileExists("/a/file.txt"));
			assertFalse(connection.fileExists("/a"));
			assertFalse(connection.fileExists("/a/missing.txt"));

			assertTrue(connection.folderExists("/a"));
			assertTrue(connection.folderExists("/"));
			assertFalse(connection.folderExists("/a/file.txt"));
			assertFalse(connection.folderExists("/b"));
		}
	}

	@Test
	public void testCreateFolderPathAbsolute() throws Exception {
		try (InMemoryRemoteConnection connection = new InMemoryRemoteConnection()) {
			connection.createFolderPath("/a/b/c");

			assertTrue(connection.hasFolder("a/b/c"));
			// Absolute paths stay absolute for each created level
			assertEquals(List.of("/|a", "/a|b", "/a/b|c"), connection.createFolderCalls);
		}
	}

	@Test
	public void testCreateFolderPathRelative() throws Exception {
		try (InMemoryRemoteConnection connection = new InMemoryRemoteConnection()) {
			connection.createFolderPath("x/y");

			assertTrue(connection.hasFolder("x/y"));
			// Relative paths stay relative to the home folder
			assertEquals(List.of("|x", "x|y"), connection.createFolderCalls);
		}
	}

	@Test
	public void testCreateFolderPathWithBackslashesAndDoubledSeparators() throws Exception {
		try (InMemoryRemoteConnection connection = new InMemoryRemoteConnection()) {
			connection.createFolderPath("/p\\q//r/");

			assertTrue(connection.hasFolder("p/q/r"));
			assertEquals(List.of("/|p", "/p|q", "/p/q|r"), connection.createFolderCalls);
		}
	}

	@Test
	public void testCreateFolderPathOnlyCreatesMissingFolders() throws Exception {
		try (InMemoryRemoteConnection connection = new InMemoryRemoteConnection()) {
			connection.withFolder("/a/b");

			connection.createFolderPath("/a/b/c");

			assertEquals(List.of("/a/b|c"), connection.createFolderCalls);
		}
	}

	@Test
	public void testCreateFolderPathExistingFolderDoesNothing() throws Exception {
		try (InMemoryRemoteConnection connection = new InMemoryRemoteConnection()) {
			connection.withFolder("/a/b");

			connection.createFolderPath("/a/b");

			assertTrue(connection.createFolderCalls.isEmpty());
		}
	}

	@Test
	public void testCreateFolderPathNullOrEmptyDoesNothing() throws Exception {
		try (InMemoryRemoteConnection connection = new InMemoryRemoteConnection()) {
			connection.createFolderPath(null);
			connection.createFolderPath("");
			connection.createFolderPath("/");

			assertTrue(connection.createFolderCalls.isEmpty());
		}
	}

	@Test
	public void testCreateFolderPathFailsOnExistingFile() throws Exception {
		try (InMemoryRemoteConnection connection = new InMemoryRemoteConnection()) {
			connection.withFile("/a/b", new byte[] { 1 });

			final Exception exception = assertThrows(Exception.class, () -> connection.createFolderPath("/a/b/c"));
			assertTrue(exception.getMessage().contains("already exists as a file"), exception.getMessage());
			assertTrue(connection.createFolderCalls.isEmpty());
		}
	}

	@Test
	public void testCreateFolderPathAcceptsFolderCreatedConcurrently() throws Exception {
		try (InMemoryRemoteConnection connection = new InMemoryRemoteConnection()) {
			// createFolder fails, but another client created the folder in the meantime
			connection.failingCreateFolderPaths.add("a");
			connection.createFolderDespiteFailure = true;

			connection.createFolderPath("/a/b");

			assertTrue(connection.hasFolder("a/b"));
		}
	}

	@Test
	public void testCreateFolderPathRethrowsFailure() throws Exception {
		try (InMemoryRemoteConnection connection = new InMemoryRemoteConnection()) {
			connection.failingCreateFolderPaths.add("a/b");

			final Exception exception = assertThrows(Exception.class, () -> connection.createFolderPath("/a/b/c"));
			assertTrue(exception.getMessage().contains("Simulated failure"), exception.getMessage());
			assertTrue(connection.hasFolder("a"));
			assertFalse(connection.hasFolder("a/b"));
		}
	}
}
