package com.github.reload.net.stack;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import com.github.reload.net.encoders.FramedMessage;
import com.github.reload.net.encoders.FramedMessage.FramedAck;
import com.github.reload.net.encoders.FramedMessage.FramedData;

/**
 * Subclasses will implement a specific link layer protocol to control the link
 */
public abstract class LinkHandler extends ChannelDuplexHandler {

	private final Logger l = Logger.getRootLogger();

	private ChannelHandlerContext ctx;

	private final Map<Long, Transmission> transmissions = new LinkedHashMap<Long, Transmission>();

	// TODO: handle link reliability notications to the caller

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		this.ctx = ctx;
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		this.ctx = null;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		FramedMessage frame = (FramedMessage) msg;
		switch (frame.getType()) {
			case DATA :
				handleData((FramedData) frame);
				l.trace("Passing DATA frame " + frame.getSequence() + " to upper layer...");
				ctx.fireChannelRead(((FramedData) frame).getPayload());
				break;
			case ACK :
				l.trace("Received ACK for frame " + frame.getSequence());
				Transmission t = transmissions.remove(frame.getSequence());
				if (t != null) {
					handleAck((FramedAck) frame, t);
				} else {
					l.trace("Unexpected ACK message on " + ctx);
				}
			default :
				assert false;
				break;
		}
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		FramedData data = getDataFrame((ByteBuf) msg);
		Transmission t = new Transmission();
		t.promise = promise;
		transmissions.put(data.getSequence(), t);
		l.trace("Passing DATA frame " + data.getSequence() + " to lower layer...");
		ctx.write(data);
		// TODO: detect transmission timeout
	}

	/**
	 * Send acknowledgment message immediately
	 * 
	 * @param ack
	 * @return
	 */
	protected void sendAckFrame(FramedAck ack) {
		l.trace("Passing ACK frame " + ack.getSequence() + " to lower layer...");
		ctx.writeAndFlush(ack);
	}

	protected abstract void handleData(FramedData data);

	protected abstract void handleAck(FramedAck ack, Transmission request);

	/**
	 * Called when the upper layer want to send a message on the link
	 * 
	 * @param payload
	 * @return
	 */
	protected abstract FramedData getDataFrame(ByteBuf payload);

	/**
	 * @return the link timeout in milliseconds before a message should me
	 *         considerated unacked, this value can be calculated dynamically by
	 *         using the RoundTripTime value passed when an ACK for a message is
	 *         received.
	 */
	protected abstract long getLinkTimeout();

	class Transmission {

		public long startTime = System.currentTimeMillis();
		public ChannelPromise promise;

		public int getRTT() {
			return (int) (System.currentTimeMillis() - startTime);
		}
	}
}