package de.soderer.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * In-memory {@link RemoteConnection} for tests of the workers and the default methods of {@link RemoteConnection}.
 * Absolute and relative paths are treated alike, "\" is accepted as separator. The default methods fileExists,
 * folderExists and createFolderPath are intentionally not overridden.
 *
 * Hooks allow to simulate failures, cancellations and manipulated directory listings of a malicious server.
 */
public class InMemoryRemoteConnection implements RemoteConnection {
	/** Modification date of all entries */
	public static final ZonedDateTime MODIFICATION_DATE = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.systemDefault());

	private final Map<String, byte[]> files = new TreeMap<>();
	private final Set<String> folders = new TreeSet<>();

	/** Additional names only shown in directory listings, keyed by the normalized folder path */
	private final Map<String, List<String>> additionalListingFolderNames = new LinkedHashMap<>();
	private final Map<String, Map<String, Long>> additionalListingFiles = new LinkedHashMap<>();

	/** Calls of createFolder as "parentFolderPath|folderName" with the unchanged parameters */
	public final List<String> createFolderCalls = new ArrayList<>();

	/** Normalized paths of all deleted files */
	public final List<String> deletedFiles = new ArrayList<>();

	/** Normalized folder paths for which createFolder throws an Exception */
	public final Set<String> failingCreateFolderPaths = new HashSet<>();

	/** If set, a failing createFolder creates the folder anyway (like a concurrent client) */
	public boolean createFolderDespiteFailure = false;

	/** If set, uploads throw an IOException after the first data was read, the partial file is kept */
	public boolean failUploads = false;

	/** If set, download streams throw an IOException after the first data was read */
	public boolean failDownloads = false;

	/** If set, getFileInfo throws an Exception */
	public boolean failFileInfo = false;

	/** Called after each read of upload data */
	public Runnable uploadReadListener = null;

	/** Called after an upload has been stored completely */
	public Runnable uploadCompletedListener = null;

	/** Called after each read of a download stream */
	public Runnable downloadReadListener = null;

	private boolean closed = false;

	/**
	 * Creates an empty connection containing only the root folder.
	 */
	public InMemoryRemoteConnection() {
		folders.add("");
	}

	/**
	 * Normalizes a path to the internal key format: "/" separators, no leading or trailing separator.
	 *
	 * @param path path in RemoteConnection format
	 * @return normalized path, "" for the root folder
	 */
	public static String normalize(final String path) {
		String normalizedPath = path == null ? "" : path.replace("\\", "/");
		while (normalizedPath.startsWith("/")) {
			normalizedPath = normalizedPath.substring(1);
		}
		while (normalizedPath.endsWith("/")) {
			normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
		}
		return normalizedPath;
	}

	private static String getParent(final String normalizedPath) {
		final int lastSeparatorIndex = normalizedPath.lastIndexOf('/');
		return lastSeparatorIndex < 0 ? "" : normalizedPath.substring(0, lastSeparatorIndex);
	}

	private static String getName(final String normalizedPath) {
		return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
	}

	/**
	 * Adds a folder including all missing parent folders.
	 *
	 * @param folderPath path of the folder
	 * @return this connection for chaining
	 */
	public InMemoryRemoteConnection withFolder(final String folderPath) {
		String normalizedPath = normalize(folderPath);
		while (!normalizedPath.isEmpty()) {
			folders.add(normalizedPath);
			normalizedPath = getParent(normalizedPath);
		}
		return this;
	}

	/**
	 * Adds a file including all missing parent folders.
	 *
	 * @param filePath path of the file
	 * @param data content of the file
	 * @return this connection for chaining
	 */
	public InMemoryRemoteConnection withFile(final String filePath, final byte[] data) {
		final String normalizedPath = normalize(filePath);
		withFolder(getParent(normalizedPath));
		files.put(normalizedPath, data);
		return this;
	}

	/**
	 * Adds a folder name that only shows up in the listing of a folder, e.g. "." or "../evil".
	 *
	 * @param folderPath path of the listed folder
	 * @param name name to show as sub folder
	 * @return this connection for chaining
	 */
	public InMemoryRemoteConnection withListingFolderName(final String folderPath, final String name) {
		additionalListingFolderNames.computeIfAbsent(normalize(folderPath), k -> new ArrayList<>()).add(name);
		return this;
	}

	/**
	 * Adds a file name that only shows up in the listing of a folder, e.g. "../evil.txt".
	 *
	 * @param folderPath path of the listed folder
	 * @param name name to show as file
	 * @param size size to show for the file
	 * @return this connection for chaining
	 */
	public InMemoryRemoteConnection withListingFileName(final String folderPath, final String name, final long size) {
		additionalListingFiles.computeIfAbsent(normalize(folderPath), k -> new LinkedHashMap<>()).put(name, size);
		return this;
	}

	/**
	 * Content of a file.
	 *
	 * @param filePath path of the file
	 * @return content, or null if there is no such file
	 */
	public byte[] getFileData(final String filePath) {
		return files.get(normalize(filePath));
	}

	/**
	 * Whether a folder exists, without using the RemoteConnection methods.
	 *
	 * @param folderPath path of the folder
	 * @return true if the folder exists
	 */
	public boolean hasFolder(final String folderPath) {
		return folders.contains(normalize(folderPath));
	}

	/**
	 * Normalized paths of all files.
	 *
	 * @return file paths
	 */
	public Set<String> getFilePaths() {
		return files.keySet();
	}

	/**
	 * Whether {@link #close()} was called.
	 *
	 * @return true if closed
	 */
	public boolean isClosed() {
		return closed;
	}

	@Override
	public void createFolder(final String parentFolderPath, final String folderName) throws Exception {
		createFolderCalls.add(parentFolderPath + "|" + folderName);
		final String parent = normalize(parentFolderPath);
		final String folderPath = parent.isEmpty() ? folderName : parent + "/" + folderName;
		if (failingCreateFolderPaths.contains(folderPath)) {
			if (createFolderDespiteFailure) {
				folders.add(folderPath);
			}
			throw new Exception("Simulated failure of createFolder: " + folderPath);
		} else if (!folders.contains(parent)) {
			throw new Exception("Parent folder does not exist: " + parentFolderPath);
		} else if (folders.contains(folderPath) || files.containsKey(folderPath)) {
			throw new Exception("Path exists already: " + folderPath);
		}
		folders.add(folderPath);
	}

	@Override
	public FolderInfo getFolderInfo(final String folderPath) throws Exception {
		final String normalizedPath = normalize(folderPath);
		if (folders.contains(normalizedPath)) {
			return new FolderInfo(normalizedPath.isEmpty() ? "/" : getName(normalizedPath), MODIFICATION_DATE);
		} else {
			return null;
		}
	}

	@Override
	public DirectoryListing getDirectory(final String path) throws Exception {
		final String normalizedPath = normalize(path);
		if (!folders.contains(normalizedPath)) {
			throw new Exception("Folder does not exist: " + path);
		}
		final List<FolderInfo> folderInfos = new ArrayList<>();
		for (final String additionalName : additionalListingFolderNames.getOrDefault(normalizedPath, new ArrayList<>())) {
			folderInfos.add(new FolderInfo(additionalName, MODIFICATION_DATE));
		}
		for (final String folder : folders) {
			if (!folder.isEmpty() && getParent(folder).equals(normalizedPath)) {
				folderInfos.add(new FolderInfo(getName(folder), MODIFICATION_DATE));
			}
		}
		final List<FileInfo> fileInfos = new ArrayList<>();
		for (final Map.Entry<String, Long> additionalFile : additionalListingFiles.getOrDefault(normalizedPath, new LinkedHashMap<>()).entrySet()) {
			fileInfos.add(new FileInfo(additionalFile.getKey(), additionalFile.getValue(), MODIFICATION_DATE));
		}
		for (final Map.Entry<String, byte[]> file : files.entrySet()) {
			if (getParent(file.getKey()).equals(normalizedPath)) {
				fileInfos.add(new FileInfo(getName(file.getKey()), file.getValue().length, MODIFICATION_DATE));
			}
		}
		return new DirectoryListing(folderInfos, fileInfos);
	}

	@Override
	public void deleteFolder(final String folderPath) throws Exception {
		final String normalizedPath = normalize(folderPath);
		if (!folders.contains(normalizedPath)) {
			throw new Exception("Folder does not exist: " + folderPath);
		}
		folders.removeIf(folder -> folder.equals(normalizedPath) || folder.startsWith(normalizedPath + "/"));
		files.keySet().removeIf(file -> file.startsWith(normalizedPath + "/"));
	}

	/**
	 * Reads the first byte with {@link InputStream#read()} and the rest in small chunks, so both read methods of
	 * wrapping streams are used.
	 */
	@Override
	public void uploadFile(final String filePath, final InputStream fileInputStream) throws Exception {
		final String normalizedPath = normalize(filePath);
		if (!folders.contains(getParent(normalizedPath))) {
			throw new Exception("Parent folder does not exist: " + filePath);
		}
		final ByteArrayOutputStream data = new ByteArrayOutputStream();
		try {
			final int firstByte = fileInputStream.read();
			if (firstByte >= 0) {
				data.write(firstByte);
				notifyListener(uploadReadListener);
				if (failUploads) {
					throw new IOException("Simulated upload failure");
				}
				final byte[] buffer = new byte[4];
				int bytesRead;
				while ((bytesRead = fileInputStream.read(buffer, 0, buffer.length)) >= 0) {
					data.write(buffer, 0, bytesRead);
					notifyListener(uploadReadListener);
				}
			}
		} catch (final IOException e) {
			// Like a real server, the partially transferred file remains
			files.put(normalizedPath, data.toByteArray());
			throw e;
		}
		files.put(normalizedPath, data.toByteArray());
		notifyListener(uploadCompletedListener);
	}

	@Override
	public FileInfo getFileInfo(final String filePath) throws Exception {
		if (failFileInfo) {
			throw new Exception("Simulated failure of getFileInfo");
		}
		final String normalizedPath = normalize(filePath);
		final byte[] data = files.get(normalizedPath);
		return data == null ? null : new FileInfo(getName(normalizedPath), data.length, MODIFICATION_DATE);
	}

	@Override
	public InputStream downloadFile(final String filePath) throws Exception {
		final byte[] data = files.get(normalize(filePath));
		if (data == null) {
			throw new Exception("File does not exist: " + filePath);
		}
		return new FilterInputStream(new ByteArrayInputStream(data)) {
			private boolean dataRead = false;

			@Override
			public int read(final byte[] buffer, final int offset, final int length) throws IOException {
				if (failDownloads && dataRead) {
					throw new IOException("Simulated download failure");
				}
				// Small chunks, so a cancellation is noticed before the end of the data
				final int bytesRead = super.read(buffer, offset, Math.min(length, 4));
				dataRead = true;
				notifyListener(downloadReadListener);
				return bytesRead;
			}
		};
	}

	@Override
	public void deleteFile(final String filePath) throws Exception {
		final String normalizedPath = normalize(filePath);
		if (files.remove(normalizedPath) == null) {
			throw new Exception("File does not exist: " + filePath);
		}
		deletedFiles.add(normalizedPath);
	}

	@Override
	public void close() {
		closed = true;
	}

	private static void notifyListener(final Runnable listener) {
		if (listener != null) {
			listener.run();
		}
	}
}
