package com.github.reload.net.encoders.content;

import io.netty.buffer.ByteBuf;
import com.github.reload.Configuration;
import com.github.reload.net.encoders.Codec;
import com.github.reload.net.encoders.Codec.ReloadCodec;
import com.github.reload.net.encoders.content.ProbeInformation.ProbeInformationCodec;
import com.github.reload.net.encoders.content.ProbeRequest.ProbeInformationType;

@ReloadCodec(ProbeInformationCodec.class)
public abstract class ProbeInformation {

	protected abstract ProbeInformationType getType();

	static class ProbeInformationCodec extends Codec<ProbeInformation> {

		private static final int INFORMATION_LENGTH_FIELD = U_INT8;

		public ProbeInformationCodec(Configuration conf) {
			super(conf);
		}

		@Override
		public void encode(ProbeInformation obj, ByteBuf buf, Object... params) throws CodecException {
			ProbeInformationType type = obj.getType();

			buf.writeByte(type.getCode());
			Field lenFld = allocateField(buf, INFORMATION_LENGTH_FIELD);

			@SuppressWarnings("unchecked")
			Codec<ProbeInformation> codec = (Codec<ProbeInformation>) getCodec(type.getInfoClass());
			codec.encode(obj, buf);

			lenFld.updateDataLength();
		}

		@Override
		public ProbeInformation decode(ByteBuf buf, Object... params) throws CodecException {
			byte typeV = buf.readByte();
			ProbeInformationType type = ProbeInformationType.valueOf(typeV);

			if (type == null)
				throw new CodecException("Unknown probe information type " + typeV);

			ByteBuf infoData = readField(buf, INFORMATION_LENGTH_FIELD);

			@SuppressWarnings("unchecked")
			Codec<ProbeInformation> codec = (Codec<ProbeInformation>) getCodec(type.getInfoClass());

			return codec.decode(infoData);
		}

	}
}