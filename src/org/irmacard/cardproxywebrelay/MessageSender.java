package org.irmacard.cardproxywebrelay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.http.HttpServletResponse;


/**
 * A class for relaying messages from the RelayWrite servlet to the RelayRead servlet.
 * 
 * @author Maarten Everts <maarten.everts@tno.nl>
 *
 */
public class MessageSender implements Runnable{
	protected boolean running = true;
	protected ArrayList<Message> channelMessages = new ArrayList<Message>();
	protected HashMap<String,HttpServletResponse> connectionMap = new HashMap<String,HttpServletResponse>();
	
	public static String SIDE_A = "a";
	public static String SIDE_B = "b";

	private static MessageSender instance = null;
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
    }
    
	@Override
	public void run() {
		while (running) {
			try {
				synchronized (signal) {
					signal.wait();
				}
				System.out.println("Woke up due to signal!");
			} catch(InterruptedException e) {
				// Ignore
			}
			Message[] pendingChannelMessages = null;
			synchronized (channelMessages) {
				pendingChannelMessages = channelMessages.toArray(new Message[0]);
				System.out.println("" + pendingChannelMessages.length + " pending messages");
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
					System.out.println("Sending message <<"
							+ new String(message.message) + ">> on channel "
							+ makeChannelId(message.channelID, message.toSide)
							+ " still pending");
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
			//System.out.println("Message <<" + new String(message) + ">> on channel " + channelID + " to side " + toSide);
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
			HttpServletResponse oldConnection = connectionMap.get(makeChannelId(channel, side));

			if (oldConnection != null) {
            	// If there is already someone listening, stop that and replace it
            	// with the new one.
//            	oldConnection.reset();
            	System.out.println("****> There is already one there!");
            }

			// If there is already someone listening, simply replace it
        	// with the new one.            
            connectionMap.put(makeChannelId(channel, side), connection);
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
