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
package com.sun.xml.ws.transport.http.servlet;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link HttpServlet} that uses Spring to obtain a configured server set up,
 * then routes incoming requests to it.
 *
 * This version of the class also works with environments where the web
 * application context is injected rather than created internally (e.g. Servlet
 * 3.0+ environments, and in embedded servlet containers.)
 *
 * @author Kohsuke Kawaguchi
 * @author Alex Leigh
 */
public class WSSpringServlet extends HttpServlet implements ApplicationContextAware {

    private static final long serialVersionUID = -2786173009814679145L;

    private WSServletDelegate delegate;

    private WebApplicationContext webApplicationContext;

    private boolean webApplicationContextInjected = false;

    public WSSpringServlet() {}

    public WSSpringServlet(WebApplicationContext webApplicationContext) {
        this.webApplicationContext = webApplicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        if (this.webApplicationContext == null && applicationContext instanceof WebApplicationContext) {
            this.webApplicationContext = (WebApplicationContext) applicationContext;
            this.webApplicationContextInjected = true;
        }
    }

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);

        // get the configured adapters from Spring
        this.webApplicationContext = initWebApplicationContext();

        Set<SpringBinding> bindings = new LinkedHashSet<>();

        bindings.addAll(this.webApplicationContext.getBeansOfType(SpringBinding.class).values());

        // create adapters
        ServletAdapterList l = new ServletAdapterList(getServletContext());
        for (SpringBinding binding : bindings)
            binding.create(l);

        delegate = new WSServletDelegate(l,getServletContext());
    }

    protected WebApplicationContext initWebApplicationContext() {
        if (this.webApplicationContext != null) {
            return this.webApplicationContext;
        } else {
            return WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
        }
    }

    /**
     * destroys the servlet and releases all associated resources,
     * such as the Spring application context and the JAX-WS delegate.
     */
    @Override
    public void destroy() {
        if (this.webApplicationContext instanceof ConfigurableApplicationContext
                && !this.webApplicationContextInjected) {
            ((ConfigurableApplicationContext) this.webApplicationContext).close();
        }
        delegate.destroy();
        delegate = null;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException {
        delegate.doPost(request,response,getServletContext());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException {
        delegate.doGet(request,response,getServletContext());
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException {
        delegate.doPut(request,response,getServletContext());
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException {
        delegate.doDelete(request,response,getServletContext());
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
            throws ServletException {
        delegate.doHead(request,response,getServletContext());
    }
}
