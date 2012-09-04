/*
 *  BEEN: Benchmarking Environment
 *  ==============================
 *
 *  File author: David Majda
 *
 *  GNU Lesser General Public License Version 2.1
 *  ---------------------------------------------
 *  Copyright (C) 2004-2006 Distributed Systems Research Group,
 *  Faculty of Mathematics and Physics, Charles University in Prague
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License version 2.1, as published by the Free Software Foundation.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 *  MA  02111-1307  USA
 */
package cz.cuni.mff.been.softwarerepository;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import cz.cuni.mff.been.common.Version;

/**
 * Version metadata attribute helper class.
 * 
 * @author David Majda
 */
public class VersionAttributeHelper extends AttributeHelper<Version> {
	/** Class instance (singleton pattern). */
	private static VersionAttributeHelper instance;

	/**
	 * Allocates a new <code>VersionAttributeHelper</code> object. Construcor is private so only
	 * instance in <code>instance</code> field can be constructed (singleton
	 * pattern).
	 */
	private VersionAttributeHelper() {
		super();
	}
	
	/**
	 * @see cz.cuni.mff.been.softwarerepository.AttributeHelper#validateInXML(org.w3c.dom.Element)
	 */
	@Override
	public String validateInXML(Element element) {
		return extractTextValueFromElement(element) == null
			? "Invalid value of element <" + element.getNodeName() + ">."
			: null;
	}
	
	/**
	 * @see cz.cuni.mff.been.softwarerepository.AttributeHelper#readValueFromElement(org.w3c.dom.Element)
	 */
	@Override
	public Version readValueFromElement(Element element) {
		return new Version(extractTextValueFromElement(element));
	}

	@Override
	public Element writeValueToElement(Document document, String tagName, Version value) {
		if (value != null) {
			Element result = document.createElement(tagName);
			result.appendChild(document.createTextNode(value.toString()));
			return result;
		} else {
			return null;
		}
	}
	
	/**
	 * Returns the only class instance (singleton pattern).
	 * 
	 * @return class instance
	 */
	public static VersionAttributeHelper getInstance() {
		if (instance == null) {
			 instance = new VersionAttributeHelper();
		}
		return instance;
	}
}