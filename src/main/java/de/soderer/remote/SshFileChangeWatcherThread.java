package de.soderer.remote;

/**
 * Daemon thread that shows the last lines of a remote file via SSH and then follows all lines appended to it ("tail -f").
 * Received data is passed to a {@link LineListener}. The thread runs until {@link #kill()} is called or the remote
 * process ends.
 */
public class SshFileChangeWatcherThread extends Thread {
	private final ServerAccessData serverData;
	private final String filePath;
	private final int numberOfLastLinesToShow;

	/** Receiver of the file data */
	protected LineListener listener;

	/** False after {@link #kill()} or an error; volatile, because it is changed from other threads */
	protected volatile boolean running = true;

	/**
	 * Creates the watcher thread (as daemon thread, not started yet).
	 *
	 * @param serverData access data of the SSH server
	 * @param filePath path of the remote file
	 * @param numberOfLastLinesToShow number of existing lines to show first
	 * @param listener receiver of the file data
	 */
	public SshFileChangeWatcherThread(final ServerAccessData serverData, final String filePath, final int numberOfLastLinesToShow, final LineListener listener) {
		setDaemon(true);
		this.serverData = serverData;
		this.filePath = filePath;
		this.numberOfLastLinesToShow = numberOfLastLinesToShow;
		this.listener = listener;
	}

	/**
	 * Connects to the server and passes the file data to the listener until the thread is killed or the remote process ends.
	 */
	@Override
	public void run() {
		try (SftpConnection sftpConnection = new SftpConnection(serverData)) {
			try {
				sftpConnection.tailviewFileData(filePath, numberOfLastLinesToShow, listener);
			} catch (final InterruptedException ie) {
				throw ie;
			} catch (final Exception e) {
				e.printStackTrace();
				running = false;
			}
		} catch (@SuppressWarnings("unused") final InterruptedException ie) {
			handleInterrupt();
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Called when the thread was interrupted. Restarts watching, unless the thread was killed.
	 */
	protected void handleInterrupt() {
		if (running) {
			run();
		}
	}

	/**
	 * Stops watching and interrupts this thread.
	 */
	public void kill() {
		running = false;
		interrupt();
	}

	/**
	 * Receiver of the data of a watched file
	 */
	public interface LineListener {
		/**
		 * Called for each chunk of received file data (one or more lines, or a partial line).
		 *
		 * @param textLineData received raw data
		 */
		void addLineData(byte[] textLineData);
	}
}
