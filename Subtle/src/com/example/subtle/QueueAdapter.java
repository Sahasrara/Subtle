package com.example.subtle;


import com.nhaarman.listviewanimations.util.Swappable;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class QueueAdapter extends ArrayAdapter<ServerFileData> implements Swappable {
	private SubtleActivity context;
	private int rowResourceId;
	private ServerFileData current;

	public QueueAdapter(SubtleActivity context, int rowResourceId) {
		super(context, rowResourceId);
		this.context = context;
		this.rowResourceId = rowResourceId;
		this.current = null;
	}
	
	public void remove(int index) {
		if (index >= 0 && index < this.getCount()) {
			this.remove(this.getItem(index));
		}
	}
	
	public boolean isNext() {
		if (this.findIndexOf(this.current)+1 < this.getCount()) {
			return true;
		} else {
			return false;
		}
	}
	
	public void advance() {
		if (this.current != null) {
			int indexOfCurrent = currentIndex();
			if (indexOfCurrent < (this.getCount()-1)) {
				this.current = this.getItem(++indexOfCurrent);
			}
		}
	}
	
	public void retreat() {
		if (this.current != null) {
			int indexOfCurrent = currentIndex();
			if (indexOfCurrent > 0) {
				this.current = this.getItem(--indexOfCurrent);
			}
		}
	}
	
	public ServerFileData current() {
		return this.current;
	}
		
	public int currentIndex() {
		return this.findIndexOf(this.current);
	}
	
	public void setCurrent(int index) {
		if (index < (this.getCount()) && index >= 0) {
			this.current = this.getItem(index);
		}
	}
	
	public void enqueue(ServerFileData toQueue) {
		if (this.current == null) {
			this.current = toQueue;
		}
		// If Already Queue, create new Object
		if (this.findIndexOf(toQueue) != -1) {
			toQueue = toQueue.copy();
		}
		this.add(toQueue);
	}
	
	/**
	 * Overrides
	 */
	@Override
	public void remove(ServerFileData fileData) {
		super.remove(fileData);
		if (fileData == this.current) {
			this.current = null;
		}
	}
	
	@Override
	public boolean areAllItemsEnabled () {
		return true;
	}
	
	@Override
	public void swapItems(int index0, int index1) {
		Integer curId = this.current.getUid();
		
		// Swap Queue Positions
		if (index0 < this.getCount() && index1 < this.getCount()) {
			ServerFileData item0 = this.getItem(index0);
			ServerFileData item1 = this.getItem(index1);
			
			this.remove(item0);
			this.insert(item1, index0);
			
			this.remove(item1);
			this.insert(item0, index1);
			
			// Reset Current
			if (item0.getUid().equals(curId) ) {
				this.current = item0;
			} else if (item1.getUid().equals(curId)) {
				this.current = item1;
			}
		}
	}
	
    @Override
    public long getItemId(final int position) {
		return this.getItem(position).hashCode();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		QueueAdapterViewHolder viewHolder;
		if (convertView == null) {
			// Get Inflator
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	        convertView = inflater.inflate(this.rowResourceId, parent, false);
	        
	        // Setup View Holder
	        viewHolder = new QueueAdapterViewHolder();
	        viewHolder.title = (TextView) convertView.findViewById(R.id.title);
	        viewHolder.detail = (TextView) convertView.findViewById(R.id.detail);
	        viewHolder.icon = (ImageView) convertView.findViewById(R.id.icon);
	        viewHolder.progressBar = (ProgressBar) convertView.findViewById(R.id.progress_bar);
	         
	        // Store View Holder
	        convertView.setTag(viewHolder);
		} else {
			viewHolder = (QueueAdapterViewHolder) convertView.getTag();
		}	
		
		/**
		 * Setup Row
		 */
		// Title
		ServerFileData row = this.getItem(position);
		String trackNumber = (row.getTrackNumber() != -1) ? row.getTrackNumber()+" - " : ""; 
		viewHolder.title.setText(String.format("%s%s", trackNumber, row.getTitle()));

		// Detail
		viewHolder.detail.setText((row.getResourceType().intValue() != ServerFileData.FILE_TYPE) ? "" : row.getArtist());
		
		// Progress
		if (row.getCached()) {
			viewHolder.progressBar.setProgress(100);
		} else {
			viewHolder.progressBar.setProgress(0);
		}
		
		// ID
		viewHolder.id = row.getUid();
		convertView.setId(row.getUid());
		
		// Color
		if (position == this.currentIndex()) {
			convertView.setBackgroundResource(SubtleActivity.QUEUE_SELECTED_COLOR);
		} else {
			convertView.setBackgroundResource(SubtleActivity.QUEUE_DESELECTED_COLOR);
		}
		
		return convertView;
	}
	
	/**
	 * View Holder
	 */
	static class QueueAdapterViewHolder {
		TextView title;
		TextView detail;
		ImageView icon;
		ProgressBar progressBar;
		int id;
	}
	
	/**
	 * Helpers
	 */
	private int findIndexOf(ServerFileData toFind) {
		int i;
		for (i = 0; i < this.getCount(); i++) {
			if (this.getItem(i).equals(toFind)) {
				break;
			}	
		}	
		return (i == this.getCount()) ? -1 : i;
	}
}


