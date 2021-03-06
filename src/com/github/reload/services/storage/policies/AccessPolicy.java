package com.github.reload.services.storage.policies;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.github.reload.net.codecs.content.Error.ErrorMessageException;
import com.github.reload.net.codecs.content.Error.ErrorType;
import com.github.reload.net.codecs.header.ResourceID;
import com.github.reload.net.codecs.secBlock.SignerIdentity;
import com.github.reload.services.storage.DataKind;
import com.github.reload.services.storage.local.StoredData;

/**
 * An access control policy used by data kinds that determines if a store
 * request should be accepted
 * 
 */
public abstract class AccessPolicy {

	private static final Map<String, AccessPolicy> policies = new HashMap<String, AccessPolicy>();

	public static final Class<NodeMatch> NODE = NodeMatch.class;
	public static final Class<UserMatch> USER = UserMatch.class;

	protected AccessPolicy() {
	}

	public static <T extends AccessPolicy> T getInstance(Class<T> clazz) {

		String name = getPolicyName(clazz);

		@SuppressWarnings("unchecked")
		T policy = (T) policies.get(name);

		if (policy == null) {
			try {
				policy = clazz.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			policies.put(name, policy);
		}

		return policy;
	}

	private static String getPolicyName(Class<? extends AccessPolicy> clazz) {
		return clazz.getAnnotation(PolicyName.class).value().toLowerCase();
	}

	public Class<? extends ResourceIDGenerator> getParamGenerator() {
		return this.getClass().getAnnotation(PolicyName.class).paramGen();
	}

	public static Map<String, AccessPolicy> getSupportedPolicies() {
		return Collections.unmodifiableMap(policies);
	}

	public String getName() {
		return getPolicyName(this.getClass());
	}

	/**
	 * Check if the store should be accepted
	 * 
	 * @throws AccessPolicyException
	 *             if the policy check fails
	 */
	public abstract void accept(ResourceID resourceId, DataKind kind, StoredData data, SignerIdentity signerIdentity) throws AccessPolicyException;

	/**
	 * Throw an exception if the given datakind builder doesn't
	 * contain the policy parameters required by this access control policy
	 * 
	 * @param dataKindBuilder
	 */
	protected void checkKindParams(DataKind.Builder dataKindBuilder) {
		// No parameter required by default
	}

	@Override
	public String toString() {
		return getName();
	}

	/**
	 * Indicates that the access control policy check fails
	 * 
	 */
	public static class AccessPolicyException extends ErrorMessageException {

		public AccessPolicyException(String message) {
			super(ErrorType.FORBITTEN, "Access Policy check failed: " + message);
		}

	}

	/**
	 * Generate the parameters in conformity to an access control policy
	 * 
	 */
	public interface ResourceIDGenerator {

	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface PolicyName {

		public String value();

		public Class<? extends ResourceIDGenerator> paramGen();
	}
}
