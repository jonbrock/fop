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

package org.apache.fop.fo.properties;

import org.apache.fop.datatypes.CompoundDatatype;
import org.apache.fop.fo.Constants;
import org.apache.fop.fo.FObj;
import org.apache.fop.fo.PropertyList;
import org.apache.fop.fo.expr.PropertyException;

/**
 * @author me
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
/**
 * This class extends Property.Maker with support for sub-properties.  
 */
public class CompoundPropertyMaker extends PropertyMaker {
    /**
     *  The list of subproperty makers supported by this compound maker.
     */ 
    private PropertyMaker[] subproperties = 
                    new PropertyMaker[Constants.COMPOUND_COUNT];

    /**
     *  The first subproperty maker which has a setByShorthand of true.
     */
    private PropertyMaker shorthandMaker = null;

    /**
     * Construct an instance of a CompoundPropertyMaker for the given property.
     * @param propId The Constant ID of the property to be made.
     */
    public CompoundPropertyMaker(int propId) {
        super(propId);
    }

    /**
     * @see org.apache.fop.fo.properties.PropertyMaker#useGeneric(PropertyMaker)
     */
    public void useGeneric(PropertyMaker generic) {
        super.useGeneric(generic);
        if (generic instanceof CompoundPropertyMaker) {
            CompoundPropertyMaker compoundGeneric = (CompoundPropertyMaker) generic;
            for (int i = 0; i < Constants.COMPOUND_COUNT; i++) {
                PropertyMaker submaker = compoundGeneric.subproperties[i];
                if (submaker != null) {
                    addSubpropMaker((PropertyMaker) submaker.clone());
                }
            }
        }
    }
    
    /**
     * Add a subproperty to this maker.
     * @param subproperty
     */
    public void addSubpropMaker(PropertyMaker subproperty) {
        // Place the base propId in the propId of the subproperty.
        subproperty.propId &= Constants.COMPOUND_MASK;
        subproperty.propId |= this.propId;
        
        subproperties[getSubpropIndex(subproperty.getPropId())] = subproperty;

        // Store the first subproperty with a setByShorthand. That subproperty
        // will be used for converting a value set on the base property.
        if (shorthandMaker == null && subproperty.setByShorthand) {
            shorthandMaker = subproperty;
        }
    }
    
    
    /**
     * Return a Maker object which is used to set the values on components
     * of compound property types, such as "space".
     * Overridden by property maker subclasses which handle
     * compound properties.
     * @param subprop The Constants ID of the component for which a Maker is to
     * returned, for example CP_OPTIMUM, if the FO attribute is
     * space.optimum='10pt'.
     * @return the Maker object specified
     */
    public PropertyMaker getSubpropMaker(int subpropId) {
        return subproperties[getSubpropIndex(subpropId)];
    }
    
    /**
     * Calculate the real value of a subproperty by unmasking and shifting
     * the value into the range [0 - (COMPOUND_COUNT-1)].
     * The value is used as index into the subproperties array. 
     * @param propId the property id of the sub property.
     * @return the array index.
     */
    private int getSubpropIndex(int subpropId) {
        return ((subpropId & Constants.COMPOUND_MASK) >>
                                    Constants.COMPOUND_SHIFT)-1;
    }

    /**
     * For compound properties which can take enumerate values.
     * Delegate the enumeration check to one of the subpropeties. 
     * @param value the string containing the property value
     * @return the Property encapsulating the enumerated equivalent of the
     * input value
     */
    protected Property checkEnumValues(String value) {
        Property result = null;
        if (shorthandMaker != null) {
            result = shorthandMaker.checkEnumValues(value);
        }
        if (result == null) {
            result = super.checkEnumValues(value);
        }
        return result;
    }

    /**
     * Return the property on the current FlowObject. Depending on the passed flags,
     * this will try to compute it based on other properties, or if it is
     * inheritable, to return the inherited value. If all else fails, it returns
     * the default value.
     * @param subpropId  The subproperty id of the property being retrieved.
     *        Is 0 when retriving a base property.
     * @param propertylist The PropertyList object being built for this FO.
     * @param bTryInherit true if inherited properties should be examined.
     * @param bTryDefault true if the default value should be returned. 
     */
    public Property get(int subpropId, PropertyList propertyList,
                        boolean bTryInherit, boolean bTryDefault)
        throws PropertyException
    {
        Property p = super.get(subpropId, propertyList, bTryInherit, bTryDefault);
        if (subpropId != 0 && p != null) {
            p = getSubprop(p, subpropId);
        }
        return p;
    }
   
    /**
     * Return a Property object based on the passed Property object.
     * This method is called if the Property object built by the parser
     * isn't the right type for this compound property.
     * @param p The Property object return by the expression parser
     * @param propertyList The PropertyList object being built for this FO.
     * @param fo The parent FO for the FO whose property is being made.
     * @return A Property of the correct type or null if the parsed value
     * can't be converted to the correct type.
     * @throws PropertyException for invalid or inconsistent FO input
     */
    protected Property convertProperty(Property p,
                                    PropertyList propertyList,
                                    FObj fo) throws PropertyException {
        // delegate to the subprop maker to do conversions
        p = shorthandMaker.convertProperty(p, propertyList, fo);
        
        if (p != null) {
            Property prop = makeCompound(propertyList, fo);
            CompoundDatatype pval = (CompoundDatatype) prop.getObject();
            for (int i = 0; i < Constants.COMPOUND_COUNT; i++) {
                PropertyMaker submaker = subproperties[i];
                if (submaker != null && submaker.setByShorthand) {
                    pval.setComponent(submaker.getPropId() & Constants.COMPOUND_MASK, p, false);
                }
            }
            return prop;
        }
        return null;
    }

    /**
     * Make a compound property with default values. 
     * @param propertyList The PropertyList object being built for this FO.
     * @return the Property object corresponding to the parameters
     * @throws PropertyException for invalid or inconsisten FO input
     */
    public Property make(PropertyList propertyList) throws PropertyException {
        if (defaultValue != null) {
            return make(propertyList, defaultValue, propertyList.getParentFObj());
        } else {
            return makeCompound(propertyList, propertyList.getParentFObj());
        }
    }
    
    /**
     * Create a Property object from an attribute specification.
     * @param propertyList The PropertyList object being built for this FO.
     * @param value The attribute value.
     * @param fo The parent FO for the FO whose property is being made.
     * @return The initialized Property object.
     * @throws PropertyException for invalid or inconsistent FO input
     */    
    public Property make(PropertyList propertyList, String value,
                         FObj fo) throws PropertyException {
        Property p = super.make(propertyList, value, fo);
        p = convertProperty(p, propertyList, fo);
        return p; 
    }
    
    /**
     * Return a property value for a compound property. If the property
     * value is already partially initialized, this method will modify it.
     * @param baseProp The Property object representing the compound property,
     * for example: SpaceProperty.
     * @param subpropId The Constants ID of the subproperty (component)
     *        whose value is specified.
     * @param propertyList The propertyList being built.
     * @param fo The parent FO for the FO whose property is being made.
     * @param value the value of the
     * @return baseProp (or if null, a new compound property object) with
     * the new subproperty added
     * @throws PropertyException for invalid or inconsistent FO input
     */
    public Property make(Property baseProp, int subpropId,
                         PropertyList propertyList, String value,
                         FObj fo) throws PropertyException {
        if (baseProp == null) {
            baseProp = makeCompound(propertyList, fo);
        }

        PropertyMaker spMaker = getSubpropMaker(subpropId);

        if (spMaker != null) {
            Property p = spMaker.make(propertyList, value, fo);
            if (p != null) {
                return setSubprop(baseProp, subpropId & Constants.COMPOUND_MASK, p);
            }
        } else {
            //getLogger().error("compound property component "
            //                       + partName + " unknown.");
        }
        return baseProp;
    }
    
    /**
     * Create a empty compound property and fill it with default values for
     * the subproperties.
     * @param propertyList The propertyList being built.
     * @param parentFO The parent FO for the FO whose property is being made.
     * @return a Property subclass object holding a "compound" property object
     *         initialized to the default values for each component.
     * @throws PropertyException
     */
    protected Property makeCompound(PropertyList propertyList, FObj parentFO)
        throws PropertyException
    {
        Property p = makeNewProperty();
        CompoundDatatype data = (CompoundDatatype) p.getObject();
        for (int i = 0; i < Constants.COMPOUND_COUNT; i++) {
            PropertyMaker submaker = subproperties[i];
            if (submaker != null) {
                Property subprop = submaker.make(propertyList);
                data.setComponent(submaker.getPropId() & Constants.COMPOUND_MASK, subprop, true);
            }
        }
        return p;
    }    
}