package org.irmacard.cardproxywebrelay;

import java.util.Date;

/**
 * Keeps track of the connection status of a newly created channel. This is used
 * to notify the other side that the first side is no longer actively
 * participating in the channel. Also it is used to clean up stray messages that
 * have a very low likelihood of every being delivered.
 * 
 * @author Wouter Lueks <lueks@cs.ru.nl>
 */
public class ChannelStatus {
	public enum ChannelState {
		CREATED,
		FIRST_CONNECTED,
		BOTH_CONNECTED,
		FIRST_LOST,
		BOTH_LOST,
	}

	public enum ConnectionState {
		WAITING,
		CONNECTED,
		TIMEOUT,
	}

	/**
	 * Connection timeout in milliseconds. A party has to reconnect to the channel
	 * within this interval to still be considered alive.
	 */
	public static final long TIMEOUT = 60000l;

	/**
	 * Time in ms after which connection can be garbage collected.
	 */
	public static final long DEAD_TIME = 3 * 60 * 1000;

	private ChannelState channelState;
	private ConnectionState sideAState;
	private ConnectionState sideBState;

	private Date lastSeenSideA;
	private Date lastSeenSideB;

	/**
	 * Is set when timeout notification has been queued.
	 */
	private boolean notified = false;

	private String channelID;

	public ChannelStatus(String channelID) {
		channelState = ChannelState.CREATED;
		sideAState = ConnectionState.WAITING;
		sideBState = ConnectionState.WAITING;

		lastSeenSideA = null;
		lastSeenSideB = null;

		this.channelID = channelID;
	}

	/**
	 * Should be called at regular intervals to update the connection state.
	 * Will only check for timeouts, connections are handled by calling
	 * activity(String side).
	 */
	public void tick() {
		Date now = new Date();

		if(sideAState == ConnectionState.CONNECTED && now.getTime() - lastSeenSideA.getTime() > TIMEOUT) {
				sideAState = ConnectionState.TIMEOUT;
				actionSideTimeout();
		}

		if(sideBState == ConnectionState.CONNECTED && now.getTime() - lastSeenSideB.getTime() > TIMEOUT) {
			sideBState = ConnectionState.TIMEOUT;
			actionSideTimeout();
		}	
	}

	public void activity(String side) {
		if(side.equals(MessageSender.SIDE_A)) {
			lastSeenSideA = new Date();

			if(sideAState == ConnectionState.WAITING) {
				sideAState = ConnectionState.CONNECTED;
				actionSideConnected();
			}	
		} else if(side.equals(MessageSender.SIDE_B)) {
			lastSeenSideB = new Date();

			if(sideBState == ConnectionState.WAITING) {
				sideBState = ConnectionState.CONNECTED;
				actionSideConnected();
			}
		}
	}

	private void actionSideConnected() {
		switch(channelState) {
		case CREATED:
			channelState = ChannelState.FIRST_CONNECTED;
			break;
		case FIRST_CONNECTED:
			channelState = ChannelState.BOTH_CONNECTED;
			break;
		default:
			System.out.println("Illegal transition: actionSideConnected");
		}
	}

	private void actionSideTimeout() {
		switch(channelState) {
		case FIRST_CONNECTED:
			// You cannot timeout before being connected, so the connected side timesout
			channelState = ChannelState.BOTH_LOST;
			break;
		case BOTH_CONNECTED:
			channelState = ChannelState.FIRST_LOST;
			break;
		case FIRST_LOST:
			channelState = ChannelState.BOTH_LOST;
			break;
		default:
			System.out.println("Illegal transition: actionSideConnected");
		}
	}

	public boolean dead() {
		return channelState == ChannelState.BOTH_LOST
				&& (lastSeenSideA == null || new Date().getTime()
						- lastSeenSideA.getTime() > DEAD_TIME)
				&& (lastSeenSideB == null || new Date().getTime()
						- lastSeenSideB.getTime() > DEAD_TIME);
	}

	public void setNotified() {
		notified = true;
	}

	private boolean isNotified() {
		return notified;
	}

	public boolean shouldBeNotified() {
		return !isNotified()
				&& (channelState == ChannelState.FIRST_LOST
				    || channelState == ChannelState.BOTH_LOST);
	}

	public String toString() {
		String state = "";

		switch(channelState) {
		case CREATED:
			state = "created";
			break;
		case FIRST_CONNECTED:
			state = "first connected";
			break;
		case BOTH_CONNECTED:
			state = "both connected";
			break;
		case FIRST_LOST:
			state = "first lost";
			break;
		case BOTH_LOST:
			state = "both lost";
			break;
		}
		
		long sideAInactive, sideBInactive;

		if(lastSeenSideA != null)
			sideAInactive = (new Date().getTime() - lastSeenSideA.getTime()) / 1000;
		else
			sideAInactive = -1;

		if(lastSeenSideB != null)
			sideBInactive = (new Date().getTime() - lastSeenSideB.getTime()) / 1000;
		else
			sideBInactive = -1;

		return "Channel " + channelID + ": " + state + " (side A inactive: "
				+ sideAInactive + " s, side B inactive: " + sideBInactive
				+ " s)";
	}

	public String getChannelID() {
		return channelID;
	}
}
