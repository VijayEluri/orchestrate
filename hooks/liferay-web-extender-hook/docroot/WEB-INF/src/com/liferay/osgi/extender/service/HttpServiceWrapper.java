/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.osgi.extender.service;

import com.liferay.osgi.extender.servlet.BundleServletContext;
import com.liferay.osgi.extender.servlet.DefaultHttpContext;

import java.util.Dictionary;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

/**
 * @author Raymond Augé
 */
public class HttpServiceWrapper implements ExtendedHttpService, HttpService {

	public HttpServiceWrapper(BundleServletContext bundleServletContext) {
		_bundleServletContext = bundleServletContext;
	}

	public void close() {
		_bundleServletContext.close();
	}

	public HttpContext createDefaultHttpContext() {
		return new DefaultHttpContext(_bundleServletContext);
	}

	public void registerFilter(
			String filterMapping, Filter filter, Dictionary initParams,
			HttpContext httpContext)
		throws NamespaceException, ServletException {

		if (httpContext == null) {
			httpContext = createDefaultHttpContext();
		}

		_bundleServletContext.registerFilter(
			filterMapping, filter, initParams, httpContext);
	}

	public void registerListener(
			String listenerClassName, Object listener, Dictionary initParams,
			HttpContext httpContext)
		throws ServletException {

		if (httpContext == null) {
			httpContext = createDefaultHttpContext();
		}

		_bundleServletContext.registerListener(
			listenerClassName, listener, initParams, httpContext);
	}

	public void registerResources(
			String alias, String name, HttpContext httpContext)
		throws NamespaceException {

		if (httpContext == null) {
			httpContext = createDefaultHttpContext();
		}

		_bundleServletContext.registerResources(alias, name, httpContext);
	}

	public void registerServlet(
			String alias, Servlet servlet, Dictionary initparams,
			HttpContext httpContext)
		throws NamespaceException, ServletException {

		if (httpContext == null) {
			httpContext = createDefaultHttpContext();
		}

		_bundleServletContext.registerServlet(
			alias, servlet, initparams, httpContext);
	}

	public void unregister(String alias) {
		_bundleServletContext.unregister(alias);
	}

	public void unregisterFilter(String filterMapping) {
		_bundleServletContext.unregisterFilter(filterMapping);
	}

	public void unregisterListener(String listenerClassName) {
		_bundleServletContext.unregisterListener(listenerClassName);
	}

	private BundleServletContext _bundleServletContext;

}