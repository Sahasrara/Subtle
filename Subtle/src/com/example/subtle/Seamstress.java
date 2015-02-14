package com.example.subtle;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Seamstress {	
	/**
	 * Seamstress Resources
	 */
	private ExecutorService pool;
	private ExecutorService downloadPool;
	
	/**
	 * Singleton
	 */
	private static Seamstress instance = null;
	public synchronized static Seamstress getInstance() {
		if(instance == null) {
			instance = new Seamstress();
		}
		return instance;
	}
	private Seamstress() {
		this.pool = Executors.newFixedThreadPool(3);
		this.downloadPool = Executors.newFixedThreadPool(2);
	}
	
	/**
	 * Public Interface
	 */
	public ExecutorService getPool() {
		return this.pool;
	}
	public ExecutorService getDownloadPool() {
		return this.downloadPool;
	}
	public void execute(Runnable toRun) {
		if (toRun != null) {
			this.pool.execute(toRun);
		}
	}
	public void shutdown() {		
		this.pool.shutdownNow();
		this.downloadPool.shutdownNow();
		this.pool = null;
		this.downloadPool = null;
	}
}
