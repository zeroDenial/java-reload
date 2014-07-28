package com.github.reload.storage;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.github.reload.crypto.CryptoHelper;
import com.github.reload.net.encoders.content.storage.FetchKindResponse;
import com.github.reload.net.encoders.content.storage.StatKindResponse;
import com.github.reload.net.encoders.content.storage.StoreAnswer;
import com.github.reload.net.encoders.content.storage.StoreKindData;
import com.github.reload.net.encoders.content.storage.StoreKindResponse;
import com.github.reload.net.encoders.content.storage.StoredData;
import com.github.reload.net.encoders.content.storage.StoredDataSpecifier;
import com.github.reload.net.encoders.content.storage.StoredMetadata;
import com.github.reload.net.encoders.header.NodeID;
import com.github.reload.net.encoders.header.ResourceID;
import com.github.reload.routing.TopologyPlugin;
import com.github.reload.storage.errors.GenerationTooLowException;
import com.google.common.collect.Maps;

/**
 * The map of the data stored locally. It stores the data which the peer is
 * responsible.
 * 
 */
public class LocalStore {

	private final TopologyPlugin plugin;

	private final Map<KindKey, StoreKindData> storedResources = Maps.newConcurrentMap();

	public LocalStore(TopologyPlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Perform controls on data and stores it
	 * 
	 * @throws GenerationTooLowException
	 * 
	 * @throws StorageException
	 * 
	 */
	public List<StoreKindResponse> store(ResourceID resourceId, List<StoreKindData> data, PublicKey storerKey) throws GeneralSecurityException, GenerationTooLowException {
		List<StoreKindResponse> response = new ArrayList<StoreKindResponse>();
		List<StoreKindResponse> generTooLowResponses = new ArrayList<StoreKindResponse>();

		for (StoreKindData receivedData : data) {

			// Verify data signature
			for (StoredData d : receivedData.getValues())
				d.verify(storerKey, resourceId, receivedData.getKind());

			KindKey key = new KindKey(resourceId, receivedData.getKind().getKindId());

			StoreKindData oldStoredKind = storedResources.put(key, receivedData);

			BigInteger oldGeneration;

			if (oldStoredKind == null)
				oldGeneration = BigInteger.ZERO;
			else
				oldGeneration = oldStoredKind.getGeneration();

			if (receivedData.getGeneration().compareTo(oldGeneration) <= 0) {
				// Restore old value
				storedResources.put(key, oldStoredKind);

				generTooLowResponses.add(new StoreKindResponse(receivedData.getKind(), oldGeneration, new ArrayList<NodeID>()));
			}

			List<NodeID> replicaIds = plugin.onReplicateData(resourceId, receivedData);

			response.add(new StoreKindResponse(receivedData.getKind(), receivedData.getGeneration(), replicaIds));
		}

		if (generTooLowResponses.size() > 0)
			throw new GenerationTooLowException(new StoreAnswer(generTooLowResponses));

		// TODO: store cleanup

		return response;
	}

	public Map<KindKey, StoreKindData> getStoredResources() {
		return Collections.unmodifiableMap(storedResources);
	}

	public void removeResource(ResourceID resourceId) {
		storedResources.remove(resourceId);
	}

	public int getSize() {
		return storedResources.size();
	}

	@SuppressWarnings("unchecked")
	public List<FetchKindResponse> fetch(ResourceID resourceId, List<StoredDataSpecifier> specifiers) {
		List<FetchKindResponse> out = new ArrayList<FetchKindResponse>();

		for (StoredDataSpecifier spec : specifiers) {
			KindKey key = new KindKey(resourceId, spec.getKind().getKindId());

			StoreKindData kindData = storedResources.get(key);

			if (kindData == null)
				continue;

			// If the generation in the request corresponds to the last
			// value we don't need to resend the same content
			if (kindData.getGeneration().equals(spec.getGeneration()))
				continue;

			List<StoredData> matchingData = new ArrayList<StoredData>();

			for (StoredData d : kindData.getValues()) {
				if (spec.getModelSpecifier().isMatching(d.getValue()))
					matchingData.add(d);
			}

			if (matchingData.isEmpty())
				matchingData.add(DataUtils.getNonExistentData(spec.getKind()));

			out.add(new FetchKindResponse(spec.getKind(), kindData.getGeneration(), matchingData));
		}

		return out;
	}

	@SuppressWarnings("unchecked")
	public List<StatKindResponse> stat(ResourceID resourceId, List<StoredDataSpecifier> specifiers) {
		List<StatKindResponse> out = new ArrayList<StatKindResponse>();

		for (StoredDataSpecifier spec : specifiers) {
			KindKey key = new KindKey(resourceId, spec.getKind().getKindId());

			StoreKindData kindData = storedResources.get(key);

			if (kindData == null)
				continue;

			// If the generation in the request corresponds to the last
			// value we don't need to resend the same content
			if (kindData.getGeneration().equals(spec.getGeneration()))
				continue;

			List<StoredMetadata> matchingData = new ArrayList<StoredMetadata>();

			for (StoredData d : kindData.getValues()) {
				if (spec.getModelSpecifier().isMatching(d.getValue()))
					matchingData.add(d.getMetadata(spec.getKind(), CryptoHelper.OVERLAY_HASHALG));
			}

			out.add(new StatKindResponse(spec.getKind(), kindData.getGeneration(), matchingData));
		}

		return out;
	}

	public static class KindKey {

		public final ResourceID resId;
		public final long kindId;

		public KindKey(ResourceID resId, long kindId) {
			this.resId = resId;
			this.kindId = kindId;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (kindId ^ (kindId >>> 32));
			result = prime * result + ((resId == null) ? 0 : resId.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			KindKey other = (KindKey) obj;
			if (kindId != other.kindId)
				return false;
			if (resId == null) {
				if (other.resId != null)
					return false;
			} else if (!resId.equals(other.resId))
				return false;
			return true;
		}
	}
}