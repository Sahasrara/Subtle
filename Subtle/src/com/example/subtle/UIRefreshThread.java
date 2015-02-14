package com.example.subtle;

import java.util.Map;
import java.util.Set;

import android.os.Handler;
import android.os.Message;

public class UIRefreshThread implements Runnable {
	/**
	 * Thread Resources
	 */
	private static boolean progressUpdatePaused;
	private Handler updateHandler;
	private Integer updateInterval;

	/**
	 * Runnable Constructor
	 */
	public UIRefreshThread(Handler updateHandler, Integer updateInterval) {
		this.updateHandler = updateHandler;
		this.updateInterval = updateInterval;
		UIRefreshThread.progressUpdatePaused = false;
	}
	
	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			/**
			 * Update Seek Bar
			 */
			if (!UIRefreshThread.progressUpdatePaused) {
				Message seekUpdate = Message.obtain();
				seekUpdate.what = SubtleActivity.SEEK_MESSAGE;
				seekUpdate.arg1 = SoundMachine.getInstance().getProgress(); // Song Progress
				updateHandler.sendMessage(seekUpdate);
			}
			
			/**
			 * Update Download Progress
			 */
			Set<Map.Entry<Integer, Integer>> downloadMapEntrySet = SubsonicServer.getInstance(null).getDownloadEntrySet(); // Download ID - UID
			if (downloadMapEntrySet.size() > 0) {
				// Setup Download Message
				Message downloadUpdate = Message.obtain();
				downloadUpdate.what = SubtleActivity.DOWNLOAD_PROGRESS_MESSAGE;
				downloadUpdate.obj = downloadMapEntrySet;
				updateHandler.sendMessage(downloadUpdate);
			}
			
			// Sleep
			try {
				Thread.sleep(this.updateInterval);
			} catch (InterruptedException e) {
				return;
			}
		}
	}
	
	public static void setProgressPaused(boolean paused) {
		UIRefreshThread.progressUpdatePaused = paused;
	}
}
