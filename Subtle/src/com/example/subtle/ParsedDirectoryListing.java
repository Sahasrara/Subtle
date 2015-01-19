package com.example.subtle;

import java.util.List;


/**
 * This contains parsed response data.
 */
public class ParsedDirectoryListing implements DirectoryListing {
	private final ServerFileData parent;
	private final ResponseType type;
	private final List<ServerFileData> listing;
	
	public ParsedDirectoryListing(ServerFileData parent, ResponseType type, List<ServerFileData> listing) {
		this.type = type;
		this.parent = parent;
		this.listing = listing;
	}

	public ServerFileData getParent() {
		return parent;
	}

	public ResponseType getType() {
		return type;
	}

	public List<ServerFileData> getListing() {
		return listing;
	}
}
