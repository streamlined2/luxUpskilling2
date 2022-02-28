package org.training.networking.chatroom;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class Server extends Worker {
	private static final Charset CHARSET = StandardCharsets.UTF_8;
	private static final int ACCEPT_TIMEOUT = 1000;
	private static final int BUFFER_SIZE = 1024;
	private static final String EMPTY_AUTHOR = "";
	private static final String GREETING = "Hi, %s!";

	private final NavigableSet<Message> messageSet = new ConcurrentSkipListSet<>();
	private final ExecutorService executorService = Executors.newCachedThreadPool();
	private final int port;

	@Override
	public void run() {
		try (ServerSocket serverSocket = new ServerSocket(port)) {
			serverSocket.setSoTimeout(ACCEPT_TIMEOUT);
			while (!isDone() && !Thread.interrupted()) {
				handleRequest(serverSocket);
			}
		} catch (IOException e) {
			if (!isDone()) {
				log.error("server communication error");
				throw new CommunicationException("server communication error", e);
			}
		}
	}

	@Override
	public void shutdown() {
		super.shutdown();
		executorService.shutdown();
	}

	private void handleRequest(ServerSocket serverSocket) throws IOException {
		try (Socket socket = serverSocket.accept()) {
			executorService.submit(new RequestHandler(socket));
		} catch (SocketTimeoutException e) {
			// let server check if thread should be interrupted and then continue waiting
			// for incoming connection
		}
	}

	private class RequestHandler implements Runnable {

		private static final Supplier<CommunicationException> AUTHOR_CHECK = () -> new CommunicationException(
				"client hasn't responded with any meaningful author name");

		private final Socket socket;
		private Optional<String> author;
		private LocalDateTime startStamp;

		private RequestHandler(Socket socket) {
			this.socket = socket;
			this.author = Optional.empty();
			this.startStamp = LocalDateTime.now();
		}

		@Override
		public void run() {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), CHARSET),
					BUFFER_SIZE);
					PrintWriter writer = new PrintWriter(
							new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), CHARSET), BUFFER_SIZE),
							true)) {

				greetNewcomer(reader, writer);
				while (!isDone() && !Thread.interrupted()) {
					receiveBroadcastMessages(reader, writer);
				}
			} catch (IOException e) {
				log.error("server working thread communication error");
				throw new CommunicationException("server working thread communication error", e);
			}
		}

		private void greetNewcomer(BufferedReader reader, PrintWriter writer) {
			author = receiveOne(reader);
			send(writer, String.format(GREETING, author.filter(name -> !name.isBlank()).orElseThrow(AUTHOR_CHECK)));
		}

		private void receiveBroadcastMessages(BufferedReader reader, PrintWriter writer) {
			receiveStoreMessages(reader);
			fetchBroadcastMessages(writer);
		}

		private void receiveStoreMessages(BufferedReader reader) {
			for (String note : receiveAvailable(reader)) {
				messageSet.add(new Message(author.orElseThrow(AUTHOR_CHECK), LocalDateTime.now(), note));
			}
		}

		private void fetchBroadcastMessages(PrintWriter writer) {
			Message firstMessage = new Message(EMPTY_AUTHOR, startStamp, null);
			for (Message message : messageSet.tailSet(firstMessage, true)) {
				if (!author.orElseThrow(AUTHOR_CHECK).equals(message.author())) {
					send(writer, String.format("%s received message from %s on %tr", author.get(), message.toString(),
							LocalDateTime.now()));
				}
				if (startStamp.isBefore(message.stamp()) || startStamp.isEqual(message.stamp())) {
					startStamp = message.stamp().plus(1, ChronoUnit.NANOS);
				}
			}
		}

	}

}
