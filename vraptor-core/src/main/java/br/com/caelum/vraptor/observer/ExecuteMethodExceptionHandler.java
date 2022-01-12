package br.com.caelum.vraptor.observer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Throwables;

import br.com.caelum.vraptor.core.ReflectionProviderException;
import br.com.caelum.vraptor.interceptor.ApplicationLogicException;
import br.com.caelum.vraptor.validator.ValidationException;

/**
 * Handles exceptions thrown by a controller method
 *
 * @author Chico Sokol
 */
public class ExecuteMethodExceptionHandler {
	//private final static Logger log = getLogger(ExecuteMethodExceptionHandler.class);
	private static final Logger log = LogManager.getLogger(ExecuteMethodExceptionHandler.class);
	
	public void handle(Exception exception) {
		if (exception instanceof ReflectionProviderException) {
			throwIfNotValidationException(exception, exception.getCause());
		}
		throwIfNotValidationException(exception, exception);
	}

	private void throwIfNotValidationException(Throwable original, Throwable alternativeCause) {
		Throwable cause = original.getCause();

		if (original instanceof ValidationException || cause instanceof ValidationException) {
			// fine... already parsed
			log.trace("swallowing {}", cause);
		} else {
			Throwables.propagateIfPossible(alternativeCause);
			throw new ApplicationLogicException(alternativeCause);
		}
	}
}
