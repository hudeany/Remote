# Remote

Java library for accessing files and folders on remote servers through **one common interface**, no matter
whether the server speaks **SFTP**, **FTP**, **SMB** or offers plain **HTTP directory listings**. Comes with
ready-to-use workers for uploading and downloading files and whole directory trees with progress reporting
and cancellation.

Package: `de.soderer.remote`

## Features

- **One interface for all protocols:** `RemoteConnection` lists folders, reads file and folder information,
  uploads, downloads and deletes files and creates and deletes folders. Code written against it works with
  every supported protocol.
- **SFTP / SSH** (`SftpConnection`): password or private key authentication, strict host key verification
  (known hosts file or expected fingerprint), remote command execution, symbolic link listing and live
  "tail" of remote log files (`SshFileChangeWatcherThread`).
- **FTP** (`FtpConnection`): active or passive mode.
- **SMB 2/3** (`SmbConnection`): Windows and Samba shares, automatic reconnect after network drops,
  create folder paths, rename files and set modification times. The outdated **SMB1** protocol is only used on explicit request
  (`Smb1Connection`).
- **HTTP / HTTPS** (`HttpFileConnection`): read-only access to web servers with Apache style "Index of"
  directory listings.
- **Proxy support** via standard `java.net.Proxy` for SFTP, FTP and HTTP.
- **Workers** for file and directory transfers with progress, item progress and cancellation, based on the
  worker classes of soderer-utilities.
- **Central access data:** `ServerAccessData` holds hostname, port, credentials, share name and host key
  setting for all protocols, so connections can be configured in one place.

## Requirements

- Java 17 or newer
- soderer-utilities (worker base classes and helpers)
- Protocol libraries, only needed for the protocols you use:

| Protocol             | Class                              | Additional dependency                                                      |
|----------------------|------------------------------------|----------------------------------------------------------------------------|
| SFTP / SSH           | `SftpConnection`                   | [JSch](https://github.com/mwiede/jsch) (`com.jcraft.jsch`)                  |
| FTP                  | `FtpConnection`                    | [Apache Commons Net](https://commons.apache.org/proper/commons-net/)        |
| SMB 2/3, SMB1        | `SmbConnection`, `Smb1Connection`  | [jcifs (codelibs)](https://github.com/codelibs/jcifs) 3.x (`org.codelibs.jcifs`) |
| HTTP / HTTPS         | `HttpFileConnection`               | none (JDK only)                                                            |

## Usage

All connections implement `RemoteConnection`, which is `Closeable`, so use them in try-with-resources.
Paths always use `/` as separator.

### SFTP

```java
char[] password = ...;

// Host key checked against ~/.ssh/known_hosts (hostKey null)
try (SftpConnection connection = new SftpConnection("server.example.com", "andreas", null, password, 22, null)) {
	DirectoryListing listing = connection.getDirectory("/var/log");
	for (FolderInfo folder : listing.folders()) {
		System.out.println("[DIR] " + folder.getFolderName());
	}
	for (FileInfo file : listing.files()) {
		System.out.println(file.getFileName() + " (" + file.getFileSize() + " bytes, " + file.getModificationDate() + ")");
	}
}
```

Host key verification is controlled by the `hostKey` parameter:

| `hostKey` value                           | Behavior                                                        |
|-------------------------------------------|-----------------------------------------------------------------|
| `null` or `""`                            | Check against `~/.ssh/known_hosts`                              |
| file path                                 | Check against this known hosts file                             |
| `"SHA256:..."` or MD5 hex fingerprint     | Check against this fingerprint during the key exchange          |
| `SftpConnection.DISABLE_HOST_KEY_CHECK`   | No check at all (insecure, only for throwaway test environments) |

Unknown host keys are always rejected, credentials are never sent to a host with an unexpected key.

For key based authentication pass the content of the private key file as `keyFileData` and its passphrase as
`password`. SSH commands can be executed on the same connection:

```java
CommandResult result = connection.executeCommandWithResult("df -h /");
if (result.isSuccessful()) {
	System.out.println(result.getStdOut());
}
```

### FTP

```java
try (FtpConnection connection = new FtpConnection("ftp.example.com", "user", password)) {
	try (InputStream inputStream = new FileInputStream("report.pdf")) {
		connection.uploadFile("/upload/report.pdf", inputStream);
	}
	try (InputStream inputStream = connection.downloadFile("/upload/report.pdf")) {
		// read the data
	}
}
```

The port defaults to 21 and passive mode is used by default. Both can be set with the longer constructors.

### SMB

```java
// SMB 2.0.2 up to 3.1.1, port 445
try (SmbConnection connection = new SmbConnection("nas.local", 445, "user", "photos", password)) {
	connection.createFolderPath("/2026/summer");
	connection.renameFile("/inbox/img001.jpg", "/2026/summer/img001.jpg", false);
}
```

SMB1 is outdated and insecure. It is only negotiated with `Smb1Connection` or `allowSmb1 = true`.

### HTTP directory listings

```java
// Read-only: uploading, deleting and creating folders throw an exception
try (HttpFileConnection connection = new HttpFileConnection(true, "downloads.example.com", "/releases")) {
	DirectoryListing listing = connection.getDirectory("/");
	// ...
}
```

Credentials are sent as URL parameters, so only use them with TLS and be aware that they may show up in
server and proxy logs.

### Connections from central access data

```java
char[] passphrase = ...;
ServerAccessData accessData = new ServerAccessData("sftp", "server.example.com", 0, null, "andreas", "~/.ssh/id_ed25519", passphrase, "");
try (RemoteConnection connection = new SftpConnection(accessData)) {
	// ...
}
```

Supported protocols are `ssh`, `sftp`, `ftp`, `smb`, `smb1`, `http` and `https`. A port of 0 selects the
protocol's default port. Parameters a protocol does not support are rejected.

### Proxies

SFTP, FTP and HTTP connections accept a standard `java.net.Proxy`:

```java
connection.setProxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("proxy.local", 1080)));
```

| Value              | `HttpFileConnection`      | `FtpConnection`                 | `SftpConnection`                |
|--------------------|---------------------------|---------------------------------|---------------------------------|
| `null`             | JVM default proxy settings | JVM default SOCKS settings      | JVM default SOCKS settings      |
| `Proxy.NO_PROXY`   | direct connection         | direct connection               | direct connection               |
| proxy types        | HTTP, SOCKS               | SOCKS only                      | HTTP, SOCKS5                    |

SFTP and FTP connections are established in the constructor, so setting a proxy reconnects through it.

### Transfers with progress

The workers transfer files and complete directory trees and report their progress to a worker parent
(`WorkerParentSimple` for files, `WorkerParentDual` for directories with per file progress). Pass `null`
as parent to run them without progress reporting.

```java
// Download a file (the local file must not exist yet)
new RemoteFileDownloadWorker(parent, connection, "/var/log/app.log", new File("app.log")).work();

// Upload a file (the remote file must not exist yet)
new RemoteFileUploadWorker(parent, connection, new File("backup.tar.gz"), "/backups/backup.tar.gz").work();

// Download a directory tree into a new local directory
new RemoteDirectoryDownloadWorker(parent, connection, "/var/www", new File("/tmp/mirror")).work();

// Upload a directory tree into a new remote directory
new RemoteDirectoryUploadWorker(parent, connection, new File("site"), "/var/www", "site").work();
```

Cancelled or failed transfers remove partially written files. Remote names that would lead outside of the
local destination directory are rejected.
