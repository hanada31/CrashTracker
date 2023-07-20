package com.iscas.crashtracker.client;

import org.dom4j.DocumentException;

import java.io.IOException;

/**
 * BaseClient
 * 
 * @author hanada
 * @version 2.0
 */
public abstract class BaseClient {

	public void start() {
		try {
			clientAnalyze();
			clientOutput();
		} catch (IOException | DocumentException e) {
			e.printStackTrace();
		}
	}

	protected abstract void clientAnalyze();

	public abstract void clientOutput() throws IOException, DocumentException;
}