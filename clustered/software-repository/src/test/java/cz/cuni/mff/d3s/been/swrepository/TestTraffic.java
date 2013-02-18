package cz.cuni.mff.d3s.been.swrepository;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.codehaus.plexus.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cz.cuni.mff.d3s.been.bpk.Bpk;
import cz.cuni.mff.d3s.been.bpk.BpkIdentifier;
import cz.cuni.mff.d3s.been.swrepoclient.SwRepoClientFactory;
import cz.cuni.mff.d3s.been.swrepoclient.SwRepoClient;
import cz.cuni.mff.d3s.been.swrepository.httpserver.HttpServer;

/**
 * A simulation of actual software repository use-cases. Consists in running a
 * {@link SoftwareRepository} server and launching clients against it, testing
 * whether the right responses and files are obtained.
 * 
 * @author darklight
 * 
 */
public class TestTraffic {

	/**
	 * Root folder of SWRepo's persistence. We're booting it in PWD, so it'll be
	 * in <code>./.persistence</code> by default
	 */
	private static final File PERSISTENCE_ROOT_FOLDER = new File(".persistence");
	private final SwRepoClientFactory clientFactory = new SwRepoClientFactory(PERSISTENCE_ROOT_FOLDER);

	HttpServer server = null;
	SwRepoClient client = null;
	File randomContentFile = null;
	BpkIdentifier bpkId = null;

	/**
	 * Fill test fields.
	 * 
	 * @throws IOException
	 *             On failure when creating test files
	 */
	@Before
	public void fillFields() throws IOException {
		randomContentFile = File.createTempFile("testSwRepoTraffic",
				"randomContent");
		bpkId = new BpkIdentifier();
		bpkId.setBpkId("evil-package");
		bpkId.setGroupId("cz.cuni.mff.d3s.been.swrepository.test");
		bpkId.setVersion("0.0.7");
	}

	/**
	 * Boot the tested HTTP server component.
	 * 
	 * @throws IOException
	 *             On socket binding failure
	 */
	@Before
	public void startServer() throws IOException {
		// find a random free socket
		final ServerSocket probeSocket = new ServerSocket(0);
		final int port = probeSocket.getLocalPort();
		probeSocket.close();
		server = new HttpServer(port);
		DataStore dataStore = new FSBasedStore();
		server.getResolver().register("/bpk*", new BpkRequestHandler(dataStore));
		server.getResolver().register("/artifact*", new ArtifactRequestHandler(dataStore));
		server.start();
		client = clientFactory.getClient("localhost", port);
	}

	/**
	 * Kill the tested HTTP server component.
	 */
	@After
	public void stopServer() {
		server.stop();
		server = null;
		client = null;
	}

	/**
	 * Scratch the test fields.
	 */
	@After
	public void scratchFields() {
		randomContentFile = null;
		bpkId = null;
	}

	/**
	 * Clean up the persistence folder.
	 * 
	 * @throws IOException When problems come up when cleaning the folder
	 */
	@After
	public void scratchPersistence() throws IOException {
		FileUtils.deleteDirectory(PERSISTENCE_ROOT_FOLDER);
	}

	@Test
	public void testUploadBpk() throws IOException {
		assertTrue(client.putBpk(bpkId, randomContentFile));
		assertFilePresent(String.format("%s-%s.bpk", bpkId.getBpkId(),
				bpkId.getVersion()), "bpks", randomContentFile,
				bpkId.getGroupId(), bpkId.getBpkId(), bpkId.getVersion());
	}

	@Test
	public void testUploadBpk_overwrite() throws IOException {
		assertTrue(client.putBpk(bpkId, randomContentFile));
		// reset fields - generates same identifier but different content
		fillFields();
		assertTrue(client.putBpk(bpkId, randomContentFile));
		assertFilePresent(String.format("%s-%s.bpk", bpkId.getBpkId(),
				bpkId.getVersion()), "bpks", randomContentFile,
				bpkId.getGroupId(), bpkId.getBpkId(), bpkId.getVersion());
	}

	@Test
	public void testUploadBpk_fileDoesntExist() {
		randomContentFile.delete();
		assertFalse(client.putBpk(bpkId, randomContentFile));
		assertFileAbsent(String.format("%s-%s.bpk", bpkId.getBpkId(),
				bpkId.getVersion()), "bpks", bpkId.getGroupId(),
				bpkId.getBpkId(), bpkId.getVersion());
	}

	@Test
	public void testUploadBpk_essentialIdentifiersNull() {
		bpkId.setBpkId(null);
		assertFalse(client.putBpk(bpkId, randomContentFile));
	}

	@Test
	public void testUploadBpk_serverDown() {
		server.stop();
		assertFalse(client.putBpk(bpkId, randomContentFile));
		assertFileAbsent(String.format("%s-%s.bpk", bpkId.getBpkId(),
				bpkId.getVersion()), "bpks", bpkId.getGroupId(),
				bpkId.getBpkId(), bpkId.getVersion());
	}

	// test delete bpk that exists
	// test delete bpk that doesn't exist

	// test upload artifact
	// test upload artifact file doesn't exist
	// test upload artifact with parts of artifact null
	// test upload artifact server down
	// test delete artifact that exists
	// test delete artifact that doesn't exist

	@Test
	public void testDownloadBpk() throws IOException {
		File persistedFile = getFileFromPathAndName(String.format("%s-%s.bpk",
				bpkId.getBpkId(), bpkId.getVersion()), "bpks",
				bpkId.getGroupId(), bpkId.getBpkId(), bpkId.getVersion());
		persistedFile.getParentFile().mkdirs();
		persistedFile.createNewFile();
		FileWriter fw = new FileWriter(persistedFile);
		final String evilContent = "THIS CONTENT IS EVIL, DON'T READ IT!";
		fw.write(evilContent);
		fw.close();

		Bpk bpk = client.getBpk(bpkId);
		assertNotNull(bpk);
		final String downloadedContent = FileUtils.fileRead(bpk.getFile());
		assertEquals(evilContent, downloadedContent);
	}

	@Test
	public void testDownloadBpk_essentialIdentifiersNull() {
		bpkId.setBpkId(null);
		assertNull(client.getBpk(bpkId));
	}

	@Test
	public void testDownloadBpk_serverDown() {
		server.stop();
		assertNull(client.getBpk(bpkId));
	}

	// test download bpk server down
	// test download artifact
	// test download artifact bad identifier
	// test download artifact server down

	/**
	 * Assert that a file can be found in the server persistence and that its
	 * content is equal to the content of the reference file.
	 * 
	 * @param fileName
	 *            The name of the file we're expecting to find
	 * @param storeName
	 *            Name of the store this file lies in
	 * @param referenceFile
	 *            Reference content of the file we're expecting to find
	 * @param pathItems
	 *            The Names of the file's path items within the persistence
	 *            folder
	 * 
	 * @throws IOException
	 *             On reference or actual file read error
	 */
	public void assertFilePresent(String fileName, String storeName,
			File referenceFile, String... pathItems) throws IOException {
		final File file = getFileFromPathAndName(fileName, storeName, pathItems);
		assertTrue(file.exists());
		final String actualFileContent = FileUtils.fileRead(referenceFile);
		final String referenceFileContent = FileUtils.fileRead(file);
		assertEquals(referenceFileContent, actualFileContent);
	}

	/**
	 * Assert that a file can not be found in the server persistence.
	 * 
	 * @param fileName
	 *            Name of the file we're not expecting to find
	 * @param storeName
	 *            Name of the store this file lies in
	 * @param pathItems
	 *            Names of the file's path items within the persistence folder
	 */
	public void assertFileAbsent(String fileName, String storeName,
			String... pathItems) {
		final File file = getFileFromPathAndName(fileName, storeName, pathItems);
		assertFalse(file.exists());
	}

	private File getFileFromPathAndName(String fileName, String storeName,
			String... pathItems) {
		Path path = FileSystems.getDefault().getPath(
				PERSISTENCE_ROOT_FOLDER.getName() + File.separator + storeName,
				pathItems);
		return new File(path.toFile(), fileName);
	}
}