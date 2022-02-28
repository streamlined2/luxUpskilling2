package org.training.networking.chatroom;

public class CommunicationException extends RuntimeException {

	public CommunicationException(String message) {
		super(message);
	}

	public CommunicationException(String message, Exception e) {
		super(message, e);
	}

}
