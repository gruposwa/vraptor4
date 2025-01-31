/***
 * Copyright (c) 2009 Caelum - www.caelum.com.br/opensource
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/***
 *
 * Copyright (c) 2009 Caelum - www.caelum.com.br/opensource All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * copyright holders nor the names of its contributors may be used to endorse or
 * promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package br.com.caelum.vraptor.http.route;

import static java.util.Arrays.asList;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.enterprise.inject.Vetoed;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Joiner;

import br.com.caelum.vraptor.Path;
import br.com.caelum.vraptor.controller.ControllerMethod;
import br.com.caelum.vraptor.controller.DefaultControllerMethod;
import br.com.caelum.vraptor.controller.HttpMethod;
import br.com.caelum.vraptor.core.Converters;
import br.com.caelum.vraptor.http.EncodingHandler;
import br.com.caelum.vraptor.http.Parameter;
import br.com.caelum.vraptor.http.ParameterNameProvider;
import br.com.caelum.vraptor.proxy.MethodInvocation;
import br.com.caelum.vraptor.proxy.Proxifier;
import br.com.caelum.vraptor.proxy.SuperMethod;
import br.com.caelum.vraptor.util.StringUtils;

/**
 * Should be used in one of two ways, either configure the type and invoke the
 * method or pass the method (java reflection) object.
 *
 * If not specified, the built route will have the lowest priority (higher value
 * of priority), so will be the last to be used.
 *
 * @author Guilherme Silveira
 */
@Vetoed
public class DefaultRouteBuilder implements RouteBuilder {
	//private static final Logger logger = LoggerFactory.getLogger(DefaultRouteBuilder.class);
	private static final Logger logger = LogManager.getLogger(DefaultRouteBuilder.class);
	
	private static final List<?> CHARACTER_TYPES = asList(char.class, Character.class);
	private static final List<?> DECIMAL_TYPES = asList(Double.class, BigDecimal.class, double.class, Float.class, float.class);
	private static final List<?> BOOLEAN_TYPES = asList(Boolean.class, boolean.class);
	private static final List<?> NUMERIC_TYPES = asList(Integer.class, Long.class, int.class, long.class, BigInteger.class, Short.class, short.class);
	
	private final Set<HttpMethod> supportedMethods = EnumSet.noneOf(HttpMethod.class);
	private final DefaultParameterControlBuilder builder = new DefaultParameterControlBuilder();
	private Route strategy = new NoStrategy();
	private int priority = Path.LOWEST;

	private final Proxifier proxifier;
	private final TypeFinder finder;
	private final Converters converters;
	private final ParameterNameProvider nameProvider;
	private final Evaluator evaluator;
	private final String originalUri;
	private final EncodingHandler encodingHandler;

	public DefaultRouteBuilder(Proxifier proxifier, TypeFinder finder, Converters converters, ParameterNameProvider nameProvider, 
			Evaluator evaluator, String uri, EncodingHandler encodingHandler) {
		this.proxifier = proxifier;
		this.finder = finder;
		this.converters = converters;
		this.nameProvider = nameProvider;
		this.evaluator = evaluator;
		this.originalUri = uri;
		this.encodingHandler = encodingHandler;
	}

	public class DefaultParameterControlBuilder implements ParameterControlBuilder {
		private final Map<String, String> parameters = new HashMap<>();
		private String name;

		private DefaultParameterControlBuilder withParameter(String name) {
			this.name = name;
			return this;
		}

		@Override
		public DefaultRouteBuilder ofType(Class<?> type) {
			parameters.put(name, regexFor(type));
			return DefaultRouteBuilder.this;
		}

		private String regexFor(Class<?> type) {
			if (NUMERIC_TYPES.contains(type)) {
				return "-?\\d+";
			} else if (CHARACTER_TYPES.contains(type)){
				return ".";
			} else if (DECIMAL_TYPES.contains(type)) {
				return "-?\\d*\\.?\\d+";
			} else if (BOOLEAN_TYPES.contains(type)) {
				return "true|false";
			} else if (Enum.class.isAssignableFrom(type)) {
				return Joiner.on("|").join(type.getEnumConstants());
			}
			return "[^/]+";
		}

		@Override
		public DefaultRouteBuilder matching(String regex) {
			parameters.put(name, regex);
			return DefaultRouteBuilder.this;
		}

		private ParametersControl build() {
			return new DefaultParametersControl(originalUri, parameters, converters, evaluator,encodingHandler);
		}
	}

	@Override
	public DefaultParameterControlBuilder withParameter(String name) {
		return builder.withParameter(name);
	}

	@Override
	public <T> T is(final Class<T> type) {
		MethodInvocation<T> handler = new MethodInvocation<T>() {
			@Override
			public Object intercept(Object proxy, Method method, Object[] args, SuperMethod superMethod) {
				boolean alreadySetTheStrategy = !strategy.getClass().equals(NoStrategy.class);
				if (alreadySetTheStrategy) {
					// the virtual machine might be invoking the finalize
					return null;
				}
				is(type, method);
				return null;
			}
		};
		return proxifier.proxify(type, handler);
	}

	@Override
	public void is(Class<?> type, Method method) {
		addParametersInfo(method);
		ControllerMethod controllerMethod = DefaultControllerMethod.instanceFor(type, method);
		Parameter[] parameterNames = nameProvider.parametersFor(method);
		this.strategy = getRouteStrategy(controllerMethod, parameterNames);

		logger.info(String.format("%-50s%s -> %10s", originalUri,
				this.supportedMethods.isEmpty() ? "[ALL]" : this.supportedMethods, method));
	}

	/**
	 * Override this method to change the default Route implementation
	 * @param controllerMethod The ControllerMethod
	 * @param parameterNames parameters of the method
	 * @return Route representation
	 */
	protected Route getRouteStrategy(ControllerMethod controllerMethod, Parameter[] parameterNames) {
		return new FixedMethodStrategy(originalUri, controllerMethod, this.supportedMethods, builder.build(), priority, parameterNames);
	}

	private void addParametersInfo(Method method) {
		String[] parameters = StringUtils.extractParameters(originalUri);
		Map<String, Class<?>> types = finder.getParameterTypes(method, sanitize(parameters));
		for (Entry<String, Class<?>> entry : types.entrySet()) {
			if (!builder.parameters.containsKey(entry.getKey())) {
				builder.withParameter(entry.getKey()).ofType(entry.getValue());
			}
		}
		for (String parameter : parameters) {
			String[] split = parameter.split(":");
			if (split.length >= 2 && !builder.parameters.containsKey(parameter)) {
				builder.withParameter(parameter).matching(split[1]);
			}
		}
	}

	private static String[] sanitize(String[] parameters) {
		String[] sanitized = new String[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			sanitized[i] = parameters[i].replaceAll("(\\:.*|\\*)$", "");
		}
		return sanitized;
	}

	/**
	 * Accepts also this http method request. If this method is not invoked, any
	 * http method is supported, otherwise all parameters passed are supported.
	 *
	 * @param method
	 * @return
	 */
	@Override
	public DefaultRouteBuilder with(HttpMethod method) {
		this.supportedMethods.add(method);
		return this;
	}

	@Override
	public DefaultRouteBuilder with(Set<HttpMethod> methods) {
		this.supportedMethods.addAll(methods);
		return this;
	}

	/**
	 * Changes Route priority
	 *
	 * @param priority
	 * @return
	 */
	@Override
	public DefaultRouteBuilder withPriority(int priority) {
		this.priority = priority;
		return this;
	}

	@Override
	public Route build() {
		if (strategy instanceof NoStrategy) {
			throw new IllegalRouteException("You have created a route, but did not specify any method to be invoked: "
					+ originalUri);
		}
		return strategy;
	}

	@Override
	public String toString() {
		if (supportedMethods.isEmpty()) {
			return String.format("<< Route: %s => %s >>", originalUri, this.strategy.toString());
		}
		return String.format("<< Route: %s %s=> %s >>", originalUri, supportedMethods, this.strategy.toString());
	}

}
