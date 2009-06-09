/*******************************************************************************
 * Copyright (c) 2009 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available
 * at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.jinterface.backend;

import java.io.IOException;
import java.net.Socket;

import org.erlide.jinterface.rpc.RpcException;
import org.erlide.jinterface.rpc.RpcFuture;
import org.erlide.jinterface.rpc.RpcResult;
import org.erlide.jinterface.rpc.RpcUtil;
import org.erlide.jinterface.util.ErlLogger;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangDecodeException;
import com.ericsson.otp.erlang.OtpErlangExit;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangPid;
import com.ericsson.otp.erlang.OtpErlangString;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.ericsson.otp.erlang.OtpMbox;
import com.ericsson.otp.erlang.OtpNode;
import com.ericsson.otp.erlang.OtpNodeStatus;
import com.ericsson.otp.erlang.SignatureException;

public class Backend {

	private static final int EPMD_PORT = 4369;
	private static final int RETRY_DELAY = Integer.parseInt(System.getProperty(
			"erlide.connect.delay", "300"));
	private static int DEFAULT_TIMEOUT;
	{
		final String t = System.getProperty("erlide.rpc.timeout", "9000");
		if ("infinity".equals(t)) {
			DEFAULT_TIMEOUT = RpcUtil.INFINITY;
		} else {
			DEFAULT_TIMEOUT = Integer.parseInt(t);
		}
	}
	private IShellManager shellManager;
	private boolean available = false;
	private String currentVersion;
	private OtpMbox eventBox;
	private int exitStatus = -1;
	private boolean fDebug;
	private final RuntimeInfo fInfo;
	private OtpNode fNode;
	private String fPeer;
	private int restarted = 0;
	private boolean stopped = false;

	public IShellManager getShellManager() {
		return shellManager;
	}

	protected Backend(final RuntimeInfo info) throws BackendException {
		if (info == null) {
			throw new BackendException(
					"Can't create backend without runtime information");
		}
		fInfo = info;
	}

	/**
	 * typed RPC
	 * 
	 */
	public RpcResult call_noexception(final String m, final String f,
			final String signature, final Object... a) {
		return call_noexception(DEFAULT_TIMEOUT, m, f, signature, a);
	}

	/**
	 * typed RPC with timeout
	 * 
	 * @throws ConversionException
	 */
	public RpcResult call_noexception(final int timeout, final String m,
			final String f, final String signature, final Object... args) {
		try {
			final OtpErlangObject result = makeCall(timeout, m, f, signature,
					args);
			return new RpcResult(result);
		} catch (final RpcException e) {
			return RpcResult.error(e.getMessage());
		} catch (SignatureException e) {
			return RpcResult.error(e.getMessage());
		}
	}

	public RpcFuture async_call(final String m, final String f,
			final String signature, final Object... args)
			throws BackendException {
		try {
			return makeAsyncCall(m, f, signature, args);
		} catch (final RpcException e) {
			throw new BackendException(e);
		} catch (SignatureException e) {
			throw new BackendException(e);
		}
	}

	public void cast(final String m, final String f, final String signature,
			final Object... args) throws BackendException {
		try {
			makeCast(m, f, signature, args);
		} catch (final RpcException e) {
			throw new BackendException(e);
		} catch (SignatureException e) {
			throw new BackendException(e);
		}
	}

	public OtpErlangObject call(final String m, final String f,
			final String signature, final Object... a) throws BackendException {
		return call(DEFAULT_TIMEOUT, m, f, signature, a);
	}

	/**
	 * typed RPC with timeout, throws Exception
	 * 
	 * @throws ConversionException
	 */
	public OtpErlangObject call(final int timeout, final String m,
			final String f, final String signature, final Object... a)
			throws BackendException {
		return call(timeout, new OtpErlangAtom("user"), m, f, signature, a);
	}

	public OtpErlangObject call(final int timeout,
			final OtpErlangObject gleader, final String m, final String f,
			final String signature, final Object... a) throws BackendException {
		try {
			return makeCall(timeout, gleader, m, f, signature, a);
		} catch (final RpcException e) {
			throw new BackendException(e);
		} catch (SignatureException e) {
			throw new BackendException(e);
		}
	}

	public void send(final OtpErlangPid pid, final Object msg) {
		if (!available) {
			return;
		}
		try {
			RpcUtil.send(fNode, pid, msg);
		} catch (final SignatureException e) {
			// shouldn't happen
			ErlLogger.warn(e);
		}
	}

	public void send(final String name, final Object msg) {
		if (!available) {
			return;
		}
		try {
			RpcUtil.send(fNode, fPeer, name, msg);
		} catch (final SignatureException e) {
			// shouldn't happen
			ErlLogger.warn(e);
		}
	}

	public OtpErlangObject receiveEvent(final long timeout)
			throws OtpErlangExit, OtpErlangDecodeException {
		if (eventBox == null) {
			return null;
		}
		return eventBox.receive(timeout);
	}

	private synchronized void checkAvailability() throws RpcException {
		if (!available) {
			if (exitStatus >= 0 && restarted < 3) {
				restart();
			} else {
				throw new RpcException("could not restart backend");
			}
		}
	}

	public void connect() {
		doConnect(getName());
	}

	public void dispose() {
		if (shellManager instanceof IDisposable) {
			((IDisposable) shellManager).dispose();
		}
		dispose(false);
	}

	public void dispose(final boolean restart) {
		ErlLogger.debug("disposing backend " + getName());

		if (fNode != null) {
			fNode.close();
		}
		if (restart) {
			return;
		}
	}

	@SuppressWarnings("boxing")
	public void doConnect(final String label) {
		ErlLogger.debug("connect to:: '" + label + "' "
				+ Thread.currentThread());
		// Thread.dumpStack();
		try {
			wait_for_epmd();

			final String cookie = getInfo().getCookie();
			if (cookie == null) {
				fNode = new OtpNode(BackendUtil.createJavaNodeName());
			} else {
				fNode = new OtpNode(BackendUtil.createJavaNodeName(), cookie);
			}
			final String nodeCookie = fNode.cookie();
			final int len = nodeCookie.length();
			final String trimmed = len > 7 ? nodeCookie.substring(0, 7)
					: nodeCookie;
			ErlLogger.debug("using cookie '%s...'%d (info: '%s')", trimmed,
					len, cookie);
			fPeer = BackendUtil.buildNodeName(label, true);

			eventBox = fNode.createMbox("rex");
			int tries = 20;
			while (!available && tries > 0) {
				available = fNode.ping(fPeer, RETRY_DELAY + (20 - tries)
						* RETRY_DELAY / 5);
				tries--;
			}
			available &= waitForCodeServer();

			if (available) {
				ErlLogger.debug("connected!");
			} else {
				ErlLogger
						.error("could not connect to backend! Please check runtime settings.");
			}

		} catch (final Exception e) {
			ErlLogger.error(e);
			available = false;
			ErlLogger
					.error("could not connect to backend! Please check runtime settings.");
		}
	}

	public String getCurrentVersion() {
		if (currentVersion == null) {
			try {
				currentVersion = getScriptId();
			} catch (final Exception e) {
			}
		}
		return currentVersion;
	}

	private OtpMbox getEventBox() {
		return eventBox;
	}

	public OtpErlangPid getEventPid() {
		final OtpMbox eventBox = getEventBox();
		if (eventBox == null) {
			return null;
		}
		return eventBox.self();
	}

	public RuntimeInfo getInfo() {
		return fInfo;
	}

	public String getJavaNodeName() {
		return fNode.node();
	}

	public String getName() {
		if (fInfo == null) {
			return "<not_connected>";
		}
		return fInfo.getNodeName();
	}

	public String getPeer() {
		return fPeer;
	}

	private String getScriptId() throws BackendException {
		OtpErlangObject r;
		r = call("init", "script_id", "");
		if (r instanceof OtpErlangTuple) {
			final OtpErlangObject rr = ((OtpErlangTuple) r).elementAt(1);
			if (rr instanceof OtpErlangString) {
				return ((OtpErlangString) rr).stringValue();
			}
		}
		return "";
	}

	private boolean init(final OtpErlangPid jRex) {
		try {
			// reload(backend);
			call("erlide_backend", "init", "p", jRex);
			return true;
		} catch (final Exception e) {
			ErlLogger.error(e);
			return false;
		}
	}

	public void initErlang() {
		final boolean inited = init(getEventPid());
		if (!inited) {
			setAvailable(false);
		}
	}

	public void initializeRuntime() {
		dispose(true);
		shellManager = new BackendShellManager(this);
	}

	public boolean isDebug() {
		return fDebug;
	}

	public boolean isStopped() {
		return stopped;
	}

	private RpcFuture makeAsyncCall(final OtpErlangObject gleader,
			final String module, final String fun, final String signature,
			final Object... args0) throws RpcException, SignatureException {
		checkAvailability();
		return RpcUtil.sendRpcCall(fNode, fPeer, gleader, module, fun,
				signature, args0);
	}

	protected RpcFuture makeAsyncCall(final String module, final String fun,
			final String signature, final Object... args0) throws RpcException,
			SignatureException {
		return makeAsyncCall(new OtpErlangAtom("user"), module, fun, signature,
				args0);
	}

	protected OtpErlangObject makeCall(final int timeout,
			final OtpErlangObject gleader, final String module,
			final String fun, final String signature, final Object... args0)
			throws RpcException, SignatureException {
		checkAvailability();
		final OtpErlangObject result = RpcUtil.rpcCall(fNode, fPeer, gleader,
				module, fun, timeout, signature, args0);
		return result;
	}

	protected OtpErlangObject makeCall(final int timeout, final String module,
			final String fun, final String signature, final Object... args0)
			throws RpcException, SignatureException {
		return makeCall(timeout, new OtpErlangAtom("user"), module, fun,
				signature, args0);
	}

	protected void makeCast(final OtpErlangObject gleader, final String module,
			final String fun, final String signature, final Object... args0)
			throws SignatureException, RpcException {
		checkAvailability();
		RpcUtil.rpcCast(fNode, fPeer, gleader, module, fun, signature, args0);
	}

	protected void makeCast(final String module, final String fun,
			final String signature, final Object... args0)
			throws SignatureException, RpcException {
		makeCast(new OtpErlangAtom("user"), module, fun, signature, args0);
	}

	public boolean ping() {
		return fNode.ping(fPeer, 500);
	}

	public void registerStatusHandler(OtpNodeStatus handler) {
		fNode.registerStatusHandler(handler);
	}

	public synchronized void restart() {
		exitStatus = -1;
		if (available) {
			return;
		}
		restarted++;
		ErlLogger.info("restarting runtime for %s", toString());
		if (fNode != null) {
			fNode.close();
			fNode = null;
		}
		initializeRuntime();
		connect();
		initErlang();
	}

	public void setAvailable(final boolean up) {
		available = up;
	}

	public void setDebug(final boolean b) {
		fDebug = b;
	}

	public void setExitStatus(final int v) {
		exitStatus = v;
	}

	protected void setRemoteRex(final OtpErlangPid watchdog) {
		try {
			getEventBox().link(watchdog);
		} catch (final OtpErlangExit e) {
		}
	}

	public void stop() {
		stopped = true;
	}

	protected void wait_for_epmd() throws BackendException {
		// If anyone has a better solution for waiting for epmd to be up, please
		// let me know
		int tries = 50;
		boolean ok = false;
		do {
			Socket s;
			try {
				s = new Socket("localhost", EPMD_PORT);
				s.close();
				ok = true;
			} catch (final IOException e) {
			}
			try {
				Thread.sleep(100);
				// ErlLogger.debug("sleep............");
			} catch (final InterruptedException e1) {
			}
			tries--;
		} while (!ok && tries > 0);
		if (!ok) {
			final String msg = "Couldn't contact epmd - erlang backend is probably not working\n"
					+ "  Possibly your host's entry in /etc/hosts is wrong.";
			ErlLogger.error(msg);
			throw new BackendException(msg);
		}
	}

	private boolean waitForCodeServer() {
		try {
			OtpErlangObject r;
			int i = 10;
			do {
				r = call("erlang", "whereis", "a", "code_server");
				Thread.sleep(200);
				i--;
			} while (!(r instanceof OtpErlangPid) && i > 0);
			if (!(r instanceof OtpErlangPid)) {
				ErlLogger.error("code server did not start in time for %s",
						getInfo().getName());
				return false;
			}
			ErlLogger.debug("code server started");
			return true;
		} catch (final Exception e) {
			ErlLogger.error("error starting code server for %s: %s", getInfo()
					.getName(), e.getMessage());
			return false;
		}
	}

}
