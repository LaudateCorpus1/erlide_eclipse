/*******************************************************************************
 * Copyright (c) 2005 Vlad Dumitrescu and others. All rights reserved. This program and
 * the accompanying materials are made available under the terms of the Eclipse Public
 * License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Vlad Dumitrescu Jakob Cederlund
 *******************************************************************************/
package org.erlide.backend.launch;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.erlide.backend.BackendCore;
import org.erlide.backend.api.BackendData;
import org.erlide.backend.api.ErlRuntimeAttributes;
import org.erlide.backend.api.IBackend;
import org.erlide.backend.api.ICodeBundle.CodeContext;
import org.erlide.runtime.api.IOtpNodeProxy;
import org.erlide.runtime.epmd.EpmdWatcher;
import org.erlide.runtime.runtimeinfo.RuntimeInfo;
import org.erlide.util.ErlLogger;
import org.erlide.util.HostnameChecker;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public class ErlangLaunchDelegate extends LaunchConfigurationDelegate {

    protected IBackend backend;

    @Override
    public void launch(final ILaunchConfiguration config, final String mode,
            final ILaunch launch, final IProgressMonitor monitor) throws CoreException {
        final BackendData data = getBackendData(config, mode, launch);
        if (data == null) {
            return;
        }

        if (data.isManaged()) {
            setCaptureOutput(launch);
        }
        IOtpNodeProxy runtime;
        if (!isErlangInternalLaunch(launch)) {
            backend = BackendCore.getBackendManager().createExecutionBackend(data);
            runtime = backend.getRuntime();
        } else {
            runtime = BackendCore.getBackendManager().getFactory().createNodeProxy(data);
        }
        if (data.isManaged()) {
            startErtsProcess(launch, data, runtime.getProcess());
        }
    }

    private BackendData getBackendData(final ILaunchConfiguration config,
            final String mode, final ILaunch launch) throws CoreException {
        if (config == null) {
            throw new IllegalArgumentException();
        }

        RuntimeInfo runtimeInfo = BackendCore.getRuntimeInfoCatalog()
                .getRuntime(config.getAttribute(ErlRuntimeAttributes.RUNTIME_NAME, ""));
        if (runtimeInfo == null || !runtimeInfo.isValid()) {
            runtimeInfo = BackendCore.getRuntimeInfoCatalog().getDefaultRuntime();
        }
        if (runtimeInfo == null) {
            ErlLogger.error("Can't create backend without a runtime defined!");
            return null;
        }
		String defaultNodeName = getDefaultNodeNameFromProjects(config, Long.toHexString(System.currentTimeMillis() & 0xFFFFFF));
		final String nodeName = config.getAttribute(ErlRuntimeAttributes.NODE_NAME, defaultNodeName);
        final boolean managed = shouldManageNode(nodeName, BackendCore.getEpmdWatcher());
        BackendData data = new BackendData(runtimeInfo, config, mode, managed);
        data = configureBackend(data, mode, launch);
        return data;
    }

	private String getDefaultNodeNameFromProjects(ILaunchConfiguration config, String defaultDefault) throws CoreException {
		String projects = config.getAttribute(ErlRuntimeAttributes.PROJECTS, defaultDefault);
		final String[] names = projects.split(BackendData.PROJECT_NAME_SEPARATOR);
		return names[0];
	}

    /*
     * Child classes override this to set specific information
     */
	protected BackendData configureBackend(final BackendData data, final String mode, final ILaunch launch) {
        data.setLaunch(launch);
        if (mode.equals("debug")) {
            data.setContext(CodeContext.DEBUGGER);
        }
        return data;
    }

    private void startErtsProcess(final ILaunch launch, final BackendData data,
            final Process process) {
        Preconditions.checkArgument(process != null);

        data.setLaunch(launch);
        final Map<String, String> map = Maps.newHashMap();
        final IProcess erts = DebugPlugin.newProcess(launch, process, data.getNodeName(),
                map);

        ErlLogger.debug("Started erts: %s >> %s", erts.getLabel(), data.getNodeName());
    }

    private void setCaptureOutput(final ILaunch launch) {
        // important, we don't want the "normal" console for the erlide backend
        final String captureOutput = System.getProperty("erlide.console.stdout", "false");
        launch.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, captureOutput);
    }

    public static boolean isErlangLaunch(final ILaunch aLaunch) {
        try {
            final ILaunchConfiguration cfg = aLaunch.getLaunchConfiguration();
            final ILaunchConfigurationType type = cfg.getType();
            final String id = type.getIdentifier();
            return IErlangLaunchDelegateConstants.CONFIGURATION_TYPE.equals(id)
                    || IErlangLaunchDelegateConstants.CONFIGURATION_TYPE_INTERNAL
                            .equals(id);
        } catch (final CoreException e) {
            ErlLogger.warn(e);
            return false;
        }
    }

    public static boolean isErlangInternalLaunch(final ILaunch aLaunch) {
        try {
            final ILaunchConfiguration cfg = aLaunch.getLaunchConfiguration();
            final ILaunchConfigurationType type = cfg.getType();
            final String id = type.getIdentifier();
            return IErlangLaunchDelegateConstants.CONFIGURATION_TYPE_INTERNAL.equals(id);
        } catch (final CoreException e) {
            ErlLogger.warn(e);
            return false;
        }
    }

    private static boolean shouldManageNode(final String name,
            final EpmdWatcher epmdWatcher) {
        final int atSignIndex = name.indexOf('@');
        String shortName = name;
        if (atSignIndex > 0) {
            shortName = name.substring(0, atSignIndex);
        }

        boolean isLocal = atSignIndex < 0;
        if (atSignIndex > 0) {
            final String hostname = name.substring(atSignIndex + 1);
            if (HostnameChecker.getInstance().isThisHost(hostname)) {
                isLocal = true;
            }
        }

        final boolean isRunning = epmdWatcher.hasLocalNode(shortName);
        final boolean result = isLocal && !isRunning;
        return result;
    }

}
