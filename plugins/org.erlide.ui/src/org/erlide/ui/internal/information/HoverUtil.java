package org.erlide.ui.internal.information;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.browser.LocationEvent;
import org.erlide.engine.model.IErlElement;
import org.erlide.ui.internal.ErlideUIPlugin;
import org.erlide.util.ErlLogger;
import org.erlide.util.ErlangFunctionCall;
import org.osgi.framework.Bundle;

public class HoverUtil {

	public static ErlangFunctionCall eventToErlangFunctionCall(final String moduleName0, final LocationEvent event) {
		String moduleName = moduleName0;
		final String location = event.location;
		final int hashPos = location.lastIndexOf('#');
		if (hashPos > 0) {
			String name = location.substring(hashPos + 1);
			final int colonPos = name.lastIndexOf(':');
			if (colonPos > 0) {
				name = name.substring(colonPos + 1);
			}
			final int slashPos = location.lastIndexOf('/');
			final int dotPos = location.lastIndexOf('.');
			if (slashPos > 0 && dotPos > 0) {
				moduleName = location.substring(slashPos + 1, dotPos);
			}
			final int quotePos = moduleName.lastIndexOf('\'');
			if (quotePos >= 0) {
				moduleName = moduleName.substring(quotePos + 1);
			}
			final int minusPos = name.lastIndexOf('-');
			if (minusPos > 0) {
				final String s = name.substring(minusPos + 1);
				name = name.substring(0, minusPos);
				try {
					final int i = Integer.parseInt(s);
					final ErlangFunctionCall erlangFunctionCall = new ErlangFunctionCall(moduleName, name, i);
					ErlLogger.debug("%s", erlangFunctionCall);
					return erlangFunctionCall;
				} catch (final NumberFormatException e) {
				}
			}
		}
		return null;
	}

	public static String getHTML(final StringBuffer buffer) {
		String result = buffer.toString();

		//MarkupParser p = new MarkupParser();
		//		p.setMarkupLanguage(new MarkdownLanguage());
		//		result = p.parseToHtml(result);
		result = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head><body>" + result + "</body></html>";
		if (result.length() > 0) {
			result = result.replace("javascript:erlhref('", "");
			result = result.replace("');\">", "\">");
		}
		//		result = result.replaceFirst("<?[^>]+>", "");

		String sheet = "<link rel=\"stylesheet\" type=\"text/css\" href=\"" + getStyleSheet() + "\">";
		//		ColorRegistry colorRegistry = JFaceResources.getColorRegistry();
		//		Color foreground = colorRegistry.get("org.eclipse.ui.workbench.HOVER_FOREGROUND"); //$NON-NLS-1$
		//		Color background = colorRegistry.get("org.eclipse.ui.workbench.HOVER_BACKGROUND"); //$NON-NLS-1$
		String style = sheet + "<style type='text/css'>body { " + //$NON-NLS-1$
				"font-family: " + JFaceResources.getDefaultFontDescriptor().getFontData()[0].getName() + "; " + //$NON-NLS-1$ //$NON-NLS-2$
				"font-size: " + Integer.toString(JFaceResources.getDefaultFontDescriptor().getFontData()[0].getHeight()) + "pt; " + //$NON-NLS-1$ //$NON-NLS-2$
				//				(background != null ? "background-color: " + toHTMLrgb(background.getRGB()) + "; " : "") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				//				(foreground != null ? "color: " + toHTMLrgb(foreground.getRGB()) + "; " : "") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				" }</style>"; //$NON-NLS-1$

		int headIndex = result.indexOf("<head>"); //$NON-NLS-1$
		StringBuilder builder = new StringBuilder(result.length() + style.length());
		builder.append(result.substring(0, headIndex + "<head>".length())); //$NON-NLS-1$
		builder.append(style);
		builder.append(result.substring(headIndex + "<head>".length())); //$NON-NLS-1$
		String html = builder.toString();
		html = html.replace("\u00C2\u00A0", "&nbsp;");

		return html;
	}

	public static URL getDocumentationLocation(final IErlElement element) {
		return null;
	}

	public static URL getDocumentationURL(final String docPath, final String anchor) {
		if (docPath != null) {
			try {
				// return new URL("file:" + docPath + "#" + anchor);
				final File file = new File(docPath);
				URL url = file.toURI().toURL();
				if (anchor != null && anchor.length() > 0) {
					url = new URL(url, "#" + anchor);
				}
				return url;
			} catch (final MalformedURLException e) {
			}
		}
		return null;
	}

	public static URL getStyleSheet() {
		final Bundle bundle = Platform.getBundle(ErlideUIPlugin.PLUGIN_ID);
		URL fgStyleSheet = bundle.getEntry("/edoc.css"); //$NON-NLS-1$
		if (fgStyleSheet != null) {
			try {
				fgStyleSheet = FileLocator.toFileURL(fgStyleSheet);
			} catch (final Exception e) {
			}
		}
		return fgStyleSheet;
	}
}
