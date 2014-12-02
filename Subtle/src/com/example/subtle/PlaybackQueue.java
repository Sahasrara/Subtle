package com.example.subtle;

import java.util.ArrayList;

public class PlaybackQueue {
	private ArrayList<ServerFileData> queue;
	private ServerFileData current;
	public PlaybackQueue() {
		this.queue = new ArrayList<ServerFileData>();
		this.current = null;
	}
	
	public synchronized ServerFileData get(int index) {
		return this.queue.get(index);
	}
	
	public synchronized int indexOf(ServerFileData toFind) {
		return findIndexOf(toFind);
	}
	
	public synchronized ServerFileData remove(int index) {
		ServerFileData toReturn = null;
		int currentIndex = this.queue.indexOf(this.current);
		if (index >= 0 && index < this.queue.size()) {
			// Remove
			toReturn = this.queue.remove(index);
			
			// Set Current (next-->prev-->null)
			if (index == currentIndex) {
				if (index < this.queue.size()) {
					this.current = this.queue.get(index);
				} else if (index >= 0 && this.queue.size() > 0) {
					this.current = this.queue.get(this.queue.size()-1);
				} else {
					this.current = null;
				}
			}
		}
		return toReturn;
	}
	
	public synchronized boolean isNext() {
		if (this.queue.indexOf(this.current)+1 < this.queue.size()) {
			return true;
		} else {
			return false;
		}
	}
	
	public synchronized void advance() {
		if (this.current != null) {
			int indexOfCurrent = findIndexOf(this.current);
			if (indexOfCurrent < (this.queue.size()-1)) {
				this.current = this.queue.get(++indexOfCurrent);
			}
		}
	}
	
	public synchronized void swap(int index0, int index1) {
		if (index0 < this.queue.size() && index1 < this.queue.size()) {
			ServerFileData tmp = this.queue.get(index0);
			this.queue.set(index0, this.queue.get(index1));
			this.queue.set(index1, tmp);
		}
	}
	
	public synchronized void retreat() {
		if (this.current != null) {
			int indexOfCurrent = findIndexOf(this.current);
			if (indexOfCurrent > 0) {
				this.current = this.queue.get(--indexOfCurrent);
			}
		}
	}
	
	public synchronized ServerFileData current() {
		return this.current;
	}
	
	public synchronized int currentIndex() {
		return this.queue.indexOf(this.current);
	}
	
	public synchronized void setCurrent(int index) {
		if (index < (this.queue.size()) && index >= 0) {
			this.current = this.queue.get(index);
		}
	}
	
	public synchronized int size() {
		return this.queue.size();
	}
	
	public synchronized void enqueue(ServerFileData toQueue) {
		if (this.current == null) {
			this.current = toQueue;
		}
		// If Already Queue, create new Object
		if (queue.indexOf(toQueue) != -1) {
			toQueue = toQueue.copy();
		}
		this.queue.add(toQueue);
	}
	
	/**
	 * Helpers
	 */
	private int findIndexOf(ServerFileData toFind) {
		int i;
		for (i = 0; i < this.queue.size(); i++) {
			if (this.queue.get(i).equals(toFind)) {
				break;
			}	
		}	
		return (i == this.queue.size()) ? -1 : i;
	}
}
