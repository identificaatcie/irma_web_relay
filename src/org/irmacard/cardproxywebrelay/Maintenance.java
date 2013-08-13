package org.irmacard.cardproxywebrelay;

/**
 * Simple thread that will tick the MessageSender ever INTERVAL seconds.
 * @author Wouter Lueks <lueks@cs.ru.nl>
 *
 */
public class Maintenance implements Runnable {
	protected boolean running = false;

	private final long INTERVAL = 30 * 1000l;

	@Override
	public void run() {
		running = true;
		System.out.println("Maintenance thread: started");

		while(running) {
			try {
				Thread.sleep(INTERVAL);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			MessageSender.Tick();
		}
	}

	public void stop() {
		running = false;
	}
}
