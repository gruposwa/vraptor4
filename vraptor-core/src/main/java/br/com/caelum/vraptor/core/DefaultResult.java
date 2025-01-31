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

package br.com.caelum.vraptor.core;


import static java.util.Collections.unmodifiableMap;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import br.com.caelum.vraptor.Result;
import br.com.caelum.vraptor.View;
import br.com.caelum.vraptor.interceptor.TypeNameExtractor;
import br.com.caelum.vraptor.ioc.Container;
import br.com.caelum.vraptor.validator.Messages;

/**
 * A basic implementation of a Result
 * @author guilherme silveira
 */
@RequestScoped
public class DefaultResult extends AbstractResult {
	private static final Logger logger = LogManager.getLogger(DefaultResult.class);
	//private static final Logger logger = LoggerFactory.getLogger(DefaultResult.class);

	private final HttpServletRequest request;
	private final Container container;
	private final ExceptionMapper exceptions;
	private final TypeNameExtractor extractor;
	private final Messages messages;
	
	private Map<String, Object> includedAttributes;
	private boolean responseCommitted;
	

	/** 
	 * @deprecated CDI eyes only
	 */
	protected DefaultResult() {
		this(null, null, null, null, null);
	}

	@Inject
	public DefaultResult(HttpServletRequest request, Container container, ExceptionMapper exceptions, TypeNameExtractor extractor,
			Messages messages) {
		this.request = request;
		this.container = container;
		this.extractor = extractor;
		this.includedAttributes = new HashMap<>();
		this.exceptions = exceptions;
		this.messages = messages;
	}
	
	@Override
	public <T extends View> T use(Class<T> view) {
		
		messages.assertAbsenceOfErrors();
	    
		responseCommitted = true;
		return container.instanceFor(view);
	}
	
	@Override
	public Result on(Class<? extends Exception> exception) {
		return exceptions.record(exception);
	}

	@Override
	public Result include(String key, Object value) {
		logger.debug("including attribute {}: {}", key, value);
		
		includedAttributes.put(key, value);
		request.setAttribute(key, value);
		return this;
	}

	@Override
	public boolean used() {
		return responseCommitted;
	}

	@Override
	public Map<String, Object> included() {
		return unmodifiableMap(includedAttributes);
	}

	@Override
	public Result include(Object value) {
		if(value == null) {
			return this;
		}
		
		String key = extractor.nameFor(value.getClass());
		return include(key, value);
	}
	
}
