package org.erlide.engine.internal

import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangString
import org.eclipse.core.runtime.CoreException
import org.erlide.engine.ErlangInitializeParams
import org.erlide.engine.IErlangEngine
import org.erlide.engine.internal.model.ErlModel
import org.erlide.engine.internal.model.erlang.ModelFindUtil
import org.erlide.engine.internal.model.erlang.ModelInternalUtils
import org.erlide.engine.internal.services.cleanup.ErlTidyCleanupProvider
import org.erlide.engine.internal.services.codeassist.ErlangCompletionService
import org.erlide.engine.internal.services.edoc.ErlideEdocExport
import org.erlide.engine.internal.services.parsing.ErlideParser
import org.erlide.engine.internal.services.parsing.ErlideScanner
import org.erlide.engine.internal.services.parsing.ScannerProvider
import org.erlide.engine.internal.services.proclist.ErlideProclist
import org.erlide.engine.internal.services.search.ErlideDoc
import org.erlide.engine.internal.services.search.ErlideOpen
import org.erlide.engine.internal.services.search.ErlideSearchServer
import org.erlide.engine.internal.services.text.ErlideIndent
import org.erlide.engine.model.OtpRpcFactory
import org.erlide.engine.model.root.IErlModel
import org.erlide.engine.services.SystemInfoService
import org.erlide.engine.services.ToggleCommentService
import org.erlide.engine.services.cleanup.CleanupProvider
import org.erlide.engine.services.codeassist.CompletionService
import org.erlide.engine.services.edoc.EdocExportService
import org.erlide.engine.services.parsing.NullScannerService
import org.erlide.engine.services.parsing.ScannerProviderService
import org.erlide.engine.services.parsing.SimpleParserService
import org.erlide.engine.services.parsing.SimpleScannerService
import org.erlide.engine.services.proclist.ProclistService
import org.erlide.engine.services.search.ModelFindService
import org.erlide.engine.services.search.ModelUtilService
import org.erlide.engine.services.search.OpenService
import org.erlide.engine.services.search.OtpDocService
import org.erlide.engine.services.search.SearchServerService
import org.erlide.engine.services.text.IndentService
import org.erlide.runtime.rpc.IOtpRpc
import org.erlide.runtime.rpc.RpcException
import org.erlide.util.ErlLogger

class ErlangServerImpl implements IErlangEngine {

	IOtpRpc backend
	volatile ErlModel erlangModel
	volatile String stateDir

	override initialize(ErlangInitializeParams params) {
		backend = OtpRpcFactory.getOtpRpc()
		stateDir = params.stateDir
	}

	override shutdown() {
	}

	override IErlModel getModel() {
		if (erlangModel === null) {
			erlangModel = new ErlModel()
		}
		if (!erlangModel.isOpen()) {
			try {
				erlangModel.open(null)
			} catch (CoreException e) {
				ErlLogger.error(e)
			}

		}
		return erlangModel
	}

	override String getStateDir() {
		return stateDir
	}

	override SearchServerService getSearchServerService() {
		return new ErlideSearchServer(backend)
	}

	override ModelUtilService getModelUtilService() {
		return new ModelInternalUtils(backend)
	}

	override ModelFindService getModelFindService() {
		return new ModelFindUtil(backend)
	}

	/**
	 * <p>
	 * Construct a {@link CleanUpProvider} appropriate for a particular IResource.
	 * </p>
	 */
	override CleanupProvider getCleanupProvider() {
		return new ErlTidyCleanupProvider(backend)
	}

	override ScannerProviderService getScannerProviderService() {
		return new ScannerProvider(backend)
	}

	override EdocExportService getEdocExportService() {
		return new ErlideEdocExport(backend)
	}

	override ProclistService getProclistService() {
		return new ErlideProclist()
	}

	override SimpleScannerService getSimpleScannerService() {
		if (backend === null) {
			return new NullScannerService()
		}
		return new ErlideScanner(backend)
	}

	override SimpleParserService getSimpleParserService() {
		return new ErlideParser(backend)
	}

	override CompletionService getCompletionService() {
		return new ErlangCompletionService(backend)
	}

	override boolean isAvailable() {
		return backend !== null
	}

	override ToggleCommentService getToggleCommentService() {
		return [ int offset, int length, String text |
			try {
				val OtpErlangObject r1 = backend.call("erlide_comment", "toggle_comment", "sii", text, offset, length)
				return r1
			} catch (RpcException e) {
				return new OtpErlangString("")
			}
		]
	}

	override IndentService getIndentService() {
		return new ErlideIndent(backend)
	}

	override OpenService getOpenService() {
		return new ErlideOpen(backend, getStateDir())
	}

	override OtpDocService getOtpDocService() {
		return new ErlideDoc(backend, getStateDir())
	}

	override SystemInfoService getSystemInfoService() {
		return new SystemInfo(backend)
	}

}
