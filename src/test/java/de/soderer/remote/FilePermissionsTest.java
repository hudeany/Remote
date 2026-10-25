package de.soderer.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

@SuppressWarnings("static-method")
public class FilePermissionsTest {
	@Test
	public void test() {
		try {
			assertEquals("600", new FilePermissions(true, true, false).getPermissionMapString());
			assertEquals("764", new FilePermissions(true, true, true, true, true, false, true, false, false).getPermissionMapString());
		} catch (final Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testNoPermissions() {
		final FilePermissions permissions = new FilePermissions();
		assertEquals("000", permissions.getPermissionMapString());
		assertFalse(permissions.isUserRead());
		assertFalse(permissions.isUserWrite());
		assertFalse(permissions.isUserExecute());
		assertFalse(permissions.isGroupRead());
		assertFalse(permissions.isGroupWrite());
		assertFalse(permissions.isGroupExecute());
		assertFalse(permissions.isOthersRead());
		assertFalse(permissions.isOthersWrite());
		assertFalse(permissions.isOthersExecute());
	}

	@Test
	public void testAllPermissions() {
		assertEquals("777", new FilePermissions(true, true, true, true, true, true, true, true, true).getPermissionMapString());
	}

	@Test
	public void testUserAndGroupConstructor() {
		final FilePermissions permissions = new FilePermissions(true, false, true, false, true, true);
		assertEquals("530", permissions.getPermissionMapString());
		assertTrue(permissions.isUserRead());
		assertFalse(permissions.isUserWrite());
		assertTrue(permissions.isUserExecute());
		assertFalse(permissions.isGroupRead());
		assertTrue(permissions.isGroupWrite());
		assertTrue(permissions.isGroupExecute());
		assertFalse(permissions.isOthersRead());
	}

	@Test
	public void testSettersAreChainable() {
		final FilePermissions permissions = new FilePermissions();
		assertSame(permissions, permissions
				.setUserRead(true)
				.setUserWrite(true)
				.setUserExecute(false)
				.setGroupRead(true)
				.setGroupWrite(false)
				.setGroupExecute(true)
				.setOthersRead(false)
				.setOthersWrite(true)
				.setOthersExecute(true));
		assertEquals("653", permissions.getPermissionMapString());
		assertTrue(permissions.isGroupExecute());
		assertTrue(permissions.isOthersWrite());
		assertTrue(permissions.isOthersExecute());
	}

	@Test
	public void testEachSinglePermission() {
		assertEquals("400", new FilePermissions().setUserRead(true).getPermissionMapString());
		assertEquals("200", new FilePermissions().setUserWrite(true).getPermissionMapString());
		assertEquals("100", new FilePermissions().setUserExecute(true).getPermissionMapString());
		assertEquals("040", new FilePermissions().setGroupRead(true).getPermissionMapString());
		assertEquals("020", new FilePermissions().setGroupWrite(true).getPermissionMapString());
		assertEquals("010", new FilePermissions().setGroupExecute(true).getPermissionMapString());
		assertEquals("004", new FilePermissions().setOthersRead(true).getPermissionMapString());
		assertEquals("002", new FilePermissions().setOthersWrite(true).getPermissionMapString());
		assertEquals("001", new FilePermissions().setOthersExecute(true).getPermissionMapString());
	}
}
