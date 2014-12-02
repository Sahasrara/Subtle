package com.example.subtle;

public class DirectoryResponse {
	private final ServerFileData parent;
	private final byte[] response;
	
	public DirectoryResponse(ServerFileData parent, byte[] response) {
		this.parent = parent;
		this.response = response;
	}

	public ServerFileData getParent() {
		return parent;
	}

	public byte[] getResponse() {
		return response;
	}
}
