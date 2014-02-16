package com.github.reload.storage;

import java.math.BigInteger;
import java.util.List;

/**
 * A response contained in a stat answer, contains all the data for a specific
 * kind that matches the request specifier
 * 
 * @author Daniel Zozin <zdenial@gmx.com>
 * 
 */
public class StatResponse extends DataResponse<StoredMetadata> {

	public StatResponse(DataKind kind, BigInteger generation, List<StoredMetadata> values) {
		super(kind, generation, values);
	}

	public StatResponse(Context context, UnsignedByteBuffer buf) throws UnknownKindException {
		super(context, buf);
	}

	@Override
	protected StoredMetadata decodeData(DataKind kind, UnsignedByteBuffer buf) {
		return new StoredMetadata(kind, buf);
	}

}
