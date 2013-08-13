package org.irmacard.cardproxywebrelay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import javax.servlet.http.HttpServletResponse;

/**
 * A class for relaying messages from the RelayWrite servlet to the RelayRead servlet.
 * 
 * @author Maarten Everts <maarten.everts@tno.nl>
 * @author Wouter Lueks <lueks@cs.ru.nl>
 *
 */
public class MessageSender implements Runnable{
	protected boolean running = true;
	protected ArrayList<Message> channelMessages = new ArrayList<Message>();
	protected HashMap<String,HttpServletResponse> connectionMap = new HashMap<String,HttpServletResponse>();
	protected HashMap<String, ChannelStatus> channelStatusMap = new HashMap<String, ChannelStatus>();
	
	public static String SIDE_A = "a";
	public static String SIDE_B = "b";
	public static String TIMEOUT_MESSAGE = "{\"name\":\"timeout\",\"type\":\"event\",\"id\":\"0\"}";

	private static MessageSender instance = null;
	private static Maintenance maintenanceInstance;
	private Object signal = new Object();

	public MessageSender() {
	}

    public void stop() {
        running = false;
        channelMessages.clear();
        connectionMap.clear();
    }
    
    public static void stopThread() {
    	// Not using getInstance() because we do not want to start a thread to stop
    	// it if the thread has already been stopped.
    	if (instance != null) {
    		instance.stop();
    		instance = null;
    	}

    	if (maintenanceInstance != null) {
    		maintenanceInstance.stop();
    		maintenanceInstance = null;
    	}
    }
    
	@Override
	public void run() {
		while (running) {
			try {
				synchronized (signal) {
					signal.wait();
				}
			} catch(InterruptedException e) {
				// Ignore
			}

			Message[] pendingChannelMessages = null;
			synchronized (channelMessages) {
				pendingChannelMessages = channelMessages.toArray(new Message[0]);
				channelMessages.clear();
			}
			for (Message message : pendingChannelMessages) {
				boolean messageSent = false;
				synchronized (connectionMap) {

					HttpServletResponse response = connectionMap.get(makeChannelId(message.channelID, message.toSide));
					if (response != null) {
						try {
							response.getOutputStream().write(message.message);
							response.getOutputStream().close();
							// also remove it from the connectionMap, otherwise we have
							// trouble if there are more messages for this channel in the queue
							connectionMap.remove(makeChannelId(message.channelID, message.toSide));
							messageSent = true;
						} catch (IOException e) {
							// messageSent will remain false, so message will be put back into
							// the queue
							e.printStackTrace();
						}
					}
				}
				if (!messageSent) {
					// Apparently the message was not sent, let's put it back in
					// the queue
					synchronized (channelMessages) {
						channelMessages.add(message);
					}
				}
			}
		}
	}
	
	private static void setup() {
		instance = new MessageSender();
		Thread messageSenderThread = new Thread(instance, "MessageSender");
		messageSenderThread.setDaemon(true);
		messageSenderThread.start();

		maintenanceInstance = new Maintenance();
		Thread maintenanceThread = new Thread(maintenanceInstance, "MessageSender#Maintenance");
		maintenanceThread.setDaemon(true);
		maintenanceThread.start();
	}
	
	private static MessageSender getInstance() {
		if (instance == null) {
			setup();
		}
		return instance;
	}
	
	private void send(String channelID, String fromSide, byte[] message) {
		synchronized (channelMessages) {
			String toSide = fromSide.equals(SIDE_A) ? SIDE_B : SIDE_A;
			channelMessages.add(new Message(channelID, toSide, message));
		}
		synchronized (signal) {
			signal.notify();
		}
	}

	public static void Send(String channelID, String fromSide, byte[] message) {
		getInstance().send(channelID, fromSide, message);
	}
	
	private void addListener(String channel, String side, HttpServletResponse connection) throws IOException {
		synchronized (connectionMap) {
			// If there is already someone listening, simply replace it
        	// with the new one.            
            connectionMap.put(makeChannelId(channel, side), connection);
		}

		synchronized(channelStatusMap) {
            // Update channel status.
			ChannelStatus cs = channelStatusMap.get(channel);
			if(cs != null)
				cs.activity(side);
		}

		synchronized (signal) {
			// A new listener has been added, maybe there are messages for it, so
			// wake up the thread sending out messages.			
			signal.notify();
		}
	}
	
	public static void AddListener(String channelID, String side, HttpServletResponse connection) throws IOException {
		getInstance().addListener(channelID, side, connection);
	}
	
	private void removeListener(String channel, String side, HttpServletResponse connection) {
		synchronized (connectionMap) {
			// Only remove it when it is actually the same one, there could already
			// be a new one!
			HttpServletResponse c = connectionMap.get(makeChannelId(channel, side));
			if (c != null && c.equals(connection)) {
				connectionMap.remove(makeChannelId(channel, side));
			}
		}
	}
	public static void RemoveListener(String channel, String side, HttpServletResponse connection) {
		getInstance().removeListener(channel, side, connection);
	}
	
	private String makeChannelId(String channel, String side) {
		return channel + "##" + side;
	}

	private void addChannel(String channel) {
		synchronized (channelStatusMap) {
			channelStatusMap.put(channel, new ChannelStatus(channel));
		}
	}
	public static void AddChannel(String channel) {
		getInstance().addChannel(channel);
	}

	private void tick() {
		List<String> deadChannels = new Vector<String>();

		synchronized(channelStatusMap) {
			for(ChannelStatus c : channelStatusMap.values()) {
				c.tick();

				// If timeout, put messages in the queue to send out notifications
				if(c.shouldBeNotified()) {
					System.out.println("Sending timeouts for: " + c.toString());
					c.setNotified();
					send(c.getChannelID(), SIDE_A, TIMEOUT_MESSAGE.getBytes());
					send(c.getChannelID(), SIDE_B, TIMEOUT_MESSAGE.getBytes());
				}

				// If completely idle for too long, remove it from the queue
				if(c.dead()) {
					System.out.println("Garbage collecting: " + c.toString());
					deadChannels.add(c.getChannelID());
				}
			}

			for(String c : deadChannels) {
				channelStatusMap.remove(c);
			}
		}

		// Remove all pending messages of dead channels
		synchronized(channelMessages) {
			List<Message> toPurgeMessages = new Vector<Message>();

			for(Message m : channelMessages) {
				for(String channelId : deadChannels) {
					if(m.channelID.equals(channelId)) {
						toPurgeMessages.add(m);
					}
				}
			}

			channelMessages.removeAll(toPurgeMessages);
		}
	}

	public static void Tick() {
		getInstance().tick();
	}

    public class Message {
    	String channelID;
    	String toSide;
    	byte[] message;
    	Message(String channelID, String toSide, byte[] message) {
    		this.channelID = channelID;
    		this.toSide = toSide;
    		this.message = message;
    	}
    }
}
