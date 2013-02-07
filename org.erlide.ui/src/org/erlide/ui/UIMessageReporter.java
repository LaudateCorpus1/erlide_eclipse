package org.erlide.ui;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.progress.UIJob;
import org.erlide.backend.MessageReporter;
import org.erlide.ui.util.PopupDialog;

public class UIMessageReporter extends MessageReporter {

    public UIMessageReporter() {
    }

    @Override
    public void displayMessage(final MessageType type, final String message,
            final ReporterPosition style) {
        new UIJob("erlide message") {
            @Override
            public IStatus runInUIThread(final IProgressMonitor monitor) {
                switch (style) {
                case MODAL:
                    PopupDialog.showModalDialog("erlide " + type, message);
                    break;
                case CENTER:
                    PopupDialog.showDialog("erlide " + type, message, 4000);
                    break;
                case CORNER:
                    PopupDialog.showBalloon("erlide " + type, message, 5000);
                    break;
                default:
                    break;
                }
                return Status.OK_STATUS;
            }
        }.schedule();
    }
}
