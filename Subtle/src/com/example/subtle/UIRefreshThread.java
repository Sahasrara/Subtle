package com.example.subtle;

import java.util.Map;
import java.util.Set;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class UIRefreshThread implements Runnable {
	/**
	 * Thread Resources
	 */
	private static Thread thread;
	private static boolean progressUpdatePaused;
	private Handler updateHandler;
	private Integer updateInterval;

	/**
	 * Runnable Constructor
	 */
	private UIRefreshThread(Handler updateHandler, Integer updateInterval) {
		this.updateHandler = updateHandler;
		this.updateInterval = updateInterval;
		UIRefreshThread.progressUpdatePaused = false;
	}
	
	public static void start(Handler updateHandler, Integer updateInterval) {
		if (UIRefreshThread.thread == null) {
			Log.v("SUBTAG", "UIRefreshThread start!");
			UIRefreshThread urt = new UIRefreshThread(updateHandler, updateInterval);
			Seamstress.getInstance().execute(urt);
		} else {
			throw new RuntimeException("You cannot set the ui refresh thread more than once!");
		}
	}
	
	@Override
	public void run() {
		UIRefreshThread.setThread(Thread.currentThread());
		
		while (!Thread.interrupted()) {
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
	
	public static void setThread(Thread thread) {
		UIRefreshThread.thread = thread;
	}
	
	public static void shutdown() {
		Log.v("SUBTAG", "UIRefreshThread stop!");
		if (UIRefreshThread.thread != null) {
			UIRefreshThread.thread.interrupt();
			try {
				UIRefreshThread.thread.join(500);
			} catch (InterruptedException e) {
				Log.e("SUBTAG", "Interrupted during UIRefreshThread join!");
			}
			if (UIRefreshThread.thread.isAlive()) {
	            Log.e("SUBTAG", "Serious problem with UIRefreshThread thread!");
	        }
			UIRefreshThread.thread = null;
		}
	}
	
}
