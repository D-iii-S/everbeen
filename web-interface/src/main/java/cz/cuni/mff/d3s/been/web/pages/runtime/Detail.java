package cz.cuni.mff.d3s.been.web.pages.runtime;

import cz.cuni.mff.d3s.been.core.ri.*;
import cz.cuni.mff.d3s.been.core.task.TaskContextEntry;
import cz.cuni.mff.d3s.been.web.components.Layout;
import cz.cuni.mff.d3s.been.web.pages.Page;
import org.apache.tapestry5.annotations.Property;

/**
 * @author Kuba Brecka
 */
@Page.Navigation(section = Layout.Section.RUNTIME_DETAIL)
public class Detail extends Page {

	@Property
	private RuntimeInfo runtime;

	@Property
	private Cpu cpu;

	@Property
	private NetworkInterface networkInterface;

	@Property
	private Filesystem filesystem;

	@Property
	private String taskDir;

	@Property
	private NetworkSample monitorInterface;

	@Property
	private FilesystemSample monitorFilesystem;

	void onActivate(String runtimeId) {
		runtime = api.getApi().getRuntime(runtimeId);
	}

}
