package com.github.reload;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import com.github.reload.message.Content;
import com.github.reload.message.GenericCertificate;
import com.github.reload.message.GenericCertificate.CertificateType;
import com.github.reload.message.HashAlgorithm;
import com.github.reload.message.Header;
import com.github.reload.message.ResourceID;
import com.github.reload.message.SecurityBlock;
import com.github.reload.message.Signature;
import com.github.reload.message.SignatureAlgorithm;
import com.github.reload.message.SignerIdentity;
import com.github.reload.message.content.AttachMessage;
import com.github.reload.message.content.NumResourcesProbeInformation;
import com.github.reload.message.content.ProbeAnswer;
import com.github.reload.message.content.ProbeRequest;
import com.github.reload.message.content.ProbeRequest.ProbeInformationType;
import com.github.reload.net.ChannelInitializerImpl;
import com.github.reload.net.MessageHandler;
import com.github.reload.net.data.Message;
import com.github.reload.storage.net.StoreAnswer;
import com.github.reload.storage.net.StoreRequest;

public class TestCodecs {

	protected Channel serverChannel;
	protected Channel clientChannel;
	protected Certificate testCert;

	private EventLoopGroup serverLoopGroup;
	private EventLoopGroup clientLoopGroup;

	@Before
	public void setUp() throws Exception {
		ServerBootstrap sb = new ServerBootstrap();
		serverLoopGroup = new NioEventLoopGroup();
		sb.group(serverLoopGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializerImpl(new MessageHandler())).childOption(ChannelOption.SO_KEEPALIVE, true);
		ChannelFuture f = sb.bind(6084);

		f.await();

		if (!f.isSuccess())
			throw new Exception(f.cause());

		serverChannel = f.channel();

		Bootstrap b = new Bootstrap();
		clientLoopGroup = new NioEventLoopGroup();
		b.group(clientLoopGroup).channel(NioSocketChannel.class).handler(new ChannelInitializerImpl(new MessageHandler()));
		ChannelFuture f2 = b.connect(new InetSocketAddress(6084));

		f2.await();

		if (!f2.isSuccess())
			throw new Exception(f2.cause());

		clientChannel = f2.channel();

		loadCert();
	}

	private void loadCert() throws Exception {
		String localCertPath = "CAcert.der";
		if (localCertPath == null || !new File(localCertPath).exists())
			throw new FileNotFoundException("Overlay certificate file not found at " + localCertPath);

		CertificateFactory certFactory;
		try {
			certFactory = CertificateFactory.getInstance("x.509");
			File overlayCertFile = new File(localCertPath);
			InputStream certStream = new FileInputStream(overlayCertFile);
			testCert = certFactory.generateCertificate(certStream);
			certStream.close();
		} catch (CertificateException e) {
			throw new CertificateException(e);
		} catch (IOException e) {
			// Ignored
		}

	}

	@After
	public void tearDown() throws Exception {
		clientLoopGroup.shutdownGracefully().await();
		serverLoopGroup.shutdownGracefully().await();
	}

	public void testMessage(Content content) throws Exception {
		setUp();
		Header tstHeader = new Header();
		GenericCertificate cert = new GenericCertificate(CertificateType.X509, testCert);

		SignerIdentity id = SignerIdentity.singleIdIdentity(HashAlgorithm.SHA1, testCert);
		Signature sign = new Signature(id, HashAlgorithm.SHA1, SignatureAlgorithm.RSA, new byte[0]);

		SecurityBlock secBlock = new SecurityBlock(Collections.singletonList(cert), sign);
		ChannelFuture f3 = clientChannel.writeAndFlush(new Message(tstHeader, content, secBlock));

		f3.await();

		// f3.await();
		//
		// if (!f3.isSuccess()) {
		// throw new Exception(f3.cause());
		// }
		//
		// assertTrue(f3.isSuccess());
		tearDown();
	}

	public static void main(String[] args) throws Exception {
		TestCodecs t = new TestCodecs();
		t.testMessage(new ProbeRequest(Collections.singletonList(ProbeInformationType.NUM_RESOUCES)));
		t.testMessage(new ProbeAnswer(Collections.singletonList(new NumResourcesProbeInformation(20))));
		t.testMessage(new AttachMessage.Builder().buildRequest());
		t.testMessage(new AttachMessage.Builder().buildAnswer());
		t.testMessage(new StoreRequest(ResourceID.valueOf("1d82a"), (short) 0, Collections.EMPTY_LIST));
		t.testMessage(new StoreAnswer(Collections.EMPTY_LIST));

	}
}