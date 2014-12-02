package com.example.subtle;

import android.content.Context;

import com.loopj.android.http.BinaryHttpResponseHandler;

public abstract class ContextAwareBinaryHttpResponseHandler extends BinaryHttpResponseHandler {
	Context parentContext;
	public ContextAwareBinaryHttpResponseHandler(String[] allowedContentTypes, Context context) {
		super(allowedContentTypes);
		this.parentContext = context;
	}
}
