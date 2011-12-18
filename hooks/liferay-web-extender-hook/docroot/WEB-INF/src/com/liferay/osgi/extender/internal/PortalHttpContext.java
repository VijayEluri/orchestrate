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

package com.liferay.osgi.extender.internal;

import com.liferay.portal.kernel.util.MimeTypesUtil;

import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.http.HttpContext;

/**
 * <a href="PortalHttpContext.java.html"><b><i>View Source</i></b></a>
 *
 * @author Raymond Augé
 */
public class PortalHttpContext implements HttpContext {

	public PortalHttpContext(ServletContext servletContext) {
		_servletContext = servletContext;
	}

	public String getMimeType(String name) {
		String mimeType = _servletContext.getMimeType(name);

		if (mimeType == null) {
			mimeType = MimeTypesUtil.getContentType(name);
		}

		return mimeType;
	}

	public URL getResource(String resource) {
		try {
			return _servletContext.getResource(resource);
		}
		catch (MalformedURLException e) {
		}

		return null;
	}

	public boolean handleSecurity(
			HttpServletRequest request, HttpServletResponse response)
		throws IOException {

		return true;
	}

	private ServletContext _servletContext;

}