package org.training.networking.chatroom;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class Client extends Worker {
	private static final long SLEEP_TIME = 500;
	private static final int BUFFER_SIZE = 1024;
	private static final Charset CHARSET = StandardCharsets.UTF_8;

	private final InetAddress serverAddress;
	private final int port;
	private final String name;

	@Override
	public void run() {
		try (Socket socket = new Socket(serverAddress, port);
				PrintWriter writer = new PrintWriter(
						new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), CHARSET), BUFFER_SIZE),
						true);
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), CHARSET),
						BUFFER_SIZE)) {

			doWork(reader, writer);
		} catch (IOException e) {
			log.error("communication error on client side");
			throw new CommunicationException("communication error on client side", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void doWork(BufferedReader reader, PrintWriter writer) throws InterruptedException {
		communicate(reader, writer, name, 1, System.out::println);
		int count = 0;
		while (!isDone() && !Thread.interrupted()) {
			communicate(reader, writer, composeMessage(count++), System.out::println);
			Thread.sleep(SLEEP_TIME);
		}
	}

	private String composeMessage(int count) {
		return String.format("message %d of %s", count, name);
	}

	protected static void communicate(BufferedReader reader, PrintWriter writer, String message, int atLeastResponses,
			Consumer<String> sink) {
		send(writer, message);
		Queue<String> replies = receiveAtLeast(reader, atLeastResponses);
		replies.forEach(sink);
	}

	protected static void communicate(BufferedReader reader, PrintWriter writer, String message,
			Consumer<String> sink) {
		send(writer, message);
		Queue<String> replies = receiveAvailable(reader);
		replies.forEach(sink);
	}

}
