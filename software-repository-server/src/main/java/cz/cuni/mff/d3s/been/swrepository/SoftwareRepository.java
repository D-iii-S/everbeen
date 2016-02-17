package cz.cuni.mff.d3s.been.swrepository;

import static cz.cuni.mff.d3s.been.swrepository.SWRepositoryServiceInfoConstants.*;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import cz.cuni.mff.d3s.been.util.SocketAddrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.cuni.mff.d3s.been.cluster.*;
import cz.cuni.mff.d3s.been.cluster.context.ClusterContext;
import cz.cuni.mff.d3s.been.core.service.ServiceInfo;
import cz.cuni.mff.d3s.been.core.service.ServiceState;
import cz.cuni.mff.d3s.been.datastore.SoftwareStore;
import cz.cuni.mff.d3s.been.swrepository.httpserver.HttpServer;

/**
 * A cluster node that can store and provide BPKs and Maven artifacts through a
 * simple HTTP server.
 * 
 * @author darklight
 */
public class SoftwareRepository implements ClusterService {

	private static final Logger log = LoggerFactory.getLogger(SoftwareRepository.class);
	private final String beenId;

	private HttpServer httpServer;
	private SoftwareStore softwareStore;
	private ServiceInfo info;
	private final ClusterContext clusterCtx;

	SoftwareRepository(ClusterContext clusterCtx, String beenId) {
		this.clusterCtx = clusterCtx;
		this.beenId = beenId;
	}

	/**
	 * Initialize the repository. HTTP server and data store must be set.
	 */
	public void init() {
		BpkRequestHandler bpkRequestHandler = new BpkRequestHandler(softwareStore);
		httpServer.getResolver().register(UrlPaths.BPK_LIST_URI, bpkRequestHandler);
		httpServer.getResolver().register(UrlPaths.BPK_URI, bpkRequestHandler);
		httpServer.getResolver().register(UrlPaths.TASK_DESCRIPTOR_LIST_URI, bpkRequestHandler);
		httpServer.getResolver().register(UrlPaths.TASK_CONTEXT_DESCRIPTOR_LIST_URI, bpkRequestHandler);
		httpServer.getResolver().register(UrlPaths.ARTIFACT_URI, new ArtifactRequestHandler(softwareStore));
	}

	@Override
	public void start() throws ServiceException {
		log.info("Starting Software Repository...");
		if (httpServer == null) {
			log.error("Cannot start Software Repository - HTTP server is null.");
			return;
		}
		if (softwareStore == null) {
			log.error("Cannot start Software Repository - Software Store is null.");
			return;
		}

		info = new ServiceInfo(SERVICE_NAME, beenId);
		info.setParam(ADDRESSES, createInterfacesInfo());
		info.setServiceState(ServiceState.OK);
		info.setHazelcastUuid(clusterCtx.getInstanceType() != NodeType.NATIVE
				? clusterCtx.getCluster().getLocalMember().getUuid() : null);

		httpServer.start();

		int period = 30;
		int timeout = 45;

		Runnable serviceInfoUpdater = new ServiceInfoUpdater(clusterCtx, info, timeout);

		clusterCtx.schedule(serviceInfoUpdater, 0, period, TimeUnit.SECONDS);

		log.info("Software Repository started.");
	}

	private String createInterfacesInfo() {
		final StringBuilder interfacesInfo = new StringBuilder();
		boolean first = true;
		for (InetSocketAddress socketAddr: httpServer.getHosts()) {
			if (!first) interfacesInfo.append(",");
			interfacesInfo.append(SocketAddrUtils.sockAddrToString(socketAddr));
			first = false;
		}
		return interfacesInfo.toString();
	}

	@Override
	public void stop() {
		log.info("Stopping Software repository...");
		try {
			clusterCtx.removeServiceInfo(info);
		} catch (IllegalStateException e) {
			// unregistering over a Hazelcast instance that is no longer active
			log.warn("Failed to unhook SoftwareRepository from the cluster. SoftwareRepository info is likely to linger.", e);
		}
		httpServer.stop();
		log.info("Software repository stopped.");
		clusterCtx.stop();
	}

	@Override
	public Reaper createReaper() {
		return new Reaper() {
			@Override
			protected void reap() throws InterruptedException {
				SoftwareRepository.this.stop();
			}
		};
	}

	/**
	 * Set the HTTP server
	 * 
	 * @param httpServer
	 *          HTTP server to set
	 */
	public void setHttpServer(HttpServer httpServer) {
		this.httpServer = httpServer;
	}

	/**
	 * Set the persistence layer
	 * 
	 * @param softwareStore
	 *          Data store to set
	 */
	public void setDataStore(SoftwareStore softwareStore) {
		this.softwareStore = softwareStore;
	}
}
