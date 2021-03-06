package com.github.reload.net.codecs.content;

import io.netty.buffer.ByteBuf;
import dagger.ObjectGraph;
import com.github.reload.net.codecs.Codec;
import com.github.reload.net.codecs.Codec.ReloadCodec;
import com.github.reload.net.codecs.content.JoinRequest.JoinRequestCodec;
import com.github.reload.net.codecs.header.NodeID;

@ReloadCodec(JoinRequestCodec.class)
public class JoinRequest extends Content {

	private final NodeID joiningNode;
	private final byte[] overlayData;

	public JoinRequest(NodeID joiningNode, byte[] overlayData) {
		this.joiningNode = joiningNode;
		this.overlayData = overlayData;
	}

	public NodeID getJoiningNode() {
		return joiningNode;
	}

	public byte[] getOverlayData() {
		return overlayData;
	}

	@Override
	public ContentType getType() {
		return ContentType.JOIN_REQ;
	}

	static class JoinRequestCodec extends Codec<JoinRequest> {

		private static final int DATA_LENGTH_FIELD = U_INT16;

		private final Codec<NodeID> nodeCodec;

		public JoinRequestCodec(ObjectGraph ctx) {
			super(ctx);
			nodeCodec = getCodec(NodeID.class);
		}

		@Override
		public void encode(JoinRequest obj, ByteBuf buf, Object... params) throws com.github.reload.net.codecs.Codec.CodecException {
			nodeCodec.encode(obj.joiningNode, buf);

			Field lenFld = allocateField(buf, DATA_LENGTH_FIELD);
			buf.writeBytes(obj.overlayData);
			lenFld.updateDataLength();
		}

		@Override
		public JoinRequest decode(ByteBuf buf, Object... params) throws com.github.reload.net.codecs.Codec.CodecException {
			NodeID joiningNode = nodeCodec.decode(buf);

			ByteBuf overlayData = readField(buf, DATA_LENGTH_FIELD);
			byte[] data = new byte[overlayData.readableBytes()];
			overlayData.readBytes(data);
			overlayData.release();

			return new JoinRequest(joiningNode, data);
		}

	}

}
