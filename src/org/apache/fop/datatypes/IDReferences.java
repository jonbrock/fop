/*-- $Id$ -- 

 ============================================================================
                   The Apache Software License, Version 1.1
 ============================================================================
 
    Copyright (C) 1999 The Apache Software Foundation. All rights reserved.
 
 Redistribution and use in source and binary forms, with or without modifica-
 tion, are permitted provided that the following conditions are met:
 
 1. Redistributions of  source code must  retain the above copyright  notice,
    this list of conditions and the following disclaimer.
 
 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.
 
 3. The end-user documentation included with the redistribution, if any, must
    include  the following  acknowledgment:  "This product includes  software
    developed  by the  Apache Software Foundation  (http://www.apache.org/)."
    Alternately, this  acknowledgment may  appear in the software itself,  if
    and wherever such third-party acknowledgments normally appear.
 
 4. The names "FOP" and  "Apache Software Foundation"  must not be used to
    endorse  or promote  products derived  from this  software without  prior
    written permission. For written permission, please contact
    apache@apache.org.
 
 5. Products  derived from this software may not  be called "Apache", nor may
    "Apache" appear  in their name,  without prior written permission  of the
    Apache Software Foundation.
 
 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 FITNESS  FOR A PARTICULAR  PURPOSE ARE  DISCLAIMED.  IN NO  EVENT SHALL  THE
 APACHE SOFTWARE  FOUNDATION  OR ITS CONTRIBUTORS  BE LIABLE FOR  ANY DIRECT,
 INDIRECT, INCIDENTAL, SPECIAL,  EXEMPLARY, OR CONSEQUENTIAL  DAMAGES (INCLU-
 DING, BUT NOT LIMITED TO, PROCUREMENT  OF SUBSTITUTE GOODS OR SERVICES; LOSS
 OF USE, DATA, OR  PROFITS; OR BUSINESS  INTERRUPTION)  HOWEVER CAUSED AND ON
 ANY  THEORY OF LIABILITY,  WHETHER  IN CONTRACT,  STRICT LIABILITY,  OR TORT
 (INCLUDING  NEGLIGENCE OR  OTHERWISE) ARISING IN  ANY WAY OUT OF THE  USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 
 This software  consists of voluntary contributions made  by many individuals
 on  behalf of the Apache Software  Foundation and was  originally created by
 James Tauber <jtauber@jtauber.com>. For more  information on the Apache 
 Software Foundation, please see <http://www.apache.org/>.
 
 */

package org.apache.fop.datatypes;

import org.apache.fop.pdf.PDFGoTo;


// Java
import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import org.apache.fop.layout.Area;
import org.apache.fop.apps.FOPException;


public class IDReferences {
    private Hashtable idReferences;
    private Vector idValidation;

    final static int ID_PADDING = 5000; // space to add before id y position

    /**
     * Constructor for IDReferences
     */
    public IDReferences()
    {
        idReferences = new Hashtable();
        idValidation = new Vector();
    }


    /**
     * Initializes the specified id.  This should be called everytime an id is encountered
     * 
     * @param id     The id to initialize
     * @param area   The area where this id was encountered
     * @exception FOPException
     */
    public void initializeID(String id, Area area) throws FOPException
    {
        if ( id!=null && !id.equals("") ) {
            if ( doesIDExist(id) ) {
                throw new FOPException("The id \""+id+"\" already exists in this document");
            }
            else {
                createNewId(id);
                removeFromIdValidationList(id);                 
                setYPosition(id,area.getPage().getBody().getYPosition() - area.getAbsoluteHeight()+ID_PADDING);                    
                area.getPage().addToIDList(id);                                    
            }
        }
    }

    /**
     * Adds id to validation list to be validated .  This should be used if it is unsure whether the id is valid
     * 
     * @param id     id to be added
     */
    public void addToIdValidationList(String id)
    {     
        idValidation.addElement(id);
    }

    

    /**
     * Removes id from validation list. This should be used if the id has been determined to be valid
     * 
     * @param id     the id to remove
     */
    public void removeFromIdValidationList(String id)
    {        
        idValidation.removeElement(id);
    }

    /**
     * Determines whether all id's are valid
     * 
     * @return true if all id's are valid, false otherwise
     */
    public boolean isEveryIdValid()
    {
        return (idValidation.size()==0);
    }



    /**
     * Returns the first invalid id still remaining in the validation list
     * 
     * @return first id in validation list
     */
    public String getNextInvalidId()
    {
        String id;
        try
        {            
            id = idValidation.firstElement().toString();
        }
        catch(NoSuchElementException nsee)
        {            
            id=null;    // should probably report error
        }
        return id;
    }



    /**
     * Determines whether specified id already exists in IDReferences
     * 
     * @param id     the id to search for
     * @return true if ID was found, false otherwise
     */
    public boolean doesIDExist(String id)
    {
        return idReferences.containsKey(id);
    }


    /**
     * Determines whether the GoTo reference for the specified id is defined
     * 
     * @param id     the id to search for
     * @return true if GoTo reference is defined, false otherwise
     */
    public boolean doesGoToReferenceExist(String id)
    {
        IDNode node = (IDNode)idReferences.get(id);        
        return node.isThereInternalLinkGoTo();
    }




    /**
     * Returns the reference to the GoTo object used for the internal link
     * 
     * @param id     the id whose reference to use
     * @return reference to GoTo object
     */
    public String getInternalLinkGotToReference(String id)
    {
        IDNode node = (IDNode)idReferences.get(id);
        return node.getInternalLinkGoToReference();
    }
   


    /**
     * creates an Internal Link GoTo object for this id
     * 
     * @param id     The id for which to set the Internal Link Go To
     * @param objectNumber
     *               The object number to use for the GoTo object
     * @return the object reference of the new GoTo object
     */
    public String createInternalLinkGoTo(String id, int objectNumber)
    {
        IDNode node = (IDNode)idReferences.get(id);   // retrieve id node
        node.createInternalLinkGoTo(objectNumber); // create Internal Link GoTo object
        return node.getInternalLinkGoToReference();  //return Internal Link Go To object reference
    }



    /**
     * Adds an id to IDReferences
     * 
     * @param id     the id to add
     */
    public void createNewId(String id)
    {        
        IDNode node=new IDNode(id);
        idReferences.put(id,node);
    }


    /**
     * Returns the PDFGoTo object for the specified id
     * 
     * @param id     the id for which the PDFGoTo to be retrieved is associated
     * @return the PDFGoTo object associated with the specified id
     */
    public PDFGoTo getPDFGoTo(String id)
    {
        IDNode node=(IDNode)idReferences.get(id);        
        return node.getInternalLinkGoTo();
    }


    /**
     * sets the page reference for the internal link's GoTo.  The GoTo will jump to this page reference.
     * 
     * @param pageReference
     *               the page reference to which the internal link GoTo should jump
     *               ex. 23 0 R
     */
    public void setInternalGoToPageReference(String id, String pageReference)
    {
        IDNode node=(IDNode)idReferences.get(id);
        node.setInternalLinkGoToPageReference(pageReference);
    }


    /**
     * Sets the x position of specified id
     * 
     * @param id     the id whose x position is to be set
     * @param y      y position of id
     */
    public void setYPosition(String id, int y)
    {
        IDNode node=(IDNode)idReferences.get(id);
        node.setYPosition(y);
     }
       
}
