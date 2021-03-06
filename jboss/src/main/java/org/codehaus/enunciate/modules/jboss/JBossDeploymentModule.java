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

package org.codehaus.enunciate.modules.jboss;

import com.sun.mirror.declaration.TypeDeclaration;
import freemarker.template.TemplateException;
import org.apache.commons.digester.RuleSet;
import org.codehaus.enunciate.EnunciateException;
import org.codehaus.enunciate.apt.EnunciateClasspathListener;
import org.codehaus.enunciate.apt.EnunciateFreemarkerModel;
import org.codehaus.enunciate.config.EnunciateConfiguration;
import org.codehaus.enunciate.config.WsdlInfo;
import org.codehaus.enunciate.config.war.WebAppConfig;
import org.codehaus.enunciate.contract.jaxrs.ResourceMethod;
import org.codehaus.enunciate.contract.jaxrs.RootResource;
import org.codehaus.enunciate.contract.jaxws.EndpointInterface;
import org.codehaus.enunciate.contract.validation.ValidationException;
import org.codehaus.enunciate.contract.validation.Validator;
import org.codehaus.enunciate.jboss.EnunciateJBossHttpServletDispatcher;
import org.codehaus.enunciate.main.Enunciate;
import org.codehaus.enunciate.main.webapp.BaseWebAppFragment;
import org.codehaus.enunciate.main.webapp.WebAppComponent;
import org.codehaus.enunciate.modules.FreemarkerDeploymentModule;
import org.codehaus.enunciate.modules.SpecProviderModule;
import org.codehaus.enunciate.modules.jboss.config.JBossRuleSet;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.*;

/**
 * <h1>JBoss Module</h1>
 *
 * <p>The JBoss module assembles a JBoss-based server-side application for hosting the WS endpoints.</p>
 *
 * <ul>
 *   <li><a href="#steps">steps</a></li>
 *   <li><a href="#config">configuration</a></li>
 *   <li><a href="#artifacts">artifacts</a></li>
 * </ul>
 *
 * <h1><a name="steps">Steps</a></h1>
 *
 * <h3>generate</h3>
 * 
 * <p>The "generate" step generates the configuration files.</p>
 *
 * <h1><a name="config">Configuration</a></h1>
 *
 * <h3>Content Negotiation</h3>
 *
 * <p>Enuncite provides content type negotiation (conneg) to Jersey that conforms to the <a href="module_rest.html#contentTypes">content type negotiation of
 * the Enunciate REST module</a>.  This means that each resource is mounted from the REST subcontext (see above) but ALSO from a subcontext that conforms to the
 * id of each content type that the resource supports.  So, if the content type id of the "application/xml" content type is "xml" then the resource at path
 * "mypath" will be mounted at both "/rest/mypath" and "/xml/mypath".</p>
 *
 * <p>The content types for each JAX-RS resource are declared by the @Produces annotation. The content type ids are customized with the
 * "enunciate/services/rest/content-types" element in the Enunciate configuration. Enunciate supplies providers for the "application/xml" and "application/json"
 * content types by default.</p>
 *
 * <p>The JBoss module supports the following configuration attributes:</p>
 *
 * <ul>
 *   <li>The "useSubcontext" attribute is used to enable/disable mounting the JAX-RS resources at the rest subcontext. Default: "true".</li>
 *   <li>The "usePathBasedConneg" attribute is used to enable/disable path-based conneg (see above). Default: "false".</a></li>
 *   <li>The "enableJaxws" attribute (boolean) can be used to disable the JAX-WS support, leaving the JAX-WS support to another module if necessary. Default: true</li>
 *   <li>The "enableJaxrs" attribute (boolean) can be used to disable the JAX-RS (RESTEasy) support, leaving the JAX-RS support to another module if necessary. Default: true</li>
 * </ul>
 *
 * <p>The JBoss module also supports a list of <tt>option</tt> child elements that each support a 'name' and 'value' attribute. This can be used to configure the RESTEasy
 * mechanism, and the properties will be passed along as context parameters.
 * <a href="http://docs.jboss.org/resteasy/docs/2.0.0.GA/userguide/html/Installation_Configuration.html#d0e72">See the RESTEasy docs for details</a>.</p>
 *
 * <h1><a name="artifacts">Artifacts</a></h1>
 *
 * <p>The JBoss deployment module exports no artifacts.</p>
 *
 * @author Ryan Heaton
 * @docFileName module_jboss.html
 */
public class JBossDeploymentModule extends FreemarkerDeploymentModule implements EnunciateClasspathListener, SpecProviderModule {

  private boolean enableJaxrs = true;
  private boolean enableJaxws = true;
  private boolean useSubcontext = true;
  private boolean jacksonAvailable = false;
  private boolean usePathBasedConneg = false;
  private final Map<String, String> options = new TreeMap<String, String>();

  /**
   * @return "jboss"
   */
  @Override
  public String getName() {
    return "jboss";
  }

  // Inherited.
  public void onClassesFound(Set<String> classes) {
    jacksonAvailable |= classes.contains("org.jboss.resteasy.plugins.providers.jackson.ResteasyJacksonProvider");
  }

  // Inherited.
  @Override
  public void initModel(EnunciateFreemarkerModel model) {
    super.initModel(model);

    if (!isDisabled()) {
      if (enableJaxrs) {
        Map<String, String> contentTypes2Ids = model.getContentTypesToIds();

        if (getEnunciate().isModuleEnabled("amf")) { //if the amf module is enabled, we'll add amf rest endpoints.
          contentTypes2Ids.put("application/x-amf", "amf");
        }
        else {
          debug("AMF module has been disabled, so it's assumed the REST endpoints won't be available in AMF format.");
        }

        if (jacksonAvailable) {
          contentTypes2Ids.put("application/json", "json"); //if we can load jackson, we've got json.
        }
        else {
          debug("Couldn't find Jackson on the classpath, so it's assumed the REST endpoints aren't available in JSON format.");
        }

        for (RootResource resource : model.getRootResources()) {
          for (ResourceMethod resourceMethod : resource.getResourceMethods(true)) {
            Map<String, Set<String>> subcontextsByContentType = new HashMap<String, Set<String>>();
            String subcontext = this.useSubcontext ? getRestSubcontext() : "";
            debug("Resource method %s of resource %s to be made accessible at subcontext \"%s\".",
                  resourceMethod.getSimpleName(), resourceMethod.getParent().getQualifiedName(), subcontext);
            subcontextsByContentType.put(null, new TreeSet<String>(Arrays.asList(subcontext)));
            resourceMethod.putMetaData("defaultSubcontext", subcontext);

            if (isUsePathBasedConneg()) {
              for (String producesMime : resourceMethod.getProducesMime()) {
                MediaType producesType = MediaType.valueOf(producesMime);

                for (Map.Entry<String, String> contentTypeToId : contentTypes2Ids.entrySet()) {
                  MediaType type = MediaType.valueOf(contentTypeToId.getKey());
                  if (producesType.isCompatible(type)) {
                    String id = '/' + contentTypeToId.getValue();
                    String fullpath = resourceMethod.getFullpath();
                    if (fullpath.startsWith(id) || fullpath.startsWith(contentTypeToId.getValue())) {
                      throw new ValidationException(resourceMethod.getPosition(), String.format("The path of this resource starts with \"%s\" and you've got path-based conneg enabled. So Enunciate can't tell whether a request for \"%s\" is a request for this resource or a request for the \"%s\" representation of resource \"%s\". You're going to have to either adjust the path of the resource or disable path-based conneg in the enunciate config (e.g. usePathBasedConneg=\"false\").", id, fullpath, id, fullpath.substring(fullpath.indexOf(contentTypeToId.getValue()) + contentTypeToId.getValue().length())));
                    }

                    debug("Resource method %s of resource %s to be made accessible at subcontext \"%s\" because it produces %s/%s.",
                          resourceMethod.getSimpleName(), resourceMethod.getParent().getQualifiedName(), id, producesType.getType(), producesType.getSubtype());
                    String contentTypeValue = String.format("%s/%s", type.getType(), type.getSubtype());
                    Set<String> subcontextList = subcontextsByContentType.get(contentTypeValue);
                    if (subcontextList == null) {
                      subcontextList = new TreeSet<String>();
                      subcontextsByContentType.put(contentTypeValue, subcontextList);
                    }
                    subcontextList.add(id);
                  }
                }
              }
            }

            resourceMethod.putMetaData("subcontexts", subcontextsByContentType);
          }
        }
      }

      if (enableJaxws) {
        EnunciateConfiguration config = model.getEnunciateConfig();
        for (WsdlInfo wsdlInfo : model.getNamespacesToWSDLs().values()) {
          for (EndpointInterface ei : wsdlInfo.getEndpointInterfaces()) {
            String path = "/soap/" + ei.getServiceName();
            if (config != null) {
              path = config.getDefaultSoapSubcontext() + '/' + ei.getServiceName();
              if (config.getSoapServices2Paths().containsKey(ei.getServiceName())) {
                path = config.getSoapServices2Paths().get(ei.getServiceName());
              }
            }

            ei.putMetaData("soapPath", path);
          }
        }
      }
    }
  }

  @Override
  public void init(Enunciate enunciate) throws EnunciateException {
    super.init(enunciate);
    if (this.enableJaxws) {
      enunciate.getConfig().setForceJAXWSSpecCompliance(true); //make sure the WSDL and client code are JAX-WS-compliant.
    }
  }

  @Override
  public void doFreemarkerGenerate() throws IOException, TemplateException {
    WebAppConfig webAppConfig = enunciate.getConfig().getWebAppConfig();
    if (webAppConfig == null) {
      webAppConfig = new WebAppConfig();
      enunciate.getConfig().setWebAppConfig(webAppConfig);
    }
    webAppConfig.addWebXmlAttribute("version", "3.0");
    webAppConfig.addWebXmlAttribute("xmlns", "http://java.sun.com/xml/ns/javaee");
    webAppConfig.addWebXmlAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
    webAppConfig.addWebXmlAttribute("xsi:schemaLocation", "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd");
  }

  @Override
  protected void doBuild() throws EnunciateException, IOException {
    super.doBuild();

    BaseWebAppFragment webappFragment = new BaseWebAppFragment(getName());
    HashMap<String, String> jbossContextParameters = new HashMap<String, String>();
    webappFragment.setContextParameters(jbossContextParameters);

    ArrayList<WebAppComponent> servlets = new ArrayList<WebAppComponent>();
    if (this.enableJaxws) {
      for (WsdlInfo wsdlInfo : getModelInternal().getNamespacesToWSDLs().values()) {
        for (EndpointInterface ei : wsdlInfo.getEndpointInterfaces()) {
          String path = (String) ei.getMetaData().get("soapPath");
          WebAppComponent wsComponent = new WebAppComponent();
          wsComponent.setName(ei.getServiceName());
          wsComponent.setClassname(ei.getEndpointImplementations().iterator().next().getQualifiedName());
          wsComponent.setUrlMappings(new TreeSet<String>(Arrays.asList(path)));
          servlets.add(wsComponent);
        }
      }
    }

    if (this.enableJaxrs) {
      WebAppComponent jaxrsServletComponent = new WebAppComponent();
      jaxrsServletComponent.setName("resteasy-jaxrs");
      jaxrsServletComponent.setClassname(EnunciateJBossHttpServletDispatcher.class.getName());
      TreeSet<String> jaxrsUrlMappings = new TreeSet<String>();
      StringBuilder resources = new StringBuilder();
      for (RootResource rootResource : getModel().getRootResources()) {
        if (resources.length() > 0) {
          resources.append(',');
        }
        resources.append(rootResource.getQualifiedName());

        for (ResourceMethod resourceMethod : rootResource.getResourceMethods(true)) {
          String resourceMethodPattern = resourceMethod.getServletPattern();
          for (Set<String> subcontextList : ((Map<String, Set<String>>) resourceMethod.getMetaData().get("subcontexts")).values()) {
            for (String subcontext : subcontextList) {
              String servletPattern;
              if ("".equals(subcontext)) {
                servletPattern = resourceMethodPattern;
              }
              else {
                servletPattern = subcontext + resourceMethodPattern;
              }

              if (jaxrsUrlMappings.add(servletPattern)) {
                debug("Resource method %s of resource %s to be made accessible by servlet pattern %s.",
                      resourceMethod.getSimpleName(), resourceMethod.getParent().getQualifiedName(), servletPattern);
              }
            }
          }
        }
      }

      //filter out all the mappings that are double-mapped by wildcards.
      TreeSet<String> filteredMappings = new TreeSet<String>(jaxrsUrlMappings);
      for (String mapping : jaxrsUrlMappings) {
        if (mapping.endsWith("/*")) {
          String prefix = mapping.substring(0, mapping.length() - 1);
          Iterator<String> it = filteredMappings.iterator();
          while (it.hasNext()) {
            String candidate = it.next();
            if (!candidate.equals(mapping) && (candidate.startsWith(prefix) || mapping.equals(candidate + "/*"))) {
              it.remove();
            }
          }
        }
      }

      StringBuilder providers = new StringBuilder();
      for (TypeDeclaration provider : getModel().getJAXRSProviders()) {
        if (providers.length() > 0) {
          providers.append(',');
        }

        providers.append(provider.getQualifiedName());
      }

      if (jacksonAvailable) {
        if (providers.length() > 0) {
          providers.append(',');
        }

        providers.append("org.codehaus.enunciate.jboss.ResteasyJacksonJaxbProvider");
      }

      if (getEnunciate().isModuleEnabled("amf")) {
        if (providers.length() > 0) {
          providers.append(',');
        }

        providers.append("org.codehaus.enunciate.modules.amf.JAXRSProvider");
      }

      jaxrsServletComponent.setUrlMappings(filteredMappings);
      jbossContextParameters.put(ResteasyContextParameters.RESTEASY_RESOURCES, resources.toString());
      jbossContextParameters.put(ResteasyContextParameters.RESTEASY_PROVIDERS, providers.toString());
      String mappingPrefix = this.useSubcontext ? getRestSubcontext() : "";
      if (!"".equals(mappingPrefix)) {
        jbossContextParameters.put("resteasy.servlet.mapping.prefix", mappingPrefix);
        jaxrsServletComponent.addInitParam("resteasy.servlet.mapping.prefix", mappingPrefix);
      }
      if (isUsePathBasedConneg()) {
        Map<String, String> contentTypesToIds = getModelInternal().getContentTypesToIds();
        if (contentTypesToIds != null && contentTypesToIds.size() > 0) {
          StringBuilder builder = new StringBuilder();
          Iterator<Map.Entry<String, String>> contentTypeIt = contentTypesToIds.entrySet().iterator();
          while (contentTypeIt.hasNext()) {
            Map.Entry<String, String> contentTypeEntry = contentTypeIt.next();
            builder.append(contentTypeEntry.getValue()).append(" : ").append(contentTypeEntry.getKey());
            if (contentTypeIt.hasNext()) {
              builder.append(", ");
            }
          }
          jbossContextParameters.put(ResteasyContextParameters.RESTEASY_MEDIA_TYPE_MAPPINGS, builder.toString());
        }
      }
      jbossContextParameters.put(ResteasyContextParameters.RESTEASY_SCAN_RESOURCES, Boolean.FALSE.toString());
      servlets.add(jaxrsServletComponent);
    }

    webappFragment.setServlets(servlets);
    if (!this.options.isEmpty()) {
      webappFragment.setContextParameters(this.options);
    }
    getEnunciate().addWebAppFragment(webappFragment);
  }

  @Override
  public Validator getValidator() {
    return this.enableJaxws ? new JBossValidator() : super.getValidator();
  }

  // Inherited.
  public boolean isJaxwsProvider() {
    return this.enableJaxws;
  }

  // Inherited.
  public boolean isJaxrsProvider() {
    return this.enableJaxrs;
  }

  public void setEnableJaxrs(boolean enableJaxrs) {
    this.enableJaxrs = enableJaxrs;
  }

  public void setEnableJaxws(boolean enableJaxws) {
    this.enableJaxws = enableJaxws;
  }

  /**
   * Whether to use path-based conneg.
   *
   * @return Whether to use path-based conneg.
   */
  public boolean isUsePathBasedConneg() {
    return usePathBasedConneg;
  }

  /**
   * Whether to use path-based conneg.
   *
   * @param usePathBasedConneg Whether to use path-based conneg.
   */
  public void setUsePathBasedConneg(boolean usePathBasedConneg) {
    this.usePathBasedConneg = usePathBasedConneg;
  }

  /**
   * Whether to use the REST subcontext.
   *
   * @param useSubcontext Whether to use the REST subcontext.
   */
  public void setUseSubcontext(boolean useSubcontext) {
    this.useSubcontext = useSubcontext;
  }

  protected String getRestSubcontext() {
    String restSubcontext = getEnunciate().getConfig().getDefaultRestSubcontext();
    //todo: override default rest subcontext?
    return restSubcontext;
  }

  public void addOption(String name, String value) {
    this.options.put(name, value);
  }

  @Override
  public RuleSet getConfigurationRules() {
    return new JBossRuleSet();
  }

  // Inherited.
  @Override
  public boolean isDisabled() {
    if (super.isDisabled()) {
      return true;
    }
    else if (getModelInternal() != null) {
      if (getModelInternal().getRootResources().isEmpty()) {
        debug("CXF module is disabled because there are no root resources to process.");
        return true;
      }
      else if (getModelInternal().getEnunciateConfig() != null && getModelInternal().getEnunciateConfig().getWebAppConfig() != null && getModelInternal().getEnunciateConfig().getWebAppConfig().isDisabled()) {
        debug("Module '%s' is disabled because the web application processing has been disabled.", getName());
        return true;
      }
    }

    return false;
  }
}
