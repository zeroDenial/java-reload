package com.github.reload.net.data;

import io.netty.util.AttributeKey;
import java.util.Map;
import com.github.reload.message.Content;
import com.github.reload.message.Header;
import com.github.reload.message.NodeID;
import com.github.reload.message.SecurityBlock;
import com.google.common.collect.Maps;

/**
 * A RELOAD message
 */
public class Message {

	private final Map<AttributeKey<?>, Object> attributes = Maps.newHashMap();

	public static final AttributeKey<NodeID> PREVIOUS_HOP = AttributeKey.valueOf("PREV_HOP");

	Header header;
	Content content;
	SecurityBlock secBlock;

	public Message(Header header, Content content, SecurityBlock secBlock) {
		this.header = header;
		this.content = content;
		this.secBlock = secBlock;
	}

	public Header getHeader() {
		return header;
	}

	public Content getContent() {
		return content;
	}

	public SecurityBlock getSecBlock() {
		return secBlock;
	}

	@SuppressWarnings("unchecked")
	public <T> T setAttribute(AttributeKey<T> key, T value) {
		return (T) attributes.put(key, value);
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttribute(AttributeKey<T> key) {
		Object a = attributes.get(key);
		if (a != null)
			return (T) a;
		return null;
	}
}