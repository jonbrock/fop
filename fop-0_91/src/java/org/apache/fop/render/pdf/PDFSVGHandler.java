/*
 * Copyright 1999-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.render.pdf;

import java.io.OutputStream;
import java.awt.Color;
import java.awt.geom.AffineTransform;

import org.w3c.dom.Document;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;

import org.apache.fop.render.Renderer;
import org.apache.fop.render.XMLHandler;
import org.apache.fop.render.RendererContext;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFNumber;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFState;
import org.apache.fop.pdf.PDFStream;
import org.apache.fop.pdf.PDFResourceContext;
import org.apache.fop.svg.PDFBridgeContext;
import org.apache.fop.svg.PDFGraphics2D;
import org.apache.fop.svg.SVGUserAgent;
import org.apache.fop.fonts.FontInfo;

// Commons-Logging
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.avalon.framework.configuration.Configuration;

import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.ViewBox;
import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.gvt.GraphicsNode;

/**
 * PDF XML handler for SVG (uses Apache Batik).
 * This handler handles XML for foreign objects when rendering to PDF.
 * It renders SVG to the PDF document using the PDFGraphics2D.
 * The properties from the PDF renderer are subject to change.
 */
public class PDFSVGHandler implements XMLHandler, PDFRendererContextConstants {

    /** logging instance */
    private static Log log = LogFactory.getLog(PDFSVGHandler.class);


    /**
     * Create a new PDF XML handler for use by the PDF renderer.
     */
    public PDFSVGHandler() {
    }

    /** @see org.apache.fop.render.XMLHandler */
    public void handleXML(RendererContext context, 
                Document doc, String ns) throws Exception {
        PDFInfo pdfi = getPDFInfo(context);

        String svg = "http://www.w3.org/2000/svg";
        if (svg.equals(ns)) {
            renderSVGDocument(context, doc, pdfi);
        }
    }

    /**
     * Get the pdf information from the render context.
     *
     * @param context the renderer context
     * @return the pdf information retrieved from the context
     */
    public static PDFInfo getPDFInfo(RendererContext context) {
        PDFInfo pdfi = new PDFInfo();
        pdfi.pdfDoc = (PDFDocument)context.getProperty(PDF_DOCUMENT);
        pdfi.outputStream = (OutputStream)context.getProperty(OUTPUT_STREAM);
        pdfi.pdfState = (PDFState)context.getProperty(PDF_STATE);
        pdfi.pdfPage = (PDFPage)context.getProperty(PDF_PAGE);
        pdfi.pdfContext = (PDFResourceContext)context.getProperty(PDF_CONTEXT);
        pdfi.currentStream = (PDFStream)context.getProperty(PDF_STREAM);
        pdfi.width = ((Integer)context.getProperty(WIDTH)).intValue();
        pdfi.height = ((Integer)context.getProperty(HEIGHT)).intValue();
        pdfi.fi = (FontInfo)context.getProperty(PDF_FONT_INFO);
        pdfi.currentFontName = (String)context.getProperty(PDF_FONT_NAME);
        pdfi.currentFontSize = ((Integer)context.getProperty(PDF_FONT_SIZE)).intValue();
        pdfi.currentXPosition = ((Integer)context.getProperty(XPOS)).intValue();
        pdfi.currentYPosition = ((Integer)context.getProperty(YPOS)).intValue();
        pdfi.cfg = (Configuration)context.getProperty(HANDLER_CONFIGURATION);
        return pdfi;
    }

    /**
     * PDF information structure for drawing the XML document.
     */
    public static class PDFInfo {
        /** see PDF_DOCUMENT */
        public PDFDocument pdfDoc;
        /** see OUTPUT_STREAM */
        public OutputStream outputStream;
        /** see PDF_STATE */
        public PDFState pdfState;
        /** see PDF_PAGE */
        public PDFPage pdfPage;
        /** see PDF_CONTEXT */
        public PDFResourceContext pdfContext;
        /** see PDF_STREAM */
        public PDFStream currentStream;
        /** see PDF_WIDTH */
        public int width;
        /** see PDF_HEIGHT */
        public int height;
        /** see PDF_FONT_INFO */
        public FontInfo fi;
        /** see PDF_FONT_NAME */
        public String currentFontName;
        /** see PDF_FONT_SIZE */
        public int currentFontSize;
        /** see PDF_XPOS */
        public int currentXPosition;
        /** see PDF_YPOS */
        public int currentYPosition;
        /** see PDF_HANDLER_CONFIGURATION */
        public Configuration cfg;
    }

    /**
     * Render the svg document.
     * @param context the renderer context
     * @param doc the svg document
     * @param pdfInfo the pdf information of the current context
     */
    protected void renderSVGDocument(RendererContext context,
            Document doc, PDFInfo pdfInfo) {
        int xOffset = pdfInfo.currentXPosition;
        int yOffset = pdfInfo.currentYPosition;

        log.debug("Generating SVG at " 
                + context.getUserAgent().getTargetResolution()
                + "dpi.");
        final float deviceResolution = context.getUserAgent().getTargetResolution();
        
        final float uaResolution = context.getUserAgent().getSourceResolution();
        SVGUserAgent ua = new SVGUserAgent(25.4f / uaResolution, new AffineTransform());

        GVTBuilder builder = new GVTBuilder();
        
        //TODO This AffineTransform here has to be fixed!!! 
        AffineTransform linkTransform = pdfInfo.pdfState.getTransform();
        linkTransform.translate(xOffset / 1000f, yOffset / 1000f);

        //Controls whether text painted by Batik is generated using text or path operations
        boolean strokeText = false;
        Configuration cfg = pdfInfo.cfg;
        if (cfg != null) {
            strokeText = cfg.getChild("stroke-text", true).getValueAsBoolean(strokeText);
        }
        
        BridgeContext ctx = new PDFBridgeContext(ua, 
                (strokeText ? null : pdfInfo.fi),
                linkTransform);
        
        GraphicsNode root;
        try {
            root = builder.build(ctx, doc);
        } catch (Exception e) {
            log.error("svg graphic could not be built: "
                                   + e.getMessage(), e);
            return;
        }
        // get the 'width' and 'height' attributes of the SVG document
        float w = (float)ctx.getDocumentSize().getWidth() * 1000f;
        float h = (float)ctx.getDocumentSize().getHeight() * 1000f;

        float sx = pdfInfo.width / (float)w;
        float sy = pdfInfo.height / (float)h;

        ctx = null;
        builder = null;

        /*
         * Clip to the svg area.
         * Note: To have the svg overlay (under) a text area then use
         * an fo:block-container
         */
        PDFRenderer renderer = (PDFRenderer)context.getRenderer();
        renderer.saveGraphicsState();
        renderer.setColor(Color.black, false, null);
        renderer.setColor(Color.black, true, null);
        // transform so that the coordinates (0,0) is from the top left
        // and positive is down and to the right. (0,0) is where the
        // viewBox puts it.
        pdfInfo.currentStream.add(sx + " 0 0 " + sy + " " + xOffset / 1000f + " "
                          + yOffset / 1000f + " cm\n");

        SVGSVGElement svg = ((SVGDocument)doc).getRootElement();
        //AffineTransform at = ViewBox.getPreserveAspectRatioTransform(
        //                          svg, w / 1000f, h / 1000f);
        AffineTransform at = ViewBox.getPreserveAspectRatioTransform(svg,
                pdfInfo.width / 1000f, pdfInfo.height / 1000f);
        /*
        if (!at.isIdentity()) {
            double[] vals = new double[6];
            at.getMatrix(vals);
            pdfInfo.currentStream.add(CTMHelper.toPDFString(at, false) + " cm\n");
        }*/

        if (pdfInfo.pdfContext == null) {
            pdfInfo.pdfContext = pdfInfo.pdfPage;
        }
        PDFGraphics2D graphics = new PDFGraphics2D(true, pdfInfo.fi, 
                pdfInfo.pdfDoc,
                pdfInfo.pdfContext, pdfInfo.pdfPage.referencePDF(),
                pdfInfo.currentFontName, pdfInfo.currentFontSize);
        graphics.setGraphicContext(new org.apache.batik.ext.awt.g2d.GraphicContext());
        pdfInfo.pdfState.push();
        AffineTransform transform = new AffineTransform();
        // scale to viewbox
        transform.translate(xOffset / 1000f, yOffset / 1000f);

        if (deviceResolution != uaResolution) {
            //Scale for higher resolution on-the-fly images from Batik
            double s = uaResolution / deviceResolution;
            at.scale(s, s);
            pdfInfo.currentStream.add("" + PDFNumber.doubleOut(s) + " 0 0 "
                                + PDFNumber.doubleOut(s) + " 0 0 cm\n");
            graphics.scale(1 / s, 1 / s);
        }

        pdfInfo.pdfState.setTransform(transform);
        graphics.setPDFState(pdfInfo.pdfState);
        graphics.setOutputStream(pdfInfo.outputStream);
        try {
            root.paint(graphics);
            pdfInfo.currentStream.add(graphics.getString());
        } catch (Exception e) {
            log.error("svg graphic could not be rendered: "
                                   + e.getMessage(), e);
        }

        renderer.restoreGraphicsState();
        pdfInfo.pdfState.pop();
    }
    
    /** @see org.apache.fop.render.XMLHandler#supportsRenderer(org.apache.fop.render.Renderer) */
    public boolean supportsRenderer(Renderer renderer) {
        return (renderer instanceof PDFRenderer);
    }
    /** @see org.apache.fop.render.XMLHandler#getNamespace() */
    public String getNamespace() {
        return SVGDOMImplementation.SVG_NAMESPACE_URI;
    }
    
}