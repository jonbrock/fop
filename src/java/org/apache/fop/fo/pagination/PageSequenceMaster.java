/*
 * Copyright 1999-2004 The Apache Software Foundation.
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

package org.apache.fop.fo.pagination;

// Java
import java.util.List;

// XML
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

// FOP
import org.apache.fop.fo.FObj;
import org.apache.fop.fo.FONode;
import org.apache.fop.fo.PropertyList;
import org.apache.fop.apps.FOPException;

/**
 * The page-sequence-master formatting object.
 * This class handles a list of subsequence specifiers
 * which are simple or complex references to page-masters.
 */
public class PageSequenceMaster extends FObj {
    // The value of properties relevant for fo:page-sequence-master.
    private String masterName;
    // End of property values
    
    private LayoutMasterSet layoutMasterSet;
    private List subSequenceSpecifiers;
    private SubSequenceSpecifier currentSubSequence;
    private int currentSubSequenceNumber;

    // The terminology may be confusing. A 'page-sequence-master' consists
    // of a sequence of what the XSL spec refers to as
    // 'sub-sequence-specifiers'. These are, in fact, simple or complex
    // references to page-masters. So the methods use the former
    // terminology ('sub-sequence-specifiers', or SSS),
    // but the actual FO's are MasterReferences.

    /**
     * @see org.apache.fop.fo.FONode#FONode(FONode)
     */
    public PageSequenceMaster(FONode parent) {
        super(parent);
    }

    /**
     * @see org.apache.fop.fo.FObj#bind(PropertyList)
     */
    public void bind(PropertyList pList) {
        masterName = pList.get(PR_MASTER_NAME).getString();
    }

    /**
     * @see org.apache.fop.fo.FONode#startOfNode()
     */
    protected void startOfNode() throws SAXParseException {
        subSequenceSpecifiers = new java.util.ArrayList();
        if (parent.getName().equals("fo:layout-master-set")) {
            this.layoutMasterSet = (LayoutMasterSet)parent;
            if (masterName == null) {
                getLogger().warn("page-sequence-master does not have "
                                       + "a master-name and so is being ignored");
            } else {
                this.layoutMasterSet.addPageSequenceMaster(masterName, this);
            }
        } else {
            throw new SAXParseException("fo:page-sequence-master must be child "
                                   + "of fo:layout-master-set, not "
                                   + parent.getName(), locator);
        }
    }
    
    /**
     * @see org.apache.fop.fo.FONode#endOfNode()
     */
    protected void endOfNode() throws SAXParseException {
        if (childNodes == null) {
           missingChildElementError("(single-page-master-reference|" +
            "repeatable-page-master-reference|repeatable-page-master-alternatives)+");
        }
    }

    /**
     * @see org.apache.fop.fo.FONode#validateChildNode(Locator, String, String)
     * XSL/FOP: (single-page-master-reference|repeatable-page-master-reference|
     *     repeatable-page-master-alternatives)+
     */
    protected void validateChildNode(Locator loc, String nsURI, String localName) 
        throws SAXParseException {
        if (nsURI == FO_URI) {
            if (!localName.equals("single-page-master-reference") 
                && !localName.equals("repeatable-page-master-reference")
                && !localName.equals("repeatable-page-master-alternatives")) {   
                    invalidChildError(loc, nsURI, localName);
            }
        } else {
            invalidChildError(loc, nsURI, localName);
        }
    }

    /**
     * Adds a new suqsequence specifier to the page sequence master.
     * @param pageMasterReference the subsequence to add
     */
    protected void addSubsequenceSpecifier(SubSequenceSpecifier pageMasterReference) {
        subSequenceSpecifiers.add(pageMasterReference);
    }

    /**
     * Returns the next subsequence specifier
     * @return a subsequence specifier
     */
    private SubSequenceSpecifier getNextSubSequence() {
        currentSubSequenceNumber++;
        if (currentSubSequenceNumber >= 0
            && currentSubSequenceNumber < subSequenceSpecifiers.size()) {
            return (SubSequenceSpecifier)subSequenceSpecifiers
              .get(currentSubSequenceNumber);
        }
        return null;
    }

    /**
     * Resets the subsequence specifiers subsystem.
     */
    public void reset() {
        currentSubSequenceNumber = -1;
        currentSubSequence = null;
        for (int i = 0; i < subSequenceSpecifiers.size(); i++) {
            ((SubSequenceSpecifier)subSequenceSpecifiers.get(i)).reset();
        }
    }

    /**
     * Returns the next simple-page-master.
     * @param isOddPage True if the next page number is odd
     * @param isFirstPage True if the next page is the first
     * @param isBlankPage True if the next page is blank
     * @return the requested page master
     * @throws FOPException if there's a problem determining the next page master
     */
    public SimplePageMaster getNextSimplePageMaster(boolean isOddPage,
                                                    boolean isFirstPage,
                                                    boolean isBlankPage)
                                                      throws FOPException {
        if (currentSubSequence == null) {
            currentSubSequence = getNextSubSequence();
            if (currentSubSequence == null) {
                throw new FOPException("no subsequences in page-sequence-master '"
                                       + masterName + "'");
            }
        }
        String pageMasterName = currentSubSequence
            .getNextPageMasterName(isOddPage, isFirstPage, isBlankPage);
        boolean canRecover = true;
        while (pageMasterName == null) {
            SubSequenceSpecifier nextSubSequence = getNextSubSequence();
            if (nextSubSequence == null) {
                if (!canRecover) {
                    throw new FOPException("subsequences exhausted in page-sequence-master '"
                                           + masterName
                                           + "', cannot recover");
                }
                getLogger().warn("subsequences exhausted in page-sequence-master '"
                                 + masterName
                                 + "', using previous subsequence");
                currentSubSequence.reset();
                canRecover = false;
            } else {
                currentSubSequence = nextSubSequence;
            }
            pageMasterName = currentSubSequence
                .getNextPageMasterName(isOddPage, isFirstPage, isBlankPage);
        }
        SimplePageMaster pageMaster = this.layoutMasterSet
            .getSimplePageMaster(pageMasterName);
        if (pageMaster == null) {
            throw new FOPException("No simple-page-master matching '"
                                   + pageMasterName + "' in page-sequence-master '"
                                   + masterName + "'");
        }
        return pageMaster;
    }

    /**
     * @see org.apache.fop.fo.FObj#getName()
     */
    public String getName() {
        return "fo:page-sequence-master";
    }
    
    /**
     * @see org.apache.fop.fo.FObj#getNameId()
     */
    public int getNameId() {
        return FO_PAGE_SEQUENCE_MASTER;
    }
}

