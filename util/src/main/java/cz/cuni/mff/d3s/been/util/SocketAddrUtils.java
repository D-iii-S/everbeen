package cz.cuni.mff.d3s.been.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.StringTokenizer;

/**
 * Utilities to work with socket addresses
 *
 * @author darklight
 * @since 7/26/14.
 */
public class SocketAddrUtils {

	private static final char LEFT_DELIM = '(';
	private static final char RIGHT_DELIM = ')';
	private static final char PORT_SEPARATOR = ':';
	private static final String LIST_SEPARATOR = ",";

	/**
	 * Create a string representation of a socket address.
	 * <p>
	 * The string representation is of the form (host):port.
	 *
	 * @param sockAddr The socket address
	 *
	 * @return The string representation of that socket address
	 */
	public static String sockAddrToString(InetSocketAddress sockAddr) {
		final String hostName = stripAddrZone(sockAddr.getHostName());
		return new StringBuilder()
				.append(LEFT_DELIM).append(hostName).append(RIGHT_DELIM)
				.append(PORT_SEPARATOR)
				.append(sockAddr.getPort()).toString();
	}

	/**
	 * Strip the zone from an IPv6 hostname
	 *
	 * @param addr Address whose zone should be stripped
	 *
	 * @return The address without zone
	 */
	private static String stripAddrZone(String addr) {
		int zoneSignPos = addr.indexOf('%');
		return (zoneSignPos > 0) ? addr.substring(0, zoneSignPos) : addr;
	}

	/**
	 * Create a string representation of multiple socket addresses.
	 * <p>
	 * The string representation is a comma delimited list of individual socket addresses.
	 *
	 * @param sockAddrs Socket addresses to include
	 *
	 * @return A list of socket addresses
	 */
	public static String sockAddrsToString(Collection<InetSocketAddress> sockAddrs) {
		final StringBuilder addrs = new StringBuilder();
		boolean first = true;
		for (InetSocketAddress sockAddr: sockAddrs) {
			if (!first) addrs.append(LIST_SEPARATOR);
			first = false;
			addrs.append(sockAddrToString(sockAddr));
		}
		return addrs.toString();
	}

	/**
	 * Parse the string representation of a socket address into a socket address.
	 * Verify that the address is reachable. If not, return <code>null</code>.
	 *
	 * @param sockAddrString The string representation of the socket address
	 * @param reachTimeout The timeout (in milliseconds) that is applied before the address is declared as unreachable
	 *
	 * @return A reachable socket address
	 *
	 * @throws java.net.UnknownHostException When the network host cannot be parsed correctly
	 * @throws java.lang.NumberFormatException When the port is not an integer
	 */
	public static InetSocketAddress parseReachableSockAddr(String sockAddrString, int reachTimeout) throws UnknownHostException {
		
		// First just parse everything from the string.
		final int lbr = sockAddrString.indexOf(LEFT_DELIM);
		final int rbr = sockAddrString.indexOf(RIGHT_DELIM,lbr+1);
		final int psp = sockAddrString.indexOf(PORT_SEPARATOR,rbr+1);
		if (lbr < 0 || rbr < 0 || psp < 0) throw new UnknownHostException(String.format("Invalid socket address string: '%s'", sockAddrString));
		final String host = sockAddrString.substring(lbr + 1, rbr);
		final int port = Integer.parseInt(sockAddrString.substring(psp + 1));

		// Now try to connect to the address and port specified.
		final InetSocketAddress address = new InetSocketAddress(host, port);
		try (Socket socket = new Socket (address.getAddress(), address.getPort())) { }
		catch (IOException e) { return (null); }
		return (address);
	}

	/**
	 * Parse the comma-separated list of socket address string representations.
	 * Verify each address for reachability. Return the first reachable address.
	 *
	 * @param sockAddrs Comma-separated list of socket address string representations
	 * @param reachTimeout The timeout (in milliseconds) that is applied before the address is declared as unreachable
	 *
	 * @return The first reachable socket address from the list, or <code>null</code> if no address is provided/reachable
	 */
	public static InetSocketAddress getFirstReachableAddress(String sockAddrs, int reachTimeout) throws UnknownHostException {
		StringTokenizer addrTok = new StringTokenizer(sockAddrs, LIST_SEPARATOR);
		while(addrTok.hasMoreTokens()) {
			final InetSocketAddress sockAddr = parseReachableSockAddr(addrTok.nextToken().trim(), reachTimeout);
			if (sockAddr != null) return sockAddr;
		}
		return null;
	}
}
