package cz.cuni.mff.d3s.been.bpk;
import java.io.File;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

import cz.cuni.mff.d3s.been.bpk.BpkConfiguration;
import cz.cuni.mff.d3s.been.bpk.BpkResolver;


public class UnmarshalTest extends Assert {

	@Test
	public void testUnmarshallingGeneratedXml() throws Exception {
		URL bpkUrl = getClass().getResource("test.bpk");
		File bpkFile = new File(bpkUrl.toURI());
		BpkConfiguration config = BpkResolver.resolve(bpkFile);
		return;
	}
}
