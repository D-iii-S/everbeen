package cz.cuni.mff.d3s.been.swrepository;

import static cz.cuni.mff.d3s.been.core.StatusCode.EX_USAGE;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;

import cz.cuni.mff.d3s.been.cluster.Instance;
import cz.cuni.mff.d3s.been.cluster.context.ClusterContext;
import cz.cuni.mff.d3s.been.datastore.SoftwareStore;
import cz.cuni.mff.d3s.been.datastore.SoftwareStoreFactory;
import cz.cuni.mff.d3s.been.swrepository.httpserver.HttpServer;

/**
 * 
 * @author donarus
 * 
 */
public class SoftwareRepositoryRunner {
	private static final Logger log = LoggerFactory.getLogger(SoftwareRepositoryRunner.class);

	@Option(name = "-h", aliases = { "--host" }, usage = "Hostname of a cluster member to connect to")
	private String host = "localhost";

	@Option(name = "-p", aliases = { "--port" }, usage = "Port of the host")
	private int port = 5701;

	@Option(name = "-gn", aliases = { "--group-name" }, usage = "Group Name")
	private String groupName = "dev";

	@Option(name = "-gp", aliases = { "--group-password" }, usage = "Group Password")
	private String groupPassword = "dev-pass";

	@Option(name = "-th", aliases = { "--http-host" }, usage = "Hostname for the HTTP server to bind, defaults to \"localhost\"")
	private String httpHost = "localhost";

	@Option(name = "-tp", aliases = { "--http-port" }, usage = "Port for the HTTP server to bind, defaults to 8000")
	private int httpPort = 8000;

	/**
	 * Run a software repository node from command-line.
	 * 
	 * @param args
	 *          None recognized
	 */
	public static void main(String[] args) {
		new SoftwareRepositoryRunner().doMain(args);
	}

	public void doMain(String[] args) {
		CmdLineParser parser = new CmdLineParser(this);

		try {
			// parse the arguments.
			parser.parseArgument(args);

		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("\nUsage:");
			parser.printUsage(System.err);

			System.exit(EX_USAGE.getCode());
		}

		HazelcastInstance inst = Instance.newNativeInstance(
				host,
				port,
				groupName,
				groupPassword);
		ClusterContext clusterCtx = new ClusterContext(inst);

		SoftwareRepository swRepo = new SoftwareRepository(clusterCtx);
		SoftwareStore dataStore = SoftwareStoreFactory.getDataStore();
		InetAddress myAddr = null;
		try {
			myAddr = InetAddress.getByName(httpHost);
		} catch (UnknownHostException e) {
			log.error(
					"Software Repository could not start: Failed to resolve local address {}. Cause was: {}",
					httpHost,
					e.getMessage());
			inst.getLifecycleService().shutdown(); // kill the SWRepository Hazelcast instance - without the HTTP server, it's useless
			return;
		}
		HttpServer httpServer = new HttpServer(myAddr, httpPort);
		swRepo.setDataStore(dataStore);
		swRepo.setHttpServer(httpServer);
		swRepo.init();
		swRepo.start();
	}
}
