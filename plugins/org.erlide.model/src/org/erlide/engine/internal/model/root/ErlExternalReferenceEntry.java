package org.erlide.engine.internal.model.root;

import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.erlide.engine.ErlangEngine;
import org.erlide.engine.model.ErlElementKind;
import org.erlide.engine.model.ErlModelException;
import org.erlide.engine.model.IParent;
import org.erlide.engine.model.OtpRpcFactory;
import org.erlide.engine.model.root.IErlExternal;
import org.erlide.engine.model.root.IErlModule;
import org.erlide.engine.model.root.IErlProject;
import org.erlide.engine.util.CommonUtils;
import org.erlide.runtime.rpc.IOtpRpc;

import com.google.common.collect.Lists;

public class ErlExternalReferenceEntry extends Openable implements IErlExternal {

    private final IPath path;
    private final boolean prebuilt;
    private final boolean hasHeaders;
    private String group;

    public ErlExternalReferenceEntry(final IParent parent, final String name,
            final IPath path, final boolean prebuilt, final boolean hasHeaders) {
        super(parent, name);
        this.path = path;
        this.prebuilt = prebuilt;
        this.hasHeaders = hasHeaders;
    }

    @Override
    public ErlElementKind getKind() {
        return ErlElementKind.EXTERNAL_FOLDER;
    }

    @Override
    public boolean isStructureKnown() {
        return prebuilt || super.isStructureKnown();
    }

    @Override
    public boolean buildStructure(final IProgressMonitor pm) throws ErlModelException {
        if (prebuilt) {
            // already done
            return true;
        }
        final IErlProject project = ErlangEngine.getInstance().getModelUtilService()
                .getProject(this);
        final IOtpRpc backend = OtpRpcFactory.getOtpRpcForProject(project);
        if (backend != null) {
            final List<String> files = ErlangEngine.getInstance().getOpenService()
                    .getLibFiles(path.toPortableString());
            final List<IErlModule> children = Lists
                    .newArrayListWithCapacity(files.size());
            for (final String file : files) {
                if (CommonUtils.isErlangFileContentFileName(getName(file))) {
                    children.add(new ErlModule(this, getName(file), file, null, null));
                }
            }
            setChildren(children);
            return true;
        }
        return false;
    }

    private String getName(final String file) {
        final IPath p = new Path(file);
        return p.lastSegment();
    }

    @Override
    public String getFilePath() {
        return path.toPortableString();
    }

    @Override
    public boolean isOTP() {
        final IParent parent = getParent();
        if (parent instanceof IErlExternal) {
            final IErlExternal external = (IErlExternal) parent;
            return external.isOTP();
        }
        return false;
    }

    @Override
    public IResource getResource() {
        return null;
    }

    @Override
    public boolean hasIncludes() {
        return hasHeaders;
    }

    public void setGroup(final String group) {
        this.group = group;
    }

    public String getGroup() {
        return group;
    }
}
