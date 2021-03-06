package com.github.reload.services.storage.policies;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.inject.Inject;
import com.github.reload.crypto.CryptoHelper;
import com.github.reload.crypto.Keystore;
import com.github.reload.crypto.ReloadCertificate;
import com.github.reload.net.codecs.header.NodeID;
import com.github.reload.net.codecs.header.ResourceID;
import com.github.reload.net.codecs.secBlock.CertHashNodeIdSignerIdentityValue;
import com.github.reload.net.codecs.secBlock.HashAlgorithm;
import com.github.reload.net.codecs.secBlock.SignerIdentity;
import com.github.reload.net.codecs.secBlock.SignerIdentityValue;
import com.github.reload.net.codecs.secBlock.SignerIdentity.IdentityType;
import com.github.reload.routing.TopologyPlugin;
import com.github.reload.services.storage.DataKind;
import com.github.reload.services.storage.local.StoredData;
import com.github.reload.services.storage.policies.AccessPolicy.PolicyName;
import com.github.reload.services.storage.policies.NodeMatch.NodeRIDGenerator;
import com.google.common.base.Optional;

/**
 * Check if the nodeid hash in the sender certificate matches the resource id
 * 
 */
@PolicyName(value = "node-match", paramGen = NodeRIDGenerator.class)
public class NodeMatch extends AccessPolicy {

	@Inject
	TopologyPlugin topology;

	@Inject
	Keystore keystore;

	@Override
	public void accept(ResourceID resourceId, DataKind kind, StoredData data, SignerIdentity signerIdentity) throws AccessPolicyException {
		if (signerIdentity.getIdentityType() != IdentityType.CERT_HASH_NODE_ID)
			throw new AccessPolicyException("Wrong signer identity type");

		validate(resourceId, signerIdentity);
	}

	private void validate(ResourceID resourceId, SignerIdentity storerIdentity) throws AccessPolicyException {
		Optional<ReloadCertificate> storerReloadCert = keystore.getCertificate(storerIdentity);
		if (!storerReloadCert.isPresent())
			throw new AccessPolicyException("Unknown signer identity");

		NodeID storerNodeId = storerReloadCert.get().getNodeId();

		byte[] resourceIdHash = resourceId.getData();

		X509Certificate storerCert = (X509Certificate) storerReloadCert.get().getOriginalCertificate();
		byte[] nodeIdHash = hashNodeId(CryptoHelper.OVERLAY_HASHALG, storerNodeId, topology);
		if (Arrays.equals(nodeIdHash, resourceIdHash)) {
			checkIdentityHash(storerCert, storerNodeId, storerIdentity);
			return;
		}

		throw new AccessPolicyException("Signer node-id not matching with resource-id");
	}

	private static byte[] hashNodeId(HashAlgorithm hashAlg, NodeID storerId, TopologyPlugin plugin) {
		int length = plugin.getResourceIdLength();
		try {
			MessageDigest d = MessageDigest.getInstance(hashAlg.toString());
			return Arrays.copyOfRange(d.digest(storerId.getData()), 0, length);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private static void checkIdentityHash(Certificate storerCert, NodeID storerId, SignerIdentity storerIdentity) throws AccessPolicyException {
		SignerIdentityValue storerIdentityValue = storerIdentity.getSignerIdentityValue();

		byte[] computedIdentityValue = CertHashNodeIdSignerIdentityValue.computeHash(storerIdentityValue.getHashAlgorithm(), storerCert, storerId);

		if (!Arrays.equals(storerIdentityValue.getHashValue(), computedIdentityValue))
			throw new AccessPolicyException("Identity hash value mismatch");
	}

	/**
	 * Parameters generator for NODE-MATCH policy
	 * 
	 */
	public static class NodeRIDGenerator implements ResourceIDGenerator {

		@Inject
		TopologyPlugin topology;

		public ResourceID getResourceId(NodeID storerId) {
			return topology.getResourceId(hashNodeId(CryptoHelper.OVERLAY_HASHALG, storerId, topology));
		}
	}
}
