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
	private static final int ACCEPT_TIMEOUT = 1000;
	private static final int BUFFER_SIZE = 1024;

	private final NavigableSet<Message> messageSet = new ConcurrentSkipListSet<>();
	private final ExecutorService executorService = Executors.newCachedThreadPool();
	private final int port;

	@Override
	public void run() {
		log.info("server started");
		try (ServerSocket serverSocket = new ServerSocket(port)) {
			serverSocket.setSoTimeout(ACCEPT_TIMEOUT);
			while (!isDone() && !Thread.interrupted()) {
				handleRequest(serverSocket);
			}
			log.info("server stopped");
		} catch (IOException e) {
			e.printStackTrace();
			log.error("server communication error");
			throw new CommunicationException("server communication error", e);
		}
	}

	@Override
	public void shutdown() {
		log.info("server shutdown started");
		super.shutdown();
		executorService.shutdown();
		log.info("server shutdown completed");
	}

	private void handleRequest(ServerSocket serverSocket) {
		try {
			executorService.submit(new RequestHandler(serverSocket.accept()));
		} catch (SocketTimeoutException e) {
			// let server proceed normally if socket timeout was reached
		} catch (IOException e) {
			e.printStackTrace();
			log.error("server working thread communication error");
			throw new CommunicationException("server working thread communication error", e);
		}
	}

	private class RequestHandler implements Runnable {
		private static final String EMPTY_AUTHOR = "";
		private static final String GREETING = "Hi, %s!";
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
			log.info("request processing for socket {} started", socket);
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(socket.getInputStream(), getCurrentCharset()), BUFFER_SIZE);
					PrintWriter writer = new PrintWriter(
							new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), getCurrentCharset()),
									BUFFER_SIZE),
							true)) {

				greetNewcomer(reader, writer);
				while (!isDone() && !Thread.interrupted()) {
					receiveBroadcastMessages(reader, writer);
				}
				log.info("request processing for socket {} finished", socket);
			} catch (IOException e) {
				e.printStackTrace();
				log.error("working thread communication error");
				throw new CommunicationException("working thread communication error", e);
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
					log.error("can't close socket properly");
				}
			}
		}

		private void greetNewcomer(BufferedReader reader, PrintWriter writer) {
			author = getOneLine(reader);
			String greeting = String.format(GREETING,
					author.filter(RequestHandler::isValidAuthorName).orElseThrow(AUTHOR_CHECK));
			send(writer, greeting);
			log.info("sent greeting: {}", greeting);
		}

		private static boolean isValidAuthorName(String name) {
			return !name.isBlank();
		}

		private void receiveBroadcastMessages(BufferedReader reader, PrintWriter writer) {
			receiveStoreMessages(reader);
			fetchBroadcastMessages(writer);
		}

		private void receiveStoreMessages(BufferedReader reader) {
			for (String note : getAvailableLines(reader)) {
				Message message = new Message(author.orElseThrow(AUTHOR_CHECK), LocalDateTime.now(), note);
				messageSet.add(message);
				log.info("message received: {}", message);
			}
		}

		private void fetchBroadcastMessages(PrintWriter writer) {
			Message firstMessage = new Message(EMPTY_AUTHOR, startStamp, null);
			for (Message message : messageSet.tailSet(firstMessage, true)) {
				if (!author.orElseThrow(AUTHOR_CHECK).equals(message.author())) {
					send(writer, String.format("%s received message from %s on %tr", author.get(), message.toString(),
							LocalDateTime.now()));
					log.info("message sent to {}: {}", author.get(), message);
				}
				if (startStamp.isBefore(message.stamp()) || startStamp.isEqual(message.stamp())) {
					startStamp = message.stamp().plus(1, ChronoUnit.NANOS);
				}
			}
		}

	}

}
