package com.github.reload.net.encoders;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import org.apache.log4j.Logger;
import com.github.reload.conf.Configuration;
import com.github.reload.net.encoders.content.Content;
import com.github.reload.net.encoders.header.Header;
import com.github.reload.net.encoders.secBlock.SecurityBlock;

/**
 * Codec for message payload (content + security block)
 */
public class MessagePayloadDecoder extends MessageToMessageDecoder<ForwardMessage> {

	private final Codec<Content> contentCodec;
	private final Codec<SecurityBlock> secBlockCodec;

	public MessagePayloadDecoder(Configuration conf) {
		contentCodec = Codec.getCodec(Content.class, conf);
		secBlockCodec = Codec.getCodec(SecurityBlock.class, conf);
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ForwardMessage msg, List<Object> out) throws Exception {
		Header header = msg.getHeader();
		ByteBuf payload = msg.getPayload();
		try {
			Content content = contentCodec.decode(payload);
			SecurityBlock secBlock = secBlockCodec.decode(payload);
			out.add(new Message(header, content, secBlock));
			Logger.getRootLogger().trace(String.format("Message payload %#x decoded", header.getTransactionId()));
		} finally {
			payload.release();
		}
	}
}