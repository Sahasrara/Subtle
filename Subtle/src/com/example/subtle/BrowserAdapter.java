package com.example.subtle;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class BrowserAdapter extends ArrayAdapter<ServerFileData> {
	private Context context;
	private int rowResourceId;
	public BrowserAdapter(SubtleActivity context, int rowResourceId) {
		super(context, rowResourceId);
		this.context = context;
		this.rowResourceId = rowResourceId;
	}
	
	@Override
	public boolean areAllItemsEnabled () {
		return true;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		BrowserAdapterViewHolder viewHolder;
		if (convertView == null) {
			// Get Inflator
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	        convertView = inflater.inflate(this.rowResourceId, parent, false);
	         
	        // Setup View Holder
	        viewHolder = new BrowserAdapterViewHolder();
	        viewHolder.title = (TextView) convertView.findViewById(R.id.title);
	        viewHolder.detail = (TextView) convertView.findViewById(R.id.detail);
	        viewHolder.icon = (ImageView) convertView.findViewById(R.id.icon);
	         
	        // Store View Holder
	        convertView.setTag(viewHolder);
		} else {
			viewHolder = (BrowserAdapterViewHolder) convertView.getTag();
		}		
		
		// Setup Row
		ServerFileData row = getItem(position);
		String trackNumber = (row.getTrackNumber() != -1) ? row.getTrackNumber()+" - " : ""; 
		viewHolder.title.setText(String.format("%s%s", trackNumber, row.getTitle()));
		viewHolder.detail.setText((row.getResourceType().intValue() != ServerFileData.FILE_TYPE) ? "" : row.getArtist());
        
		// Color Cached
		if (row.getCached()) {
			convertView.setBackgroundResource(SubtleActivity.BROWSER_ROW_CACHED);
		} else {
			convertView.setBackgroundResource(SubtleActivity.BROWSER_ROW_DECACHED);
		}
		convertView.setId(row.getUid());
		
		return convertView;
	}

	/**
	 * View Holder
	 */
	static class BrowserAdapterViewHolder {
		TextView title;
		TextView detail;
		ImageView icon;
	}
}
