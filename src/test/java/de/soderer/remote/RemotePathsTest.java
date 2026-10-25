package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

@SuppressWarnings("static-method")
public class RemotePathsTest {
	@Test
	public void testJoinRelative() {
		assertEquals("a/b", RemotePaths.join("a", "b"));
		assertEquals("a/b/c", RemotePaths.join("a/b", "c"));
	}

	@Test
	public void testJoinAbsolute() {
		assertEquals("/b", RemotePaths.join("/", "b"));
		assertEquals("/a/b", RemotePaths.join("/a", "b"));
		assertEquals("/a/b", RemotePaths.join("/a/", "b"));
	}

	@Test
	public void testJoinWithoutParentStaysRelative() {
		assertEquals("b", RemotePaths.join("", "b"));
		assertEquals("b", RemotePaths.join(null, "b"));
	}
}
