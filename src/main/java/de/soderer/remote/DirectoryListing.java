package de.soderer.remote;

import java.util.List;

/**
 * Content of a remote folder, as returned by {@link RemoteConnection#getDirectory(String)}.
 *
 * @param folders sub folders of the folder
 * @param files files of the folder
 */
public record DirectoryListing(List<FolderInfo> folders, List<FileInfo> files) {
}
