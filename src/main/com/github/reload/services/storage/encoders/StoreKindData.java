package com.github.reload.services.storage.encoders;

import io.netty.buffer.ByteBuf;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import com.github.reload.components.ComponentsContext;
import com.github.reload.net.encoders.Codec;
import com.github.reload.net.encoders.Codec.ReloadCodec;
import com.github.reload.services.storage.DataKind;
import com.github.reload.services.storage.encoders.StoreKindData.StoreKindDataCodec;

@ReloadCodec(StoreKindDataCodec.class)
public class StoreKindData {

	protected final DataKind kind;

	private final BigInteger generationCounter;

	private final List<StoredData> data;

	public StoreKindData(DataKind kind, BigInteger generationCounter, List<StoredData> data) {
		this.kind = kind;
		this.generationCounter = generationCounter;
		this.data = data;
	}

	public DataKind getKind() {
		return kind;
	}

	public BigInteger getGeneration() {
		return generationCounter;
	}

	public List<StoredData> getValues() {
		return data;
	}

	static class StoreKindDataCodec extends Codec<StoreKindData> {

		protected static final int GEN_COUNTER_FIELD = U_INT64;
		protected static final int VALUES_LENGTH_FIELD = U_INT32;

		private final Codec<DataKind> kindCodec;
		private final Codec<StoredData> dataCodec;

		public StoreKindDataCodec(ComponentsContext ctx) {
			super(ctx);
			kindCodec = getCodec(DataKind.class);
			dataCodec = getCodec(StoredData.class);
		}

		@Override
		public void encode(StoreKindData obj, ByteBuf buf, Object... params) throws com.github.reload.net.encoders.Codec.CodecException {
			kindCodec.encode(obj.kind, buf);

			byte[] genCounterBytes = toUnsigned(obj.generationCounter);
			// Make sure generation counter field is always of the fixed size by
			// padding with zeros
			buf.writeZero(GEN_COUNTER_FIELD - genCounterBytes.length);

			buf.writeBytes(genCounterBytes);

			Field lenFld = allocateField(buf, VALUES_LENGTH_FIELD);

			for (StoredData d : obj.data) {
				dataCodec.encode(d, buf);
			}

			lenFld.updateDataLength();
		}

		@Override
		public StoreKindData decode(ByteBuf buf, Object... params) throws com.github.reload.net.encoders.Codec.CodecException {
			DataKind kind = kindCodec.decode(buf);

			byte[] genCounterData = new byte[GEN_COUNTER_FIELD];
			buf.readBytes(genCounterData);
			BigInteger generationCounter = new BigInteger(1, genCounterData);

			List<StoredData> data = decodeStoredDataList(kind, buf);
			return new StoreKindData(kind, generationCounter, data);
		}

		private List<StoredData> decodeStoredDataList(DataKind kind, ByteBuf buf) throws com.github.reload.net.encoders.Codec.CodecException {
			ByteBuf dataFld = readField(buf, VALUES_LENGTH_FIELD);

			List<StoredData> data = new ArrayList<StoredData>();

			while (dataFld.readableBytes() > 0) {
				data.add(dataCodec.decode(dataFld, kind.getDataModel()));
			}

			return data;
		}

	}
}
