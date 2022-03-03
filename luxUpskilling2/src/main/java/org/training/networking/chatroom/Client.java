package org.training.networking.chatroom;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Queue;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class Client extends Worker {
	private static final long SLEEP_TIME = 500;
	private static final int BUFFER_SIZE = 1024;

	private final InetAddress serverAddress;
	private final int port;
	private final String name;

	@Override
	public void run() {
		log.info("{} started", name);
		try (Socket socket = new Socket(serverAddress, port);
				PrintWriter writer = new PrintWriter(new BufferedWriter(
						new OutputStreamWriter(socket.getOutputStream(), getCurrentCharset()), BUFFER_SIZE), true);
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(socket.getInputStream(), getCurrentCharset()), BUFFER_SIZE)) {

			doWork(reader, writer);
			log.info("{} stopped", name);
		} catch (IOException e) {
			e.printStackTrace();
			log.error("communication error on client side");
			throw new CommunicationException("communication error on client side", e);
		} catch (InterruptedException e) {
			e.printStackTrace();
			Thread.currentThread().interrupt();
		}
	}

	private void doWork(BufferedReader reader, PrintWriter writer) throws InterruptedException {
		log.info("{} sends author name", name);
		communicate(reader, writer, name, 1, System.out::println);
		int count = 0;
		while (!isDone() && !Thread.interrupted()) {
			Thread.sleep(SLEEP_TIME);
			String message = composeMessage(count++);
			log.info("{} sends {}", name, message);
			communicate(reader, writer, message, System.out::println);
		}
	}

	private String composeMessage(int count) {
		return String.format("message %d of %s", count, name);
	}

	protected void communicate(BufferedReader reader, PrintWriter writer, String message, int atLeastResponses,
			Consumer<String> sink) {
		send(writer, message);
		Queue<String> replies = getAtLeastLines(reader, atLeastResponses);
		replies.forEach(sink);
	}

	protected void communicate(BufferedReader reader, PrintWriter writer, String message,
			Consumer<String> sink) {
		send(writer, message);
		Queue<String> replies = getAvailableLines(reader);
		replies.forEach(sink);
	}

}
