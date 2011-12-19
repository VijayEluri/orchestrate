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

package com.liferay.web.extender.servlet;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.servlet.PortletServlet;
import com.liferay.portal.kernel.servlet.ServletContextPool;
import com.liferay.portal.kernel.struts.StrutsAction;
import com.liferay.portal.kernel.util.ReleaseInfo;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.osgi.OSGiConstants;
import com.liferay.portal.util.PortalUtil;
import com.liferay.web.extender.service.HttpServiceFactory;

import java.io.IOException;

import java.util.Hashtable;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;

/**
 * @author Raymond Augé
 */
public class OSGiServlet extends PortletServlet implements StrutsAction {

	public static final String INVOKER_PATH = "/invoke";
	public static final String SERVLET_MAPPING = "/osgi/";

	public OSGiServlet(BundleContext bundleContext) {
		_bundleContext = bundleContext;
	}

	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		super.init(servletConfig);

		Hashtable<String,Object> properties = new Hashtable<String, Object>();

		properties.put(OSGiConstants.BEAN_ID, HttpService.class.getName());
		properties.put(OSGiConstants.ORIGINAL_BEAN, Boolean.TRUE);
		properties.put(OSGiConstants.SERVICE_VENDOR, ReleaseInfo.getVendor());

		HttpServiceFactory httpServiceFactory = new HttpServiceFactory(
			_bundleContext);

		_bundleContext.registerService(
			new String[] {HttpService.class.getName()}, httpServiceFactory,
			properties);
	}

	public String execute(
			HttpServletRequest request, HttpServletResponse response)
		throws Exception {

		service(request, response);

		return null;
	}

	public String execute(
			StrutsAction originalStrutsAction, HttpServletRequest request,
			HttpServletResponse response)
		throws Exception {

		service(request, response);

		return null;
	}

	@Override
	public void service(
			HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		String portletId = (String)request.getAttribute(WebKeys.PORTLET_ID);

		String pathInfo = request.getPathInfo();

		if (pathInfo.startsWith(SERVLET_MAPPING)) {
			pathInfo = pathInfo.substring(5);
		}

		String servletContextName = pathInfo;

		if (servletContextName.startsWith(StringPool.SLASH)) {
			servletContextName = servletContextName.substring(1);
		}

		int pos = servletContextName.indexOf(StringPool.SLASH);

		if (pos != -1) {
			pathInfo = servletContextName.substring(
				pos, servletContextName.length());

			servletContextName = servletContextName.substring(0, pos);
		}

		ServletContext servletContext = ServletContextPool.get(
			servletContextName);

		if (servletContext == null) {
			PortalUtil.sendError(
				HttpServletResponse.SC_NOT_FOUND,
				new IllegalArgumentException(
					"No application mapped to this path"), request, response);

			return;
		}

		service(request, response, servletContext, portletId, pathInfo);
	}

	protected void service(
			HttpServletRequest request, HttpServletResponse response,
			ServletContext servletContext, String portletId, String pathInfo)
		throws IOException, ServletException {

		if (pathInfo.endsWith(INVOKER_PATH)) {
			if (Validator.isNotNull(portletId)) {
				super.service(request, response);

				return;
			}

			PortalUtil.sendError(
				HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
				new IllegalAccessException("Illegal request"), request,
				response);

			return;
		}

		BundleServletContext bundleServletContext =
			(BundleServletContext)servletContext;

		Thread currentThread = Thread.currentThread();

		ClassLoader bundleClassLoader = bundleServletContext.getClassLoader();
		ClassLoader contextClassLoader = currentThread.getContextClassLoader();

		try {
			currentThread.setContextClassLoader(bundleClassLoader);

			if (Validator.isNotNull(portletId) &&
				pathInfo.equals(INVOKER_PATH)) {

				super.service(request, response);

				return;
			}

			RequestDispatcher requestDispatcher =
				bundleServletContext.getRequestDispatcher(pathInfo);

			if (requestDispatcher != null) {
				requestDispatcher.forward(request, response);

				return;
			}

			PortalUtil.sendError(
				HttpServletResponse.SC_NOT_FOUND,
				new IllegalArgumentException(
					"No servlet or resource mapped to this path"), request,
					response);
		}
		finally {
			currentThread.setContextClassLoader(contextClassLoader);
		}
	}

	private static Log _log = LogFactoryUtil.getLog(OSGiServlet.class);

	private BundleContext _bundleContext;

}