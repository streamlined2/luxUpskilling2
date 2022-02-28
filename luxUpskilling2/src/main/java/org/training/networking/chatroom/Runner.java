package org.training.networking.chatroom;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.log4j.Log4j;

@Log4j
public class Runner {

	private static final int CLIENT_COUNT = 3;
	private static final InetAddress SERVER_ADDRESS = InetAddress.getLoopbackAddress();
	private static final int SERVER_PORT = 4444;
	private static final long WORKING_TIME = 10_000;
	private static final long GRACE_TIME = 500;

	public static void main(String[] args) {

		ExecutorService serverService = Executors.newSingleThreadExecutor();
		ExecutorService clientService = Executors.newFixedThreadPool(CLIENT_COUNT);

		Server server = new Server(SERVER_PORT);
		serverService.submit(server);

		Client[] clients = new Client[CLIENT_COUNT];
		for (int k = 0; k < clients.length; k++) {
			clients[k] = new Client(SERVER_ADDRESS, SERVER_PORT, String.format("client #%d", k));
			clientService.submit(clients[k]);
		}

		try {
			Thread.sleep(WORKING_TIME);

			for (var client : clients) {
				client.shutdown();
			}
			server.shutdown();

			Thread.sleep(GRACE_TIME);
			clientService.shutdown();
			serverService.shutdown();
		} catch (InterruptedException e) {
			log.error("runner thread interrupted");
		}

	}

}
