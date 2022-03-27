/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.common.iteration;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * An CloseableIteration that returns the bag union of the results of a number of CloseableIterations. 'Bag union' means
 * that the UnionIteration does not filter duplicate objects.
 */
public class UnionIteration<E, X extends Exception> extends LookAheadIteration<E, X> {

	private final CloseableIteration<E, X> delegate;

	private UnionIteration() {
		delegate = null;
	}

	/**
	 * Creates a new UnionIteration that returns the bag union of the results of a number of CloseableIterations.
	 *
	 * @param args The CloseableIterations containing the elements to iterate over.
	 */
	@SafeVarargs
	public UnionIteration(CloseableIteration<? extends E, X>... args) {
		this(Arrays.asList(args));
	}

	/**
	 * Creates a new UnionIteration that returns the bag union of the results of a number of CloseableIterations.
	 *
	 * @param args The CloseableIterations containing the elements to iterate over.
	 * @deprecated Use {@link UnionIteration#getInstance(List)} instead;
	 */
	@Deprecated(since = "4.0.0", forRemoval = true)
	public UnionIteration(Iterable<? extends CloseableIteration<? extends E, X>> args) {
		this.delegate = new ArbitrarySizeUnionIteration<>(args);
	}

	public static <E, X extends Exception> CloseableIteration<E, X> getInstance(
			List<? extends CloseableIteration<? extends E, X>> args) {
		return new ArbitrarySizeUnionIteration<>(args);
	}

	public static <E, X extends Exception> CloseableIteration<E, X> getInstance(
			CloseableIteration<? extends E, X> leftIteration,
			CloseableIteration<? extends E, X> rightIteration) {

		if (rightIteration instanceof EmptyIteration) {
			return (CloseableIteration<E, X>) leftIteration;
		} else if (leftIteration instanceof EmptyIteration) {
			return (CloseableIteration<E, X>) rightIteration;
		} else {
			return new DualUnionIteration<>(leftIteration, rightIteration);
		}
	}

	@Override
	public E getNextElement() throws X {
		if (delegate.hasNext()) {
			return delegate.next();
		}
		return null;
	}

	@Override
	public void handleClose() throws X {
		if (delegate != null) {
			delegate.close();
		}
	}

	private static class ArbitrarySizeUnionIteration<E, X extends Exception> extends UnionIteration<E, X> {

		private final Iterator<? extends CloseableIteration<? extends E, X>> argIter;

		private CloseableIteration<? extends E, X> currentIter;

		private ArbitrarySizeUnionIteration(List<? extends CloseableIteration<? extends E, X>> args) {
			super();
			argIter = args.iterator();
		}

		private ArbitrarySizeUnionIteration(Iterable<? extends CloseableIteration<? extends E, X>> args) {
			super();
			argIter = args.iterator();
		}

		@Override
		public E getNextElement() throws X {
			if (isClosed()) {
				return null;
			}

			while (true) {

				if (currentIter != null) {
					if (currentIter.hasNext()) {
						return currentIter.next();
					} else {
						currentIter.close();
					}
				}

				if (argIter.hasNext()) {
					currentIter = argIter.next();
				} else {
					// All elements have been returned
					return null;
				}
			}
		}

		@Override
		public void handleClose() throws X {
			try {
				// Close this iteration, this will prevent lookAhead() from calling
				// getNextElement() again
				super.handleClose();
			} finally {
				try {
					List<Throwable> collectedExceptions = null;
					while (argIter.hasNext()) {
						try {
							argIter.next().close();
						} catch (Throwable e) {
							if (collectedExceptions == null) {
								collectedExceptions = new ArrayList<>();
							}
							collectedExceptions.add(e);
						}
					}
					if (collectedExceptions != null && !collectedExceptions.isEmpty()) {
						throw new UndeclaredThrowableException(collectedExceptions.get(0));
					}
				} finally {
					if (currentIter != null) {
						currentIter.close();
					}
				}
			}
		}

	}

	private static class DualUnionIteration<E, X extends Exception> extends UnionIteration<E, X> {

		private CloseableIteration<? extends E, X> iteration1;
		private final CloseableIteration<? extends E, X> iteration2;

		public DualUnionIteration(CloseableIteration<? extends E, X> iteration1,
				CloseableIteration<? extends E, X> iteration2) {
			super();
			this.iteration1 = iteration1;
			this.iteration2 = iteration2;
		}

		@Override
		public E getNextElement() throws X {
			if (iteration1 == null) {
				if (iteration2.hasNext()) {
					return iteration2.next();
				} else {
					iteration2.close();
				}
			} else {
				if (iteration1.hasNext()) {
					return iteration1.next();
				} else if (iteration2.hasNext()) {
					iteration1.close();
					iteration1 = null;
					return iteration2.next();
				} else {
					iteration1.close();
					iteration1 = null;
					iteration2.close();
				}
			}

			return null;
		}

		@Override
		public void handleClose() throws X {
			try {
				if (iteration1 != null) {
					iteration1.close();
				}
			} finally {
				iteration2.close();
			}
		}
	}

	private static class SingleUnionIteration<E, X extends Exception> extends UnionIteration<E, X> {

		private final CloseableIteration<? extends E, X> iteration;

		public SingleUnionIteration(CloseableIteration<? extends E, X> iteration) {
			super();
			this.iteration = iteration;
		}

		@Override
		public E getNextElement() throws X {
			if (iteration.hasNext()) {
				return iteration.next();
			} else {
				iteration.close();
			}
			return null;
		}

		@Override
		public void handleClose() throws X {
			iteration.close();
		}
	}

}
