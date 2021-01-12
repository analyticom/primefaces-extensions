/*
 * Copyright (c) 2011-2021 PrimeFaces Extensions
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.primefaces.extensions.component.social;

import java.io.IOException;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;

import org.primefaces.extensions.util.Attrs;
import org.primefaces.extensions.util.ExtLangUtils;
import org.primefaces.renderkit.CoreRenderer;
import org.primefaces.util.EscapeUtils;
import org.primefaces.util.HTML;
import org.primefaces.util.LangUtils;
import org.primefaces.util.WidgetBuilder;

/**
 * Renderer for the {@link Social} component.
 *
 * @author Melloware mellowaredev@gmail.com
 * @since 6.2
 */
public class SocialRenderer extends CoreRenderer {

    /**
     * {@inheritDoc}
     */
    @Override
    public void decode(final FacesContext context, final UIComponent component) {
        decodeBehaviors(context, component);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void encodeEnd(final FacesContext context, final UIComponent component) throws IOException {
        final Social social = (Social) component;
        encodeMarkup(context, social);
        encodeScript(context, social);
    }

    /**
     * Create the HTML markup for the DOM.
     */
    private void encodeMarkup(final FacesContext context, final Social social) throws IOException {
        final ResponseWriter writer = context.getResponseWriter();
        final String clientId = social.getClientId(context);
        final String widgetVar = social.resolveWidgetVar();
        final String styleClass = getStyleClassBuilder(context)
                    .add(social.getTheme())
                    .add(social.getStyleClass())
                    .build();

        writer.startElement("div", social);
        writer.writeAttribute("id", clientId, "id");
        writer.writeAttribute(HTML.WIDGET_VAR, widgetVar, null);
        writer.writeAttribute(Attrs.CLASS, styleClass, "styleClass");
        if (social.getStyle() != null) {
            writer.writeAttribute(Attrs.STYLE, social.getStyle(), Attrs.STYLE);
        }
        writer.endElement("div");
    }

    /**
     * Create the Javascript.
     */
    private void encodeScript(final FacesContext context, final Social social) throws IOException {
        final WidgetBuilder wb = getWidgetBuilder(context);
        wb.init("ExtSocial", social);
        wb.attr("showLabel", social.isShowLabel());
        wb.attr("shareIn", social.getShareIn());
        if (!LangUtils.isValueBlank(social.getUrl())) {
            wb.attr("url", social.getUrl());
        }
        if (!LangUtils.isValueBlank(social.getText())) {
            wb.attr("text", social.getText());
        }

        final boolean showCount = Boolean.parseBoolean(social.getShowCount());
        if (showCount) {
            wb.attr("showCount", true);
        }
        else {
            if (social.getShowCount().equalsIgnoreCase("inside")) {
                wb.attr("showCount", social.getShowCount());
            }
            else {
                wb.attr("showCount", false);
            }
        }

        // shares array
        if (social.getShares() != null) {
            wb.append(",shares: [");
            final String[] shares = social.getShares().split(",");
            for (int i = 0; i < shares.length; i++) {
                // { share: "pinterest", media: "http://mysite.com" },
                final String share = ExtLangUtils.lowerCase(shares[i]);
                if (LangUtils.isValueBlank(share)) {
                    continue;
                }
                if (i != 0) {
                    wb.append(",");
                }
                wb.append("{");
                addShareProperty(wb, share);
                if ("twitter".equalsIgnoreCase(share)) {
                    wb.attr("via", social.getTwitterUsername());
                    wb.attr("hashtags", social.getTwitterHashtags());
                }
                if ("email".equalsIgnoreCase(share)) {
                    wb.attr("to", social.getEmailTo());
                }
                if ("pinterest".equalsIgnoreCase(share)) {
                    wb.attr("media", social.getPinterestMedia());
                }
                wb.append("}");
            }
            wb.append("]");
        }

        // javascript
        wb.append(",on: {");
        if (social.getOnclick() != null) {
            addCallback(wb, "click", "function(e)", social.getOnclick());
        }
        if (social.getOnmouseenter() != null) {
            addCallback(wb, "mouseenter", "function(e)", social.getOnmouseenter());
        }
        if (social.getOnmouseleave() != null) {
            addCallback(wb, "mouseleave", "function(e)", social.getOnmouseleave());
        }
        wb.append("}");

        encodeClientBehaviors(context, social);

        wb.finish();
    }

    private void addShareProperty(final WidgetBuilder wb, final String value) throws IOException {
        if (value != null) {
            wb.append("share");
            wb.append(":\"");
            wb.append(EscapeUtils.forJavaScriptAttribute(value));
            wb.append("\"");
        }
    }

    public void addCallback(final WidgetBuilder wb, final String name, final String signature, final String callback) throws IOException {
        if (callback != null) {
            wb.append(name);
            wb.append(":");
            wb.append(signature);
            wb.append("{");
            wb.append(callback);
            wb.append("},");
        }
    }

}
