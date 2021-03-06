package com.github.reload.net.codecs.content;

import io.netty.buffer.ByteBuf;
import dagger.ObjectGraph;
import com.github.reload.net.codecs.Codec;
import com.github.reload.net.codecs.Codec.ReloadCodec;
import com.github.reload.net.codecs.content.PingRequest.PingRequestCodec;
import com.google.common.base.Objects;

@ReloadCodec(PingRequestCodec.class)
public class PingRequest extends Content {

	private final int payloadLength;

	public PingRequest() {
		payloadLength = 0;
	}

	public PingRequest(int payloadLength) {
		this.payloadLength = payloadLength;
	}

	public int getPayloadLength() {
		return payloadLength;
	}

	@Override
	public ContentType getType() {
		return ContentType.PING_REQ;
	}

	static class PingRequestCodec extends Codec<PingRequest> {

		private static final int PADDING_LENGTH_FIELD = U_INT16;

		public PingRequestCodec(ObjectGraph ctx) {
			super(ctx);
		}

		@Override
		public void encode(PingRequest obj, ByteBuf buf, Object... params) throws com.github.reload.net.codecs.Codec.CodecException {
			Field lenFld = allocateField(buf, PADDING_LENGTH_FIELD);
			buf.writerIndex(buf.writerIndex() + obj.payloadLength);
			lenFld.updateDataLength();
		}

		@Override
		public PingRequest decode(ByteBuf buf, Object... params) throws com.github.reload.net.codecs.Codec.CodecException {
			ByteBuf padding = readField(buf, PADDING_LENGTH_FIELD);
			PingRequest req = new PingRequest(padding.readableBytes());
			padding.release();
			return req;
		}

	}

	@Override
	public String toString() {
		return Objects.toStringHelper(this).add("super", super.toString()).add("payloadLength", payloadLength).toString();
	}

}
