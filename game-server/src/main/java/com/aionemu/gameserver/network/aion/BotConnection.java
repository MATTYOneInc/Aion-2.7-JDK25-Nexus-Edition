package com.aionemu.gameserver.network.aion;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.slf4j.LoggerFactory;

import com.aionemu.commons.network.AConnection;

/**
 * Headless no-op connection used by AI-controlled bot players.
 *
 * <p>
 * It satisfies {@code Player.getClientConnection() != null} (so {@code isOnline() == true} and
 * account lookups such as {@code getAccount().getMembership()} work), but is fully write-disabled:
 * every outgoing packet is dropped instead of touching the real network. This avoids NPEs inside
 * {@link AionConnection}'s network plumbing (which expects a live, registered SelectionKey /
 * Dispatcher) without spinning up a real socket pump.
 * </p>
 *
 * <p>
 * The connection is marked write-disabled by reflecting the private {@code closed} field of
 * {@link AConnection} to {@code true}. With {@code isWriteDisabled() == true}, {@code sendPacket()}
 * short-circuits before reaching {@code enableWriteInterest()} (which needs a live key), and
 * {@code close()} also short-circuits before dereferencing the (absent) Dispatcher.
 * </p>
 */
public class BotConnection extends AionConnection {

	public BotConnection() throws IOException {
		super(createLoopbackChannel(), null);
		setWriteDisabled();
	}

	private static SocketChannel createLoopbackChannel() throws IOException {
		ServerSocketChannel ssc = ServerSocketChannel.open();
		ssc.socket().bind(new InetSocketAddress("127.0.0.1", 0));
		SocketChannel client = SocketChannel.open();
		client.connect(ssc.getLocalAddress());
		ssc.close();
		return client;
	}

	private void setWriteDisabled() {
		try {
			Field closed = AConnection.class.getDeclaredField("closed");
			closed.setAccessible(true);
			closed.set(this, true);
		} catch (Exception e) {
			LoggerFactory.getLogger(BotConnection.class)
				.warn("BotConnection: could not mark write-disabled, packets may error: " + e.getMessage());
		}
	}

	/** Bots are never closed through the network; ignore close requests. */
	@Override
	public void closeNow() {
		// no-op
	}
}
