package org.erlide.ui.wizards;

import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.xtext.xbase.lib.InputOutput;
import org.erlide.engine.NewProjectData;
import org.erlide.engine.model.root.ProjectConfigType;

@SuppressWarnings("all")
public class ConfigSelectionListener implements SelectionListener {
    private final NewProjectData info;

    public ConfigSelectionListener(final NewProjectData info) {
        this.info = info;
    }

    @Override
    public void widgetDefaultSelected(final SelectionEvent e) {
    }

    @Override
    public void widgetSelected(final SelectionEvent e) {
        final Object _data = e.widget.getData();
        info.setConfigType((ProjectConfigType) _data);
        final ProjectConfigType _configType = info.getConfigType();
        final String _plus = "ws: " + _configType;
        InputOutput.<String> println(_plus);
    }
}
