package cz.cuni.mff.d3s.been.web.services;

import cz.cuni.mff.d3s.been.api.BeenApi;
import cz.cuni.mff.d3s.been.api.BeenApiImpl;

import java.net.InetSocketAddress;

/**
 * User: donarus
 * Date: 4/27/13
 * Time: 11:48 AM
 */
public interface BeenApiService extends BeenApi {


    public boolean isConnected();

    public boolean connect(InetSocketAddress address);

}
