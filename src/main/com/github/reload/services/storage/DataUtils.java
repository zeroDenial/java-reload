package com.github.reload.services.storage;

import java.math.BigInteger;
import com.github.reload.components.ComponentsContext;
import com.github.reload.net.encoders.content.Error;
import com.github.reload.net.encoders.content.Error.ErrorMessageException;
import com.github.reload.net.encoders.content.Error.ErrorType;
import com.github.reload.net.encoders.header.ResourceID;
import com.github.reload.net.encoders.secBlock.Signature;
import com.github.reload.services.storage.AccessPolicy.AccessPolicyException;
import com.github.reload.services.storage.DataModel.DataValueBuilder;
import com.github.reload.services.storage.encoders.StoredData;

public class DataUtils {

	/**
	 * Check if the stored data object is valid for the specified data kind
	 * 
	 * @throws AccessPolicyException
	 * @throws DataTooLargeException
	 */
	static void performKindChecks(ResourceID resourceId, StoredData requestData, DataKind kind, ComponentsContext ctx) throws ErrorMessageException {
		kind.getAccessPolicy().accept(resourceId, requestData, requestData.getSignature().getIdentity(), ctx);

		if (requestData.getValue().getSize() > kind.getAttribute(DataKind.MAX_SIZE))
			throw new ErrorMessageException(new Error(ErrorType.DATA_TOO_LARGE, "Size of the data exceeds the maximum allowed size"));
	}

	/**
	 * Syntetic data value used to indicate non existent data in particular
	 * situations
	 */
	static StoredData getNonExistentData(DataKind kind) {
		DataValueBuilder<?> svb = kind.getDataModel().newValueBuilder();
		return new StoredData(BigInteger.ZERO, 0, svb.build(), Signature.EMPTY_SIGNATURE);
	}
}