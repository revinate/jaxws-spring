/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.revinate.ws.spring;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.pipe.TubelineAssembler;
import com.sun.xml.ws.api.pipe.TubelineAssemblerFactory;
import com.sun.xml.ws.api.server.*;
import com.sun.xml.ws.binding.BindingImpl;
import com.sun.xml.ws.server.EndpointFactory;
import com.sun.xml.ws.server.ServerRtException;
import com.sun.xml.ws.util.xml.XmlUtil;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.context.ServletContextAware;
import org.xml.sax.EntityResolver;

import javax.servlet.ServletContext;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingType;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.soap.SOAPBinding;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Endpoint. A service object and the infrastructure around it.
 *
 * @author Kohsuke Kawaguchi
 */
public class SpringService implements FactoryBean<WSEndpoint>, ServletContextAware, InitializingBean {

    @NotNull
    private Class<?> implType;

    // everything else can be null
    private Invoker invoker;
    private QName serviceName;
    private QName portName;
    private Container container;

    /**
     * Source for the service's primary WSDL.
     * Set by {@link #afterPropertiesSet()}.
     */
    private SDDocumentSource primaryWsdl;

    /**
     * Resource for the service's primary WSDL.
     *
     * @see #setPrimaryWsdl(Object)
     */
    private Object primaryWSDLResource;

    /**
     * Sources for the service's metadata.
     * Set by {@link #afterPropertiesSet()}.
     */
    private Collection<? extends SDDocumentSource> metadata;

    /**
     * Resources for the service's metadata.
     *
     * @see #setMetadata(java.util.Collection)
     */
    private Collection<Object> metadataResources;

    /**
     * Entity resolver to use for resolving XML resources.
     *
     * @see #setResolver(org.xml.sax.EntityResolver)
     */
    private EntityResolver resolver;

    /**
     * Either {@link TubelineAssembler} or {@link TubelineAssemblerFactory}.
     */
    private Object assembler;

    // binding.

    // either everything is null, in which case we default to SOAP 1.1 + features from annotation

    // ... or a WSBinding configured externally
    private WSBinding binding;

    // ... or a BindingID and features
    private BindingID bindingID;
    private List<WebServiceFeature> features;

    /**
     * Technically speaking, handlers belong to
     * {@link WSBinding} and as such it should be configured there,
     * but it's just more convenient to let people do so at this object,
     * because often people use a stock binding ID constant
     * instead of a configured {@link WSBinding} bean.
     */
    private List<Handler> handlers;

    private ServletContext servletContext;

    /**
     * Set automatically by Spring if JAX-WS is used inside web container.
     */
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    /**
     * Fully qualified class name of the SEI class. Required.
     */
    public void setImpl(Class implType) {
        this.implType = implType;
    }

    /**
     * Sets the bean that implements the web service methods.
     */
    public void setBean(Object sei) {
        this.invoker = InstanceResolver.createSingleton(sei).createInvoker();
        if (this.implType == null) {
            // sei could be a AOP proxy, so getClass() is not always reliable.
            // so if set explicitly via setImpl, don't override that.
            this.implType = sei.getClass();
        }
    }

    /**
     * Sets {@link Invoker} for this endpoint.
     * Defaults to {@link InstanceResolver#createDefault(Class) the standard invoker}.
     */
    public void setInvoker(Invoker invoker) {
        this.invoker = invoker;
    }

    /**
     * Sets the {@link TubelineAssembler} or {@link TubelineAssemblerFactory} instance.
     * <p>
     * This is an advanced configuration option for those who would like to control
     * what processing JAX-WS runtime performs. The default value is {@code null},
     * in which case the {@link TubelineAssemblerFactory} is looked up from the <tt>META-INF/services</tt>.
     */
    public void setAssembler(Object assembler) {
        if (assembler instanceof TubelineAssembler || assembler instanceof TubelineAssemblerFactory) {
            this.assembler = assembler;
        } else {
            throw new IllegalArgumentException("Invalid type for assembler " + assembler);
        }
    }

    /**
     * Sets the service name of this endpoint.
     * Defaults to the name inferred from the impl attribute.
     */
    public void setServiceName(QName serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * Sets the port name of this endpoint.
     * Defaults to the name inferred from the impl attribute.
     */
    public void setPortName(QName portName) {
        this.portName = portName;
    }

    /**
     * Sets the custom {@link Container}. Optional.
     */
    // TODO: how to set the default container?
    public void setContainer(Container container) {
        this.container = container;
    }

    /**
     * Accepts an externally configured {@link WSBinding}
     * for advanced users.
     */
    // is there a better way to do this in Spring?
    // http://opensource.atlassian.com/projects/spring/browse/SPR-2528?page=all
    // says it doesn't support method overloading, so that's out.
    public void setBinding(WSBinding binding) {
        this.binding = binding;
    }

    /**
     * Sets the binding ID, such as <tt>{@value SOAPBinding#SOAP11HTTP_BINDING}</tt>
     * or <tt>{@value SOAPBinding#SOAP12HTTP_BINDING}</tt>.
     *
     * <p>
     * If none is specified, {@link BindingType} annotation on SEI is consulted.
     * If that fails, {@link SOAPBinding#SOAP11HTTP_BINDING}.
     *
     * @see SOAPBinding#SOAP11HTTP_BINDING
     * @see SOAPBinding#SOAP12HTTP_BINDING
     * @see HTTPBinding#HTTP_BINDING
     */
    public void setBindingID(String id) {
        this.bindingID = BindingID.parse(id);
    }

    /**
     * {@link WebServiceFeature}s that are activated in this endpoint.
     */
    public void setFeatures(List<WebServiceFeature> features) {
        this.features = features;
    }

    /**
     * {@link Handler}s for this endpoint.
     * Note that the order is significant.
     *
     * <p>
     * If there's just one handler and that handler is declared elsewhere,
     * you can use this as a nested attribute like <tt>handlers="#myHandler"</tt>.
     * Or otherwise a nested &lt;bean&gt; or &lt;ref&gt; tag can be used to
     * specify multiple handlers.
     */
    public void setHandlers(List<Handler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Optional WSDL for this endpoint.
     * <p>
     * Defaults to the WSDL discovered in <tt>META-INF/wsdl</tt>,
     * <p>
     * It can be either {@link String}, {@link URL}, or {@link SDDocumentSource}.
     * <p>
     * If <code>primaryWsdl</code> is a <code>String</code>,
     * {@link ServletContext} (if available) and {@link ClassLoader}
     * are searched for this path, then failing that, it's treated as an
     * absolute {@link URL}.
     */
    public void setPrimaryWsdl(Object primaryWsdl) throws IOException {
        this.primaryWSDLResource = primaryWsdl;
    }

    /**
     * Optional metadata for this endpoint.
     * <p>
     * The collection can contain {@link String}, {@link URL}, or {@link SDDocumentSource}
     * elements.
     * <p>
     * If element is a <code>String</code>,
     * {@link ServletContext} (if available) and {@link ClassLoader}
     * are searched for this path, then failing that, it's treated as an
     * absolute {@link URL}.
     */
    public void setMetadata(Collection<Object> metadata) {
        this.metadataResources = metadata;
    }

    /**
     * Sets the {@link EntityResolver} to be used for resolving schemas/WSDLs
     * that are referenced. Optional.
     *
     * <p>
     * If omitted, the default catalog resolver is created by looking at
     * <tt>/WEB-INF/jax-ws-catalog.xml</tt> (if we run as a servlet) or
     * <tt>/META-INF/jax-ws-catalog.xml</tt> (otherwise.)
     */
    public void setResolver(EntityResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Lazily created {@link WSEndpoint} instance.
     */
    private WSEndpoint<?> endpoint;

    public WSEndpoint<?> getObject() throws Exception {
        if (endpoint == null) {
            if (binding == null) {
                if (bindingID == null) {
                    bindingID = BindingID.parse(implType);
                }
                if (features == null || features.isEmpty()) {
                    binding = BindingImpl.create(bindingID);
                } else {
                    binding = BindingImpl.create(bindingID,
                            features.toArray(new WebServiceFeature[features.size()]));
                }
            } else {
                if (bindingID != null) {
                    throw new IllegalStateException("Both bindingID and binding are configured");
                }
                if (features != null) {
                    throw new IllegalStateException("Both features and binding are configured");
                }
            }

            // configure handlers. doing this here ensures
            // that we are not doing this more than once.
            if (handlers != null) {
                List<Handler> chain = binding.getHandlerChain();
                chain.addAll(handlers);
                binding.setHandlerChain(chain);
            }

            if (primaryWsdl == null) {
                // attempt to find it on the impl class.
                EndpointFactory.verifyImplementorClass(implType, null);
                String wsdlLocation = EndpointFactory.getWsdlLocation(implType);
                if (wsdlLocation != null) {
                    primaryWsdl = convertStringToSource(wsdlLocation);
                }
            }

            // resolver defaulting.
            EntityResolver resolver = this.resolver;
            if (resolver == null) {
                if (servletContext != null) {
                    resolver = XmlUtil.createEntityResolver(servletContext.getResource("/WEB-INF/jax-ws-catalog.xml"));
                } else {
                    resolver = XmlUtil.createEntityResolver(getClass().getClassLoader().getResource("/META-INF/jax-ws-catalog.xml"));
                }
            }

            endpoint = WSEndpoint.create(implType, false, invoker, serviceName,
                    portName, new ContainerWrapper(), binding, primaryWsdl, metadata, resolver, true);
        }
        return endpoint;
    }

    /**
     * Called automatically by Spring after all properties have been set, including
     * {@link #servletContext}.  This implementation creates
     * <code>SDDocumentSource</code>s from the {@link #primaryWSDLResource} and
     * {@link #metadataResources} properties, if provided.
     *
     * <p>See {@link #setMetadata(java.util.Collection)} and
     * {@link #setPrimaryWsdl(Object)} for conversion rules.
     *
     * @throws Exception if an error occurs while creating
     * <code>SDDocumentSource</code>s from the {@link #primaryWSDLResource} and
     * {@link #metadataResources} properties
     *
     * @see #resolveSDDocumentSource(Object)
     */
    public void afterPropertiesSet() throws Exception {
        if (this.primaryWSDLResource != null) {
            this.primaryWsdl = this.resolveSDDocumentSource(this.primaryWSDLResource);
        }

        if (this.metadataResources != null) {
            List<SDDocumentSource> tempList = new ArrayList<>(this.metadataResources.size());

            for (Object resource : this.metadataResources) {
                tempList.add(this.resolveSDDocumentSource(resource));
            }

            this.metadata = tempList;
        }
    }

    /**
     * Resolves a resource ({@link String}, {@link URL}, or {@link SDDocumentSource})
     * to a {@link SDDocumentSource}.
     * <p/>
     * See {@link #convertStringToSource(String)} for processing rules relating
     * to a <code>String</code> argument.
     *
     * @param resource the <code>String</code>, <code>URL</code>,
     * or <code>SDDocumentSource</code> to resolve
     *
     * @return a <code>SDDocumentSource</code> for the provided <code>resource</code>
     *
     * @throws IllegalArgumentException if <code>resource</code> is not an
     * instance of <code>String</code>, <code>URL</code>, or
     * <code>SDDocumentSource</code>
     *
     * @see #convertStringToSource(String)
     * @see SDDocumentSource#create(java.net.URL)
     */
    private SDDocumentSource resolveSDDocumentSource(Object resource) {
        SDDocumentSource source;

        if (resource instanceof String) {
            source = this.convertStringToSource((String) resource);
        } else if (resource instanceof URL) {
            source = SDDocumentSource.create((URL) resource);
        } else if (resource instanceof SDDocumentSource) {
            source = (SDDocumentSource) resource;
        } else {
            throw new IllegalArgumentException("Unknown type \"" + resource.getClass().getName() + "\" for resource " + resource);
        }

        return source;
    }

    /**
     * Converts {@link String} into {@link SDDocumentSource}.
     * <p/>
     * If <code>resourceLocation</code> is a <code>String</code>,
     * {@link ServletContext} (if available) and {@link ClassLoader}
     * are searched for this path, then failing that, it's treated as an
     * absolute {@link URL}.
     *
     * @throws ServerRtException if <code>resourceLocation</code> cannot be
     * resolved through {@link ServletContext} (if available), {@link ClassLoader},
     * or as an absolute {@link java.net.URL}.
     */
    private SDDocumentSource convertStringToSource(String resourceLocation) {
        URL url = null;

        if (servletContext != null) {
            // in the servlet environment, consult ServletContext so that we can load
            // WEB-INF/wsdl/... and so on.
            try {
                url = servletContext.getResource(resourceLocation);
            } catch (MalformedURLException e) {
                // ignore it and try the next method
            }
        }

        if (url == null) {
            // also check a resource in classloader.
            ClassLoader cl = implType.getClassLoader();
            url = cl.getResource(resourceLocation);
        }

        if (url == null) {
            try {
                url = new URL(resourceLocation);
            } catch (MalformedURLException e) {
                // ignore it throw exception later
            }
        }

        if (url == null) {
            throw new ServerRtException("cannot.load.wsdl", resourceLocation);
        }

        return SDDocumentSource.create(url);
    }

    public boolean isSingleton() {
        return true;
    }

    public Class<WSEndpoint> getObjectType() {
        return WSEndpoint.class;
    }

    private class ContainerWrapper extends Container {

        public <T> T getSPI(Class<T> spiType) {
            // allow specified TubelineAssembler to be used
            if (spiType == TubelineAssemblerFactory.class) {
                if (assembler instanceof TubelineAssemblerFactory) {
                    return spiType.cast(assembler);
                }
                if (assembler instanceof TubelineAssembler) {
                    return spiType.cast(new TubelineAssemblerFactory() {
                        public TubelineAssembler doCreate(BindingID bindingId) {
                            return (TubelineAssembler)assembler;
                        }
                    });
                }
            }

            if (spiType == ServletContext.class) {
                return spiType.cast(servletContext);
            }

            if (container != null) {
                // delegate to the specified container
                T t = container.getSPI(spiType);
                if (t != null) {
                    return t;
                }
            }

            if (spiType == Module.class) {
                // fall back default implementation
                return spiType.cast(module);
            }

            return null;
        }

        private final Module module = new Module() {
            private final List<BoundEndpoint> endpoints = new ArrayList<>();

            public @NotNull List<BoundEndpoint> getBoundEndpoints() {
                return endpoints;
            }
        };
    }
}
