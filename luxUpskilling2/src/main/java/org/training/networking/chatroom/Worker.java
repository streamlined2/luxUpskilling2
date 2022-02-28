package org.training.networking.chatroom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Worker implements Runnable {
	
	private static final Charset CURRENT_CHARSET = StandardCharsets.UTF_8;

	protected volatile boolean done = false;
	
	public boolean isDone() {
		return done;
	}

	protected void shutdown() {
		done = true;
	}
	
	protected static Charset getCurrentCharset() {
		return CURRENT_CHARSET;
	}

	protected static void send(PrintWriter writer, String message) {
		writer.println(message);
	}

	protected static Queue<String> receiveAvailable(BufferedReader reader) {
		try {
			Queue<String> replies = new LinkedList<>();
			while (reader.ready()) {
				String line = reader.readLine();
				if (line == null) {
					break;
				}
				replies.add(line);
			}
			return replies;
		} catch (IOException e) {
			log.error("can't read message");
			throw new CommunicationException("can't read message", e);
		}
	}

	protected static Queue<String> receiveAtLeast(BufferedReader reader, int atLeast) {
		try {
			Queue<String> replies = new LinkedList<>();
			for (int count = 0; count < atLeast; count++) {
				String line = reader.readLine();
				if (line == null) {
					throw new CommunicationException(
							String.format("no more data: expected %d lines, received %d", atLeast, replies.size()));
				}
				replies.add(line);
			}
			return replies;
		} catch (IOException e) {
			log.error("can't read {} lines of message", atLeast);
			throw new CommunicationException(String.format("can't read %d lines of message", atLeast), e);
		}
	}

	protected static Optional<String> receiveOne(BufferedReader reader) {
		return Optional.ofNullable(receiveAtLeast(reader, 1).poll());
	}

}
