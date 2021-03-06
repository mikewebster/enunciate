/*
 * Copyright 2006-2008 Web Cohesion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.enunciate.contract.jaxb;

import com.sun.mirror.declaration.PackageDeclaration;
import net.sf.jelly.apt.decorations.declaration.DecoratedPackageDeclaration;
import org.codehaus.enunciate.contract.Facet;
import org.codehaus.enunciate.contract.HasFacets;

import javax.xml.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A package declaration decorated so as to be able to describe itself an XML-Schema root element.
 *
 * @author Ryan Heaton
 * @see "The JAXB 2.0 Specification"
 * @see <a href="http://www.w3.org/TR/2004/REC-xmlschema-1-20041028/structures.html">XML Schema Part 1: Structures Second Edition</a>
 */
public class Schema extends DecoratedPackageDeclaration implements Comparable<Schema>, HasFacets {

  private final XmlSchema xmlSchema;
  private final XmlAccessorType xmlAccessorType;
  private final XmlAccessorOrder xmlAccessorOrder;
  private final Set<Facet> facets = new TreeSet<Facet>();

  public Schema(PackageDeclaration delegate, Package pckg) {
    super(delegate);

    //try to load the package info on the classpath first.  This is because APT seems to have a bug
    //in that it doesn't pick up the package info of an already-compiled class.
    xmlSchema = pckg == null || pckg.getAnnotation(XmlSchema.class) == null ? getAnnotation(XmlSchema.class) : pckg.getAnnotation(XmlSchema.class);
    xmlAccessorType = pckg == null || pckg.getAnnotation(XmlAccessorType.class) == null ? getAnnotation(XmlAccessorType.class) : pckg.getAnnotation(XmlAccessorType.class);
    xmlAccessorOrder = pckg == null || pckg.getAnnotation(XmlAccessorOrder.class) == null ? getAnnotation(XmlAccessorOrder.class) : pckg.getAnnotation(XmlAccessorOrder.class);
    this.facets.addAll(Facet.gatherFacets(delegate));
  }

  /**
   * The namespace of this package, or null if none.
   *
   * @return The namespace of this package.
   */
  public String getNamespace() {
    String namespace = null;

    if (xmlSchema != null) {
      namespace = xmlSchema.namespace();
    }

    return namespace;
  }

  /**
   * The element form default of this namespace.
   *
   * @return The element form default of this namespace.
   */
  public XmlNsForm getElementFormDefault() {
    XmlNsForm form = null;

    if ((xmlSchema != null) && (xmlSchema.elementFormDefault() != XmlNsForm.UNSET)) {
      form = xmlSchema.elementFormDefault();
    }

    return form;
  }

  /**
   * The attribute form default of this namespace.
   *
   * @return The attribute form default of this namespace.
   */
  public XmlNsForm getAttributeFormDefault() {
    XmlNsForm form = null;

    if ((xmlSchema != null) && (xmlSchema.attributeFormDefault() != XmlNsForm.UNSET)) {
      form = xmlSchema.attributeFormDefault();
    }

    return form;
  }

  /**
   * The default access type for the beans in this package.
   *
   * @return The default access type for the beans in this package.
   */
  public XmlAccessType getAccessType() {
    XmlAccessType accessType = XmlAccessType.PUBLIC_MEMBER;

    if (xmlAccessorType != null) {
      accessType = xmlAccessorType.value();
    }

    return accessType;
  }

  /**
   * The default accessor order of the beans in this package.
   *
   * @return The default accessor order of the beans in this package.
   */
  public XmlAccessOrder getAccessorOrder() {
    XmlAccessOrder order = XmlAccessOrder.UNDEFINED;

    if (xmlAccessorOrder != null) {
      order = xmlAccessorOrder.value();
    }

    return order;
  }

  /**
   * Gets the specified namespace prefixes for this package.
   *
   * @return The specified namespace prefixes for this package.
   */
  public Map<String, String> getSpecifiedNamespacePrefixes() {
    HashMap<String, String> namespacePrefixes = new HashMap<String, String>();
    if (xmlSchema != null) {
      XmlNs[] xmlns = xmlSchema.xmlns();
      if (xmlns != null) {
        for (XmlNs ns : xmlns) {
          namespacePrefixes.put(ns.namespaceURI(), ns.prefix());
        }
      }
    }

    return namespacePrefixes;
  }

  /**
   * Two "schemas" are equal if they decorate the same package.
   *
   * @param schema The schema to which to compare this schema.
   * @return The comparison.
   */
  public int compareTo(Schema schema) {
    return getQualifiedName().compareTo(schema.getQualifiedName());
  }

  /**
   * The facets here applicable.
   *
   * @return The facets here applicable.
   */
  public Set<Facet> getFacets() {
    return facets;
  }
}
