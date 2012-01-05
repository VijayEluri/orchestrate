/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
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

import com.liferay.portal.apache.bridges.struts.LiferayServletContext;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.osgi.OSGiConstants;
import com.liferay.portal.util.PortalUtil;
import com.liferay.web.extender.internal.servlet.OSGiServlet;
import com.liferay.web.extender.internal.servlet.ResourceServlet;
import com.liferay.web.extender.service.HttpServiceTracker;

import java.io.InputStream;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.NamespaceException;

/**
 * @author Raymond Augé
 */
public class BundleServletContext extends LiferayServletContext {

	public BundleServletContext(
		ServletContext servletContext, Servlet portletServlet) {

		super(servletContext);

		_portletServlet = portletServlet;
	}

	public static String getServletContextName(Bundle bundle) {
		Dictionary<String,String> headers = bundle.getHeaders();

		String webContextPath = headers.get(OSGiConstants.WEB_CONTEXTPATH);

		if (Validator.isNull(webContextPath)) {
			return null;
		}

		return webContextPath.substring(1);
	}

	public void close() {
		if (_httpServiceTracker == null) {
			return;
		}

		_httpServiceTracker.close();
	}

	@Override
	public Object getAttribute(String name) {
		if (name.equals("bundle.classloader")) {
			return _classLoader;
		}

		Object value = _contextAttributes.get(name);

		if ((value != null) ||
			name.equals(
				"org.springframework.web.context.WebApplicationContext.ROOT")) {

			return value;
		}

		return super.getAttribute(name);
	}

	public ClassLoader getClassLoader() {
		if (_classLoader == null) {
			Bundle bundle = (Bundle)getAttribute(OSGiConstants.OSGI_BUNDLE);

			BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);

			_classLoader = bundleWiring.getClassLoader();
		}

		return _classLoader;
	}

	@Override
	public String getContextPath() {
		if (_contextPath == null) {
			StringBundler sb = new StringBundler(4);

			String contextPath = super.getContextPath();

			if (!contextPath.equals(StringPool.SLASH)) {
				sb.append(contextPath);
			}

			sb.append(PortalUtil.getPathMain());
			sb.append(OSGiServlet.SERVLET_MAPPING);
			sb.append(getServletContextName());

			_contextPath = sb.toString();
		}

		return _contextPath;
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		String alias = path;

		FilterChain filterChain = getFilterChain(alias);

		if (path.startsWith(OSGiServlet.SERVLET_MAPPING) &&
			path.endsWith(OSGiServlet.INVOKER_PATH)) {

			return new BundleRequestDispatcher(
				path, false, path, _portletServlet, filterChain);
		}

		if (!isValidPath(path)) {
			return null;
		}

		if (Validator.isNull(alias)) {
			alias = StringPool.SLASH;
		}

		if (_servletsMap.containsKey(alias)) {
			return new BundleRequestDispatcher(
				alias, false, path, _servletsMap.get(alias), filterChain);
		}

		String extensionMapping = FileUtil.getExtension(alias).toLowerCase();

		if (Validator.isNotNull(extensionMapping)) {
			extensionMapping = _EXTENSION_PREFIX.concat(extensionMapping);
		}

		alias = alias.substring(0, alias.lastIndexOf(StringPool.SLASH));

		while (alias.length() != 0) {
			if (_servletsMap.containsKey(alias)) {
				return new BundleRequestDispatcher(
					alias, false, path, _servletsMap.get(alias), filterChain);
			}
			else if (_servletsMap.containsKey(alias.concat(extensionMapping))) {
				return new BundleRequestDispatcher(
					alias.concat(extensionMapping), true, path,
					_servletsMap.get(alias.concat(extensionMapping)),
					filterChain);
			}

			alias = alias.substring(0, alias.lastIndexOf(StringPool.SLASH));
		}

		if (_servletsMap.containsKey(
				StringPool.SLASH.concat(extensionMapping))) {

			return new BundleRequestDispatcher(
				StringPool.SLASH.concat(extensionMapping), true, path,
				_servletsMap.get(StringPool.SLASH.concat(extensionMapping)),
				filterChain);
		}

		if (_servletsMap.containsKey(StringPool.SLASH)) {
			return new BundleRequestDispatcher(
				StringPool.SLASH, false, path,
				_servletsMap.get(StringPool.SLASH), filterChain);
		}

		return null;
	}

	@Override
	public URL getResource(String path) throws MalformedURLException {
		Bundle bundle = (Bundle)getAttribute(OSGiConstants.OSGI_BUNDLE);

		try {
			return bundle.getEntry(path);
		}
		catch (IllegalStateException ise) {
			return null;
		}
	}

	@Override
	public InputStream getResourceAsStream(String path) {
		try {
			URL resource = getResource(path);

			if (resource != null) {
				return resource.openStream();
			}
		}
		catch (Exception e) {
			if (_log.isDebugEnabled()) {
				_log.debug(e, e);
			}
		}

		return null;
	}

	@Override
	public Set<String> getResourcePaths(String path) {
		Set<String> resourcePaths = new HashSet<String>();

		Bundle bundle = (Bundle)getAttribute(OSGiConstants.OSGI_BUNDLE);

		Enumeration<String> resources = bundle.getEntryPaths(path);

		if ((resources != null) && resources.hasMoreElements()) {
			while (resources.hasMoreElements()) {
				resourcePaths.add(resources.nextElement());
			}
		}

		return resourcePaths;
	}

	@Override
	public String getServletContextName() {
		if (_servletContextName == null) {
			Bundle bundle = (Bundle)getAttribute(OSGiConstants.OSGI_BUNDLE);

			_servletContextName = getServletContextName(bundle);
		}

		return _servletContextName;
	}

	public void open() {
		Bundle bundle = (Bundle)getAttribute(OSGiConstants.OSGI_BUNDLE);

		Dictionary<String,String> headers = bundle.getHeaders();

		String webContextPath = headers.get(OSGiConstants.WEB_CONTEXTPATH);

		BundleContext bundleContext = bundle.getBundleContext();

		Hashtable<String, Object> properties = new Hashtable<String, Object>();

		properties.put("osgi.web.symbolicname", bundle.getSymbolicName());
		properties.put("osgi.web.version", bundle.getVersion().toString());
		properties.put("osgi.web.contextpath", webContextPath);

		bundleContext.registerService(ServletContext.class, this, properties);

		_httpServiceTracker = new HttpServiceTracker(bundleContext);

		_httpServiceTracker.open();
	}

	public void registerFilter(
			String filterMapping, Filter filter, Dictionary initParams,
			HttpContext httpContext)
		throws ServletException, NamespaceException {

		validate(filterMapping, filter, httpContext);

		FilterConfig filterConfig = new BundleFilterConfig(
			this, filterMapping, initParams, httpContext);

		filter.init(filterConfig);

		_filtersMap.put(filterMapping, filter);
		_filterList.add(new Object[] {filterMapping, filter});
	}

	public void registerListener(
			String listenerClassName, Object listener, Dictionary initParams,
			HttpContext httpContext)
		throws ServletException {

		if (listener instanceof HttpSessionListener) {
			// TODO throw new UnsupportedOperationException();
		}
		else if (listener instanceof HttpSessionAttributeListener) {
			// TODO throw new UnsupportedOperationException();
		}
		else if (listener instanceof ServletContextListener) {
			ServletContextListener servletContextListener =
				(ServletContextListener)listener;

			ServletContextEvent servletContextEvent = new ServletContextEvent(
				this);

			servletContextListener.contextInitialized(servletContextEvent);
		}
		else if (listener instanceof ServletContextAttributeListener) {
			_contextAttributeListeners.put(
				listenerClassName,
				(ServletContextAttributeListener)listener);
		}
		else if (listener instanceof ServletRequestListener) {
			// TODO throw new UnsupportedOperationException();
		}
		else if (listener instanceof ServletRequestAttributeListener) {
			// TODO throw new UnsupportedOperationException();
		}
	}

	public void registerResources(
			String alias, String name, HttpContext httpContext)
		throws NamespaceException {

		validate(name);

		Servlet servlet = new ResourceServlet(name, httpContext);

		try {
			registerServlet(alias, servlet, null, httpContext);
		}
		catch (ServletException se) {
			throw new IllegalArgumentException(se);
		}
	}

	public void registerServlet(
			String alias, Servlet servlet, Dictionary initParams,
			HttpContext httpContext)
		throws ServletException, NamespaceException {

		validate(alias, servlet, httpContext);

		ServletConfig servletConfig = new BundleServletConfig(
			this, alias, initParams, httpContext);

		servlet.init(servletConfig);

		_servletsMap.put(alias, servlet);
	}

	@Override
	public void removeAttribute(String name) {
		Object value = _contextAttributes.remove(name);

		for(ServletContextAttributeListener listener :
				_contextAttributeListeners.values()) {

			listener.attributeRemoved(
				new ServletContextAttributeEvent(this, name, value));
		}
	}

	@Override
	public void setAttribute(String name, Object value) {
		Object originalValue = _contextAttributes.get(name);

		_contextAttributes.put(name, value);

		for(ServletContextAttributeListener listener :
				_contextAttributeListeners.values()) {

			if (originalValue != null) {
				listener.attributeReplaced(
					new ServletContextAttributeEvent(
						this, name, originalValue));
			}
			else {
				listener.attributeAdded(
					new ServletContextAttributeEvent(this, name, value));
			}
		}
	}

	public void unregister(String alias) {
		Servlet servlet = _servletsMap.remove(alias);

		if (servlet != null) {
			servlet.destroy();
		}
	}

	public void unregisterFilter(String filterMapping) {
		Filter filter = _filtersMap.get(filterMapping);

		if (filter != null) {
			filter.destroy();
		}
	}

	public void unregisterListener(String listenerClassName) {
		Object listener = _listeners.get(listenerClassName);

		if (listener instanceof HttpSessionListener) {
		}
		else if (listener instanceof HttpSessionAttributeListener) {
		}
		else if (listener instanceof ServletContextListener) {
			ServletContextListener servletContextListener =
				(ServletContextListener)listener;

			ServletContextEvent servletContextEvent = new ServletContextEvent(
				this);

			servletContextListener.contextDestroyed(servletContextEvent);
		}
		else if (listener instanceof ServletContextAttributeListener) {
			_contextAttributeListeners.remove(listenerClassName);
		}
		else if (listener instanceof ServletRequestListener) {
		}
		else if (listener instanceof ServletRequestAttributeListener) {
		}
	}

	protected FilterChain getFilterChain(String alias) {
		BundleFilterChain bundleFilterChain = new BundleFilterChain();

		for (Object[] filterDefinition : _filterList) {
			String filterMapping = (String)filterDefinition[0];
			Filter filter = (Filter)filterDefinition[1];

			if (filterMapping.equals(alias)) {
				bundleFilterChain.addFilter(filter);
			}

			if (filterMapping.contains(StringPool.STAR)) {
				filterMapping = filterMapping.replaceAll(StringPool.STAR, ".*");
			}

			if (alias.matches(filterMapping)) {
				bundleFilterChain.addFilter(filter);
			}
		}

		return bundleFilterChain;
	}

	protected boolean isValidPath(String path) {
		for (String illegalPath : _ILLEGAL_PATHS) {
			if (path.contains(illegalPath)) {
				return false;
			}
		}

		return true;
	}

	protected void validate(String name) {
		if (name == null) {
			throw new IllegalArgumentException("Name cannot be null");
		}

		if (name.endsWith(StringPool.SLASH) && !name.equals(StringPool.SLASH)) {
			throw new IllegalArgumentException("Invalid name " + name);
		}
	}

	protected void validate(
			String filterMapping, Filter filter, HttpContext httpContext)
		throws NamespaceException {

		if (filterMapping == null) {
			throw new IllegalArgumentException("FilterMapping cannot be null");
		}

		if (filterMapping.endsWith(StringPool.SLASH) &&
			!filterMapping.equals(StringPool.SLASH)) {

			throw new IllegalArgumentException(
				"Invalid filterMapping " + filterMapping);
		}

		if (filter == null) {
			throw new IllegalArgumentException("Filter must not be null");
		}

		if (_filtersMap.containsValue(filter)) {
			throw new IllegalArgumentException("Filter is already registered");
		}

		if (httpContext == null) {
			throw new IllegalArgumentException("HttpContext cannot be null");
		}
	}

	protected void validate(
			String alias, Servlet servlet, HttpContext httpContext)
		throws NamespaceException {

		if (Validator.isNull(alias)) {
			throw new IllegalArgumentException(
				"Empty aliases are not allowed");
		}

		if (!alias.startsWith(StringPool.SLASH) ||
			(alias.endsWith(StringPool.SLASH) &&
			 !alias.equals(StringPool.SLASH))) {

			throw new IllegalArgumentException(
				"Alias must start with / but must not end with it");
		}

		if (_servletsMap.containsKey(alias)) {
			throw new NamespaceException("Alias " + alias + " already exists");
		}

		if (servlet == null) {
			throw new IllegalArgumentException("Servlet must not be null");
		}

		if (_servletsMap.containsValue(servlet)) {
			throw new IllegalArgumentException("Servlet is already registered");
		}

		if (httpContext == null) {
			throw new IllegalArgumentException("HttpContext cannot be null");
		}
	}

	private static final String[] _ILLEGAL_PATHS = new String[] {
		"WEB-INF/", "OSGI-INF/", "META-INF/", "OSGI-OPT/"
	};
	private static final String _INVOKER_PATH = "/invoke";
	private static final String _EXTENSION_PREFIX = "*.";

	private static final Log _log = LogFactoryUtil.getLog(
		BundleServletContext.class);

	private ClassLoader _classLoader;
	private Map<String,Object> _contextAttributes =
		new ConcurrentHashMap<String,Object>();
	private Map<String, ServletContextAttributeListener> _contextAttributeListeners =
		new ConcurrentHashMap<String, ServletContextAttributeListener>();
	private String _contextPath;
	private Map<String,Filter> _filtersMap =
		new ConcurrentHashMap<String,Filter>();
	private List<Object[]> _filterList = new ArrayList<Object[]>();
	private HttpServiceTracker _httpServiceTracker;
	private Map<String,Object> _listeners =
		new ConcurrentHashMap<String, Object>();
	//private ServiceRegistration<ServletContext> _serviceRegisteration;
	private String _servletContextName;
	private Map<String,Servlet> _servletsMap =
		new ConcurrentHashMap<String,Servlet>();
	private Servlet _portletServlet;

}