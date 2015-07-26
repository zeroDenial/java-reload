package com.github.reload;

import java.net.InetSocketAddress;
import java.util.Set;
import org.apache.log4j.Logger;
import com.github.reload.conf.Configuration;
import com.github.reload.net.AttachService;
import com.github.reload.net.NetModule;
import com.github.reload.net.NetworkException;
import com.github.reload.net.connections.Connection;
import com.github.reload.net.connections.ConnectionManager;
import com.github.reload.net.encoders.header.DestinationList;
import com.github.reload.net.encoders.header.NodeID;
import com.github.reload.net.encoders.header.ResourceID;
import com.github.reload.net.ice.HostCandidate.OverlayLinkType;
import com.github.reload.routing.TopologyPlugin;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * This helper class is used to connect and join to the overlay
 */
class OverlayConnector {

	private static final Logger l = Logger.getRootLogger();

	private Bootstrap bootstrap;
	private Overlay overlay;

	private Configuration conf;

	public OverlayConnector(Configuration conf) {
		Builder b = DaggerOverlayInitializer.builder();
		b.coreModule(new CoreModule(conf));
		b.netModule(new NetModule());

		OverlayInitializer initializer = b.build();

		bootstrap = initializer.getBootstrap();
		this.conf = initializer.getConfiguration();
		overlay = initializer.getOverlay();
	}

	/**
	 * Connect to a bootstrap server and then to the admitting peer
	 * 
	 * @return the amount of local nodeids successful joined
	 */
	final SettableFuture<Overlay> connectToOverlay(final boolean joinNeeded) {

		final SettableFuture<Overlay> overlayConnFut = SettableFuture.create();

		if (bootstrap.isOverlayInitiator()) {
			l.info(String.format("RELOAD overlay %s initialized by %s at %s.", conf.get(Configuration.OVERLAY_NAME), bootstrap.getLocalNodeId(), bootstrap.getLocalAddress()));
			overlayConnFut.set(overlay);
			return overlayConnFut;
		}

		ListenableFuture<Connection> bootConnFut = connectToBootstrap(conf.get(Configuration.BOOT_NODES), conf.get(Configuration.LINK_TYPES));

		Futures.addCallback(bootConnFut, new FutureCallback<Connection>() {

			@Override
			public void onSuccess(Connection neighbor) {
				attachToAP(neighbor.getNodeId(), overlayConnFut, joinNeeded);
			}

			@Override
			public void onFailure(Throwable t) {
				overlayConnFut.setException(t);
			}
		});

		return overlayConnFut;
	}

	private ListenableFuture<Connection> connectToBootstrap(final Set<InetSocketAddress> bootstrapNodes, Set<OverlayLinkType> linkTypes) {

		ConnectionManager connMgr = ctx.get(ConnectionManager.class);

		final SettableFuture<Connection> bootConnFut = SettableFuture.create();

		// Called by the first successful connection to a bootstrap node, other
		// successfully connection will be closed
		FutureCallback<Connection> connCB = new FutureCallback<Connection>() {

			int remainingServers = bootstrapNodes.size();

			@Override
			public void onSuccess(Connection result) {
				if (!bootConnFut.set(result)) {
					result.close();
				}
			}

			@Override
			public void onFailure(Throwable t) {
				if (remainingServers == 0) {
					bootConnFut.setException(new NetworkException("Cannot connect to any bootstrap server"));
				} else {
					remainingServers--;
				}
			}
		};

		for (InetSocketAddress node : bootstrapNodes) {
			for (OverlayLinkType linkType : linkTypes) {
				ListenableFuture<Connection> conn = connMgr.connectTo(node, linkType);
				Futures.addCallback(conn, connCB);
			}
		}

		return bootConnFut;
	}

	private void attachToAP(NodeID bootstrapServer, final SettableFuture<Overlay> overlayConnFut, final boolean joinNeeded) {
		final DestinationList dest = new DestinationList();

		// Pass request through bootstrap server
		dest.add(bootstrapServer);

		// ResourceId destination corresponding to local node-id to route the
		// attach request to the correct Admitting Peer
		dest.add(ResourceID.valueOf(bootstrap.getLocalNodeId().getData()));

		AttachService attachConnector = ctx.get(AttachService.class);

		ListenableFuture<Connection> apConnFut = attachConnector.attachTo(dest, true);

		Futures.addCallback(apConnFut, new FutureCallback<Connection>() {

			@Override
			public void onSuccess(Connection apConn) {
				if (joinNeeded) {
					ListenableFuture<NodeID> joinCB = ctx.get(TopologyPlugin.class).requestJoin();
					Futures.addCallback(joinCB, new FutureCallback<NodeID>() {

						@Override
						public void onSuccess(NodeID result) {
							overlayConnFut.set(overlay);
						}

						@Override
						public void onFailure(Throwable t) {
							overlayConnFut.setException(t);
						}
					});
				} else {
					overlayConnFut.set(overlay);
				}
			}

			@Override
			public void onFailure(Throwable t) {
				overlayConnFut.setException(t);
			}
		});

	}
}