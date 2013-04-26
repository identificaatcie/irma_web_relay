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
	
	private static MessageSender instance = null;
	
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
			if (channelMessages.size() == 0) {
				try {
					synchronized (channelMessages) {
						channelMessages.wait();
					}
				} catch(InterruptedException e) {
					// Ignore
				}
			}

			synchronized (connectionMap) {
				Message[] pendingChannelMessages = null;
				synchronized (channelMessages) {
					pendingChannelMessages = channelMessages.toArray(new Message[0]);
					channelMessages.clear();
				}
				for (Message message : pendingChannelMessages) {
					HttpServletResponse response = connectionMap.get(message.channelID);
					if (response == null) {
						// There is no one listening for that channel right now, let's put
						// it back in the queue
						synchronized (channelMessages) {
							channelMessages.add(message);
						}
					} else {
						try {
							response.getOutputStream().write(message.message);
							response.getOutputStream().close();
						} catch (IOException e) {
							// Maybe try again? Let's put it back into the queue
							synchronized (channelMessages) {
								channelMessages.add(message);
							}
							e.printStackTrace();
						}
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
	
	private void send(String channelID, byte[] message) {
		synchronized (channelMessages) {
			channelMessages.add(new Message(channelID, message));
			channelMessages.notify();
		}
	}

	public static void Send(String channelID, byte[] message) {
		getInstance().send(channelID, message);
	}
	
	private void addListener(String channelID, HttpServletResponse connection) throws IOException {
		synchronized (connectionMap) {
			HttpServletResponse oldConnection = connectionMap.get(channelID);
            if (oldConnection != null) {
            	// If there is already someone listening, stop that and replace it
            	// with the new one.
            	oldConnection.getWriter().flush();
            	oldConnection.getWriter().close();
            }
            connectionMap.put(channelID, connection);
		}
		synchronized (channelMessages) {
			// A new listener has been added, maybe there are messages for it, so
			// wake up the thread sending out messages.
			channelMessages.notify();
		}
	}
	
	public static void AddListener(String channelID, HttpServletResponse connection) throws IOException {
		getInstance().addListener(channelID, connection);
	}
	
	private void removeListener(String channelID) {
		synchronized (connectionMap) {
			connectionMap.remove(channelID);
		}
	}
	public static void RemoveListener(String channelID) {
		getInstance().removeListener(channelID);
	}
	
	
    
    public class Message {
    	String channelID;
    	byte[] message;
    	Message(String channelID, byte[] message) {
    		this.channelID = channelID;
    		this.message = message;
    	}
    }
}
